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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zaproxy.addon.restextraction.model.ApiNamespace;
import org.zaproxy.addon.restextraction.model.ArgumentLocation;
import org.zaproxy.addon.restextraction.model.DecimalType;
import org.zaproxy.addon.restextraction.model.Header;
import org.zaproxy.addon.restextraction.model.HttpMethod;
import org.zaproxy.addon.restextraction.model.IntegerType;
import org.zaproxy.addon.restextraction.model.ParameterSpec;
import org.zaproxy.addon.restextraction.model.ParameterType;
import org.zaproxy.addon.restextraction.model.PatternDraft;
import org.zaproxy.addon.restextraction.model.SpecItem;
import org.zaproxy.addon.restextraction.model.StringType;
import org.zaproxy.addon.restextraction.model.UrlPath;
import org.zaproxy.addon.restextraction.model.Verdict;
import org.zaproxy.addon.restextraction.model.VerdictStatus;
import org.zaproxy.addon.restextraction.model.ViolationKind;
import org.zaproxy.addon.restextraction.model.WarningKind;
import org.zaproxy.zap.testutils.TestUtils;

/** Complete listener → domain → immutable report → documentation sessions, no external network. */
class VerificationScenarioTest extends TestUtils {
    private static final String BASE = "http://127.0.0.1:8092";
    private static final String TEMPLATE = "/posts/[POST_ID]/comments";
    private ScenarioSession session;
    private ApiNamespace namespace;

    @BeforeEach
    void setup() {
        mockMessages(new ExtensionRestExtraction());
        session = new ScenarioSession();
        namespace = session.startedNamespace(BASE, TEMPLATE);
    }

    private SpecItem parameter(
            HttpMethod method,
            ArgumentLocation location,
            String name,
            ParameterType type,
            boolean required) {
        return SpecItem.parameter(method, new ParameterSpec(location, name, type, required));
    }

    private void add(SpecItem item) {
        session.spec(namespace, TEMPLATE, item);
    }

    private void postSpec() {
        add(SpecItem.contentType(HttpMethod.POST, "application/json"));
        add(
                parameter(
                        HttpMethod.POST,
                        ArgumentLocation.PATH,
                        "POST_ID",
                        new IntegerType(BigInteger.ONE, BigInteger.valueOf(10000)),
                        false));
        add(parameter(HttpMethod.POST, ArgumentLocation.BODY, "title", new StringType(), true));
        add(
                parameter(
                        HttpMethod.POST,
                        ArgumentLocation.BODY,
                        "userId",
                        new IntegerType(null, null),
                        false));
    }

    private void send(String method, String path, String type, String body) {
        session.send(method, BASE + path, 200, List.of(), type, body);
    }

    private Verdict last() {
        var report = session.endpoint(namespace, 0);
        return report.getVerdict(report.getCalls().get(report.getCalls().size() - 1));
    }

    @Test
    void SC_V1_storyExample() {
        postSpec();
        send("POST", "/posts/1/comments", "application/json", "{\"title\":\"Hi\",\"userId\":3}");
        assertEquals(VerdictStatus.VALID, last().getStatus());
        assertEquals(3, last().getChecks().size());
        assertEquals(1, session.endpoint(namespace, 0).getValidCount());
    }

    @Test
    void SC_V2_orderedViolations() {
        postSpec();
        add(
                parameter(
                        HttpMethod.POST,
                        ArgumentLocation.QUERY,
                        "_limit",
                        new IntegerType(BigInteger.ONE, BigInteger.valueOf(100)),
                        false));
        send(
                "POST",
                "/posts/abc/comments?_limit=500",
                "application/json",
                "{\"title\":\"Hi\",\"userId\":\"3\",\"x\":1}");
        assertEquals(
                List.of(
                        ViolationKind.INVALID_TYPE,
                        ViolationKind.OUT_OF_RANGE,
                        ViolationKind.INVALID_TYPE,
                        ViolationKind.EXTRA_ARGUMENT),
                last().getViolations().stream().map(v -> v.getKind()).toList());
        assertTrue(
                last().getChecks().stream()
                        .anyMatch(c -> c.getArgument().getName().equals("title") && c.isMatched()));
    }

    @Test
    void SC_V3_methodAndContentType() {
        add(SpecItem.contentType(HttpMethod.GET, "application/json"));
        send("PUT", "/posts/1/comments", "", "");
        assertEquals(ViolationKind.UNSPECIFIED_METHOD, last().getViolations().get(0).getKind());
        add(SpecItem.contentType(HttpMethod.POST, "application/json"));
        send("POST", "/posts/1/comments", "text/plain", "x");
        assertEquals(
                ViolationKind.UNSPECIFIED_CONTENT_TYPE, last().getViolations().get(0).getKind());
    }

    @Test
    void SC_V4_SC_V5_neutralAndRejudgedOldCalls() {
        for (int i = 0; i < 10; i++)
            send(i % 2 == 0 ? "GET" : "POST", "/posts/1/comments?x=" + i, "", "");
        assertEquals(10, session.endpoint(namespace, 0).getCalls().size());
        assertEquals(0, session.endpoint(namespace, 0).getInvalidCount());
        for (int i = 0; i < 10; i++)
            assertEquals(VerdictStatus.NOT_VERIFIED, session.verdict(namespace, 0, i).getStatus());
        assertTrue(session.html(namespace).contains("No spec"));
        add(SpecItem.method(HttpMethod.GET));
        assertEquals(5, session.endpoint(namespace, 0).getValidCount());
        assertEquals(5, session.endpoint(namespace, 0).getInvalidCount());
    }

