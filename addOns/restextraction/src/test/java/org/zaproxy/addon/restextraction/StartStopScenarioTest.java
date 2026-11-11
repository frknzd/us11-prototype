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
import static org.hamcrest.Matchers.hasSize;
import static org.zaproxy.addon.restextraction.ScenarioSession.argumentsOf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zaproxy.addon.restextraction.model.ApiNamespace;
import org.zaproxy.addon.restextraction.model.EndpointReport;
import org.zaproxy.zap.testutils.TestUtils;

/** Scenario tests SC2 and SC12: what Start and Stop do to the recording, and what stays. */
class StartStopScenarioTest extends TestUtils {

    private static final String BASE_URL = "https://jsonplaceholder.typicode.com";
    private static final String TEMPLATE = "/posts/[POST_ID]/comments";

    private ScenarioSession session;

    @BeforeEach
    void setUp() {
        mockMessages(new ExtensionRestExtraction());
        session = new ScenarioSession();
    }

    // SC2

    @Test
    void shouldOnlyRecordCallsBetweenStartAndStop() {
        // Given
        ApiNamespace namespace = session.namespace(BASE_URL, TEMPLATE);
        // When
        session.send("GET", BASE_URL + "/posts/1/comments", 200);
        session.send("GET", BASE_URL + "/posts/2/comments", 200);

        session.start(namespace);
        session.send("GET", BASE_URL + "/posts/3/comments", 200);
        session.send("GET", BASE_URL + "/posts/4/comments", 200);

        session.stop(namespace);
        session.send("GET", BASE_URL + "/posts/5/comments", 200);
        session.send("GET", BASE_URL + "/posts/6/comments", 200);

        // Then
        EndpointReport comments = session.endpoint(namespace, 0);
        assertThat(argumentsOf(comments, "POST_ID"), contains("3", "4"));
    }

    @Test
    void shouldGoOnRecordingAfterSecondStart() {
        // Given
        ApiNamespace namespace = session.startedNamespace(BASE_URL, TEMPLATE);
        // When
        session.send("GET", BASE_URL + "/posts/1/comments", 200);
        session.stop(namespace);
        session.send("GET", BASE_URL + "/posts/2/comments", 200);

        session.start(namespace);
        session.send("GET", BASE_URL + "/posts/3/comments", 200);

        // Then
        EndpointReport comments = session.endpoint(namespace, 0);
        assertThat(argumentsOf(comments, "POST_ID"), contains("1", "3"));
    }

    // SC12: a new ZAP session has nothing to call here. That the add-on registers no session
    // listener is checked as EX6 in ExtensionRestExtractionUnitTest. This is the other half: what
    // was recorded stays for the whole run.

    @Test
    void shouldKeepNamespacesAndCallsForWholeRun() {
        // Given
        ApiNamespace first = session.startedNamespace(BASE_URL, TEMPLATE);
        // When
        session.send("GET", BASE_URL + "/posts/1/comments", 200);

        ApiNamespace second =
                session.startedNamespace("http://localhost:3000", "/api/Products/[ID]");
        session.send("GET", "http://localhost:3000/api/Products/7", 200);
        session.send("GET", BASE_URL + "/posts/2/comments", 200);

        // Then
        assertThat(session.endpoint(first, 0).getCalls(), hasSize(2));
        assertThat(session.endpoint(second, 0).getCalls(), hasSize(1));
    }
}
