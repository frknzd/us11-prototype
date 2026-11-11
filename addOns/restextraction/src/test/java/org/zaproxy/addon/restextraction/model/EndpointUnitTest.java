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
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Unit test for {@link Endpoint}. */
class EndpointUnitTest {

    private static final String BASE_URL = "https://jsonplaceholder.typicode.com";
    private static final String TEMPLATE = "/posts/[POST_ID]/comments";

    // ER1, ER2: recording

    @Test
    void shouldRecordCallWhenPathMatches() {
        // Given
        Endpoint endpoint = new Endpoint(TEMPLATE);
        ApiCall call = call(HttpMethod.GET, "/posts/1223/comments", 200);

        // When
        boolean recorded = endpoint.record(call, path("/posts/1223/comments"));

        // Then
        assertThat(recorded, is(true));
        List<RecordedCall> calls = endpoint.createReport().getCalls();
        assertThat(calls, hasSize(1));
        assertThat(calls.get(0).getCall(), is(equalTo(call)));
        assertThat(calls.get(0).getArguments(), is(equalTo(Map.of("POST_ID", "1223"))));
    }

    @Test
    void shouldNotRecordCallWhenPathDoesNotMatch() {
        // Given
        Endpoint endpoint = new Endpoint(TEMPLATE);
        ApiCall call = call(HttpMethod.GET, "/users/1223", 200);

        // When
        boolean recorded = endpoint.record(call, path("/users/1223"));

        // Then
        assertThat(recorded, is(false));
        assertThat(endpoint.createReport().getCalls(), is(empty()));
    }

    // ER3 to ER6, ER9: counting per method

    @Test
    void shouldCountCallsPerMethodUsed() {
        // Given
        Endpoint endpoint = new Endpoint(TEMPLATE);
        // When
        record(endpoint, HttpMethod.GET, "/posts/1/comments");
        record(endpoint, HttpMethod.POST, "/posts/1/comments");
        record(endpoint, HttpMethod.GET, "/posts/2/comments");
        record(endpoint, HttpMethod.GET, "/posts/abcd/comments");

        // Then
        Map<HttpMethod, Integer> counts = endpoint.createReport().getCallCounts();

        assertThat(counts, is(equalTo(Map.of(HttpMethod.GET, 3, HttpMethod.POST, 1))));
    }

    @Test
    void shouldGiveEmptyReportWhenNothingWasRecorded() {
        // Given
        Endpoint endpoint = new Endpoint(TEMPLATE);

        // When
        EndpointReport report = endpoint.createReport();

        // Then
        assertThat(report.getCallCounts(), is(anEmptyMap()));
        assertThat(report.getCalls(), is(empty()));
    }

    @Test
    void shouldCountSameUrlTwiceWhenCalledTwice() {
        // Given
        Endpoint endpoint = new Endpoint(TEMPLATE);
        // When
        record(endpoint, HttpMethod.GET, "/posts/1223/comments");
        record(endpoint, HttpMethod.GET, "/posts/1223/comments");

        // Then
        EndpointReport report = endpoint.createReport();

        assertThat(report.getCallCounts(), is(equalTo(Map.of(HttpMethod.GET, 2))));
        assertThat(report.getCalls(), hasSize(2));
    }

    @Test
    void shouldRecordEveryMethodOnSamePath() {
        // Given
        Endpoint endpoint = new Endpoint(TEMPLATE);
        // When
        record(endpoint, HttpMethod.GET, "/posts/1223/comments");
        record(endpoint, HttpMethod.POST, "/posts/1223/comments");
        record(endpoint, HttpMethod.PUT, "/posts/1223/comments");
        record(endpoint, HttpMethod.DELETE, "/posts/1223/comments");

        // Then
        Map<HttpMethod, Integer> counts = endpoint.createReport().getCallCounts();

        assertThat(
                counts,
                is(
                        equalTo(
                                Map.of(
                                        HttpMethod.GET, 1,
                                        HttpMethod.POST, 1,
                                        HttpMethod.PUT, 1,
                                        HttpMethod.DELETE, 1))));
    }

    @Test
    void shouldCountUnknownMethodUnderOther() {
        // Given
        Endpoint endpoint = new Endpoint(TEMPLATE);
        // When
        record(endpoint, HttpMethod.OTHER, "/posts/1223/comments");

        // Then
        Map<HttpMethod, Integer> counts = endpoint.createReport().getCallCounts();

        assertThat(counts, is(equalTo(Map.of(HttpMethod.OTHER, 1))));
    }

    // ER7, ER8: what is kept of a call

