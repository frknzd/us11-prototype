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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Unit test for {@link ApiCall}. */
class ApiCallUnitTest {

    private static final String URL = "https://example.com/users/1/posts/2";
    private static final String HEADERS =
            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n";

    private static ApiCall apiCall(String url) {
        return new ApiCall(HttpMethod.GET, url, 200, HEADERS, "{}");
    }

    @Test
    void shouldReturnMethodPassedToConstructor() {
        // Given
        ApiCall call = new ApiCall(HttpMethod.POST, URL, 201, HEADERS, "{}");
        // When / Then
        assertThat(call.getMethod(), is(equalTo(HttpMethod.POST)));
    }

    @Test
    void shouldReturnUrlPassedToConstructor() {
        // Given
        ApiCall call = apiCall(URL);
        // When / Then
        assertThat(call.getUrl(), is(equalTo(URL)));
    }

    @Test
    void shouldReturnStatusCodePassedToConstructor() {
        // Given
        ApiCall call = new ApiCall(HttpMethod.GET, URL, 404, HEADERS, "{}");
        // When / Then
        assertThat(call.getStatusCode(), is(equalTo(404)));
    }

    @Test
    void shouldReturnResponseBodyPassedToConstructor() {
        // Given
        String body = "{\"id\":1,\"title\":\"hello\"}";
        ApiCall call = new ApiCall(HttpMethod.GET, URL, 200, HEADERS, body);
        // When / Then
        assertThat(call.getResponseBody(), is(equalTo(body)));
    }

    @Test
    void shouldKeepPercentEncodedUrl() {
        // Given
        String url = "https://example.com/users/a%2Fb/posts/hello%20world";
        ApiCall call = apiCall(url);
        // When / Then
        assertThat(call.getUrl(), is(equalTo(url)));
    }

    @Test
    void shouldKeepTrailingSlash() {
        // Given
        String url = "https://example.com/posts/1/";
        ApiCall call = apiCall(url);
        // When / Then
        assertThat(call.getUrl(), is(equalTo(url)));
    }

    @Test
    void shouldKeepHostCase() {
        // Given
        String url = "https://EXAMPLE.COM/posts/1";
        ApiCall call = apiCall(url);
        // When / Then
        assertThat(call.getUrl(), is(equalTo(url)));
    }

    @Test
    void shouldDropQueryString() {
        // Given
        ApiCall call = apiCall("https://example.com/posts/1?sort=asc&page=2");
        // When / Then
        assertThat(call.getUrl(), is(equalTo("https://example.com/posts/1")));
    }

    @Test
    void shouldDropEmptyQueryString() {
        // Given
        ApiCall call = apiCall("https://example.com/posts/1?");
        // When / Then
        assertThat(call.getUrl(), is(equalTo("https://example.com/posts/1")));
    }

    @Test
    void shouldDropEverythingAfterFirstQuestionMark() {
        // Given
        ApiCall call = apiCall("https://example.com/posts/1?next=/a?b=c");
        // When / Then
        assertThat(call.getUrl(), is(equalTo("https://example.com/posts/1")));
    }

    @Test
    void shouldSetTimeWhenCreated() {
        // Given
        Instant before = Instant.now();
        ApiCall call = apiCall(URL);
        Instant after = Instant.now();
        // When / Then
        assertThat(call.getTime(), is(greaterThanOrEqualTo(before)));
        assertThat(call.getTime(), is(lessThanOrEqualTo(after)));
    }

    @Test
    void shouldReturnResponseHeadersPassedToConstructor() {
        // Given
        ApiCall call = apiCall(URL);
        // When / Then
        assertThat(call.getResponseHeaders(), is(equalTo(HEADERS)));
    }

    @Test
    void shouldKeepEmptyResponseHeaders() {
        // Given
        ApiCall call = new ApiCall(HttpMethod.GET, URL, 200, "", "{}");
        // When / Then
        assertThat(call.getResponseHeaders(), is(equalTo("")));
    }

    @Test
    void shouldThrowWhenUrlIsNull() {
        // When
        NullPointerException e = assertThrows(NullPointerException.class, () -> apiCall(null));
        // Then
        assertThat(e.getMessage(), is(equalTo("url")));
    }

    @Test
    void shouldKeepEmptyUrl() {
        // Given
        ApiCall call = apiCall("");
        // When / Then
        assertThat(call.getUrl(), is(equalTo("")));
    }

    @Test
    void shouldThrowWhenMethodIsNull() {
        // When
        NullPointerException e =
                assertThrows(
                        NullPointerException.class,
                        () -> new ApiCall(null, URL, 200, HEADERS, "{}"));
        // Then
        assertThat(e.getMessage(), is(equalTo("method")));
    }

    @Test
    void shouldThrowWhenResponseHeadersIsNull() {
        // When
        NullPointerException e =
                assertThrows(
                        NullPointerException.class,
                        () -> new ApiCall(HttpMethod.GET, URL, 200, null, "{}"));
        // Then
        assertThat(e.getMessage(), is(equalTo("responseHeaders")));
    }

    @Test
    void shouldThrowWhenResponseBodyIsNull() {
        // When
        NullPointerException e =
                assertThrows(
                        NullPointerException.class,
                        () -> new ApiCall(HttpMethod.GET, URL, 200, HEADERS, null));
        // Then
        assertThat(e.getMessage(), is(equalTo("responseBody")));
    }

    @Test
    void shouldKeepEmptyResponseBody() {
        // Given
        ApiCall call = new ApiCall(HttpMethod.GET, URL, 204, HEADERS, "");
        // When / Then
        assertThat(call.getResponseBody(), is(equalTo("")));
    }
}
