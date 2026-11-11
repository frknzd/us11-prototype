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
package org.zaproxy.addon.restextraction.ui;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.zaproxy.addon.restextraction.ExtensionRestExtraction;
import org.zaproxy.addon.restextraction.model.ApiCall;
import org.zaproxy.addon.restextraction.model.ApiReport;
import org.zaproxy.addon.restextraction.model.EndpointReport;
import org.zaproxy.addon.restextraction.model.HttpMethod;
import org.zaproxy.addon.restextraction.model.RecordedCall;
import org.zaproxy.zap.testutils.TestUtils;

/** Unit test for {@link ApiDocumentationWriter}, with hand-made reports. */
class ApiDocumentationWriterUnitTest extends TestUtils {

    private static final String BASE_URL = "https://jsonplaceholder.typicode.com";
    private static final String TEMPLATE = "/posts/[POST_ID]/comments";
    private static final String HEADERS =
            "HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\n\r\n";
    private static final String SCRIPT = "<script>alert(1)</script>";
    private static final String ESCAPED_SCRIPT = "&lt;script&gt;alert(1)&lt;/script&gt;";

    private ApiDocumentationWriter writer;

    @BeforeEach
    void setUp() {
        mockMessages(new ExtensionRestExtraction());
        writer = new ApiDocumentationWriter();
    }

    // DW1, DW2, DW11: layout and empty reports

    @Test
    void shouldShowBaseUrlAndNoEndpointsTextWhenNamespaceIsEmpty() {
        // Given
        ApiReport report = new ApiReport(BASE_URL, List.of());

        // When
        String html = writer.write(report);

        // Then
        assertThat(html, containsString("<h2>" + BASE_URL + "</h2>"));
        assertThat(html, containsString("No endpoints yet."));
    }

    @Test
    void shouldShowTemplateAndNoCallsTextWhenEndpointHasNoCalls() {
        // Given
        ApiReport report = reportOf(new EndpointReport(TEMPLATE, Map.of(), List.of()));

        // When
        String html = writer.write(report);

        // Then
        assertThat(html, containsString("<h3>" + TEMPLATE + "</h3>"));
        assertThat(html, containsString("No calls yet."));
        assertThat(html, not(containsString("No endpoints yet.")));
    }

    @Test
    void shouldShowBaseUrlOnceAndOneSectionPerTemplate() {
        // Given
        ApiReport report =
                reportOf(
                        endpoint(TEMPLATE, recorded(HttpMethod.GET, 200, "[]", "POST_ID", "1223")),
                        endpoint("/users/[USER_ID]", recorded(HttpMethod.GET, 200, "{}")));

        // When
        String html = writer.write(report);

        // Then
        assertThat(countOf(BASE_URL, html), is(1));
        assertThat(countOf("<h3>", html), is(2));
        assertThat(html, containsString("<h3>/users/[USER_ID]</h3>"));
    }

    // DW3, DW4, DW10: methods, counts and calls

    @Test
    void shouldShowEveryUsedMethodWithItsCount() {
        // Given
        Map<HttpMethod, Integer> counts = Map.of(HttpMethod.GET, 3, HttpMethod.POST, 1);
        ApiReport report = reportOf(new EndpointReport(TEMPLATE, counts, List.of()));

        // When
        String html = writer.write(report);

        // Then
        assertThat(html, containsString("GET: 3, POST: 1"));
        assertThat(html, not(containsString("PUT")));
    }

