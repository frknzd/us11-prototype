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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Unit test for {@link RestExtractor}. */
class RestExtractorUnitTest {

    private static final String BASE_URL = "https://jsonplaceholder.typicode.com";
    private static final String TEMPLATE = "/posts/[POST_ID]/comments";
    private static final String COMMENTS_URL = BASE_URL + "/posts/1223/comments";

    // RX1, RX2: adding and delegating

    @Test
    void shouldReturnNamespaceThatTakesPartInRecording() {
        // Given
        RestExtractor extractor = new RestExtractor();
        ExtractionObserver observer = mock(ExtractionObserver.class);
        extractor.addObserver(observer);

        // When
        ApiNamespace namespace = extractor.addNamespace(BASE_URL);
        namespace.addEndpoint(TEMPLATE);
        namespace.start();
        extractor.record(call(COMMENTS_URL));

        // Then
        assertThat(namespace.getBaseUrl(), is(equalTo(BASE_URL)));
        verify(observer).callRecorded(namespace);
    }

    @Test
    void shouldPassAddEndpointOnToNamespace() {
        // Given
        RestExtractor extractor = new RestExtractor();
        ApiNamespace namespace = mock(ApiNamespace.class);
        Endpoint endpoint = new Endpoint(TEMPLATE);
        when(namespace.addEndpoint(TEMPLATE)).thenReturn(endpoint);

        // When
        Endpoint added = extractor.addEndpoint(namespace, TEMPLATE);

        // Then
        assertThat(added, is(sameInstance(endpoint)));
    }

    @Test
    void shouldPassStartAndStopOnToNamespace() {
        // Given
        RestExtractor extractor = new RestExtractor();
        ApiNamespace namespace = mock(ApiNamespace.class);

        // When
        extractor.startExtraction(namespace);
        // Then
        verify(namespace).start();
        verify(namespace, never()).stop();

        // When
        extractor.stopExtraction(namespace);
        // Then
        verify(namespace).stop();
    }

    @Test
    void shouldGetReportFromNamespace() {
        // Given
        RestExtractor extractor = new RestExtractor();
        ApiNamespace namespace = mock(ApiNamespace.class);
        ApiReport report = new ApiReport(BASE_URL, List.of());
        // When
        when(namespace.createReport()).thenReturn(report);

        // Then
        assertThat(extractor.getReport(namespace), is(sameInstance(report)));
    }

    // RX12, RX14: removing a namespace

    @Test
    void shouldStopRemovedNamespaceAndRecordNothingForIt() {
        // Given
        RestExtractor extractor = new RestExtractor();
        ApiNamespace namespace = started(extractor, BASE_URL, TEMPLATE);
        ExtractionObserver observer = mock(ExtractionObserver.class);
        extractor.addObserver(observer);

        // When
        extractor.removeNamespace(namespace);
        extractor.record(call(COMMENTS_URL));

        // Then
        assertThat(namespace.isExtracting(), is(false));
        assertThat(callsOf(extractor, namespace), is(0));
        verifyNoInteractions(observer);
    }

    @Test
    void shouldAllowAddingBaseUrlOfRemovedNamespaceAgain() {
        // Given
        RestExtractor extractor = new RestExtractor();
        ApiNamespace removed = started(extractor, BASE_URL, TEMPLATE);
        extractor.removeNamespace(removed);

        // When
        ApiNamespace added = extractor.addNamespace(BASE_URL);

        // Then
        assertThat(added.getBaseUrl(), is(equalTo(BASE_URL)));
    }

    @Test
    void shouldKeepOtherNamespacesWhenOneIsRemoved() {
        // Given
        RestExtractor extractor = new RestExtractor();
        ApiNamespace removed = started(extractor, "http://localhost:3000", "/api/Products/[ID]");
        ApiNamespace kept = started(extractor, BASE_URL, TEMPLATE);

        // When
        extractor.removeNamespace(removed);
        extractor.record(call(COMMENTS_URL));

        // Then
        assertThat(callsOf(extractor, kept), is(1));
    }

    @Test
    void shouldDoNothingWhenNamespaceIsRemovedTwice() {
        // Given
        RestExtractor extractor = new RestExtractor();
        ApiNamespace namespace = extractor.addNamespace(BASE_URL);
        extractor.removeNamespace(namespace);

        // When / Then
        assertDoesNotThrow(() -> extractor.removeNamespace(namespace));
    }

    // RX13: remove and clear are passed on

