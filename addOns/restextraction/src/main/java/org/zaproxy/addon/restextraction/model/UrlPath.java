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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A URL path split into its decoded parts, so {@code /users/John%20Doe/} becomes {@code users} and
 * {@code John Doe}. Request paths, base paths and endpoint templates all go through this class, so
 * they follow the same rules and nobody has to split URLs again.
 *
 * <p>It only reads the path. Matching parts against {@code [NAME]} variables is done by {@link
 * EndpointPattern}. The parts never change, so one instance can be shared between threads.
 */
public final class UrlPath {

    private final List<String> parts;

    /**
     * Splits the path on {@code /} first and then decodes each part, so {@code %2F} stays inside
     * its part. One trailing slash is ignored, a {@code +} stays a {@code +} and case is kept.
     *
     * @param path the URL encoded path without the query section
     * @throws IllegalArgumentException if a part has a broken % escape, like {@code %zz}
     */
    public UrlPath(String path) {
        this(decodedParts(Objects.requireNonNull(path, "path")));
    }

    private UrlPath(List<String> parts) {
        this.parts = List.copyOf(parts);
    }

    /**
     * Checks if this path starts with the parts of the given prefix. Only whole parts count, so
     * {@code /apiv2/posts} does not start with {@code /api}.
     */
    public boolean startsWith(UrlPath prefix) {
        return prefix.size() <= size() && parts.subList(0, prefix.size()).equals(prefix.parts);
    }

    /**
     * Returns what is left after the prefix, so {@code /api/v1/posts/1} after {@code /api/v1} gives
     * {@code /posts/1}.
     *
     * @throws IllegalArgumentException if this path does not start with the prefix
     */
    public UrlPath after(UrlPath prefix) {
        if (!startsWith(prefix)) {
            throw new IllegalArgumentException("The path does not start with the prefix");
        }
        return new UrlPath(parts.subList(prefix.size(), size()));
    }

    /** Returns the number of parts. */
    public int size() {
        return parts.size();
    }

    /** Returns the decoded part at the given index, counting from 0. */
    public String part(int index) {
        return parts.get(index);
    }

    private static List<String> decodedParts(String path) {
        List<String> parts = new ArrayList<>();
        // Split first and decode after, so an encoded / (%2F) stays inside one part.
        for (String part : splitPath(removeTrailingSlash(path))) {
            try {
                parts.add(decodePart(part));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Invalid path '" + path + "': '" + part + "' has a broken % escape", e);
            }
        }
        return parts;
    }

    private static String removeTrailingSlash(String path) {
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private static List<String> splitPath(String path) {
        String pathWithoutLeadingSlash = path.startsWith("/") ? path.substring(1) : path;
        if (pathWithoutLeadingSlash.isEmpty()) {
            return List.of();
        }
        return List.of(pathWithoutLeadingSlash.split("/", -1));
    }

    /**
     * Decodes one part, like {@code hello%20world} to {@code hello world}.
     *
     * <p>URLDecoder would turn {@code +} into a space, which is only right for form data, so we
     * escape it first.
     *
     * @throws IllegalArgumentException if the part has a broken % escape
     */
    private static String decodePart(String part) {
        return URLDecoder.decode(part.replace("+", "%2B"), StandardCharsets.UTF_8);
    }
}
