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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Unit test for {@link EndpointReport}. */
class EndpointReportUnitTest {

    private static final String TEMPLATE = "/posts/[POST_ID]/comments";

    private static RecordedCall recordedCall(HttpMethod method, String postId) {
        ApiCall call =
                new ApiCall(
                        method,
                        "https://jsonplaceholder.typicode.com/posts/" + postId + "/comments",
                        200,
                        "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n",
                        "[]");
        return new RecordedCall(call, Map.of("POST_ID", postId));
    }

    @Test
    void shouldReturnTemplatePassedToConstructor() {
        // Given
        EndpointReport report = new EndpointReport(TEMPLATE, Map.of(), List.of());
        // When / Then
        assertThat(report.getTemplate(), is(equalTo(TEMPLATE)));
    }

    @Test
    void shouldGiveCallCountsInMethodOrder() {
        // Given
        Map<HttpMethod, Integer> counts =
                Map.of(HttpMethod.OTHER, 1, HttpMethod.DELETE, 2, HttpMethod.GET, 3);
        EndpointReport report = new EndpointReport(TEMPLATE, counts, List.of());
        // When / Then
        assertThat(report.getCallCounts(), is(equalTo(counts)));
        assertThat(
                report.getCallCounts().keySet(),
                contains(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.OTHER));
    }

    @Test
    void shouldReturnCallsInGivenOrder() {
        // Given
        RecordedCall first = recordedCall(HttpMethod.GET, "1223");
        RecordedCall second = recordedCall(HttpMethod.POST, "abcd");
        EndpointReport report = new EndpointReport(TEMPLATE, Map.of(), List.of(first, second));
        // When / Then
        assertThat(report.getCalls(), contains(first, second));
    }

    @Test
    void shouldAcceptEmptyCountsAndCalls() {
        // Given
        EndpointReport report = new EndpointReport(TEMPLATE, new HashMap<>(), new ArrayList<>());
        // When / Then
        assertThat(report.getCallCounts(), is(anEmptyMap()));
        assertThat(report.getCalls(), is(empty()));
    }

    // RP1: can't be modified

    @Test
    void shouldNotAllowChangingCallCounts() {
        // Given
        EndpointReport report = new EndpointReport(TEMPLATE, Map.of(HttpMethod.GET, 3), List.of());
        // When
        Map<HttpMethod, Integer> counts = report.getCallCounts();
        // Then
        assertThrows(UnsupportedOperationException.class, () -> counts.put(HttpMethod.GET, 0));
    }

    @Test
    void shouldNotAllowChangingCalls() {
        // Given
        EndpointReport report = new EndpointReport(TEMPLATE, Map.of(), List.of());
        // When
        List<RecordedCall> calls = report.getCalls();
        RecordedCall call = recordedCall(HttpMethod.GET, "1223");
        // Then
        assertThrows(UnsupportedOperationException.class, () -> calls.add(call));
    }

    @Test
    void shouldNotChangeWhenGivenCollectionsChangeLater() {
        // Given
        Map<HttpMethod, Integer> counts = new HashMap<>();
        counts.put(HttpMethod.GET, 1);
        List<RecordedCall> calls = new ArrayList<>();
        calls.add(recordedCall(HttpMethod.GET, "1223"));
        EndpointReport report = new EndpointReport(TEMPLATE, counts, calls);

        counts.put(HttpMethod.GET, 2);
        // When
        calls.add(recordedCall(HttpMethod.GET, "abcd"));

        // Then
        assertThat(report.getCallCounts(), is(equalTo(Map.of(HttpMethod.GET, 1))));
        assertThat(report.getCalls().size(), is(1));
    }

    // ER13: the values seen per variable

    @Test
    void shouldCountValuesSeenInTheOrderTheyFirstCameIn() {
        // Given
        List<RecordedCall> calls =
                List.of(
                        recordedCall(HttpMethod.GET, "1"),
                        recordedCall(HttpMethod.GET, "1"),
                        recordedCall(HttpMethod.POST, "abcd"),
                        recordedCall(HttpMethod.GET, "1"));
        // When
        EndpointReport report = new EndpointReport(TEMPLATE, Map.of(), calls);
        // Then
        assertThat(report.getValuesSeen().keySet(), contains("POST_ID"));
        assertThat(report.getValuesSeen().get("POST_ID").keySet(), contains("1", "abcd"));
        assertThat(report.getValuesSeen().get("POST_ID"), is(equalTo(Map.of("1", 3, "abcd", 1))));
    }

