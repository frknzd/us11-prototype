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

import java.time.Instant;
import java.util.Objects;

/**
 * One request and its response as they went over the wire. It is the input of the domain: the
 * listener makes one for every response, so nothing in the model has to know ZAP's message classes.
 * It can't be changed after it is made.
 */
public class ApiCall {
    private final HttpMethod method;
    private final String url;
    private final int statusCode;
    private final String responseBody;
    private final Instant time;
    private final String responseHeaders;

    /**
     * @param url the full URL, still percent-encoded. A query is dropped, it is not part of the
     *     endpoint.
     * @param responseHeaders the status line and headers of the response as text
     * @param responseBody the body as text, already cut or marked by the listener, empty if none
     * @throws NullPointerException if an argument is null, with the name of that argument
     */
    public ApiCall(
            HttpMethod method,
            String url,
            int statusCode,
            String responseHeaders,
            String responseBody) {
        this.method = Objects.requireNonNull(method, "method");

        // Split the URL at the first '?' to remove the query string, if present.
        this.url = Objects.requireNonNull(url, "url").split("\\?", 2)[0];
        this.statusCode = statusCode;
        this.responseHeaders = Objects.requireNonNull(responseHeaders, "responseHeaders");
        this.responseBody = Objects.requireNonNull(responseBody, "responseBody");
        // The moment we saw the call, not the moment ZAP sent the request.
        this.time = Instant.now();
    }

    public HttpMethod getMethod() {
        return method;
    }

    /** The URL without the query, still percent-encoded. */
    public String getUrl() {
        return url;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseHeaders() {
        return responseHeaders;
    }

    public String getResponseBody() {
        return responseBody;
    }

    /** The moment the add-on saw the response. */
    public Instant getTime() {
        return time;
    }
}
