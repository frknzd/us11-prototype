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
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.parosproxy.paros.network.HttpMessage;
import org.zaproxy.addon.restextraction.model.ApiCall;
import org.zaproxy.addon.restextraction.model.HttpMethod;
import org.zaproxy.addon.restextraction.model.RestExtractor;
import org.zaproxy.zap.testutils.TestUtils;

/** Unit test for {@link RestTrafficListener}, with hand-made messages and a mocked extractor. */
class RestTrafficListenerUnitTest extends TestUtils {

    private static final String URL = "https://jsonplaceholder.typicode.com/posts/1223/comments";
    private static final String JSON = "application/json; charset=utf-8";
    private static final int MAX_BODY_LENGTH = 64 * 1024;

    private RestExtractor extractor;
    private RestTrafficListener listener;

    @BeforeEach
    void setUp() {
        mockMessages(new ExtensionRestExtraction());
        extractor = mock(RestExtractor.class);
        listener = new RestTrafficListener(extractor);
    }

    // TL1, TL2: what the callbacks do

    @Test
    void shouldRecordResponseOnce() throws Exception {
        // Given
        HttpMessage message = message("GET", URL, 200, JSON, "[]");

        // When / Then
        assertThat(listener.onHttpResponseReceive(message), is(true));

        verify(extractor).record(any(ApiCall.class));
    }

    @Test
    void shouldNotRecordRequest() {
        // Given / When / Then
        assertThat(listener.onHttpRequestSend(new HttpMessage()), is(true));
        verifyNoInteractions(extractor);
    }

    // TL3 to TL8: toApiCall

    @Test
    void shouldTakeCallFromMessageWithoutChangingIt() throws Exception {
        // Given
        HttpMessage message =
                message(
                        "POST",
                        "https://example.com/posts/a%2Fb?sort=desc",
                        404,
                        JSON,
                        "{\"error\":\"not found\"}");
        String requestHeader = message.getRequestHeader().toString();
        String responseHeader = message.getResponseHeader().toString();

        // When
        ApiCall call = recordedCallFor(message);

        // Then
        assertThat(call.getMethod(), is(HttpMethod.POST));
        assertThat(call.getUrl(), is("https://example.com/posts/a%2Fb"));
        assertThat(call.getStatusCode(), is(404));
        assertThat(call.getResponseHeaders(), is(responseHeader));
        assertThat(call.getResponseBody(), is("{\"error\":\"not found\"}"));
        assertThat(message.getRequestHeader().toString(), is(requestHeader));
        assertThat(message.getResponseHeader().toString(), is(responseHeader));
        assertThat(message.getResponseBody().toString(), is("{\"error\":\"not found\"}"));
    }

    @Test
    void shouldKeepPortThatIsNotDefault() throws Exception {
        // Given
        HttpMessage message = message("GET", "http://localhost:3000/api/Products/1", 200, JSON, "");

        // When
        ApiCall call = recordedCallFor(message);

        // Then
        assertThat(call.getUrl(), is("http://localhost:3000/api/Products/1"));
    }

    @ParameterizedTest
    @CsvSource({
        "GET, GET",
        "POST, POST",
        "PUT, PUT",
        "DELETE, DELETE",
        "PATCH, PATCH",
        "HEAD, HEAD",
        "OPTIONS, OPTIONS",
        "PROPFIND, OTHER",
        "get, OTHER"
    })
    void shouldTurnMethodIntoEnumValue(String method, HttpMethod expected) throws Exception {
        // Given
        HttpMessage message = message(method, URL, 200, JSON, "[]");

        // When
        ApiCall call = recordedCallFor(message);

        // Then
        assertThat(call.getMethod(), is(expected));
    }

    // TL9: traffic is never blocked

    @Test
    void shouldReturnTrueWhenRecordingFails() throws Exception {
        // Given
        doThrow(new IllegalStateException("recording failed")).when(extractor).record(any());
        HttpMessage message = message("GET", URL, 200, JSON, "[]");

        // When / Then
        assertThat(listener.onHttpResponseReceive(message), is(true));
    }

    @Test
    void shouldReturnTrueWhenMessageCanNotBeRead() {
        // Given
        HttpMessage withoutRequest = new HttpMessage();

        // When / Then
        assertThat(listener.onHttpResponseReceive(withoutRequest), is(true));

        verifyNoInteractions(extractor);
    }

