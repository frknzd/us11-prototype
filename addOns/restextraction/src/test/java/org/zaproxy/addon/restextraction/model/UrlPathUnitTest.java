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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Unit test for {@link UrlPath}. */
class UrlPathUnitTest {

    // UP1 to UP6, UP11 to UP13: reading the path

    @Test
    void shouldSplitPathIntoParts() {
        // Given
        UrlPath path = path("/posts/1/comments");
        // When / Then
        assertThat(partsOf(path), contains("posts", "1", "comments"));
    }

    @Test
    void shouldDropOneTrailingSlash() {
        // Given
        UrlPath path = path("/posts/1/");
        // When / Then
        assertThat(partsOf(path), contains("posts", "1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", ""})
    void shouldHaveNoPartsWhenPathIsEmpty(String emptyPath) {
        // Given
        UrlPath path = path(emptyPath);
        // When / Then
        assertThat(path.size(), is(0));
    }

    @Test
    void shouldDecodePartsWhenPercentEncoded() {
        // Given
        UrlPath path = path("/users/John%20Doe");
        // When / Then
        assertThat(partsOf(path), contains("users", "John Doe"));
    }

    @Test
    void shouldKeepEncodedSlashInOnePart() {
        // Given
        UrlPath path = path("/a%2Fb");
        // When / Then
        assertThat(partsOf(path), contains("a/b"));
    }

    @Test
    void shouldKeepEmptyPartInTheMiddle() {
        // Given
        UrlPath path = path("/posts//comments");
        // When / Then
        assertThat(partsOf(path), contains("posts", "", "comments"));
    }

    @Test
    void shouldKeepCaseOfParts() {
        // Given
        UrlPath path = path("/Posts");
        // When / Then
        assertThat(path.part(0), is(equalTo("Posts")));
        assertThat(partsOf(path), is(not(equalTo(partsOf(path("/posts"))))));
    }

    @Test
    void shouldKeepPlusInParts() {
        // Given
        UrlPath path = path("/c++/x");
        // When / Then
        assertThat(partsOf(path), contains("c++", "x"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/a%2", "/a%zz"})
    void shouldThrowWhenEscapeIsBroken(String brokenPath) {
        // When
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> new UrlPath(brokenPath));
        // Then
        assertThat(e.getMessage(), containsString(brokenPath));
    }

    // UP7 to UP9: startsWith

    @Test
    void shouldStartWithPrefixWhenFirstPartsAreEqual() {
        // Given
        UrlPath path = path("/api/v1/posts");
        // When / Then
        assertThat(path.startsWith(path("/api/v1")), is(true));
    }

    @Test
    void shouldNotStartWithPrefixWhenPartIsOnlyPartlyEqual() {
        // Given
        UrlPath path = path("/apiv2/posts");
        // When / Then
        assertThat(path.startsWith(path("/api")), is(false));
    }

    @Test
    void shouldNotStartWithPrefixWhenCaseDiffers() {
        // Given
        UrlPath path = path("/Api/posts");
        // When / Then
        assertThat(path.startsWith(path("/api")), is(false));
    }

    @Test
    void shouldNotStartWithPrefixWhenPrefixIsLonger() {
        // Given
        UrlPath path = path("/api");
        // When / Then
        assertThat(path.startsWith(path("/api/v1")), is(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/posts", "/posts", "/"})
    void shouldStartWithPrefixWhenPrefixHasNoParts(String anyPath) {
        // Given
        UrlPath path = path(anyPath);
        // When / Then
        assertThat(path.startsWith(path("/")), is(true));
    }

    // UP10: after

    @Test
    void shouldGiveRemainingPartsAfterPrefix() {
        // Given
        UrlPath path = path("/api/v1/posts/1");
        // When
        UrlPath rest = path.after(path("/api/v1"));
        // Then
        assertThat(partsOf(rest), contains("posts", "1"));
    }

    @Test
    void shouldNotChangeOriginalWhenAfterIsCalled() {
        // Given
        UrlPath path = path("/api/v1/posts/1");
        // When
        path.after(path("/api/v1"));
        // Then
        assertThat(partsOf(path), contains("api", "v1", "posts", "1"));
    }

    @Test
    void shouldGiveNoPartsAfterPrefixWhenPrefixIsWholePath() {
        // Given
        UrlPath path = path("/api/v1");
        // When
        UrlPath rest = path.after(path("/api/v1"));
        // Then
        assertThat(rest.size(), is(0));
    }

    // UP14: after is only allowed when startsWith is true

    @ParameterizedTest
    @ValueSource(strings = {"/posts/1", "/apiv2/posts", "/"})
    void shouldThrowWhenAfterIsCalledWithOtherPrefix(String notUnderApi) {
        // Given
        UrlPath path = path(notUnderApi);
        // When / Then
        assertThrows(IllegalArgumentException.class, () -> path.after(path("/api")));
    }

    private static UrlPath path(String path) {
        return new UrlPath(path);
    }

    private static List<String> partsOf(UrlPath path) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < path.size(); i++) {
            parts.add(path.part(i));
        }
        return parts;
    }
}
