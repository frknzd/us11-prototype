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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.zaproxy.addon.restextraction.ScenarioSession.argumentsOf;
import static org.zaproxy.addon.restextraction.ScenarioSession.urlsOf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zaproxy.addon.restextraction.model.ApiNamespace;
import org.zaproxy.addon.restextraction.model.EndpointReport;
import org.zaproxy.zap.testutils.TestUtils;

/** Scenario tests SC3, SC4, SC9 and SC13: several namespaces, endpoints and a base path. */
class NamespaceScenarioTest extends TestUtils {

    private static final String PLACEHOLDER = "https://jsonplaceholder.typicode.com";
    private static final String JUICE_SHOP = "http://localhost:3000";

    private ScenarioSession session;

    @BeforeEach
    void setUp() {
        mockMessages(new ExtensionRestExtraction());
        session = new ScenarioSession();
    }

    // SC3

    @Test
    void shouldKeepCallsOfTwoNamespacesApart() {
        // Given
        ApiNamespace placeholder =
                session.startedNamespace(PLACEHOLDER, "/posts/[POST_ID]/comments");
        ApiNamespace juiceShop = session.startedNamespace(JUICE_SHOP, "/api/Products/[ID]");

        // When
        session.send("GET", PLACEHOLDER + "/posts/1/comments", 200);
        session.send("GET", JUICE_SHOP + "/api/Products/6", 200);
        session.send("GET", JUICE_SHOP + "/rest/basket/1", 200);
        session.send("GET", PLACEHOLDER + "/posts/2/comments", 200);
        session.send("GET", JUICE_SHOP + "/api/Products/24", 200);
        session.send("GET", JUICE_SHOP + "/posts/3/comments", 200);
        session.send("GET", PLACEHOLDER + "/api/Products/9", 200);

        // Then
        assertThat(argumentsOf(session.endpoint(placeholder, 0), "POST_ID"), contains("1", "2"));
        assertThat(argumentsOf(session.endpoint(juiceShop, 0), "ID"), contains("6", "24"));
    }

    // SC4

    @Test
    void shouldPutEveryCallUnderItsOwnEndpoint() {
        // Given
        ApiNamespace namespace =
                session.startedNamespace(
                        PLACEHOLDER, "/posts/[POST_ID]/comments", "/users/[USER_ID]", "/albums");

        // When
        session.send("GET", PLACEHOLDER + "/users/7", 200);
        session.send("GET", PLACEHOLDER + "/posts/1/comments", 200);
        session.send("GET", PLACEHOLDER + "/albums", 200);
        session.send("POST", PLACEHOLDER + "/albums", 201);
        session.send("GET", PLACEHOLDER + "/users/8", 200);
        session.send("GET", PLACEHOLDER + "/todos/1", 200);

        // Then
        assertThat(argumentsOf(session.endpoint(namespace, 0), "POST_ID"), contains("1"));
        assertThat(argumentsOf(session.endpoint(namespace, 1), "USER_ID"), contains("7", "8"));
        assertThat(session.endpoint(namespace, 2).getCalls(), hasSize(2));
    }

    // SC9

    @Test
    void shouldPutCallUnderBothEndpointsWhenPatternsOverlap() {
        // Given
        ApiNamespace namespace =
                session.startedNamespace(PLACEHOLDER, "/posts/[POST_ID]", "/posts/latest");

        // When
        session.send("GET", PLACEHOLDER + "/posts/latest", 200);
        session.send("GET", PLACEHOLDER + "/posts/1223", 200);

        // Then
        assertThat(
                argumentsOf(session.endpoint(namespace, 0), "POST_ID"), contains("latest", "1223"));
        assertThat(urlsOf(session.endpoint(namespace, 1)), contains(PLACEHOLDER + "/posts/latest"));
    }

    // SC13

    @Test
    void shouldOnlyRecordCallsUnderBasePathOfSameOrigin() {
        // Given
        ApiNamespace namespace = session.startedNamespace("https://h/api/v1", "/posts/[ID]");

        // When
        session.send("GET", "https://h/api/v1/posts/7", 200);
        session.send("GET", "https://h/api/v1x/posts/7", 200);
        session.send("GET", "http://h/api/v1/posts/7", 200);
        session.send("GET", "https://H:443/api/v1/posts/7/", 200);

        // Then
        EndpointReport posts = session.endpoint(namespace, 0);
        assertThat(posts.getCalls(), hasSize(2));
        assertThat(argumentsOf(posts, "ID"), contains("7", "7"));
        assertThat(
                urlsOf(posts),
                contains("https://h/api/v1/posts/7", "https://H:443/api/v1/posts/7/"));
    }

    @Test
    void shouldRecordNothingForNamespaceWithoutMatchingTraffic() {
        // Given
        ApiNamespace namespace = session.startedNamespace(PLACEHOLDER, "/posts/[POST_ID]");

        // When
        session.send("GET", JUICE_SHOP + "/posts/1", 200);
        session.send("GET", "https://jsonplaceholder.typicode.com.evil.com/posts/1", 200);

        // Then
        assertThat(session.endpoint(namespace, 0).getCalls(), is(empty()));
    }
}
