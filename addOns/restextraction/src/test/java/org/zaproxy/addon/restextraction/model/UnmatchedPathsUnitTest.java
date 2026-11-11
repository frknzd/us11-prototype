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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class UnmatchedPathsUnitTest {
    @Test
    void UM1_UM2_UM3_UM4_UP1_UP2_countsBoundsSnapshotsAndValueIdentity() {
        UnmatchedPaths paths = new UnmatchedPaths();
        paths.add(new UrlPath("/a/1"));
        paths.add(new UrlPath("/a/1/"));
        paths.add(new UrlPath("/b"));
        assertEquals(2, paths.counts().get(new UrlPath("/a/1")));
        var snapshot = paths.counts();
        assertEquals(new UrlPath("/a/b"), new UrlPath("/a/b/"));
        assertEquals(new UrlPath("/a/b").hashCode(), new UrlPath("/a/b/").hashCode());
        assertEquals("/users/John Doe", new UrlPath("/users/John%20Doe").toString());
        assertFalse(new UrlPath("/a%2Fb").equals(new UrlPath("/a/b")));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
        for (int i = 0; i < 998; i++) paths.add(new UrlPath("/new/" + i));
        for (int i = 0; i < 5; i++) paths.add(new UrlPath("/overflow/" + i));
        paths.add(new UrlPath("/b"));
        assertEquals(1000, paths.counts().size());
        assertEquals(5, paths.getOverflow());
        assertEquals(2, paths.counts().get(new UrlPath("/b")));
        assertEquals(2, snapshot.size());
        paths.clear();
        assertTrue(paths.counts().isEmpty());
        assertEquals(0, paths.getOverflow());
    }

    @Test
    void UM5_concurrentCounts() throws Exception {
        UnmatchedPaths paths = new UnmatchedPaths();
        var pool = Executors.newFixedThreadPool(8);
        try {
            var tasks = new ArrayList<java.util.concurrent.Future<?>>();
            for (int t = 0; t < 8; t++) {
                final int thread = t;
                tasks.add(
                        pool.submit(
                                () -> {
                                    for (int i = 0; i < 1000; i++)
                                        paths.add(new UrlPath("/" + thread + "/" + i));
                                }));
            }
            for (var task : tasks) task.get(10, TimeUnit.SECONDS);
            assertEquals(
                    8000,
                    paths.getOverflow()
                            + paths.counts().values().stream().mapToInt(Integer::intValue).sum());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void PD1_PD2_PD3_PD4_PD5_PD9_namesToggleAndEncoding() {
        PatternDraft draft = new PatternDraft(new UrlPath("/api/42/comments"));
        assertEquals(3, draft.size());
        assertFalse(draft.isVariable(1));
        assertEquals("42", draft.part(1));
        assertEquals("/api/42/comments", draft.toTemplate());
        draft.toggle(1);
        assertEquals("42", draft.name(1));
        assertEquals("/api/[42]/comments", draft.toTemplate());
        draft.rename(1, "POST_ID");
        assertEquals("/api/[POST_ID]/comments", draft.toTemplate());
        assertThrows(IllegalArgumentException.class, () -> draft.rename(1, "a b"));
        draft.toggle(1);
        assertEquals("/api/42/comments", draft.toTemplate());
        var names = new PatternDraft(new UrlPath("/a-b/%C3%A9/x%20y"));
        names.toggle(0);
        names.toggle(1);
        assertEquals("a_b", names.name(0));
        assertEquals("_", names.name(1));
        assertEquals("x_y", names.name(2));
        assertTrue(names.isVariable(2));
        var repeated = new PatternDraft(new UrlPath("/42/x/42/y/42"));
        for (int i : List.of(0, 2, 4)) repeated.toggle(i);
        assertEquals("42", repeated.name(0));
        assertEquals("42_2", repeated.name(2));
        assertEquals("42_3", repeated.name(4));
        assertThrows(IllegalArgumentException.class, () -> repeated.rename(0, ""));
    }

    @Test
    void PD6_PD7_PD8_PD10_invalidAndForcedVariables() {
        for (String path : List.of("/a//b", "/")) {
            var draft = new PatternDraft(new UrlPath(path));
            assertTrue(draft.problem().isPresent());
            assertThrows(IllegalArgumentException.class, () -> draft.toggle(0));
        }
        for (String path : List.of("/.", "/..", "/a%5Bb", "/50%25", "/a%20b")) {
            var draft = new PatternDraft(new UrlPath(path));
            assertFalse(draft.canBeLiteral(0));
            assertTrue(draft.isVariable(0));
            assertThrows(IllegalArgumentException.class, () -> draft.toggle(0));
            assertTrue(draft.problem().isEmpty());
        }
        var encoded = new PatternDraft(new UrlPath("/%C3%BC/a+b/x:y@z"));
        assertEquals("/%C3%BC/a+b/x:y@z", encoded.toTemplate());
        // SPEC-U08 takes precedence over the contradictory old PD8 whitespace-literal example.
        var space = new PatternDraft(new UrlPath("/my%20doc"));
        assertEquals("/[my_doc]", space.toTemplate());
        var duplicates = new PatternDraft(new UrlPath("/a/b"));
        duplicates.toggle(0);
        duplicates.toggle(1);
        duplicates.rename(1, "a");
        assertTrue(duplicates.problem().isPresent());
    }

    private ApiCall call(String url) {
        return new ApiCall(HttpMethod.GET, url, 200, "", "");
    }

    @Test
    void NS1_NS2_NS3_NS4_NS5_RX3_secondaryDataNeverEntersReport() {
        RestExtractor ex = new RestExtractor();
        ApiNamespace ns = ex.addNamespace("http://localhost/api");
        ex.addEndpoint(ns, "/posts/[ID]");
        ex.record(call("http://localhost/api/x"));
        assertTrue(ex.getUnmatchedPaths(ns).isEmpty());
        ex.startExtraction(ns);
        ex.record(call("http://other/api/x"));
        ex.record(call("http://localhost/outside"));
        assertTrue(ex.getUnmatchedPaths(ns).isEmpty());
        ex.record(call("http://localhost/api/x"));
        assertEquals(1, ex.getUnmatchedPaths(ns).get(new UrlPath("/x")));
        var snapshot = ex.getUnmatchedPaths(ns);
        ex.record(call("http://localhost/api/posts/1"));
        assertEquals(1, ex.getUnmatchedPaths(ns).size());
        assertEquals(1, ex.getReport(ns).getEndpoints().get(0).getCalls().size());
        ex.addEndpoint(ns, "/x");
        assertEquals(1, ex.getUnmatchedPaths(ns).get(new UrlPath("/x")));
        assertTrue(ex.getReport(ns).getEndpoints().get(1).getSpec().isEmpty());
        ex.record(call("http://localhost/api/other"));
        assertEquals(1, snapshot.size());
        ex.clearUnmatchedPaths(ns);
        assertTrue(ex.getUnmatchedPaths(ns).isEmpty());
        assertEquals(0, ex.getUnmatchedOverflow(ns));
        assertEquals(1, ex.getReport(ns).getEndpoints().get(0).getCalls().size());
        ex.record(call("http://localhost/api/other"));
        ex.clear(ns);
        assertTrue(ex.getUnmatchedPaths(ns).isEmpty());
        ex.record(call("http://localhost/api/other"));
        ex.removeNamespace(ns);
        assertTrue(ex.getUnmatchedPaths(ns).isEmpty());
    }
}
