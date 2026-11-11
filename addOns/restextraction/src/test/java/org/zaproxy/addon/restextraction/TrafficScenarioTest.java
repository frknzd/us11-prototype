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
package org.zaproxy.addon.restextraction;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.zaproxy.addon.restextraction.ScenarioSession.JSON;
import static org.zaproxy.addon.restextraction.ScenarioSession.statusCodesOf;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.parosproxy.paros.core.proxy.ProxyListener;
import org.parosproxy.paros.extension.ExtensionHook;
import org.parosproxy.paros.network.HttpMessage;
import org.zaproxy.addon.restextraction.model.ApiNamespace;
import org.zaproxy.addon.restextraction.model.EndpointReport;
import org.zaproxy.addon.restextraction.model.HttpMethod;
import org.zaproxy.zap.testutils.TestUtils;

/**
 * Scenario tests SC7, SC8, SC10, SC11 and SC14: unusual methods, failed calls, several threads,
 * large and binary bodies, and ZAP without a GUI.
 */
class TrafficScenarioTest extends TestUtils {

    private static final String BASE_URL = "https://jsonplaceholder.typicode.com";
    private static final String TEMPLATE = "/posts/[POST_ID]/comments";
    private static final String COMMENTS_URL = BASE_URL + "/posts/1/comments";

    private ScenarioSession session;
    private ApiNamespace namespace;

    @BeforeEach
    void setUp() {
        mockMessages(new ExtensionRestExtraction());
        session = new ScenarioSession();
        namespace = session.startedNamespace(BASE_URL, TEMPLATE);
    }

    // SC7

    @Test
    void shouldReportPatchAndCustomMethodAsOther() {
        // When
        session.send("PATCH", COMMENTS_URL, 200);
        session.send("PURGE", COMMENTS_URL, 200);
        session.send("PROPFIND", COMMENTS_URL, 207);

        // Then
        EndpointReport comments = session.endpoint(namespace, 0);
        assertThat(
                comments.getCallCounts(),
                is(equalTo(Map.of(HttpMethod.PATCH, 1, HttpMethod.OTHER, 2))));
        assertThat(session.html(namespace), containsString("PATCH: 1, OTHER: 2"));
    }

    // SC8

    @Test
    void shouldRecordFailedCallsWithTheirStatus() {
        // When
        session.send("GET", COMMENTS_URL, 200);
        session.send("GET", BASE_URL + "/posts/999999/comments", 404);
        session.send("POST", COMMENTS_URL, 500);

        // Then
        EndpointReport comments = session.endpoint(namespace, 0);
        assertThat(statusCodesOf(comments), contains(200, 404, 500));
        String html = session.html(namespace);
        assertThat(html, containsString("<b>GET</b>, status 404"));
        assertThat(html, containsString("<b>POST</b>, status 500"));
    }

    // SC10

    @Test
    void shouldCountExactlyWhenEightThreadsReplayStoryExample() throws Exception {
        // Given
        int threads = 8;
        int replays = 1000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        // When
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    for (int replay = 0; replay < replays; replay++) {
                                        sendStoryExample();
                                    }
                                    return null;
                                }));
            }
            start.countDown();
            for (Future<?> result : results) {
                result.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        // Then
        EndpointReport comments = session.endpoint(namespace, 0);
        assertThat(
                comments.getCallCounts(),
                is(
                        equalTo(
                                Map.of(
                                        HttpMethod.GET,
                                        3 * threads * replays,
                                        HttpMethod.POST,
                                        threads * replays))));
        assertThat(comments.getCalls(), hasSize(4 * threads * replays));
    }

    private void sendStoryExample() {
        session.send("GET", BASE_URL + "/posts/1/comments", 200);
        session.send("GET", BASE_URL + "/posts/abcd/comments", 200);
        session.send("GET", BASE_URL + "/posts/1/comments", 200);
        session.send("POST", BASE_URL + "/posts/1/comments", 201);
        session.send("GET", BASE_URL + "/users/1", 200);
    }

    // SC11

    @Test
    void shouldCutLargeBodyAndMarkBinaryBody() {
        // Given
        int maxBodyLength = 64 * 1024;
        // When
        session.send("GET", COMMENTS_URL, 200, JSON, "[" + "1,".repeat(40_000) + "1]");
        session.send("GET", COMMENTS_URL, 200, "image/png", "\u0089PNG\r\n\u001a\n");

        // Then
        EndpointReport comments = session.endpoint(namespace, 0);
        // When
        String large = comments.getCalls().get(0).getCall().getResponseBody();
        // Then
        assertThat(large, endsWith("[cut off after 64 KB]"));
        assertThat(large.length(), is(lessThan(maxBodyLength + 100)));
        assertThat(
                comments.getCalls().get(1).getCall().getResponseBody(), is("[binary, not shown]"));

        assertThat(session.shownBody(namespace, 0, 0), endsWith("[cut off after 64 KB]"));
        assertThat(session.shownBody(namespace, 0, 1), is("[binary, not shown]"));
    }

    // SC14: without a GUI no namespace can be added, so the story example only passes through

    @Test
    void shouldLetStoryExamplePassUnchangedWhenHookedWithoutView() {
        // Given
        ExtensionHook hook = mock(ExtensionHook.class);
        ExtensionRestExtraction extension =
                new ExtensionRestExtraction() {
                    @Override
                    protected boolean hasView() {
                        return false;
                    }
                };

        // When
        extension.hook(hook);

        // Then
        ArgumentCaptor<ProxyListener> hooked = ArgumentCaptor.forClass(ProxyListener.class);
        verify(hook).addProxyListener(hooked.capture());
        verify(hook, never()).getHookView();
        ProxyListener listener = hooked.getValue();
        List<HttpMessage> storyExample =
                List.of(
                        storyMessage("GET", "/posts/1/comments", 200),
                        storyMessage("GET", "/posts/abcd/comments", 200),
                        storyMessage("GET", "/posts/1/comments", 200),
                        storyMessage("POST", "/posts/1/comments", 201),
                        storyMessage("GET", "/users/1", 200));
        for (HttpMessage message : storyExample) {
            String request = message.getRequestHeader().toString();
            String response = message.getResponseHeader().toString();
            String body = message.getResponseBody().toString();

            assertThat(listener.onHttpRequestSend(message), is(true));
            assertThat(listener.onHttpResponseReceive(message), is(true));

            assertThat(message.getRequestHeader().toString(), is(request));
            assertThat(message.getResponseHeader().toString(), is(response));
            assertThat(message.getResponseBody().toString(), is(body));
        }
    }

    private static HttpMessage storyMessage(String method, String path, int status) {
        String url = BASE_URL + path;
        return ScenarioSession.message(
                method, url, status, JSON, ScenarioSession.bodyFor(method, url));
    }
}
