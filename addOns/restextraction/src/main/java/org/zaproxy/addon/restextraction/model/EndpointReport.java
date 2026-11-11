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

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What one endpoint had recorded when the report was made. It holds how often each method was used,
 * the calls themselves and the values each variable had. It is a snapshot, so calls recorded later
 * don't show up in it.
 */
public final class EndpointReport {

    private final String template;
    private final Map<HttpMethod, Integer> callCounts;
    private final List<RecordedCall> calls;
    private final Map<String, Map<String, Integer>> valuesSeen;

    /**
     * @param template the pattern as the user typed it, like {@code /posts/[POST_ID]}
     * @param callCounts how many calls each method had, only for methods that were used
     * @param calls the calls in the order they came in
     * @throws NullPointerException if an argument, a method, a count or a call is null
     * @throws IllegalArgumentException if a count is below 1
     */
    public EndpointReport(
            String template, Map<HttpMethod, Integer> callCounts, List<RecordedCall> calls) {
        this.template = Objects.requireNonNull(template, "template");
        this.callCounts = copyOfCounts(Objects.requireNonNull(callCounts, "callCounts"));
        this.calls = copyOfCalls(Objects.requireNonNull(calls, "calls"));
        this.valuesSeen = valuesSeenIn(this.calls);
    }

    public String getTemplate() {
        return template;
    }

    /**
     * Returns the count of each method that was used. GET always comes before POST and so on, in
     * the order of {@link HttpMethod}, so the report looks the same every time. The map can't be
     * changed.
     */
    public Map<HttpMethod, Integer> getCallCounts() {
        return callCounts;
    }

    /** Returns the calls in the order they came in. The list can't be changed. */
    public List<RecordedCall> getCalls() {
        return calls;
    }

    /**
     * Returns per variable the values it had and how many calls had each value, like {@code
     * POST_ID={1=3, abcd=1}}. Variables and values are in the order they first came in. The maps
     * can't be changed.
     */
    public Map<String, Map<String, Integer>> getValuesSeen() {
        return valuesSeen;
    }

    private static Map<String, Map<String, Integer>> valuesSeenIn(List<RecordedCall> calls) {
        Map<String, Map<String, Integer>> values = new LinkedHashMap<>();
        for (RecordedCall call : calls) {
            for (Map.Entry<String, String> argument : call.getArguments().entrySet()) {
                values.computeIfAbsent(argument.getKey(), name -> new LinkedHashMap<>())
                        .merge(argument.getValue(), 1, Integer::sum);
            }
        }
        values.replaceAll((name, seen) -> Collections.unmodifiableMap(seen));
        return Collections.unmodifiableMap(values);
    }

    private static Map<HttpMethod, Integer> copyOfCounts(Map<HttpMethod, Integer> callCounts) {
        Map<HttpMethod, Integer> copy = new EnumMap<>(HttpMethod.class);
        callCounts.forEach(
                (method, count) -> {
                    Objects.requireNonNull(method, "callCounts");
                    Objects.requireNonNull(count, "callCounts");
                    if (count < 1) {
                        throw new IllegalArgumentException(
                                "The count of " + method + " must be at least 1, was " + count);
                    }
                    copy.put(method, count);
                });
        return Collections.unmodifiableMap(copy);
    }

    private static List<RecordedCall> copyOfCalls(List<RecordedCall> calls) {
        calls.forEach(call -> Objects.requireNonNull(call, "calls"));
        return List.copyOf(calls);
    }
}
