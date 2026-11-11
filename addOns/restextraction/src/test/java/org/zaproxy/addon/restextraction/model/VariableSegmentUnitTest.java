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
import static org.hamcrest.Matchers.is;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit test for {@link VariableSegment}. */
class VariableSegmentUnitTest {

    @Test
    void shouldMatchVariableWithAnyPart() {
        // Given
        PathSegment segment = new VariableSegment("POST_ID");
        // When / Then
        assertThat(segment.matches("42"), is(true));
        assertThat(segment.matches("hello world"), is(true));
    }

    @Test
    void shouldNotMatchVariableWithEmptyPart() {
        // Given
        PathSegment segment = new VariableSegment("POST_ID");
        // When / Then
        assertThat(segment.matches(""), is(false));
    }

    @Test
    void shouldAddArgumentForVariable() {
        // Given
        PathSegment segment = new VariableSegment("POST_ID");
        Map<String, String> args = new HashMap<>();
        // When
        segment.addArgument("42", args);
        // Then
        assertThat(args, is(equalTo(Map.of("POST_ID", "42"))));
    }

    @Test
    void shouldKeepOtherArgumentsWhenAddingVariable() {
        // Given
        PathSegment segment = new VariableSegment("POST_ID");
        Map<String, String> args = new HashMap<>(Map.of("USER_ID", "1"));
        // When
        segment.addArgument("42", args);
        Map<String, String> expected = Map.of("USER_ID", "1", "POST_ID", "42");
        // Then
        assertThat(args, is(equalTo(expected)));
    }
}