    @Test
    void shouldPassRemoveEndpointOnToNamespace() {
        // Given
        RestExtractor extractor = new RestExtractor();
        ApiNamespace namespace = mock(ApiNamespace.class);

        // When
        extractor.removeEndpoint(namespace, TEMPLATE);

        // Then
        verify(namespace).removeEndpoint(TEMPLATE);
    }

    @Test
    void shouldPassClearOnToNamespace() {
        // Given
        RestExtractor extractor = new RestExtractor();
        ApiNamespace namespace = mock(ApiNamespace.class);

        // When
        extractor.clear(namespace);

        // Then
        verify(namespace).clear();
    }

    // RX15: clearing one endpoint is passed on

    @Test
    void shouldPassClearEndpointOnToNamespace() {
        // Given
        RestExtractor extractor = new RestExtractor();
        ApiNamespace namespace = mock(ApiNamespace.class);

        // When
        extractor.clearEndpoint(namespace, TEMPLATE);

        // Then
        verify(namespace).clearEndpoint(TEMPLATE);
    }

    // RX3 to RX5, RX8, RX9: recording and observers

    @Test
    void shouldTellEveryObserverOnceWhenCallIsRecorded() {
        // Given
        RestExtractor extractor = new RestExtractor();
        ApiNamespace namespace = started(extractor, BASE_URL, TEMPLATE);
        ExtractionObserver first = mock(ExtractionObserver.class);
        ExtractionObserver second = mock(ExtractionObserver.class);
        extractor.addObserver(first);
        extractor.addObserver(second);

        // When
        extractor.record(call(COMMENTS_URL));

        // Then
        verify(first).callRecorded(namespace);
        verify(second).callRecorded(namespace);
    }

    @Test
    void shouldNotTellObserversWhenCallMatchesNothing() {
        // Given
        RestExtractor extractor = new RestExtractor();
        // When
        started(extractor, BASE_URL, TEMPLATE);
        ExtractionObserver observer = mock(ExtractionObserver.class);
        extractor.addObserver(observer);

        extractor.record(call(BASE_URL + "/users/1"));

        // Then
        verifyNoInteractions(observer);
    }

    @Test
    void shouldNotTellObserversWhenNamespaceIsNotStarted() {
        // Given
        RestExtractor extractor = new RestExtractor();
        extractor.addNamespace(BASE_URL).addEndpoint(TEMPLATE);
        ExtractionObserver observer = mock(ExtractionObserver.class);
        extractor.addObserver(observer);

        // When
        extractor.record(call(COMMENTS_URL));

        // Then
        verifyNoInteractions(observer);
    }

    @Test
    void shouldReportOnlyTheNamespaceThatRecordedTheCall() {
        // Given
        RestExtractor extractor = new RestExtractor();
        ApiNamespace placeholder = started(extractor, BASE_URL, TEMPLATE);
        ApiNamespace other = started(extractor, "https://api.example.com", TEMPLATE);
        ExtractionObserver observer = mock(ExtractionObserver.class);
        extractor.addObserver(observer);

        // When
        extractor.record(call(COMMENTS_URL));

        // Then
        verify(observer).callRecorded(placeholder);
        verify(observer, never()).callRecorded(other);
        assertThat(callsOf(extractor, placeholder), is(1));
        assertThat(callsOf(extractor, other), is(0));
    }

    @Test
    void shouldRecordWithoutObservers() {
        // Given
        RestExtractor extractor = new RestExtractor();
        ApiNamespace namespace = started(extractor, BASE_URL, TEMPLATE);

        // When / Then
        assertDoesNotThrow(() -> extractor.record(call(COMMENTS_URL)));

        assertThat(callsOf(extractor, namespace), is(1));
    }

    @Test
    void shouldDoNothingWhenThereAreNoNamespaces() {
        // Given
        RestExtractor extractor = new RestExtractor();
        ExtractionObserver observer = mock(ExtractionObserver.class);
        extractor.addObserver(observer);

        // When / Then
        assertDoesNotThrow(() -> extractor.record(call(COMMENTS_URL)));

        verifyNoInteractions(observer);
    }

    @Test
    void shouldStillTellOtherObserversWhenOneThrows() {
        // Given
        RestExtractor extractor = new RestExtractor();
        ApiNamespace namespace = started(extractor, BASE_URL, TEMPLATE);
        ExtractionObserver broken = mock(ExtractionObserver.class);
        doThrow(new IllegalStateException("panel is gone")).when(broken).callRecorded(namespace);
        ExtractionObserver working = mock(ExtractionObserver.class);
        extractor.addObserver(broken);
        extractor.addObserver(working);

        // When / Then
        assertDoesNotThrow(() -> extractor.record(call(COMMENTS_URL)));

        verify(working).callRecorded(namespace);
        assertThat(callsOf(extractor, namespace), is(1));
    }

