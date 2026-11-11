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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Tokenized JSON field selector. Wildcards consume one step, never a regex. */
public final class FieldName {
    private final List<Part> parts;

    private FieldName(List<Part> parts) {
        this.parts = List.copyOf(parts);
    }

    public static FieldName parse(String text) {
        Objects.requireNonNull(text, "text");
        if (text.isEmpty() || !text.equals(text.strip())) throw problem(1);
        List<Part> parts = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int start = i;
            StringBuilder key = new StringBuilder();
            while (i < text.length()) {
                char c = text.charAt(i);
                if (c == '\\') {
                    if (++i == text.length() || ".[]{}\\".indexOf(text.charAt(i)) < 0)
                        throw problem(i + 1);
                    key.append(text.charAt(i++));
                } else if (".[]{}".indexOf(c) >= 0) break;
                else {
                    key.append(c);
                    i++;
                }
            }
            if (!key.isEmpty()) parts.add(new Part(0, key.toString()));
            else if (!(start == 0 && text.startsWith("[]"))) throw problem(i + 1);
            while (i < text.length() && (text.charAt(i) == '[' || text.charAt(i) == '{')) {
                char c = text.charAt(i++);
                if (i == text.length() || text.charAt(i++) != (c == '[' ? ']' : '}'))
                    throw problem(i);
                parts.add(new Part(c == '[' ? 1 : 2, ""));
            }
            if (i == text.length()) break;
            if (text.charAt(i++) != '.' || i == text.length()) throw problem(i);
        }
        return new FieldName(parts);
    }

    public boolean matches(String argumentName) {
        FieldName other;
        try {
            other = parse(argumentName);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (parts.size() != other.parts.size()) return false;
        for (int i = 0; i < parts.size(); i++) {
            Part p = parts.get(i), a = other.parts.get(i);
            if (p.kind == 2) {
                if (a.kind != 0) return false;
            } else if (!p.equals(a)) return false;
        }
        return true;
    }

    public boolean isWildcard() {
        return parts.stream().anyMatch(p -> p.kind != 0);
    }

    public boolean isMoreSpecificThan(FieldName other) {
        for (int i = 0; i < Math.min(parts.size(), other.parts.size()); i++) {
            Part a = parts.get(i), b = other.parts.get(i);
            if (!a.equals(b)) return a.kind == 0 && b.kind == 2;
        }
        return false;
    }

    private static SpecValidationException problem(int position) {
        return new SpecValidationException("field", Integer.toString(position));
    }

    private record Part(int kind, String key) {}
}
