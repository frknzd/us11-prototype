/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2026 The ZAP Development Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.zaproxy.addon.restextraction.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/** Strict RFC 8259 scanner. Iterative frames avoid recursion even for hostile nesting. */
final class JsonReader {
    private final String text;
    private final int maxDepth;
    private final List<Argument> arguments = new ArrayList<>();
    private final Deque<Frame> frames = new ArrayDeque<>();
    private int pos;
    private boolean tooDeep;

    JsonReader(String text, int maxDepth) {
        this.text = Objects.requireNonNull(text, "text");
        this.maxDepth = maxDepth;
    }

    List<Argument> read() {
        if (pos < text.length() && text.charAt(pos) == '\ufeff') pos++;
        value("", 0);
        while (!frames.isEmpty()) {
            whitespace();
            Frame f = frames.peek();
            if (f.object) {
                switch (f.state) {
                    case 0, 4 -> {
                        if (peek('}')) {
                            if (f.state == 4) throw error("trailing comma");
                            pos++;
                            frames.pop();
                        } else {
                            f.key = string();
                            f.state = 1;
                        }
                    }
                    case 1 -> {
                        expect(':');
                        f.state = 2;
                    }
                    case 2 -> {
                        f.state = 3;
                        String path =
                                f.depth >= maxDepth
                                        ? ""
                                        : (f.depth == 0
                                                ? escape(f.key)
                                                : f.path + "." + escape(f.key));
                        value(path, f.depth + 1);
                    }
                    case 3 -> {
                        if (peek('}')) {
                            pos++;
                            frames.pop();
                        } else {
                            expect(',');
                            f.state = 4;
                        }
                    }
                        // Frame states are private and assigned only by these transitions.
                }
            } else if (f.state == 0 || f.state == 2) {
                if (peek(']')) {
                    if (f.state == 2) throw error("trailing comma");
                    pos++;
                    frames.pop();
                } else {
                    f.state = 1;
                    value(f.depth >= maxDepth ? "" : f.path + "[]", f.depth + 1);
                }
            } else if (peek(']')) {
                pos++;
                frames.pop();
            } else {
                expect(',');
                f.state = 2;
            }
        }
        whitespace();
        if (pos != text.length()) throw error("trailing content");
        return List.copyOf(arguments);
    }

    boolean isTooDeep() {
        return tooDeep;
    }

    private void value(String path, int depth) {
        whitespace();
        if (pos == text.length()) throw error("expected value");
        char c = text.charAt(pos);
        JsonKind kind;
        String result;
        if (c == '{' || c == '[') {
            pos++;
            kind = c == '{' ? JsonKind.OBJECT : JsonKind.ARRAY;
            result = "";
            frames.push(new Frame(c == '{', path, depth));
        } else if (c == '"') {
            result = string();
            kind = JsonKind.STRING;
        } else if (c == 't') {
            literal("true");
            result = "true";
            kind = JsonKind.BOOLEAN;
        } else if (c == 'f') {
            literal("false");
            result = "false";
            kind = JsonKind.BOOLEAN;
        } else if (c == 'n') {
            literal("null");
            result = "null";
            kind = JsonKind.NULL;
        } else {
            result = number();
            kind = JsonKind.NUMBER;
        }
        if (depth > maxDepth) tooDeep = true;
        else if (!(depth == 0 && (kind == JsonKind.OBJECT || kind == JsonKind.ARRAY))
                && arguments.size() <= 200) {
            arguments.add(
                    new Argument(
                            depth == 0 ? "$" : path, ArgumentLocation.BODY, result, kind, false));
        }
    }

    private String string() {
        expect('"');
        StringBuilder value = new StringBuilder();
        while (pos < text.length()) {
            char c = text.charAt(pos++);
            if (c == '"') return value.toString();
            if (c < 0x20) throw error("control character in string");
            if (c != '\\') {
                value.append(c);
                continue;
            }
            if (pos == text.length()) throw error("unfinished escape");
            char escaped = text.charAt(pos++);
            switch (escaped) {
                case '"', '\\', '/' -> value.append(escaped);
                case 'b' -> value.append('\b');
                case 'f' -> value.append('\f');
                case 'n' -> value.append('\n');
                case 'r' -> value.append('\r');
                case 't' -> value.append('\t');
                case 'u' -> {
                    int code = 0;
                    for (int i = 0; i < 4; i++) {
                        if (pos == text.length()) throw error("unfinished unicode escape");
                        int hex = hex(text.charAt(pos++));
                        if (hex < 0) throw error("invalid unicode escape");
                        code = code * 16 + hex;
                    }
                    value.append((char) code);
                }
                default -> throw error("invalid escape");
            }
        }
        throw error("unclosed string");
    }

    private String number() {
        int start = pos;
        if (peek('-')) pos++;
        if (peek('0')) pos++;
        else {
            if (pos == text.length() || text.charAt(pos) < '1' || text.charAt(pos) > '9')
                throw error("invalid number");
            digits();
        }
        if (peek('.')) {
            pos++;
            requireDigits();
        }
        if (peek('e') || peek('E')) {
            pos++;
            if (peek('+') || peek('-')) pos++;
            requireDigits();
        }
        return text.substring(start, pos);
    }

    private void requireDigits() {
        int start = pos;
        digits();
        if (start == pos) throw error("expected digit");
    }

    private void digits() {
        while (pos < text.length() && text.charAt(pos) >= '0' && text.charAt(pos) <= '9') pos++;
    }

    private void literal(String value) {
        if (!text.startsWith(value, pos)) throw error("invalid literal");
        pos += value.length();
    }

    private void expect(char c) {
        whitespace();
        if (!peek(c)) throw error("expected " + c);
        pos++;
    }

    private boolean peek(char c) {
        return pos < text.length() && text.charAt(pos) == c;
    }

    private void whitespace() {
        while (pos < text.length() && " \t\r\n".indexOf(text.charAt(pos)) >= 0) pos++;
    }

    private IllegalArgumentException error(String reason) {
        return new IllegalArgumentException(reason + " at position " + (pos + 1));
    }

    static int hex(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    }

    private static String escape(String key) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (".[]{}\\".indexOf(c) >= 0) escaped.append('\\');
            escaped.append(c);
        }
        return escaped.toString();
    }

    private static final class Frame {
        private final boolean object;
        private final String path;
        private final int depth;
        private int state;
        private String key;

        private Frame(boolean object, String path, int depth) {
            this.object = object;
            this.path = path;
            this.depth = depth;
        }
    }
}