    @Test
    void shouldShowArgumentsMethodStatusAndHeadersOfEveryCall() {
        // Given
        ApiReport report =
                reportOf(
                        endpoint(
                                "/users/[USER_ID]/posts/[POST_ID]",
                                recorded(
                                        HttpMethod.POST,
                                        201,
                                        "{}",
                                        "USER_ID",
                                        "7",
                                        "POST_ID",
                                        "1223")));

        // When
        String html = writer.write(report);

        // Then
        assertThat(html, containsString("<b>POST</b>, status 201"));
        assertThat(html, containsString("USER_ID = 7, POST_ID = 1223"));
        assertThat(html, containsString("Content-Type: application/json; charset=utf-8"));
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 500})
    void shouldShowStatusOfFailedCalls(int statusCode) {
        // Given
        ApiReport report = reportOf(endpoint(TEMPLATE, recorded(HttpMethod.GET, statusCode, "{}")));

        // When
        String html = writer.write(report);

        // Then
        assertThat(html, containsString("<b>GET</b>, status " + statusCode));
    }

    @Test
    void shouldKeepOrderOfCalls() {
        // Given
        ApiReport report =
                reportOf(
                        endpoint(
                                TEMPLATE,
                                recorded(HttpMethod.GET, 200, "[]", "POST_ID", "first"),
                                recorded(HttpMethod.GET, 200, "[]", "POST_ID", "second")));

        // When
        String html = writer.write(report);

        // Then
        assertThat(html.indexOf("POST_ID = first"), is(greaterThan(-1)));
        assertThat(html.indexOf("POST_ID = first"), is(lessThan(html.indexOf("POST_ID = second"))));
    }

    // DW5: the documentation has no bodies

    @ParameterizedTest
    @ValueSource(strings = {"{\"id\":1223}", SCRIPT, "[binary, not shown]"})
    void shouldNeverWriteBody(String body) {
        // Given
        ApiReport report = reportOf(endpoint(TEMPLATE, recorded(HttpMethod.GET, 200, body)));

        // When
        String html = writer.write(report);

        // Then
        assertThat(html, not(containsString("Response body")));
        assertThat(html, not(containsString(body)));
        assertThat(html, not(containsString(ESCAPED_SCRIPT)));
        assertThat(html, not(containsString("1223}")));
        assertThat(html, containsString("Content-Type: application/json; charset=utf-8"));
    }

    // DW8, DW9: the body of one call

    @Test
    void shouldGiveNonAsciiBodyUnchanged() {
        // Given
        String body = "café 中文";

        // When / Then
        assertThat(writer.formatBody(HEADERS, body), is(body));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"id\":1}\n[cut off after 64 KB]", "[binary, not shown]"})
    void shouldGiveCutOrBinaryBodyAsItIsWithItsMark(String markedBody) {
        // When / Then
        assertThat(writer.formatBody(HEADERS, markedBody), is(markedBody));
    }

    @Test
    void shouldGiveBodyAsPlainTextWithoutEscaping() {
        // When / Then
        assertThat(writer.formatBody("HTTP/1.1 200 OK\r\n\r\n", SCRIPT), is(SCRIPT));
    }

    // DW6: everything from traffic is escaped

    @Test
    void shouldEscapeScriptInArgument() {
        // Given
        ApiReport report =
                reportOf(
                        endpoint(TEMPLATE, recorded(HttpMethod.GET, 200, "[]", "POST_ID", SCRIPT)));

        // When
        String html = writer.write(report);

        // Then
        assertThat(html, containsString("POST_ID = " + ESCAPED_SCRIPT));
        assertThat(html, not(containsString(SCRIPT)));
    }

    @Test
    void shouldEscapeScriptInResponseHeaders() {
        // Given
        ApiCall call =
                new ApiCall(
                        HttpMethod.GET,
                        BASE_URL + "/posts/1/comments",
                        200,
                        "HTTP/1.1 200 OK\r\nX-Note: " + SCRIPT + "\r\n\r\n",
                        "[]");
        ApiReport report = reportOf(endpoint(TEMPLATE, new RecordedCall(call, Map.of())));

        // When
        String html = writer.write(report);

        // Then
        assertThat(html, containsString("X-Note: " + ESCAPED_SCRIPT));
        assertThat(html, not(containsString(SCRIPT)));
    }

    @Test
    void shouldEscapeBaseUrlAndTemplate() {
        // Given
        ApiReport report =
                new ApiReport(
                        "https://host.com/a&b",
                        List.of(new EndpointReport("/q<1>/\"x\"/'y'", Map.of(), List.of())));

        // When
        String html = writer.write(report);

        // Then
        assertThat(html, containsString("<h2>https://host.com/a&amp;b</h2>"));
        assertThat(html, containsString("<h3>/q&lt;1&gt;/&quot;x&quot;/&#39;y&#39;</h3>"));
    }

    // DW12: long lines are wrapped

    @Test
    void shouldWrapLongHeaderLine() {
        // Given
        String value = "abcdefghij".repeat(50);
        ApiCall call =
                new ApiCall(
                        HttpMethod.GET,
                        BASE_URL + "/posts/1/comments",
                        200,
                        "HTTP/1.1 200 OK\r\nReport-To: " + value + "\r\n\r\n",
                        "[]");
        ApiReport report = reportOf(endpoint(TEMPLATE, new RecordedCall(call, Map.of())));

        // When
        String html = writer.write(report);

        // Then
        String headers = html.substring(html.indexOf("<pre>") + 5, html.indexOf("</pre>"));
        for (String line : headers.split("\n")) {
            assertThat(line.length(), is(lessThanOrEqualTo(100)));
        }
        assertThat(headers.replace("\n", ""), containsString("Report-To: " + value));
    }

    @Test
    void shouldKeepShortLinesAsTheyAre() {
        // Given
        ApiReport report = reportOf(endpoint(TEMPLATE, recorded(HttpMethod.GET, 200, "{}")));

        // When
        String html = writer.write(report);

        // Then
        assertThat(html, containsString("Content-Type: application/json; charset=utf-8"));
    }

    // DW13: JSON bodies are indented by formatBody

    @Test
    void shouldIndentJsonBody() {
        // Given
        String body = "{\"postId\":1,\"tags\":[\"a\",\"b\"],\"author\":{\"name\":\"Leanne\"}}";

        // When
        String formatted = writer.formatBody(HEADERS, body);

        // Then
        assertThat(
                formatted,
                is(
                        "{\n"
                                + "  \"postId\": 1,\n"
                                + "  \"tags\": [\n"
                                + "    \"a\",\n"
                                + "    \"b\"\n"
                                + "  ],\n"
                                + "  \"author\": {\n"
                                + "    \"name\": \"Leanne\"\n"
                                + "  }\n"
                                + "}"));
    }

    @Test
    void shouldKeepBracketsAndQuotesInsideJsonStrings() {
        // Given
        String body = "{\"note\":\"a {b} [c], \\\"d\\\": e\",\"empty\":{},\"list\":[]}";

        // When
        String formatted = writer.formatBody(HEADERS, body);

        // Then
        assertThat(
                formatted,
                is(
                        "{\n"
                                + "  \"note\": \"a {b} [c], \\\"d\\\": e\",\n"
                                + "  \"empty\": {},\n"
                                + "  \"list\": []\n"
                                + "}"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"id\":1",
                "{\"id\":1}}",
                "{\"id\":\"open}",
                "[1,2]extra",
                "[binary, not shown]",
                "{\"id\":one}",
                "{\"id\":1}\n[cut off after 64 KB]",
                "not json at all"
            })
    void shouldGiveBodyThatIsNotWholeJsonAsItIs(String body) {
        // When / Then
        assertThat(writer.formatBody(HEADERS, body), is(body));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\n\r\n",
                "HTTP/1.1 200 OK\r\n\r\n"
            })
    void shouldNotIndentJsonWithOtherOrNoContentType(String headers) {
        // When / Then
        assertThat(writer.formatBody(headers, "{\"id\":1}"), is("{\"id\":1}"));
    }

    // DW14: the values seen per variable

    @Test
    void shouldShowValuesSeenPerVariableWithTheirCalls() {
        // Given
        ApiReport report =
                reportOf(
                        endpoint(
                                "/users/[USER_ID]/posts/[POST_ID]",
                                recorded(HttpMethod.GET, 200, "[]", "USER_ID", "7", "POST_ID", "1"),
                                recorded(
                                        HttpMethod.GET,
                                        200,
                                        "[]",
                                        "USER_ID",
                                        "7",
                                        "POST_ID",
                                        "abcd"),
                                recorded(
                                        HttpMethod.POST,
                                        201,
                                        "{}",
                                        "USER_ID",
                                        "7",
                                        "POST_ID",
                                        "1")));

        // When
        String html = writer.write(report);

        // Then
        assertThat(html, containsString("Values of USER_ID: 7 (3 calls)"));
        assertThat(html, containsString("Values of POST_ID: 1 (2 calls), abcd (1 call)"));
    }

    @Test
    void shouldShowNoValuesForEndpointWithoutVariables() {
        // Given
        ApiReport report = reportOf(endpoint("/albums", recorded(HttpMethod.GET, 200, "[]")));

        // When
        String html = writer.write(report);

        // Then
        assertThat(html, not(containsString("Values of")));
    }

    @Test
    void shouldEscapeValuesSeen() {
        // Given
        ApiReport report =
                reportOf(
                        endpoint(TEMPLATE, recorded(HttpMethod.GET, 200, "[]", "POST_ID", SCRIPT)));

        // When
        String html = writer.write(report);

        // Then
        assertThat(html, containsString("Values of POST_ID: " + ESCAPED_SCRIPT + " (1 call)"));
        assertThat(html, not(containsString(SCRIPT)));
    }

    // DW7: order of the endpoints

    @Test
    void shouldKeepOrderOfEndpoints() {
        // Given
        ApiReport report =
                reportOf(
                        new EndpointReport("/users/[USER_ID]", Map.of(), List.of()),
                        new EndpointReport(TEMPLATE, Map.of(), List.of()),
                        new EndpointReport("/albums", Map.of(), List.of()));

        // When
        String html = writer.write(report);

        int users = html.indexOf("/users/[USER_ID]");
        int comments = html.indexOf(TEMPLATE);
        int albums = html.indexOf("/albums");
        // Then
        assertThat(users, is(greaterThan(-1)));
        assertThat(users, is(lessThan(comments)));
        assertThat(comments, is(lessThan(albums)));
    }

    private static ApiReport reportOf(EndpointReport... endpoints) {
        return new ApiReport(BASE_URL, List.of(endpoints));
    }

    /** An endpoint report with these calls, counted per method like the endpoint does. */
    private static EndpointReport endpoint(String template, RecordedCall... calls) {
        Map<HttpMethod, Integer> counts = new LinkedHashMap<>();
        for (RecordedCall call : calls) {
            counts.merge(call.getCall().getMethod(), 1, Integer::sum);
        }
        return new EndpointReport(template, counts, List.of(calls));
    }

    /**
     * @param arguments variable names and values in turns, like {@code "POST_ID", "1223"}
     */
    private static RecordedCall recorded(
            HttpMethod method, int statusCode, String body, String... arguments) {
        Map<String, String> args = new LinkedHashMap<>();
        for (int i = 0; i < arguments.length; i += 2) {
            args.put(arguments[i], arguments[i + 1]);
        }
        ApiCall call =
                new ApiCall(method, BASE_URL + "/posts/1223/comments", statusCode, HEADERS, body);
        return new RecordedCall(call, args);
    }

    private static int countOf(String text, String html) {
        int count = 0;
        for (int i = html.indexOf(text); i >= 0; i = html.indexOf(text, i + text.length())) {
            count++;
        }
        return count;
    }
}
