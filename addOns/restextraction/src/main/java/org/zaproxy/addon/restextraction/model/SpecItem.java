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

import java.util.Objects;

public final class SpecItem {
    private final SpecItemKind kind;
    private final HttpMethod method;
    private final String mediaType;
    private final ParameterSpec parameter;

    private SpecItem(
            SpecItemKind kind, HttpMethod method, String mediaType, ParameterSpec parameter) {
        this.kind = kind;
        this.method = Objects.requireNonNull(method, "method");
        if (method == HttpMethod.OTHER) throw new SpecValidationException("method");
        this.mediaType = mediaType;
        this.parameter = parameter;
    }

    public static SpecItem method(HttpMethod method) {
        return new SpecItem(SpecItemKind.METHOD, method, "", null);
    }

    public static SpecItem contentType(HttpMethod method, String mediaType) {
        String type = MediaType.normalise(mediaType);
        if (!MediaType.isValid(type)) throw new SpecValidationException("mediaType");
        return new SpecItem(SpecItemKind.CONTENT_TYPE, method, type, null);
    }

    public static SpecItem parameter(HttpMethod method, ParameterSpec p) {
        Objects.requireNonNull(p, "parameter");
        SpecItemKind kind =
                switch (p.getLocation()) {
                    case PATH -> SpecItemKind.PATH_VARIABLE;
                    case QUERY -> SpecItemKind.QUERY_PARAMETER;
                    case BODY -> SpecItemKind.BODY_FIELD;
                    case HEADER -> SpecItemKind.HEADER;
                };
        return new SpecItem(kind, method, "", p);
    }

    public SpecItemKind getKind() {
        return kind;
    }

    public HttpMethod getMethod() {
        return method;
    }

    public String getMediaType() {
        return mediaType;
    }

    public ParameterSpec getParameter() {
        return parameter;
    }

    boolean sameKey(SpecItem other) {
        if (method != other.method || kind != other.kind) return false;
        if (kind == SpecItemKind.METHOD) return true;
        if (kind == SpecItemKind.CONTENT_TYPE) return mediaType.equals(other.mediaType);
        return kind == SpecItemKind.HEADER
                ? parameter.getName().equalsIgnoreCase(other.parameter.getName())
                : parameter.getName().equals(other.parameter.getName());
    }
}
