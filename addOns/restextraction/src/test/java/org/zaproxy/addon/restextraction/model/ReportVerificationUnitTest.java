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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ReportVerificationUnitTest {
    private ApiCall call(String query) {
        return new ApiCall(
                HttpMethod.GET,
                "http://localhost/posts/1",
                200,
                "",
                "",
                new RequestData(query, List.of(), "", "", BodyState.NONE));
    }

    private SpecItem query(int min) {
        return SpecItem.parameter(
                HttpMethod.GET,
                new ParameterSpec(
                        ArgumentLocation.QUERY,
                        "n",
                        new IntegerType(BigInteger.valueOf(min), null),
                        true));
    }

    @Test
    void EI1_EI2_EI3_EI4_EI5_EI6_snapshotsRejudgingAndCache() {
        Endpoint endpoint = new Endpoint("/posts/[ID]");
        for (String q : List.of("n=1", "n=x", ""))
            assertTrue(endpoint.record(call(q), new UrlPath("/posts/1")));
        EndpointReport before = endpoint.createReport();
        assertTrue(before.getSpec().isEmpty());
        var firstCall = before.getCalls().get(0);
        var parsed = firstCall.getRequest();
        endpoint.addSpecItem(query(0));
        EndpointReport after = endpoint.createReport();
        assertEquals(1, after.getValidCount());
        assertEquals(2, after.getInvalidCount());
        assertEquals(1, after.getViolationCounts().get(ViolationKind.INVALID_TYPE));
        assertEquals(1, after.getViolationCounts().get(ViolationKind.MISSING_ARGUMENT));
        assertEquals(VerdictStatus.NOT_VERIFIED, before.getVerdict(firstCall).getStatus());
        assertSame(parsed, after.getCalls().get(0).getRequest());
        assertSame(after.getVerdict(firstCall), endpoint.createReport().getVerdict(firstCall));
        endpoint.addSpecItem(SpecItem.contentType(HttpMethod.GET, "application/json"));
        assertNotSame(after.getVerdict(firstCall), endpoint.createReport().getVerdict(firstCall));
        assertThrows(UnsupportedOperationException.class, () -> after.getViolationCounts().clear());
        endpoint.clear();
        assertTrue(endpoint.createReport().getCalls().isEmpty());
        assertFalse(endpoint.getSpec().isEmpty());
        assertEquals(3, after.getCalls().size());
    }

    @Test
    void ES9_invalidPathAndDuplicateRefusedAtomically() {
        Endpoint endpoint = new Endpoint("/posts/[ID]");
        var wrong =
                SpecItem.parameter(
                        HttpMethod.GET,
                        new ParameterSpec(ArgumentLocation.PATH, "OTHER", new StringType(), false));
        assertThrows(IllegalArgumentException.class, () -> endpoint.addSpecItem(wrong));
        assertTrue(endpoint.getSpec().isEmpty());
        var right =
                SpecItem.parameter(
                        HttpMethod.GET,
                        new ParameterSpec(ArgumentLocation.PATH, "ID", new StringType(), false));
        endpoint.addSpecItem(right);
        EndpointSpec old = endpoint.getSpec();
        assertThrows(IllegalArgumentException.class, () -> endpoint.addSpecItem(right));
        assertSame(old, endpoint.getSpec());
    }

    @Test
    void NS6_RX1_RX2_RX4_facadeAndLifecycle() {
        RestExtractor ex = new RestExtractor();
        ApiNamespace ns = ex.addNamespace("http://localhost");
        ex.addEndpoint(ns, "/posts/[ID]");
        ex.startExtraction(ns);
        ex.record(call("n=1"));
        var first = ex.getReport(ns).getEndpoints().get(0);
        assertTrue(first.getSpec().isEmpty());
        assertEquals(
                VerdictStatus.NOT_VERIFIED, first.getVerdict(first.getCalls().get(0)).getStatus());
        var item = query(0);
        ex.addSpecItem(ns, "/posts/[ID]", item);
        assertEquals(1, ex.getReport(ns).getEndpoints().get(0).getValidCount());
        ex.replaceSpecItem(ns, "/posts/[ID]", item, query(2));
        assertEquals(1, ex.getReport(ns).getEndpoints().get(0).getInvalidCount());
        ex.removeSpecItem(ns, "/posts/[ID]", SpecItem.method(HttpMethod.GET));
        assertTrue(ex.getReport(ns).getEndpoints().get(0).getSpec().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ex.addSpecItem(ns, "/missing", item));
        ex.addSpecItem(ns, "/posts/[ID]", item);
        ex.clear(ns);
        assertFalse(ex.getReport(ns).getEndpoints().get(0).getSpec().isEmpty());
        ex.removeEndpoint(ns, "/posts/[ID]");
        ex.addEndpoint(ns, "/posts/[ID]");
        assertTrue(ex.getReport(ns).getEndpoints().get(0).getSpec().isEmpty());
    }

    @Test
    void replacementValidatesFinalStateAndRefusalKeepsPreviousSpec() {
        Endpoint e = new Endpoint("/x");
        var json = SpecItem.contentType(HttpMethod.POST, "application/json");
        e.addSpecItem(json);
        e.addSpecItem(SpecItem.contentType(HttpMethod.POST, "text/plain"));
        e.addSpecItem(
                SpecItem.parameter(
                        HttpMethod.POST,
                        new ParameterSpec(
                                ArgumentLocation.BODY, "title", new StringType(), false)));
        EndpointSpec old = e.getSpec();
        assertThrows(IllegalArgumentException.class, () -> e.removeSpecItem(json));
        assertSame(old, e.getSpec());
        e.replaceSpecItem(
                json, SpecItem.contentType(HttpMethod.POST, "application/x-www-form-urlencoded"));
        assertEquals(
                2, e.getSpec().operation(HttpMethod.POST).orElseThrow().getContentTypes().size());
    }

    @Test
    void CC1_recordingAndEditingUseOneSpecPerReport() throws Exception {
        Endpoint e = new Endpoint("/posts/[ID]");
        var initial = query(0);
        e.addSpecItem(initial);
        var pool = Executors.newFixedThreadPool(9);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < 8; t++)
                futures.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    for (int i = 0; i < 1000; i++)
                                        e.record(call("n=1"), new UrlPath("/posts/1"));
                                    return null;
                                }));
            futures.add(
                    pool.submit(
                            () -> {
                                start.await();
                                SpecItem previous = initial;
                                for (int i = 0; i < 200; i++) {
                                    SpecItem next = query(i % 2 == 0 ? 2 : 0);
                                    e.replaceSpecItem(previous, next);
                                    previous = next;
                                    EndpointReport report = e.createReport();
                                    var parameter =
                                            report.getSpec()
                                                    .operation(HttpMethod.GET)
                                                    .orElseThrow()
                                                    .getParameters()
                                                    .get(0);
                                    for (RecordedCall c : report.getCalls())
                                        assertSame(
                                                parameter,
                                                report.getVerdict(c)
                                                        .getChecks()
                                                        .get(0)
                                                        .getParameter());
                                }
                                return null;
                            }));
            start.countDown();
            for (var future : futures) future.get(30, TimeUnit.SECONDS);
            assertEquals(8000, e.createReport().getCalls().size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void CC2_specEditFinishesWhileRecordingIsBlocked() throws Exception {
        Endpoint endpoint = new Endpoint("/posts/[ID]");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ApiCall blocked =
                new ApiCall(HttpMethod.GET, "http://localhost/posts/1", 200, "", "") {
                    @Override
                    public RequestData getRequest() {
                        entered.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        }
                        return super.getRequest();
                    }
                };
        var pool = Executors.newFixedThreadPool(2);
        try {
            var recording = pool.submit(() -> endpoint.record(blocked, new UrlPath("/posts/1")));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            pool.submit(() -> endpoint.addSpecItem(query(0))).get(2, TimeUnit.SECONDS);
            release.countDown();
            assertTrue(recording.get(2, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void CC2_recordingDoesNotAcquireSpecEditMonitor() throws Exception {
        Endpoint e = new Endpoint("/posts/[ID]");
        var pool = Executors.newSingleThreadExecutor();
        try {
            synchronized (e) {
                assertTrue(
                        pool.submit(() -> e.record(call("n=1"), new UrlPath("/posts/1")))
                                .get(2, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
