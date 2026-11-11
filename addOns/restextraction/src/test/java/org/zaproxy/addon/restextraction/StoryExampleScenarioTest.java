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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.zaproxy.addon.restextraction.ScenarioSession.argumentsOf;
import static org.zaproxy.addon.restextraction.ScenarioSession.bodyFor;
import static org.zaproxy.addon.restextraction.ScenarioSession.statusCodesOf;
import static org.zaproxy.addon.restextraction.ScenarioSession.urlsOf;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zaproxy.addon.restextraction.model.ApiNamespace;
import org.zaproxy.addon.restextraction.model.EndpointReport;
import org.zaproxy.addon.restextraction.model.HttpMethod;
import org.zaproxy.zap.testutils.TestUtils;

/** Scenario tests SC1, SC5 and SC6: the example of the user story, from listener to report. */
class StoryExampleScenarioTest extends TestUtils {

    private static final String BASE_URL = "https://jsonplaceholder.typicode.com";
    private static final String TEMPLATE = "/posts/[POST_ID]/comments";

    private ScenarioSession session;
    private ApiNamespace namespace;

    @BeforeEach
    void setUp() {
        mockMessages(new ExtensionRestExtraction());
        session = new ScenarioSession();
        namespace = session.startedNamespace(BASE_URL, TEMPLATE);
    }

    // SC1

    @Test
    void shouldReportStoryExample() {
        // When
        session.send("GET", BASE_URL + "/posts/1/comments", 200);
        session.send("GET", BASE_URL + "/posts/abcd/comments", 200);
        session.send("GET", BASE_URL + "/posts/1/comments", 200);
        session.send("POST", BASE_URL + "/posts/1/comments", 201);
        session.send("GET", BASE_URL + "/users/1", 200);

        // Then
        EndpointReport comments = session.endpoint(namespace, 0);
        assertThat(comments.getTemplate(), is(TEMPLATE));
        assertThat(
                comments.getCallCounts(),
                is(equalTo(Map.of(HttpMethod.GET, 3, HttpMethod.POST, 1))));
        assertThat(argumentsOf(comments, "POST_ID"), contains("1", "abcd", "1", "1"));
        assertThat(statusCodesOf(comments), contains(200, 200, 200, 201));
        assertThat(urlsOf(comments), everyItem(not(containsString("/users/1"))));
        assertThat(
                comments.getCalls().get(1).getCall().getResponseBody(),
                is(bodyFor("GET", BASE_URL + "/posts/abcd/comments")));
        assertThat(
                comments.getCalls().get(3).getCall().getResponseHeaders(),
                containsString("Content-Type: application/json"));
    }

    @Test
    void shouldWriteStoryExampleAsDocumentation() {
        // When
        session.send("GET", BASE_URL + "/posts/1/comments", 200);
        session.send("GET", BASE_URL + "/posts/abcd/comments", 200);
        session.send("GET", BASE_URL + "/posts/1/comments", 200);
        session.send("POST", BASE_URL + "/posts/1/comments", 201);
        session.send("GET", BASE_URL + "/users/1", 200);

        // Then
        String html = session.html(namespace);

        assertThat(html, containsString("<h2>" + BASE_URL + "</h2>"));
        assertThat(html, containsString("<h3>" + TEMPLATE + "</h3>"));
        assertThat(html, containsString("GET: 3, POST: 1"));
        assertThat(html, containsString("POST_ID = abcd"));
        assertThat(html, containsString("<b>POST</b>, status 201"));
        assertThat(html, not(containsString("/users/1")));
        assertThat(html, not(containsString("request")));
        // US11 includes the captured request line, but still excludes response bodies.
        assertThat(html, containsString("GET " + BASE_URL + "/posts/abcd/comments"));
        assertThat(
                session.shownBody(namespace, 0, 1),
                is("{\n  \"request\": \"GET " + BASE_URL + "/posts/abcd/comments\"\n}"));
    }

    // SC5

    @Test
    void shouldCountCallsWithQueryTogetherWithSamePath() {
        // When
        session.send("GET", BASE_URL + "/posts/1/comments?_limit=5", 200);
        session.send("GET", BASE_URL + "/posts/1/comments", 200);
        session.send("GET", BASE_URL + "/posts/1/comments?_page=2&_limit=5", 200);

        // Then
        EndpointReport comments = session.endpoint(namespace, 0);
        assertThat(comments.getCallCounts(), is(equalTo(Map.of(HttpMethod.GET, 3))));
        assertThat(argumentsOf(comments, "POST_ID"), contains("1", "1", "1"));
        assertThat(
                urlsOf(comments),
                contains(
                        BASE_URL + "/posts/1/comments",
                        BASE_URL + "/posts/1/comments",
                        BASE_URL + "/posts/1/comments"));
    }

    // SC6

    @Test
    void shouldReportPutAndDeleteWithTheirCounts() {
        // When
        session.send("PUT", BASE_URL + "/posts/1/comments", 200);
        session.send("DELETE", BASE_URL + "/posts/1/comments", 200);
        session.send("PUT", BASE_URL + "/posts/2/comments", 200);
        session.send("GET", BASE_URL + "/posts/2/comments", 200);

        // Then
        EndpointReport comments = session.endpoint(namespace, 0);
        assertThat(comments.getCalls(), hasSize(4));
        assertThat(
                comments.getCallCounts(),
                is(equalTo(Map.of(HttpMethod.GET, 1, HttpMethod.PUT, 2, HttpMethod.DELETE, 1))));
        assertThat(session.html(namespace), containsString("GET: 1, PUT: 2, DELETE: 1"));
    }
}
