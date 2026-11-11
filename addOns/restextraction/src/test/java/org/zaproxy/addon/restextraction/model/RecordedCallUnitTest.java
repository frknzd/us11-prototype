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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit test for {@link RecordedCall}. */
class RecordedCallUnitTest {

    private static final ApiCall CALL =
            new ApiCall(
                    HttpMethod.GET,
                    "https://jsonplaceholder.typicode.com/posts/1223/comments",
                    200,
                    "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n",
                    "[]");

    // RC1: getters

    @Test
    void shouldReturnCallPassedToConstructor() {
        // Given
        RecordedCall recorded = new RecordedCall(CALL, Map.of("POST_ID", "1223"));
        // When / Then
        assertThat(recorded.getCall(), is(sameInstance(CALL)));
    }

    @Test
    void shouldReturnArgumentsPassedToConstructor() {
        // Given
        RecordedCall recorded = new RecordedCall(CALL, Map.of("POST_ID", "1223"));
        // When / Then
        assertThat(recorded.getPathArguments(), is(equalTo(Map.of("POST_ID", "1223"))));
    }

    @Test
    void shouldKeepOrderOfArguments() {
        // Given
        Map<String, String> arguments = new LinkedHashMap<>();
        arguments.put("USER_ID", "7");
        arguments.put("POST_ID", "1223");
        arguments.put("COMMENT_ID", "3");
        RecordedCall recorded = new RecordedCall(CALL, arguments);
        // When / Then
        assertThat(
                recorded.getPathArguments().keySet(), contains("USER_ID", "POST_ID", "COMMENT_ID"));
    }

    // RP1: can't be modified

    @Test
    void shouldNotAllowChangingArguments() {
        // Given
        RecordedCall recorded = new RecordedCall(CALL, Map.of("POST_ID", "1223"));
        // When
        Map<String, String> arguments = recorded.getPathArguments();
        // Then
        assertThrows(UnsupportedOperationException.class, () -> arguments.put("POST_ID", "1"));
    }

    @Test
    void shouldNotChangeWhenGivenMapChangesLater() {
        // Given
        Map<String, String> arguments = new LinkedHashMap<>();
        arguments.put("POST_ID", "1223");
        RecordedCall recorded = new RecordedCall(CALL, arguments);
        // When
        arguments.put("POST_ID", "1");
        // Then
        assertThat(recorded.getPathArguments(), is(equalTo(Map.of("POST_ID", "1223"))));
    }

    @Test
    void shouldThrowWhenCallIsNull() {
        // When
        NullPointerException e =
                assertThrows(NullPointerException.class, () -> new RecordedCall(null, Map.of()));
        // Then
        assertThat(e.getMessage(), is(equalTo("call")));
    }

    @Test
    void shouldThrowWhenArgumentsAreNull() {
        // When
        NullPointerException e =
                assertThrows(
                        NullPointerException.class,
                        () -> new RecordedCall(CALL, (Map<String, String>) null));
        // Then
        assertThat(e.getMessage(), is(equalTo("arguments")));
    }

    @Test
    void shouldThrowWhenArgumentNameOrValueIsNull() {
        // Given
        Map<String, String> nullName = new HashMap<>();
        nullName.put(null, "1223");
        Map<String, String> nullValue = new HashMap<>();
        nullValue.put("POST_ID", null);

        // When
        NullPointerException e1 =
                assertThrows(NullPointerException.class, () -> new RecordedCall(CALL, nullName));
        NullPointerException e2 =
                assertThrows(NullPointerException.class, () -> new RecordedCall(CALL, nullValue));

        // Then
        assertThat(e1.getMessage(), is(equalTo("arguments")));
        assertThat(e2.getMessage(), is(equalTo("arguments")));
    }
}
