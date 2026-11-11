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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** UI-independent literal/variable chips; final validation reuses the US10 pattern parser. */
public final class PatternDraft {
    private final List<String> parts = new ArrayList<>();
    private final List<String> names = new ArrayList<>();
    private final List<Boolean> variables = new ArrayList<>();
    private final boolean unusable;

    public PatternDraft(UrlPath path) {
        for (int i = 0; i < path.size(); i++) {
            parts.add(path.part(i));
            names.add("");
            variables.add(false);
        }
        unusable = parts.isEmpty() || parts.contains("");
        if (!unusable)
            for (int i = 0; i < size(); i++)
                if (!canBeLiteral(i)) {
                    variables.set(i, true);
                    names.set(i, defaultName(i));
                }
    }

    public int size() {
        return parts.size();
    }

    public String part(int index) {
        return parts.get(index);
    }

    public boolean isVariable(int index) {
        return variables.get(index);
    }

    public String name(int index) {
        return names.get(index);
    }

    public boolean canBeLiteral(int index) {
        String p = part(index);
        return !p.isEmpty()
                && !p.equals(".")
                && !p.equals("..")
                && p.indexOf('[') < 0
                && p.indexOf(']') < 0
                && p.indexOf('%') < 0
                && p.codePoints().noneMatch(Character::isWhitespace);
    }

    public void toggle(int index) {
        if (unusable)
            throw new SpecValidationException(parts.isEmpty() ? "emptyPath" : "emptyPart");
        if (isVariable(index)) {
            if (!canBeLiteral(index)) throw new SpecValidationException("lockedLiteral");
            variables.set(index, false);
        } else {
            variables.set(index, true);
            names.set(index, defaultName(index));
        }
    }

    public void rename(int index, String name) {
        if (name.isEmpty()
                || !name.chars()
                        .allMatch(
                                c ->
                                        c >= 'A' && c <= 'Z'
                                                || c >= 'a' && c <= 'z'
                                                || c >= '0' && c <= '9'
                                                || c == '_'))
            throw new SpecValidationException("variableName");
        names.set(index, name);
    }

    private String defaultName(int index) {
        StringBuilder clean = new StringBuilder();
        part(index)
                .codePoints()
                .forEach(
                        c ->
                                clean.append(
                                        c >= 'A' && c <= 'Z'
                                                        || c >= 'a' && c <= 'z'
                                                        || c >= '0' && c <= '9'
                                                        || c == '_'
                                                ? (char) c
                                                : '_'));
        String base = clean.toString(), candidate = base;
        int suffix = 2;
        while (used(index, candidate)) candidate = base + "_" + suffix++;
        return candidate;
    }

    private boolean used(int index, String candidate) {
        for (int i = 0; i < size(); i++)
            if (i != index && isVariable(i) && name(i).equals(candidate)) return true;
        return false;
    }

    public String toTemplate() {
        Optional<String> problem = problem();
        if (problem.isPresent()) throw new SpecValidationException("pattern", problem.get());
        return build();
    }

    public Optional<String> problem() {
        if (parts.isEmpty()) return Optional.of("restextraction.validation.emptyPath");
        if (parts.contains("")) return Optional.of("restextraction.validation.emptyPart");
        try {
            new EndpointPattern(build());
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.of(e.getMessage());
        }
    }

    private String build() {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < size(); i++)
            result.add(isVariable(i) ? "[" + name(i) + "]" : encode(part(i)));
        return "/" + String.join("/", result);
    }

    private static String encode(String part) {
        StringBuilder result = new StringBuilder();
        for (byte b : part.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 255;
            if (c >= 'A' && c <= 'Z'
                    || c >= 'a' && c <= 'z'
                    || c >= '0' && c <= '9'
                    || "-._~!$&'()*+,;=:@".indexOf(c) >= 0) result.append((char) c);
            else
                result.append('%')
                        .append("0123456789ABCDEF".charAt(c >>> 4))
                        .append("0123456789ABCDEF".charAt(c & 15));
        }
        return result.toString();
    }
}