    @Test
    void SC_V6_preflight() {
        postSpec();
        session.send(
                "OPTIONS",
                BASE + "/posts/1/comments",
                204,
                List.of(new Header("Access-Control-Request-Method", "POST")),
                "",
                "");
        assertTrue(last().isPreflight());
        send("POST", "/posts/1/comments", "application/json", "{\"title\":\"Hi\"}");
        assertEquals(VerdictStatus.VALID, last().getStatus());
    }

    @Test
    void SC_V7_unmatchedBuilder() {
        send("GET", "/users/7", "", "");
        assertEquals(1, session.unmatched(namespace).get(new UrlPath("/users/7")));
        PatternDraft draft = new PatternDraft(new UrlPath("/users/7"));
        draft.toggle(1);
        draft.rename(1, "USER_ID");
        session.addEndpoint(namespace, draft.toTemplate());
        send("GET", "/users/8", "", "");
        assertEquals(1, session.endpoint(namespace, 1).getCalls().size());
        assertTrue(session.endpoint(namespace, 1).getSpec().isEmpty());
        assertEquals(1, session.unmatched(namespace).size());
    }

    @Test
    void SC_V8_repeatedQuery() {
        add(parameter(HttpMethod.GET, ArgumentLocation.QUERY, "tag", new StringType(), false));
        send("GET", "/posts/1/comments?tag=a&tag=b", "", "");
        assertEquals(VerdictStatus.VALID, last().getStatus());
        assertEquals(2, last().getChecks().size());
        assertEquals(WarningKind.REPEATED_PARAMETER, last().getWarnings().get(0).getKind());
    }

    @Test
    void SC_V9_nestedWildcardsAndSpecificity() {
        add(
                parameter(
                        HttpMethod.POST,
                        ArgumentLocation.BODY,
                        "items[].id",
                        new IntegerType(null, null),
                        false));
        add(
                parameter(
                        HttpMethod.POST,
                        ArgumentLocation.BODY,
                        "scores{}",
                        new IntegerType(null, null),
                        false));
        add(
                parameter(
                        HttpMethod.POST,
                        ArgumentLocation.BODY,
                        "scores.bonus",
                        new DecimalType(null, null),
                        false));
        send(
                "POST",
                "/posts/1/comments",
                "application/json",
                "{\"items\":[{\"id\":1},{\"id\":\"2\"}],\"scores\":{\"a\":1,\"bonus\":0.5}}");
        assertEquals(4, last().getChecks().size());
        assertEquals(1, last().getViolations().size());
        assertEquals("Decimal", last().getChecks().get(3).getParameter().getType().getName());
    }

    @Test
    void SC_V10_partialHeaderSpec() {
        add(
                parameter(
                        HttpMethod.GET,
                        ArgumentLocation.HEADER,
                        "X-Api-Key",
                        new StringType(),
                        true));
        List<Header> headers = new ArrayList<>();
        for (int i = 0; i < 8; i++) headers.add(new Header("X-Other-" + i, "value"));
        session.send(
                "GET",
                BASE + "/posts/1/comments?a=1",
                200,
                headers,
                "application/json",
                "{\"x\":1}");
        assertEquals(1, last().getViolations().size());
        assertEquals(ViolationKind.MISSING_ARGUMENT, last().getViolations().get(0).getKind());
        assertEquals("X-Api-Key", last().getViolations().get(0).getName());
    }

    @Test
    void SC_V11_form() {
        add(SpecItem.contentType(HttpMethod.POST, "application/x-www-form-urlencoded"));
        add(
                parameter(
                        HttpMethod.POST,
                        ArgumentLocation.BODY,
                        "userId",
                        new IntegerType(null, null),
                        false));
        add(parameter(HttpMethod.POST, ArgumentLocation.BODY, "title", new StringType(), false));
        send("POST", "/posts/1/comments", "application/x-www-form-urlencoded", "title=Hi&userId=3");
        assertEquals(VerdictStatus.VALID, last().getStatus());
        assertEquals(2, last().getChecks().size());
        assertTrue(last().getChecks().get(1).isMatched());
    }

    @Test
    void SC_V12_malformedDocumentation() {
        postSpec();
        send("POST", "/posts/1/comments", "application/json", "{\"title\":\"Hi\",}");
        assertEquals(
                List.of(ViolationKind.MALFORMED_BODY),
                last().getViolations().stream().map(v -> v.getKind()).toList());
        assertTrue(session.html(namespace).contains("trailing comma"));
    }

    @Test
    void SC_V13_warmReportPerformance() {
        postSpec();
        add(
                parameter(
                        HttpMethod.POST,
                        ArgumentLocation.QUERY,
                        "_limit",
                        new IntegerType(BigInteger.ONE, BigInteger.valueOf(100)),
                        false));
        for (int i = 0; i < 1000; i++)
            send(
                    "POST",
                    "/posts/1/comments?_limit=5",
                    "application/json",
                    "{\"title\":\"Hi\",\"userId\":3}");
        session.endpoint(namespace, 0);
        assertTimeout(
                Duration.ofMillis(500),
                () -> {
                    var report = session.endpoint(namespace, 0);
                    assertEquals(1000, report.getCalls().size());
                    assertEquals(1000, report.getValidCount());
                });
    }
}
