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

import java.util.List;
import java.util.Objects;

/** Immutable wire-level request, captured before any parsing. */
public final class RequestData {
    private final String query;
    private final List<Header> headers;
    private final String contentType;
    private final String body;
    private final BodyState bodyState;

    public RequestData(
            String query,
            List<Header> headers,
            String contentType,
            String body,
            BodyState bodyState) {
        this.query = Objects.requireNonNull(query, "query");
        this.headers = List.copyOf(Objects.requireNonNull(headers, "headers"));
        this.contentType = Objects.requireNonNull(contentType, "contentType");
        this.body = Objects.requireNonNull(body, "body");
        this.bodyState = Objects.requireNonNull(bodyState, "bodyState");
    }

    public static RequestData none() {
        return new RequestData("", List.of(), "", "", BodyState.NONE);
    }

    public String getQuery() {
        return query;
    }

    public List<Header> getHeaders() {
        return headers;
    }

    public String getContentType() {
        return contentType;
    }

    public String getBody() {
        return body;
    }

    public BodyState getBodyState() {
        return bodyState;
    }

    public boolean hasBody() {
        return !body.isEmpty();
    }
}
