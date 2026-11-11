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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.sameInstance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.event.TableModelEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zaproxy.addon.restextraction.ExtensionRestExtraction;
import org.zaproxy.addon.restextraction.model.ApiCall;
import org.zaproxy.addon.restextraction.model.EndpointReport;
import org.zaproxy.addon.restextraction.model.HttpMethod;
import org.zaproxy.addon.restextraction.model.RecordedCall;
import org.zaproxy.zap.testutils.TestUtils;

/** Unit test for {@link CallTableModel}, with hand-made endpoint reports. */
class CallTableModelUnitTest extends TestUtils {

    private static final String BASE_URL = "https://jsonplaceholder.typicode.com";
    private static final String COMMENTS = "/posts/[POST_ID]/comments";
    private static final String USERS = "/users/[USER_ID]";

    private CallTableModel model;

    @BeforeEach
    void setUp() {
        mockMessages(new ExtensionRestExtraction());
        model = new CallTableModel();
    }

    // CT1

    @Test
    void shouldHaveColumnsForTimeMethodStatusEndpointAndArguments() {
        // Given / When
        List<String> columns = new ArrayList<>();
        for (int i = 0; i < model.getColumnCount(); i++) {
            columns.add(model.getColumnName(i));
        }

        // Then
        assertThat(columns, contains("Time", "Method", "Status", "Endpoint", "Arguments", "Check"));
    }

    @Test
    void shouldShowTheValuesOfACallInItsRow() {
        // Given
        RecordedCall call =
                recorded(HttpMethod.POST, 201, "/posts/1223/comments", "POST_ID", "1223");

        // When
        model.setEndpoints(List.of(endpoint(COMMENTS, call)));

        // Then
        assertThat((String) model.getValueAt(0, 0), matchesPattern("\\d{2}:\\d{2}:\\d{2}"));
        assertThat(model.getValueAt(0, 1), is("POST"));
        assertThat(model.getValueAt(0, 2), is(201));
        assertThat(model.getValueAt(0, 3), is(COMMENTS));
        assertThat(model.getValueAt(0, 4), is("POST_ID = 1223"));
    }

    // CT2

    @Test
    void shouldMergeCallsOfSeveralEndpointsByTime() throws Exception {
        // Given
        RecordedCall first = recorded(HttpMethod.GET, 200, "/posts/1/comments", "POST_ID", "1");
        Thread.sleep(5);
        RecordedCall second = recorded(HttpMethod.GET, 200, "/users/7", "USER_ID", "7");
        Thread.sleep(5);
        RecordedCall third = recorded(HttpMethod.GET, 200, "/posts/2/comments", "POST_ID", "2");

        // When
        model.setEndpoints(List.of(endpoint(COMMENTS, first, third), endpoint(USERS, second)));

        // Then
        assertThat(rows(), contains(first, second, third));
        assertThat(model.getValueAt(1, 3), is(USERS));
    }

    // CT3

    @Test
    void shouldShowArgumentsInTemplateOrder() {
        // Given
        RecordedCall call =
                recorded(
                        HttpMethod.GET,
                        200,
                        "/users/7/posts/1223",
                        "USER_ID",
                        "7",
                        "POST_ID",
                        "1223");

        // When
        model.setEndpoints(List.of(endpoint("/users/[USER_ID]/posts/[POST_ID]", call)));

        // Then
        assertThat(model.getValueAt(0, 4), is("USER_ID = 7, POST_ID = 1223"));
    }

    @Test
    void shouldShowNoArgumentsForEndpointWithoutVariables() {
        // Given
        RecordedCall call = recorded(HttpMethod.GET, 200, "/albums");

        // When
        model.setEndpoints(List.of(endpoint("/albums", call)));

        // Then
        assertThat(model.getValueAt(0, 4), is(""));
    }

    // CT4

    @Test
    void shouldOnlyAddRowsWhenOneMoreCallIsShown() {
        // Given
        RecordedCall first = recorded(HttpMethod.GET, 200, "/posts/1/comments", "POST_ID", "1");
        RecordedCall second = recorded(HttpMethod.GET, 200, "/posts/2/comments", "POST_ID", "2");
        model.setEndpoints(List.of(endpoint(COMMENTS, first)));
        List<TableModelEvent> events = new ArrayList<>();
        model.addTableModelListener(events::add);

        // When
        model.setEndpoints(List.of(endpoint(COMMENTS, first, second)));

        // Then
        assertThat(rows(), contains(first, second));
        assertThat(events.size(), is(1));
        assertThat(events.get(0).getType(), is(TableModelEvent.INSERT));
        assertThat(events.get(0).getFirstRow(), is(1));
        assertThat(events.get(0).getLastRow(), is(1));
    }

