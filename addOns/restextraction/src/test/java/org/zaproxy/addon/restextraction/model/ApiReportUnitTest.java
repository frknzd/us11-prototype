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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit test for {@link ApiReport}. */
class ApiReportUnitTest {

    private static final String BASE_URL = "https://jsonplaceholder.typicode.com";

    private static EndpointReport endpointReport(String template) {
        return new EndpointReport(template, Map.of(), List.of());
    }

    @Test
    void shouldReturnBaseUrlPassedToConstructor() {
        // Given
        ApiReport report = new ApiReport(BASE_URL, List.of());
        // When / Then
        assertThat(report.getBaseUrl(), is(equalTo(BASE_URL)));
    }

    @Test
    void shouldReturnEndpointsInGivenOrder() {
        // Given
        EndpointReport comments = endpointReport("/posts/[POST_ID]/comments");
        EndpointReport users = endpointReport("/users/[USER_ID]");
        ApiReport report = new ApiReport(BASE_URL, List.of(comments, users));
        // When / Then
        assertThat(report.getEndpoints(), contains(comments, users));
    }

    @Test
    void shouldAcceptNamespaceWithoutEndpoints() {
        // Given
        ApiReport report = new ApiReport(BASE_URL, new ArrayList<>());
        // When / Then
        assertThat(report.getEndpoints(), is(empty()));
    }

    // RP1: can't be modified

    @Test
    void shouldNotAllowChangingEndpoints() {
        // Given
        ApiReport report = new ApiReport(BASE_URL, List.of());
        // When
        List<EndpointReport> endpoints = report.getEndpoints();
        EndpointReport users = endpointReport("/users/[USER_ID]");
        // Then
        assertThrows(UnsupportedOperationException.class, () -> endpoints.add(users));
    }

    @Test
    void shouldNotChangeWhenGivenListChangesLater() {
        // Given
        List<EndpointReport> endpoints = new ArrayList<>();
        endpoints.add(endpointReport("/posts/[POST_ID]/comments"));
        ApiReport report = new ApiReport(BASE_URL, endpoints);

        // When
        endpoints.add(endpointReport("/users/[USER_ID]"));

        // Then
        assertThat(report.getEndpoints().size(), is(1));
    }

    @Test
    void shouldThrowWhenBaseUrlIsNull() {
        // When
        NullPointerException e =
                assertThrows(NullPointerException.class, () -> new ApiReport(null, List.of()));
        // Then
        assertThat(e.getMessage(), is(equalTo("baseUrl")));
    }

    @Test
    void shouldThrowWhenEndpointsAreNull() {
        // When
        NullPointerException e =
                assertThrows(NullPointerException.class, () -> new ApiReport(BASE_URL, null));
        // Then
        assertThat(e.getMessage(), is(equalTo("endpoints")));
    }

    @Test
    void shouldThrowWhenOneOfTheEndpointsIsNull() {
        // Given
        List<EndpointReport> endpoints = new ArrayList<>();
        endpoints.add(endpointReport("/users/[USER_ID]"));
        endpoints.add(null);
        // When
        NullPointerException e =
                assertThrows(NullPointerException.class, () -> new ApiReport(BASE_URL, endpoints));
        // Then
        assertThat(e.getMessage(), is(equalTo("endpoints")));
    }
}
