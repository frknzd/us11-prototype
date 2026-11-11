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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Unit test for {@link ApiNamespace}. */
class ApiNamespaceUnitTest {

    private static final String BASE_URL = "https://jsonplaceholder.typicode.com";
    private static final String TEMPLATE = "/posts/[POST_ID]/comments";

    // NS12, NS13, NS22: the base URL

    @Test
    void shouldReturnBaseUrlPassedToConstructor() {
        // Given
        ApiNamespace namespace = new ApiNamespace(BASE_URL);
        // When / Then
        assertThat(namespace.getBaseUrl(), is(equalTo(BASE_URL)));
    }

    @Test
    void shouldStoreBaseUrlNormalised() {
        // Given
        ApiNamespace namespace = new ApiNamespace("HTTPS://Host.com:443/api/v1/");
        // When / Then
        assertThat(namespace.getBaseUrl(), is(equalTo("https://host.com/api/v1")));
    }

    @Test
    void shouldKeepPortInBaseUrlWhenNotDefault() {
        // Given
        ApiNamespace namespace = new ApiNamespace("https://host.com:8443/api");
        // When / Then
        assertThat(namespace.getBaseUrl(), is(equalTo("https://host.com:8443/api")));
    }

    @Test
    void shouldThrowWhenBaseUrlIsNull() {
        // When
        NullPointerException e =
                assertThrows(NullPointerException.class, () -> new ApiNamespace(null));
        // Then
        assertThat(e.getMessage(), is(equalTo("baseUrl")));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "abc",
                "jsonplaceholder.typicode.com",
                "/posts",
                "https:///posts",
                "https://host.com/api?version=1",
                "https://host.com/api#top",
                "https://host.com/my api"
            })
    void shouldThrowWhenBaseUrlIsInvalid(String baseUrl) {
        // When
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> new ApiNamespace(baseUrl));
        // Then
        assertThat(e.getMessage(), containsString("Invalid base URL '" + baseUrl + "'"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://host.com/posts/1/comments",
                "https://host.com:443/posts/1/comments"
            })
    void shouldCoverLowerCaseUrlsWhenBaseUrlIsInCapitals(String url) {
        // Given
        ApiNamespace namespace = started("HTTPS://Host.com");
        // When / Then
        assertThat(namespace.record(call(url)), is(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://h.com/api", "https://h.com/api/"})
    void shouldBehaveTheSameWithOrWithoutTrailingSlash(String baseUrl) {
        // Given
        ApiNamespace namespace = started(baseUrl);
        // When / Then
        assertThat(namespace.getBaseUrl(), is(equalTo("https://h.com/api")));
        assertThat(namespace.record(call("https://h.com/api/posts/1/comments")), is(true));
        assertThat(namespace.record(call("https://h.com/apiX/posts/1/comments")), is(false));
    }

    // NS1 to NS4: start and stop

    @Test
    void shouldNotBeExtractingWhenCreated() {
        // Given
        ApiNamespace namespace = new ApiNamespace(BASE_URL);
        // When / Then
        assertThat(namespace.isExtracting(), is(equalTo(false)));
    }

    @Test
    void shouldNotRecordWhenNotStarted() {
        // Given
        ApiNamespace namespace = new ApiNamespace(BASE_URL);
        namespace.addEndpoint(TEMPLATE);

        // When
        boolean recorded = namespace.record(call(BASE_URL + "/posts/1/comments"));

        // Then
        assertThat(recorded, is(false));
        assertThat(callsOf(namespace, 0), is(empty()));
    }

    @Test
    void shouldRecordMatchingCallAfterStart() {
        // Given
        ApiNamespace namespace = started(BASE_URL);
        ApiCall call = call(BASE_URL + "/posts/1/comments");

        // When
        boolean recorded = namespace.record(call);

        // Then
        assertThat(recorded, is(true));
        assertThat(callsOf(namespace, 0), hasSize(1));
        assertThat(callsOf(namespace, 0).get(0).getCall(), is(equalTo(call)));
    }

    @Test
    void shouldBeExtractingAfterStart() {
        // Given
        ApiNamespace namespace = new ApiNamespace(BASE_URL);
        // When
        namespace.start();
        // Then
        assertThat(namespace.isExtracting(), is(equalTo(true)));
    }

    @Test
    void shouldNotBeExtractingAfterStop() {
        // Given
        ApiNamespace namespace = new ApiNamespace(BASE_URL);
        namespace.start();
        // When
        namespace.stop();
        // Then
        assertThat(namespace.isExtracting(), is(equalTo(false)));
    }

    @Test
    void shouldBeExtractingAgainAfterRestart() {
        // Given
        ApiNamespace namespace = new ApiNamespace(BASE_URL);
        namespace.start();
        // When
        namespace.stop();
        namespace.start();
        // Then
        assertThat(namespace.isExtracting(), is(equalTo(true)));
    }

    @Test
    void shouldKeepRecordedCallsAfterStop() {
        // Given
        ApiNamespace namespace = started(BASE_URL);
        // When
        namespace.record(call(BASE_URL + "/posts/1/comments"));

        namespace.stop();
        boolean recorded = namespace.record(call(BASE_URL + "/posts/2/comments"));

        // Then
        assertThat(recorded, is(false));
        assertThat(callsOf(namespace, 0), hasSize(1));
    }

    @Test
    void shouldAllowStartTwiceAndStopWhenNotStarted() {
        // Given
        ApiNamespace namespace = new ApiNamespace(BASE_URL);

        // When / Then
        assertDoesNotThrow(namespace::stop);
        assertThat(namespace.isExtracting(), is(false));

        // When
        namespace.start();
        // Then
        assertDoesNotThrow(namespace::start);
        assertThat(namespace.isExtracting(), is(true));
    }

    // NS5 to NS11: origin and base path

    @Test
    void shouldNotCoverOtherHost() {
        // Given
        ApiNamespace namespace = started("https://h.com");
        // When / Then
        assertThat(namespace.record(call("https://other.com/posts/1/comments")), is(false));
    }

    @Test
    void shouldNotCoverHostThatOnlyStartsWithBaseHost() {
        // Given
        ApiNamespace namespace = started(BASE_URL);
        ApiCall call = call("https://jsonplaceholder.typicode.com.evil.com/posts/1/comments");
        // When / Then
        assertThat(namespace.record(call), is(false));
    }

    @Test
    void shouldNotCoverOtherPort() {
        // Given
        ApiNamespace namespace = started("https://h.com");
        // When / Then
        assertThat(namespace.record(call("https://h.com:8443/posts/1/comments")), is(false));
    }

    @Test
    void shouldCoverDefaultPortWrittenExplicitly() {
        // Given
        ApiNamespace namespace = started("https://h.com");
        // When / Then
        assertThat(namespace.record(call("https://h.com:443/posts/1/comments")), is(true));
    }

    @Test
    void shouldCoverDefaultHttpPortWrittenExplicitly() {
        // Given
        ApiNamespace namespace = started("http://h.com");
        // When / Then
        assertThat(namespace.record(call("http://h.com:80/posts/1/comments")), is(true));
    }

    @Test
    void shouldNotCoverOtherScheme() {
        // Given
        ApiNamespace namespace = started("https://h.com");
        // When / Then
        assertThat(namespace.record(call("http://h.com/posts/1/comments")), is(false));
    }

    @Test
    void shouldCoverHostWithOtherCase() {
        // Given
        ApiNamespace namespace = started(BASE_URL);
        ApiCall call = call("https://JSONPLACEHOLDER.typicode.com/posts/1/comments");
        // When / Then
        assertThat(namespace.record(call), is(true));
    }

    @Test
    void shouldGiveEndpointsOnlyThePathAfterTheBasePath() {
        // Given
        ApiNamespace namespace = started("https://h.com/api/v1");

        // When
        boolean recorded = namespace.record(call("https://h.com/api/v1/posts/1/comments"));

        // Then
        assertThat(recorded, is(true));
        assertThat(
                callsOf(namespace, 0).get(0).getPathArguments(),
                is(equalTo(Map.of("POST_ID", "1"))));
    }

    @Test
    void shouldNotCoverPathThatOnlyStartsWithBasePath() {
        // Given
        ApiNamespace namespace = started("https://h.com/api/v1");
        // When / Then
        assertThat(namespace.record(call("https://h.com/api/v1x/posts/1/comments")), is(false));
    }

    @Test
    void shouldNotCoverPathWithOtherCase() {
        // Given
        ApiNamespace namespace = started("https://h.com/api");
        // When / Then
        assertThat(namespace.record(call("https://h.com/API/posts/1/comments")), is(false));
    }

    @Test
    void shouldNotCoverPathOutsideBasePath() {
        // Given
        ApiNamespace namespace = started("https://h.com/api");
        // When / Then
        assertThat(namespace.record(call("https://h.com/posts/1/comments")), is(false));
    }

    // NS14 to NS19, NS21: endpoints

    @Test
    void shouldRecordCallWithQuery() {
        // Given
        ApiNamespace namespace = started(BASE_URL);
        ApiCall call = call(BASE_URL + "/posts/1/comments?_limit=5");
        // When / Then
        assertThat(namespace.record(call), is(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/posts/[POST_ID]/comments", "/posts/[POST_ID]/comments/"})
    void shouldRefuseSameTemplateTwice(String sameTemplate) {
        // Given
        ApiNamespace namespace = new ApiNamespace(BASE_URL);
        namespace.addEndpoint(TEMPLATE);

        // When
        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class, () -> namespace.addEndpoint(sameTemplate));

        // Then
        assertThat(e.getMessage(), containsString("already added"));
        assertThat(namespace.createReport().getEndpoints(), hasSize(1));
    }

    @Test
    void shouldNotAddEndpointWhenTemplateIsInvalid() {
        // Given
        ApiNamespace namespace = new ApiNamespace(BASE_URL);

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> namespace.addEndpoint("posts/[ID"));

        assertThat(namespace.createReport().getEndpoints(), is(empty()));
    }

    @Test
    void shouldRecordCallUnderEveryMatchingEndpoint() {
        // Given
        ApiNamespace namespace = new ApiNamespace(BASE_URL);
        namespace.addEndpoint("/posts/[ID]");
        namespace.addEndpoint("/posts/latest");
        namespace.start();

        // When
        boolean recorded = namespace.record(call(BASE_URL + "/posts/latest"));

        // Then
        assertThat(recorded, is(true));
        assertThat(callsOf(namespace, 0), hasSize(1));
        assertThat(callsOf(namespace, 1), hasSize(1));
    }

    @Test
    void shouldRecordWhenOnlySecondEndpointMatches() {
        // Given
        ApiNamespace namespace = new ApiNamespace(BASE_URL);
        namespace.addEndpoint("/users/[USER_ID]");
        namespace.addEndpoint(TEMPLATE);
        namespace.start();

        // When
        boolean recorded = namespace.record(call(BASE_URL + "/posts/1/comments"));

        // Then
        assertThat(recorded, is(true));
        assertThat(callsOf(namespace, 0), is(empty()));
        assertThat(callsOf(namespace, 1), hasSize(1));
    }

    @Test
    void shouldNotRecordWhenNoEndpointMatches() {
        // Given
        ApiNamespace namespace = started(BASE_URL);
        // When / Then
        assertThat(namespace.record(call(BASE_URL + "/users/1")), is(false));
    }

    @Test
    void shouldRecordForEndpointAddedWhileExtracting() {
        // Given
        ApiNamespace namespace = new ApiNamespace(BASE_URL);
        namespace.start();
        namespace.addEndpoint(TEMPLATE);

        // When
        boolean recorded = namespace.record(call(BASE_URL + "/posts/1/comments"));

        // Then
        assertThat(recorded, is(true));
    }

    @Test
    void shouldKeepArgumentsSeparateWhenTemplatesShareVariableName() {
        // Given
        ApiNamespace namespace = new ApiNamespace(BASE_URL);
        namespace.addEndpoint("/users/[ID]/posts/[POST_ID]");
        namespace.addEndpoint("/users/[USER_ID]/posts/[ID]");
        namespace.start();

        // When
        namespace.record(call(BASE_URL + "/users/1/posts/10"));

        // Then
        assertThat(
                callsOf(namespace, 0).get(0).getPathArguments(),
                is(equalTo(Map.of("ID", "1", "POST_ID", "10"))));
        assertThat(
                callsOf(namespace, 1).get(0).getPathArguments(),
                is(equalTo(Map.of("USER_ID", "1", "ID", "10"))));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://jsonplaceholder.typicode.com/posts/%zz/comments",
                "https://jsonplaceholder.typicode.com/posts/100%/comments",
                "not a url",
                "/posts/1/comments"
            })
    void shouldNotRecordUrlThatCanNotBeParsed(String url) {
        // Given
        ApiNamespace namespace = started(BASE_URL);
        ApiCall call = call(url);

        // When
        boolean recorded = assertDoesNotThrow(() -> namespace.record(call));

        // Then
        assertThat(recorded, is(false));
    }

    // NS23 to NS25, NS27: removing an endpoint

    @Test
    void shouldRemoveEndpointWithItsCalls() {
        // Given
        ApiNamespace namespace = started(BASE_URL);
        namespace.addEndpoint("/users/[USER_ID]");
        namespace.record(call(BASE_URL + "/posts/1/comments"));
        namespace.record(call(BASE_URL + "/users/7"));

        // When
        namespace.removeEndpoint(TEMPLATE);

        // Then
        List<EndpointReport> endpoints = namespace.createReport().getEndpoints();
        assertThat(endpoints, hasSize(1));
        assertThat(endpoints.get(0).getTemplate(), is(equalTo("/users/[USER_ID]")));
        assertThat(endpoints.get(0).getCalls(), hasSize(1));
    }

    @Test
    void shouldNotRecordForRemovedEndpoint() {
        // Given
        ApiNamespace namespace = started(BASE_URL);
        namespace.removeEndpoint(TEMPLATE);

        // When
        boolean recorded = namespace.record(call(BASE_URL + "/posts/1/comments"));

        // Then
        assertThat(recorded, is(false));
    }

    @Test
    void shouldRemoveEndpointWhenOnlyTrailingSlashDiffers() {
        // Given
        ApiNamespace namespace = started(BASE_URL);

        // When
        namespace.removeEndpoint(TEMPLATE + "/");

        // Then
        assertThat(namespace.createReport().getEndpoints(), is(empty()));
    }

    @Test
    void shouldDoNothingWhenRemovedEndpointIsNotThere() {
        // Given
        ApiNamespace namespace = started(BASE_URL);

        // When
        assertDoesNotThrow(() -> namespace.removeEndpoint("/albums"));

        // Then
        assertThat(namespace.createReport().getEndpoints(), hasSize(1));
    }

    @Test
    void shouldAllowAddingRemovedTemplateAgain() {
        // Given
        ApiNamespace namespace = started(BASE_URL);
        namespace.removeEndpoint(TEMPLATE);

        // When
        namespace.addEndpoint(TEMPLATE);

        // Then
        assertThat(namespace.createReport().getEndpoints(), hasSize(1));
    }

    @Test
    void shouldNotThrowWhenEndpointsAreRemovedWhileRecording() throws Exception {
        // Given
        ApiNamespace namespace = started(BASE_URL);
        for (int i = 0; i < 50; i++) {
            namespace.addEndpoint("/albums/" + i + "/[ID]");
        }
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<?>> recorders = new ArrayList<>();

        // When
        try {
            for (int t = 0; t < 8; t++) {
                recorders.add(
                        pool.submit(
                                () -> {
                                    for (int i = 0; i < 500; i++) {
                                        namespace.record(
                                                call(BASE_URL + "/albums/" + i % 50 + "/1"));
                                    }
                                }));
            }
            for (int i = 0; i < 50; i++) {
                namespace.removeEndpoint("/albums/" + i + "/[ID]");
            }

            // Then
            for (Future<?> recorder : recorders) {
                recorder.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(namespace.createReport().getEndpoints(), hasSize(1));
    }

    // NS26: clear

    @Test
    void shouldForgetCallsButKeepEndpointsAndStateWhenCleared() {
        // Given
        ApiNamespace namespace = started(BASE_URL);
        namespace.addEndpoint("/users/[USER_ID]");
        namespace.record(call(BASE_URL + "/posts/1/comments"));
        namespace.record(call(BASE_URL + "/users/7"));

        // When
        namespace.clear();

        // Then
        assertThat(callsOf(namespace, 0), is(empty()));
        assertThat(callsOf(namespace, 1), is(empty()));
        assertThat(namespace.createReport().getEndpoints(), hasSize(2));
        assertThat(namespace.isExtracting(), is(true));
    }

    @Test
    void shouldRecordAgainAfterClear() {
        // Given
        ApiNamespace namespace = started(BASE_URL);
        namespace.record(call(BASE_URL + "/posts/1/comments"));
        namespace.clear();

        // When
        boolean recorded = namespace.record(call(BASE_URL + "/posts/2/comments"));

        // Then
        assertThat(recorded, is(true));
        assertThat(callsOf(namespace, 0), hasSize(1));
    }

    // NS28, NS29: clear one endpoint

    @ParameterizedTest
    @ValueSource(strings = {"/users/[USER_ID]", "/users/[USER_ID]/"})
    void shouldForgetCallsOfOnlyThatEndpointWhenItIsCleared(String template) {
        // Given
        ApiNamespace namespace = started(BASE_URL);
        namespace.addEndpoint("/users/[USER_ID]");
        namespace.record(call(BASE_URL + "/posts/1/comments"));
        namespace.record(call(BASE_URL + "/users/7"));

        // When
        namespace.clearEndpoint(template);

        // Then
        assertThat(callsOf(namespace, 0), hasSize(1));
        assertThat(callsOf(namespace, 1), is(empty()));
        assertThat(namespace.createReport().getEndpoints(), hasSize(2));
        assertThat(namespace.isExtracting(), is(true));
        assertThat(namespace.record(call(BASE_URL + "/users/8")), is(true));
        assertThat(callsOf(namespace, 1), hasSize(1));
    }

    @Test
    void shouldDoNothingWhenClearedEndpointIsNotThere() {
        // Given
        ApiNamespace namespace = started(BASE_URL);
        namespace.record(call(BASE_URL + "/posts/1/comments"));

        // When
        namespace.clearEndpoint("/albums");

        // Then
        assertThat(callsOf(namespace, 0), hasSize(1));
    }

    // NS20: the report

    @Test
    void shouldGiveReportWithNormalisedBaseUrlAndEndpointsInOrder() {
        // Given
        ApiNamespace namespace = new ApiNamespace("https://Host.com:443/api/");
        namespace.addEndpoint("/users/[USER_ID]");
        namespace.addEndpoint(TEMPLATE);
        namespace.addEndpoint("/albums");

        // When
        ApiReport report = namespace.createReport();

        // Then
        assertThat(report.getBaseUrl(), is(equalTo("https://host.com/api")));
        assertThat(report.getEndpoints(), hasSize(3));
        assertThat(report.getEndpoints().get(0).getTemplate(), is(equalTo("/users/[USER_ID]")));
        assertThat(report.getEndpoints().get(1).getTemplate(), is(equalTo(TEMPLATE)));
        assertThat(report.getEndpoints().get(2).getTemplate(), is(equalTo("/albums")));
    }

    @Test
    void shouldReturnEndpointThatWasAdded() {
        // Given
        ApiNamespace namespace = new ApiNamespace(BASE_URL);
        // When
        Endpoint endpoint = namespace.addEndpoint(TEMPLATE);
        // Then
        assertThat(endpoint.getTemplate(), is(equalTo(TEMPLATE)));
    }

    /** A started namespace with the endpoint {@code /posts/[POST_ID]/comments}. */
    private static ApiNamespace started(String baseUrl) {
        ApiNamespace namespace = new ApiNamespace(baseUrl);
        namespace.addEndpoint(TEMPLATE);
        namespace.start();
        return namespace;
    }

    private static List<RecordedCall> callsOf(ApiNamespace namespace, int endpoint) {
        return namespace.createReport().getEndpoints().get(endpoint).getCalls();
    }

    private static ApiCall call(String url) {
        return new ApiCall(
                HttpMethod.GET,
                url,
                200,
                "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n",
                "[]");
    }
}