    @Test
    void shouldTellNothingChangedWhenTheSameCallsAreShownAgain() {
        // Given
        RecordedCall first = recorded(HttpMethod.GET, 200, "/posts/1/comments", "POST_ID", "1");
        model.setEndpoints(List.of(endpoint(COMMENTS, first)));
        List<TableModelEvent> events = new ArrayList<>();
        model.addTableModelListener(events::add);

        // When
        model.setEndpoints(List.of(endpoint(COMMENTS, first)));

        // Then
        assertThat(events.size(), is(0));
    }

    // CT5

    @Test
    void shouldGiveTheCallOfARowAndBack() {
        // Given
        RecordedCall first = recorded(HttpMethod.GET, 200, "/posts/1/comments", "POST_ID", "1");
        RecordedCall second = recorded(HttpMethod.DELETE, 200, "/posts/2/comments", "POST_ID", "2");
        RecordedCall notShown = recorded(HttpMethod.GET, 200, "/users/7", "USER_ID", "7");

        // When
        model.setEndpoints(List.of(endpoint(COMMENTS, first, second)));

        // Then
        assertThat(model.getCallAt(1), is(sameInstance(second)));
        assertThat(model.rowOf(second), is(1));
        assertThat(model.rowOf(notShown), is(-1));
    }

    // CT6

    @Test
    void shouldHaveNoRowsWithoutCalls() {
        // Given / When
        model.setEndpoints(List.of(endpoint(COMMENTS), endpoint(USERS)));

        // Then
        assertThat(model.getRowCount(), is(0));
    }

    @Test
    void shouldHaveNoRowsWithoutEndpoints() {
        // Given
        model.setEndpoints(
                List.of(endpoint(COMMENTS, recorded(HttpMethod.GET, 200, "/posts/1/comments"))));

        // When
        model.setEndpoints(List.of());

        // Then
        assertThat(model.getRowCount(), is(0));
    }

    // CT7, CT8: filter

    @Test
    void shouldMatchEveryCallWithoutMethodOrText() {
        // Given
        model.setEndpoints(
                List.of(endpoint(COMMENTS, recorded(HttpMethod.GET, 200, "/posts/1/comments"))));

        // When / Then
        assertThat(model.matches(0, null, ""), is(true));
        assertThat(model.matches(0, null, "   "), is(true));
    }

    @Test
    void shouldMatchOnlyCallsWithTheGivenMethod() {
        // Given
        model.setEndpoints(
                List.of(
                        endpoint(
                                COMMENTS,
                                recorded(HttpMethod.GET, 200, "/posts/1/comments"),
                                recorded(HttpMethod.POST, 201, "/posts/1/comments"))));

        // When / Then
        assertThat(model.matches(0, HttpMethod.GET, ""), is(true));
        assertThat(model.matches(1, HttpMethod.GET, ""), is(false));
        assertThat(model.matches(1, HttpMethod.POST, ""), is(true));
    }

    @Test
    void shouldMatchTextInAnyColumnIgnoringCase() {
        // Given
        model.setEndpoints(
                List.of(
                        endpoint(
                                USERS,
                                recorded(HttpMethod.GET, 404, "/users/abcd", "USER_ID", "abcd"))));

        // When / Then
        assertThat(model.matches(0, null, "user_id = ABCD"), is(true));
        assertThat(model.matches(0, null, "/users/["), is(true));
        assertThat(model.matches(0, null, "404"), is(true));
        assertThat(model.matches(0, null, " get "), is(true));
        assertThat(model.matches(0, null, "posts"), is(false));
        assertThat(model.matches(0, HttpMethod.POST, "abcd"), is(false));
    }

    private List<RecordedCall> rows() {
        List<RecordedCall> calls = new ArrayList<>();
        for (int i = 0; i < model.getRowCount(); i++) {
            calls.add(model.getCallAt(i));
        }
        return calls;
    }

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
            HttpMethod method, int statusCode, String path, String... arguments) {
        Map<String, String> args = new LinkedHashMap<>();
        for (int i = 0; i < arguments.length; i += 2) {
            args.put(arguments[i], arguments[i + 1]);
        }
        ApiCall call =
                new ApiCall(
                        method,
                        BASE_URL + path,
                        statusCode,
                        "HTTP/1.1 " + statusCode + "\r\nContent-Type: application/json\r\n\r\n",
                        "[]");
        return new RecordedCall(call, args);
    }
}