    @Test
    void shouldKeepValuesOfEveryVariableApart() {
        // Given
        ApiCall call =
                new ApiCall(
                        HttpMethod.GET,
                        "https://jsonplaceholder.typicode.com/users/7/posts/7",
                        200,
                        "HTTP/1.1 200 OK\r\n\r\n",
                        "[]");
        Map<String, String> arguments = new LinkedHashMap<>();
        arguments.put("USER_ID", "7");
        arguments.put("POST_ID", "7");
        // When
        EndpointReport report =
                new EndpointReport(
                        "/users/[USER_ID]/posts/[POST_ID]",
                        Map.of(),
                        List.of(new RecordedCall(call, arguments)));
        // Then
        assertThat(report.getValuesSeen().keySet(), contains("USER_ID", "POST_ID"));
        assertThat(report.getValuesSeen().get("USER_ID"), is(equalTo(Map.of("7", 1))));
        assertThat(report.getValuesSeen().get("POST_ID"), is(equalTo(Map.of("7", 1))));
    }

    @Test
    void shouldHaveNoValuesSeenWithoutVariablesOrCalls() {
        // Given
        ApiCall call =
                new ApiCall(
                        HttpMethod.GET,
                        "https://jsonplaceholder.typicode.com/albums",
                        200,
                        "HTTP/1.1 200 OK\r\n\r\n",
                        "[]");
        // When
        EndpointReport withoutVariables =
                new EndpointReport("/albums", Map.of(), List.of(new RecordedCall(call, Map.of())));
        EndpointReport withoutCalls = new EndpointReport(TEMPLATE, Map.of(), List.of());
        // Then
        assertThat(withoutVariables.getValuesSeen(), is(anEmptyMap()));
        assertThat(withoutCalls.getValuesSeen(), is(anEmptyMap()));
    }

    @Test
    void shouldNotAllowChangingValuesSeen() {
        // Given
        EndpointReport report =
                new EndpointReport(
                        TEMPLATE, Map.of(), List.of(recordedCall(HttpMethod.GET, "1223")));
        // When
        Map<String, Map<String, Integer>> values = report.getValuesSeen();
        Map<String, Integer> postIds = values.get("POST_ID");
        // Then
        assertThrows(UnsupportedOperationException.class, () -> values.remove("POST_ID"));
        assertThrows(UnsupportedOperationException.class, () -> postIds.put("1223", 5));
    }

    @Test
    void shouldThrowWhenTemplateIsNull() {
        // When
        NullPointerException e =
                assertThrows(
                        NullPointerException.class,
                        () -> new EndpointReport(null, Map.of(), List.of()));
        // Then
        assertThat(e.getMessage(), is(equalTo("template")));
    }

    @Test
    void shouldThrowWhenCallCountsAreNull() {
        // When
        NullPointerException e =
                assertThrows(
                        NullPointerException.class,
                        () -> new EndpointReport(TEMPLATE, null, List.of()));
        // Then
        assertThat(e.getMessage(), is(equalTo("callCounts")));
    }

    @Test
    void shouldThrowWhenCallsAreNull() {
        // When
        NullPointerException e =
                assertThrows(
                        NullPointerException.class,
                        () -> new EndpointReport(TEMPLATE, Map.of(), null));
        // Then
        assertThat(e.getMessage(), is(equalTo("calls")));
    }

    @Test
    void shouldThrowWhenCountIsNull() {
        // Given
        Map<HttpMethod, Integer> counts = new HashMap<>();
        counts.put(HttpMethod.GET, null);
        // When
        NullPointerException e =
                assertThrows(
                        NullPointerException.class,
                        () -> new EndpointReport(TEMPLATE, counts, List.of()));
        // Then
        assertThat(e.getMessage(), is(equalTo("callCounts")));
    }

    @Test
    void shouldThrowWhenOneOfTheCallsIsNull() {
        // Given
        List<RecordedCall> calls = new ArrayList<>();
        calls.add(recordedCall(HttpMethod.GET, "1223"));
        calls.add(null);
        // When
        NullPointerException e =
                assertThrows(
                        NullPointerException.class,
                        () -> new EndpointReport(TEMPLATE, Map.of(), calls));
        // Then
        assertThat(e.getMessage(), is(equalTo("calls")));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void shouldThrowWhenCountIsBelowOne(int count) {
        // Given
        Map<HttpMethod, Integer> counts = Map.of(HttpMethod.GET, count);
        // When
        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new EndpointReport(TEMPLATE, counts, List.of()));
        // Then
        assertThat(e.getMessage(), containsString("GET"));
    }
}
