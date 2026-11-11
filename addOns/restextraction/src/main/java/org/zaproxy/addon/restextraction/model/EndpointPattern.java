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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The pattern of one endpoint as the developer typed it, like {@code /posts/[POST_ID]}. It is
 * relative to the namespace.
 */
public class EndpointPattern {
    // The name of the variable may contain letters, digits and '_' which must be wrapped between
    // '[' and ']' brackets as described in the User Story 10.
    private static final Pattern VARIABLE = Pattern.compile("\\[([A-Za-z0-9_]+)]");
    private static final Pattern ENCODED_BRACKET = Pattern.compile("%5[BbDd]");

    private final String template;
    private final List<PathSegment> segments;

    /**
     * Creates the pattern and its segments.
     *
     * @throws IllegalArgumentException if the template is not a valid pattern
     */
    public EndpointPattern(String template) {
        this.template = Objects.requireNonNull(template, "template");
        this.segments = parse(template);
    }

    /** Variable names in template order, shared by validation and the specification form. */
    public List<String> getVariableNames() {
        UrlPath path = new UrlPath(template);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < path.size(); i++) {
            String part = path.part(i);
            if (part.startsWith("[") && part.endsWith("]"))
                names.add(part.substring(1, part.length() - 1));
        }
        return List.copyOf(names);
    }

    public String getTemplate() {
        return template;
    }

    /**
     * Tells if the path fits this pattern.
     *
     * @param path the path after the base path, like {@code /posts/7}
     */
    public boolean matches(UrlPath path) {
        if (path.size() != segments.size()) {
            return false;
        }
        for (int i = 0; i < segments.size(); i++) {
            if (!segments.get(i).matches(path.part(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gives the value of every variable in the path, for example {@code POST_ID = 7}.
     *
     * @param path the path after the base path, like in {@link #matches}
     * @return the values by variable name in template order, empty if the path does not match
     */
    public Map<String, String> argumentsOf(UrlPath path) {
        if (!matches(path)) {
            return Map.of();
        }
        Map<String, String> args = new LinkedHashMap<>();
        for (int i = 0; i < segments.size(); i++) {
            segments.get(i).addArgument(path.part(i), args);
        }
        return args;
    }

    private static List<PathSegment> parse(String template) {
        checkTemplate(template);
        UrlPath path = readPath(template);
        List<PathSegment> segments = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < path.size(); i++) {
            segments.add(toSegment(template, path.part(i), names));
        }
        if (segments.isEmpty()) {
            throw invalid(template, "it has no path");
        }
        return List.copyOf(segments);
    }

    /** The checks on the template as typed, before it is split and decoded. */
    private static void checkTemplate(String template) {
        if (!template.startsWith("/")) {
            throw invalid(template, "it must start with /, type only the path");
        }
        if (template.contains("?") || template.contains("#")) {
            throw invalid(template, "it can't have a query or fragment (? or #)");
        }
        if (template.chars().anyMatch(Character::isWhitespace)) {
            throw invalid(
                    template,
                    "it can't have spaces. Use %20 in literal parts, variable names can only"
                            + " have letters, digits and _");
        }
        // Must be checked here: once decoded, %5BID%5D can't be told apart from the variable [ID].
        if (ENCODED_BRACKET.matcher(template).find()) {
            throw invalid(
                    template,
                    "it can't have an encoded bracket (%5B or %5D), brackets are only for"
                            + " variables");
        }
    }

    private static UrlPath readPath(String template) {
        try {
            return new UrlPath(template);
        } catch (IllegalArgumentException e) {
            throw invalid(template, "it has a broken % escape");
        }
    }

    /**
     * Turns one decoded part of the template into a variable or a literal segment.
     *
     * @param names the variable names seen so far, to find a name that is used twice
     */
    private static PathSegment toSegment(String template, String part, Set<String> names) {
        if (part.isEmpty()) {
            throw invalid(template, "it has an empty part (//)");
        }
        if (part.equals(".") || part.equals("..")) {
            throw invalid(template, "it can't have . or .. parts");
        }
        Matcher variable = VARIABLE.matcher(part);
        if (variable.matches()) {
            String name = variable.group(1);
            if (!names.add(name)) {
                throw invalid(template, "[" + name + "] is used more than once");
            }
            return new VariableSegment(name);
        }
        if (part.contains("[") || part.contains("]")) {
            throw invalid(template, "'" + part + "' is not a valid variable, use [NAME]");
        }
        return new LiteralSegment(part);
    }

    private static IllegalArgumentException invalid(String template, String reason) {
        return new IllegalArgumentException(
                "Invalid endpoint pattern '" + template + "': " + reason);
    }
}