    @Test
    void shouldKeepCallsInArrivalOrder() {
        // Given
        Endpoint endpoint = new Endpoint(TEMPLATE);
        ApiCall first = call(HttpMethod.GET, "/posts/3/comments", 200);
        ApiCall second = call(HttpMethod.POST, "/posts/1/comments", 201);
        ApiCall third = call(HttpMethod.GET, "/posts/2/comments", 200);
        // When
        endpoint.record(first, path("/posts/3/comments"));
        endpoint.record(second, path("/posts/1/comments"));
        endpoint.record(third, path("/posts/2/comments"));

        // Then
        List<RecordedCall> calls = endpoint.createReport().getCalls();

        assertThat(calls, hasSize(3));
        assertThat(calls.get(0).getCall(), is(equalTo(first)));
        assertThat(calls.get(1).getCall(), is(equalTo(second)));
        assertThat(calls.get(2).getCall(), is(equalTo(third)));
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 500})
    void shouldRecordCallWhenResponseIsAnError(int statusCode) {
        // Given
        Endpoint endpoint = new Endpoint(TEMPLATE);
        ApiCall call = call(HttpMethod.GET, "/posts/1223/comments", statusCode);

        // When
        boolean recorded = endpoint.record(call, path("/posts/1223/comments"));

        // Then
        assertThat(recorded, is(true));
        RecordedCall kept = endpoint.createReport().getCalls().get(0);
        assertThat(kept.getCall().getStatusCode(), is(statusCode));
    }

    @Test
    void shouldKeepDecodedArguments() {
        // Given
        Endpoint endpoint = new Endpoint("/users/[NAME]");
        ApiCall call = call(HttpMethod.GET, "/users/John%20Doe", 200);

        // When
        endpoint.record(call, path("/users/John%20Doe"));

        // Then
        RecordedCall kept = endpoint.createReport().getCalls().get(0);
        assertThat(kept.getArguments(), is(equalTo(Map.of("NAME", "John Doe"))));
    }

    // ER10, ER11: the report

    @Test
    void shouldNotChangeReportWhenCallIsRecordedLater() {
        // Given
        Endpoint endpoint = new Endpoint(TEMPLATE);
        // When
        record(endpoint, HttpMethod.GET, "/posts/1/comments");
        EndpointReport report = endpoint.createReport();

        record(endpoint, HttpMethod.GET, "/posts/2/comments");

        // Then
        assertThat(report.getCalls(), hasSize(1));
        assertThat(report.getCallCounts(), is(equalTo(Map.of(HttpMethod.GET, 1))));
        assertThat(endpoint.createReport().getCalls(), hasSize(2));
    }

    @Test
    void shouldGiveTemplateInReport() {
        // Given
        Endpoint endpoint = new Endpoint(TEMPLATE);
        // When / Then
        assertThat(endpoint.createReport().getTemplate(), is(equalTo(TEMPLATE)));
    }

    @Test
    void shouldGiveCountsInMethodOrder() {
        // Given
        Endpoint endpoint = new Endpoint(TEMPLATE);
        // When
        record(endpoint, HttpMethod.DELETE, "/posts/1/comments");
        record(endpoint, HttpMethod.GET, "/posts/1/comments");

        // Then
        Map<HttpMethod, Integer> counts = endpoint.createReport().getCallCounts();

        assertThat(counts.keySet(), contains(HttpMethod.GET, HttpMethod.DELETE));
    }

    @Test
    void shouldReturnTemplatePassedToConstructor() {
        // Given
        Endpoint endpoint = new Endpoint(TEMPLATE);
        // When / Then
        assertThat(endpoint.getTemplate(), is(equalTo(TEMPLATE)));
    }

    // ER12: clear

    @Test
    void shouldHaveNoCallsAfterClear() {
        // Given
        Endpoint endpoint = new Endpoint(TEMPLATE);
        record(endpoint, HttpMethod.GET, "/posts/1/comments");
        record(endpoint, HttpMethod.POST, "/posts/1/comments");

        // When
        endpoint.clear();

        // Then
        EndpointReport report = endpoint.createReport();
        assertThat(report.getCalls(), is(empty()));
        assertThat(report.getCallCounts(), is(anEmptyMap()));
        assertThat(report.getTemplate(), is(equalTo(TEMPLATE)));
    }

    @Test
    void shouldNotChangeEarlierReportWhenCleared() {
        // Given
        Endpoint endpoint = new Endpoint(TEMPLATE);
        record(endpoint, HttpMethod.GET, "/posts/1/comments");
        EndpointReport before = endpoint.createReport();

        // When
        endpoint.clear();

        // Then
        assertThat(before.getCalls(), hasSize(1));
    }

    // ER4: the constructor

    @Test
    void shouldThrowWhenTemplateIsInvalid() {
        // Given / When / Then
        assertThrows(IllegalArgumentException.class, () -> new Endpoint("posts/[POST_ID"));
    }

    private static void record(Endpoint endpoint, HttpMethod method, String path) {
        endpoint.record(call(method, path, 200), path(path));
    }

    private static ApiCall call(HttpMethod method, String path, int statusCode) {
        return new ApiCall(
                method,
                BASE_URL + path,
                statusCode,
                "HTTP/1.1 " + statusCode + "\r\nContent-Type: application/json\r\n\r\n",
                "[]");
    }

    private static UrlPath path(String path) {
        return new UrlPath(path);
    }
}