    // RX6, RX7: duplicate and nested base URLs

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://jsonplaceholder.typicode.com",
                "https://jsonplaceholder.typicode.com/",
                "https://jsonplaceholder.typicode.com:443",
                "HTTPS://JSONPlaceholder.typicode.com"
            })
    void shouldRefuseSameBaseUrlTwice(String sameBaseUrl) {
        // Given
        RestExtractor extractor = new RestExtractor();
        ApiNamespace namespace = started(extractor, BASE_URL, TEMPLATE);
        ExtractionObserver observer = mock(ExtractionObserver.class);
        extractor.addObserver(observer);

        // When
        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class, () -> extractor.addNamespace(sameBaseUrl));

        // Then
        assertThat(e.getMessage(), containsString("already added"));
        // When
        extractor.record(call(COMMENTS_URL));
        // Then
        verify(observer).callRecorded(namespace);
    }

    @Test
    void shouldNotAddNamespaceWhenBaseUrlIsInvalid() {
        // Given
        RestExtractor extractor = new RestExtractor();
        // When / Then
        assertThrows(IllegalArgumentException.class, () -> extractor.addNamespace("abc"));
    }

    @Test
    void shouldRecordInBothNamespacesWhenBaseUrlsAreNested() {
        // Given
        RestExtractor extractor = new RestExtractor();
        ApiNamespace api = started(extractor, "https://h.com/api", "/v1/posts/[POST_ID]");
        ApiNamespace version = started(extractor, "https://h.com/api/v1", "/posts/[POST_ID]");

        // When
        extractor.record(call("https://h.com/api/v1/posts/1223"));

        // Then
        assertThat(callsOf(extractor, api), is(1));
        assertThat(callsOf(extractor, version), is(1));
    }

    // RX10, RX11: the listener runs on several threads

    @Test
    void shouldCountEveryCallWhenRecordedFromSeveralThreads() throws Exception {
        // Given
        RestExtractor extractor = new RestExtractor();
        ApiNamespace namespace = started(extractor, BASE_URL, TEMPLATE);
        int threads = 8;
        int callsPerThread = 1000;

        // When
        runAtTheSameTime(
                threads,
                () -> {
                    for (int i = 0; i < callsPerThread; i++) {
                        extractor.record(call(COMMENTS_URL));
                    }
                });

        // Then
        EndpointReport report = extractor.getReport(namespace).getEndpoints().get(0);
        assertThat(report.getCalls(), hasSize(threads * callsPerThread));
        assertThat(report.getCallCounts().get(HttpMethod.GET), is(threads * callsPerThread));
    }

    @Test
    void shouldAllowAddingEndpointsWhileRecording() throws Exception {
        // Given
        RestExtractor extractor = new RestExtractor();
        ApiNamespace namespace = started(extractor, BASE_URL, TEMPLATE);
        int recordingThreads = 8;
        int endpointsToAdd = 50;
        ExecutorService adder = Executors.newSingleThreadExecutor();
        // When
        try {
            Future<?> adding =
                    adder.submit(
                            () -> {
                                for (int i = 0; i < endpointsToAdd; i++) {
                                    extractor.addEndpoint(namespace, "/albums/" + i + "/[ID]");
                                }
                            });

            runAtTheSameTime(
                    recordingThreads,
                    () -> {
                        for (int i = 0; i < 500; i++) {
                            extractor.record(call(COMMENTS_URL));
                        }
                    });
            adding.get(30, TimeUnit.SECONDS);
        } finally {
            adder.shutdownNow();
        }

        // Then
        ApiReport report = extractor.getReport(namespace);
        assertThat(report.getEndpoints(), hasSize(1 + endpointsToAdd));
        assertThat(report.getEndpoints().get(0).getCalls(), hasSize(recordingThreads * 500));
    }

    /** Runs the task on every thread at once and fails if one of them threw. */
    private static void runAtTheSameTime(int threads, Runnable task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    task.run();
                                    return null;
                                }));
            }
            start.countDown();
            for (Future<?> result : results) {
                result.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static ApiNamespace started(RestExtractor extractor, String baseUrl, String template) {
        ApiNamespace namespace = extractor.addNamespace(baseUrl);
        extractor.addEndpoint(namespace, template);
        extractor.startExtraction(namespace);
        return namespace;
    }

    private static int callsOf(RestExtractor extractor, ApiNamespace namespace) {
        return extractor.getReport(namespace).getEndpoints().get(0).getCalls().size();
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
