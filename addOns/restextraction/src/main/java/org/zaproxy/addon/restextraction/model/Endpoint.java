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

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One endpoint of an API, like {@code /posts/[POST_ID]/comments}, and the calls that were made to
 * it. The pattern decides if a path belongs to this endpoint, the endpoint keeps the calls.
 */
public class Endpoint {

    private final EndpointPattern pattern;
    // Guarded by itself: the proxy threads add calls while the Swing thread asks for a report.
    private final List<RecordedCall> calls = new ArrayList<>();

    /**
     * @param template the pattern relative to the namespace, like {@code /posts/[POST_ID]}
     * @throws IllegalArgumentException if the template is not a valid pattern
     */
    public Endpoint(String template) {
        this.pattern = new EndpointPattern(template);
    }

    public String getTemplate() {
        return pattern.getTemplate();
    }

    /**
     * Keeps the call if the path fits the pattern of this endpoint.
     *
     * @param path the path of the call after the base path of the namespace
     * @return true if the call was recorded
     */
    public boolean record(ApiCall call, UrlPath path) {
        if (!pattern.matches(path)) {
            return false;
        }
        RecordedCall recorded = new RecordedCall(call, pattern.argumentsOf(path));
        synchronized (calls) {
            calls.add(recorded);
        }
        return true;
    }

    /** Forgets the recorded calls. Reports that were made before keep theirs. */
    public void clear() {
        synchronized (calls) {
            calls.clear();
        }
    }

    /** Gives the calls recorded so far and their count per method. Later calls are not in it. */
    public EndpointReport createReport() {
        List<RecordedCall> snapshot;
        synchronized (calls) {
            snapshot = List.copyOf(calls);
        }
        // Counted on the copy, so the counts and the list of calls always agree.
        Map<HttpMethod, Integer> callCounts = new EnumMap<>(HttpMethod.class);
        for (HttpMethod method : HttpMethod.values()) {
            int count = countCalls(snapshot, method);
            if (count > 0) {
                callCounts.put(method, count);
            }
        }
        return new EndpointReport(pattern.getTemplate(), callCounts, snapshot);
    }

    private static int countCalls(List<RecordedCall> calls, HttpMethod method) {
        int count = 0;
        for (RecordedCall recorded : calls) {
            if (recorded.getCall().getMethod() == method) {
                count++;
            }
        }
        return count;
    }
}