    // TL10 to TL12: large, binary and empty bodies

    @Test
    void shouldCutBodyThatIsLargerThan64Kb() throws Exception {
        // Given
        HttpMessage message = message("GET", URL, 200, JSON, "a".repeat(MAX_BODY_LENGTH + 5000));

        // When
        String body = recordedCallFor(message).getResponseBody();

        // Then
        assertThat(body, startsWith("a".repeat(MAX_BODY_LENGTH) + "\n"));
        assertThat(body, endsWith("[cut off after 64 KB]"));
        assertThat(body.length(), is(MAX_BODY_LENGTH + "\n[cut off after 64 KB]".length()));
    }

    @Test
    void shouldKeepBodyOfExactly64Kb() throws Exception {
        // Given
        String wholeBody = "a".repeat(MAX_BODY_LENGTH);
        HttpMessage message = message("GET", URL, 200, JSON, wholeBody);

        // When / Then
        assertThat(recordedCallFor(message).getResponseBody(), is(wholeBody));
    }

    @ParameterizedTest
    @CsvSource({"image/png", "application/octet-stream", "application/pdf"})
    void shouldReplaceBinaryBody(String contentType) throws Exception {
        // Given
        HttpMessage message = message("GET", URL, 200, contentType, "\u0089PNG\r\n\u001a\n");

        // When / Then
        assertThat(recordedCallFor(message).getResponseBody(), is("[binary, not shown]"));
    }

    @ParameterizedTest
    @CsvSource({"application/json", "text/plain", "text/html", "application/xml"})
    void shouldKeepTextBody(String contentType) throws Exception {
        // Given
        HttpMessage message = message("GET", URL, 200, contentType, "{\"id\":1223}");

        // When / Then
        assertThat(recordedCallFor(message).getResponseBody(), is("{\"id\":1223}"));
    }

    @ParameterizedTest
    @CsvSource({"image/png", "application/json"})
    void shouldKeepEmptyBodyEmpty(String contentType) throws Exception {
        // Given
        HttpMessage message = message("DELETE", URL, 204, contentType, "");

        // When / Then
        assertThat(recordedCallFor(message).getResponseBody(), is(""));
    }

    @Test
    void shouldKeepEmptyBodyEmptyWhenThereIsNoContentType() throws Exception {
        // Given
        HttpMessage message = message("DELETE", URL, 204, null, "");

        // When / Then
        assertThat(recordedCallFor(message).getResponseBody(), is(""));
    }

    @Test
    void shouldKeepBodyAsTextWhenThereIsNoContentType() throws Exception {
        // Given
        HttpMessage message = message("GET", URL, 200, null, "{\"id\":1223}");

        // When / Then
        assertThat(recordedCallFor(message).getResponseBody(), is("{\"id\":1223}"));
    }

    @Test
    void shouldCutLargeBodyWhenThereIsNoContentType() throws Exception {
        // Given
        HttpMessage message = message("GET", URL, 200, null, "a".repeat(MAX_BODY_LENGTH + 1));

        // When
        String body = recordedCallFor(message).getResponseBody();

        // Then
        assertThat(body, startsWith("a".repeat(MAX_BODY_LENGTH) + "\n"));
        assertThat(body, endsWith("[cut off after 64 KB]"));
    }

    private ApiCall recordedCallFor(HttpMessage message) {
        listener.onHttpResponseReceive(message);
        ArgumentCaptor<ApiCall> captured = ArgumentCaptor.forClass(ApiCall.class);
        verify(extractor).record(captured.capture());
        return captured.getValue();
    }

    /**
     * @param contentType the Content-Type of the response, or null to leave the header out
     */
    private static HttpMessage message(
            String method, String url, int status, String contentType, String body)
            throws Exception {
        HttpMessage message = new HttpMessage();
        message.setRequestHeader(method + " " + url + " HTTP/1.1\r\n\r\n");
        String contentTypeLine = contentType == null ? "" : "Content-Type: " + contentType + "\r\n";
        message.setResponseHeader("HTTP/1.1 " + status + " Status\r\n" + contentTypeLine + "\r\n");
        message.setResponseBody(body);
        return message;
    }
}
