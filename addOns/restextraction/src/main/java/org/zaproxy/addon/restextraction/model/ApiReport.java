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

/**
 * What one namespace had recorded when the report was made, with a report for each of its
 * endpoints. It provides a snapshot for the panel and the writer to read.
 */
public final class ApiReport {

    private final String baseUrl;
    private final List<EndpointReport> endpoints;

    /**
     * @param baseUrl the base URL of the namespace, like {@code https://api.example.com/v1}
     * @param endpoints the report of each endpoint, in the order the endpoints were added
     * @throws NullPointerException if an argument or one of the endpoint reports is null
     */
    public ApiReport(String baseUrl, List<EndpointReport> endpoints) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(endpoints, "endpoints")
                .forEach(endpoint -> Objects.requireNonNull(endpoint, "endpoints"));
        this.endpoints = List.copyOf(endpoints);
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    /** Returns the endpoint reports in the order the endpoints were added. It can't be changed. */
    public List<EndpointReport> getEndpoints() {
        return endpoints;
    }
}
