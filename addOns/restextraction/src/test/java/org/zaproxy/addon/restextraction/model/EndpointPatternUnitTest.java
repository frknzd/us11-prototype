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
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Unit test for {@link EndpointPattern}. */
class EndpointPatternUnitTest {

    private static final String TEMPLATE = "/users/[USER_ID]/posts/[POST_ID]";

    // EM1, EM2, EM6, EM8, AR1, AR2: matching with variables

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/users/12/posts/42",
                "/users/12/posts/42/",
                "/users/hello%20world/posts/42",
                "/users/a%2Fb/posts/42",
                "/users/a+b/posts/42"
            })
    void shouldMatchPathWithValuesForEveryVariable(String path) {
        // Given
        EndpointPattern pattern = new EndpointPattern(TEMPLATE);
        // When / Then
        assertThat(pattern.matches(path(path)), is(true));
    }

    @Test
    void shouldMatchWhenTemplateHasTrailingSlash() {
        // Given
        EndpointPattern pattern = new EndpointPattern(TEMPLATE + "/");
        // When / Then
        assertThat(pattern.matches(path("/users/12/posts/42")), is(true));
    }

    @Test
    void shouldGiveValueOfEveryVariable() {
        // Given
        EndpointPattern pattern = new EndpointPattern(TEMPLATE);
        Map<String, String> expected = Map.of("USER_ID", "12", "POST_ID", "42");
        // When / Then
        assertThat(pattern.argumentsOf(path("/users/12/posts/42")), is(equalTo(expected)));
    }

    @Test
    void shouldGiveValuesInTemplateOrder() {
        // Given
        EndpointPattern pattern = new EndpointPattern("/[Z]/[USER_ID]/[A]/[POST_ID]/[M]");
        // When / Then
        assertThat(
                pattern.argumentsOf(path("/1/2/3/4/5")).keySet(),
                contains("Z", "USER_ID", "A", "POST_ID", "M"));
    }

    @Test
    void shouldGiveDecodedValues() {
        // Given
        EndpointPattern pattern = new EndpointPattern(TEMPLATE);
        Map<String, String> expected = Map.of("USER_ID", "a/b", "POST_ID", "hello world");
        // When / Then
        assertThat(
                pattern.argumentsOf(path("/users/a%2Fb/posts/hello%20world")),
                is(equalTo(expected)));
    }

    @Test
    void shouldKeepPlusInValues() {
        // Given
        EndpointPattern pattern = new EndpointPattern(TEMPLATE);
        Map<String, String> expected = Map.of("USER_ID", "a+b", "POST_ID", "42");
        // When / Then
        assertThat(pattern.argumentsOf(path("/users/a+b/posts/42")), is(equalTo(expected)));
    }

    // EM3 to EM5, EM7, EM9: cases that should not match

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/users/12/comments/42",
                "/users/12/posts",
                "/users/12/posts/42/likes",
                "/users//posts/42",
                "/Users/12/posts/42",
                "/users/12/posts/42//",
                "/",
                ""
            })
    void shouldNotMatchOtherPaths(String path) {
        // Given
        EndpointPattern pattern = new EndpointPattern(TEMPLATE);
        // When / Then
        assertThat(pattern.matches(path(path)), is(false));
        assertThat(pattern.argumentsOf(path(path)), is(anEmptyMap()));
    }

    // AR3: pattern without variables

    @Test
    void shouldMatchLiteralOnlyPattern() {
        // Given
        EndpointPattern pattern = new EndpointPattern("/health");
        // When / Then
        assertThat(pattern.matches(path("/health")), is(true));
        assertThat(pattern.matches(path("/health/db")), is(false));
        assertThat(pattern.matches(path("/Health")), is(false));
    }

    @Test
    void shouldGiveNoValuesForLiteralOnlyPattern() {
        // Given
        EndpointPattern pattern = new EndpointPattern("/health");
        // When / Then
        assertThat(pattern.argumentsOf(path("/health")), is(anEmptyMap()));
    }

    @Test
    void shouldMatchDecodedLiteral() {
        // Given
        EndpointPattern pattern = new EndpointPattern("/files/my%20doc");
        // When / Then
        assertThat(pattern.matches(path("/files/my%20doc")), is(true));
        assertThat(pattern.matches(path("/files/my%20Doc")), is(false));
    }

    // EP5 to EP22: template validation

    static Stream<Arguments> invalidTemplates() {
        return Stream.of(
                // not a path
                arguments("", "must start with /"),
                arguments("posts/[POST_ID]", "must start with /"),
                arguments(" /posts", "must start with /"),
                arguments("https://example.com/posts", "must start with /"),
                // no path at all
                arguments("/", "has no path"),
                arguments("//", "has no path"),
                // empty parts
                arguments("//posts", "empty part"),
                arguments("/posts//[POST_ID]", "empty part"),
                arguments("/posts//", "empty part"),
                // query, fragment and spaces
                arguments("/posts?sort=asc", "query or fragment"),
                arguments("/posts/[POST_ID]?", "query or fragment"),
                arguments("/posts#top", "query or fragment"),
                arguments("/posts/ ", "spaces"),
                arguments("/my doc", "spaces"),
                arguments("/posts/\t[POST_ID]", "spaces"),
                // dot parts
                arguments("/posts/./1", ". or .."),
                arguments("/posts/..", ". or .."),
                arguments("/posts/%2E%2E", ". or .."),
                arguments("/posts/%2E%2E/x", ". or .."),
                arguments("/posts/%2e/x", ". or .."),
                // broken variables
                arguments("/posts/[POST_ID", "not a valid variable"),
                arguments("/posts/POST_ID]", "not a valid variable"),
                arguments("/posts/[]", "not a valid variable"),
                arguments("/posts/[POST-ID]", "not a valid variable"),
                arguments("/posts/[POST.ID]", "not a valid variable"),
                arguments("/posts/id[POST_ID]", "not a valid variable"),
                arguments("/posts/[POST_ID]id", "not a valid variable"),
                arguments("/posts/[[POST_ID]]", "not a valid variable"),
                arguments("/posts/[POST_ID][ID]", "not a valid variable"),
                arguments("/posts/][", "not a valid variable"),
                arguments("/posts/[a b]", "variable names can only have letters, digits and _"),
                // broken % escapes
                arguments("/posts/100%", "broken % escape"),
                arguments("/posts/%2", "broken % escape"),
                arguments("/posts/%zz", "broken % escape"),
                // encoded brackets
                arguments("/%5BID%5D", "encoded bracket"),
                arguments("/posts/%5bid%5d", "encoded bracket"),
                arguments("/posts/id%5D", "encoded bracket"),
                // same name twice
                arguments("/users/[ID]/posts/[ID]", "[ID] is used more than once"),
                arguments("/[ID]/x/y/[ID]", "[ID] is used more than once"));
    }

    @ParameterizedTest
    @MethodSource("invalidTemplates")
    void shouldRejectInvalidTemplate(String template, String reason) {
        // When
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> new EndpointPattern(template));
        // Then
        assertThat(e.getMessage(), containsString("Invalid endpoint pattern '" + template + "'"));
        assertThat(e.getMessage(), containsString(reason));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/posts",
                "/posts/",
                "/[ID]",
                "/[1]",
                "/[_]",
                "/[a_very_long_variable_name_with_digits_123]",
                "/posts/[POST_ID]/comments",
                "/[id]/[ID]",
                "/v1.0/items",
                "/a+b",
                "/c++",
                "/a%2Fb",
                "/files/my%20doc",
                "/caf\u00e9",
                "/posts/.hidden",
                "/posts/..."
            })
    void shouldAcceptUnusualButValidTemplate(String template) {
        // Given / When / Then
        assertDoesNotThrow(() -> new EndpointPattern(template));
    }

    @Test
    void shouldAllowSameNameInDifferentPatterns() {
        // Given / When / Then
        assertDoesNotThrow(() -> new EndpointPattern("/users/[ID]"));
        assertDoesNotThrow(() -> new EndpointPattern("/posts/[ID]"));
    }

    @Test
    void shouldTreatNamesWithOtherCaseAsDifferentVariables() {
        // Given
        EndpointPattern pattern = new EndpointPattern("/[id]/[ID]");
        Map<String, String> expected = Map.of("id", "1", "ID", "2");
        // When / Then
        assertThat(pattern.argumentsOf(path("/1/2")), is(equalTo(expected)));
    }

    @Test
    void shouldMatchEncodedSlashLiteralOnlyAsOnePart() {
        // Given
        EndpointPattern pattern = new EndpointPattern("/a%2Fb");
        // When / Then
        assertThat(pattern.matches(path("/a%2Fb")), is(true));
        assertThat(pattern.matches(path("/a/b")), is(false));
    }

    @Test
    void shouldMatchNonAsciiLiteral() {
        // Given
        EndpointPattern pattern = new EndpointPattern("/caf\u00e9");
        // When / Then
        assertThat(pattern.matches(path("/caf%C3%A9")), is(true));
    }

    @Test
    void shouldThrowWhenTemplateIsNull() {
        // Given / When / Then
        assertThrows(NullPointerException.class, () -> new EndpointPattern(null));
    }

    @Test
    void shouldReturnTemplateAsTyped() {
        // Given
        EndpointPattern pattern = new EndpointPattern(TEMPLATE);
        // When / Then
        assertThat(pattern.getTemplate(), is(equalTo(TEMPLATE)));
    }

    private static UrlPath path(String path) {
        return new UrlPath(path);
    }
}
