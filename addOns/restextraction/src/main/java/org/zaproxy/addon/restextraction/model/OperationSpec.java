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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class OperationSpec {
    private final HttpMethod method;
    private final List<String> contentTypes;
    private final List<ParameterSpec> parameters;

    public OperationSpec(
            HttpMethod method, List<String> contentTypes, List<ParameterSpec> parameters) {
        this.method = Objects.requireNonNull(method, "method");
        this.contentTypes = contentTypes.stream().map(MediaType::normalise).toList();
        this.parameters = List.copyOf(parameters);
    }

    public HttpMethod getMethod() {
        return method;
    }

    public List<String> getContentTypes() {
        return contentTypes;
    }

    public List<ParameterSpec> getParameters() {
        return parameters;
    }

    public Verdict verify(RecordedCall call) {
        var parsed = call.getRequest();
        var raw = call.getCall().getRequest();
        List<Check> checks = new ArrayList<>();
        List<Violation> violations = new ArrayList<>();
        List<Argument> unchecked = new ArrayList<>();
        List<Warning> warnings = new ArrayList<>(parsed.getWarnings());
        if (!contentTypes.isEmpty()
                && raw.hasBody()
                && !contentTypes.contains(parsed.getContentType()))
            violations.add(
                    new Violation(
                            ViolationKind.UNSPECIFIED_CONTENT_TYPE,
                            null,
                            "",
                            parsed.getContentType(),
                            List.of(method.name(), String.join(", ", contentTypes))));
        if (parsed.isBodyMalformed())
            violations.add(
                    new Violation(
                            ViolationKind.MALFORMED_BODY,
                            ArgumentLocation.BODY,
                            "",
                            "",
                            List.of(parsed.getMalformedBodyReason())));
        boolean skipBody = parsed.isBodyMalformed() || !parsed.isBodyChecked();
        if (!parsed.isBodyChecked() && declares(ArgumentLocation.BODY)) {
            String reason =
                    raw.getBodyState() == BodyState.CUT
                            ? "cut"
                            : raw.getBodyState() == BodyState.BINARY ? "binary" : "type";
            warnings.add(
                    new Warning(
                            WarningKind.BODY_NOT_CHECKED,
                            ArgumentLocation.BODY,
                            "",
                            List.of(reason, parsed.getContentType())));
        }
        boolean json = MediaType.isJson(parsed.getContentType());
        Set<ParameterSpec> found = new HashSet<>();
        List<String> invalidContainers = new ArrayList<>();
        Map<RepeatedName, Integer> repeated = new LinkedHashMap<>();
        for (Argument a : call.getArguments()) {
            if (a.getLocation() == ArgumentLocation.BODY && skipBody) continue;
            if (a.getLocation() == ArgumentLocation.QUERY
                    || a.getLocation() == ArgumentLocation.BODY && !json)
                repeated.merge(new RepeatedName(a.getLocation(), a.getName()), 1, Integer::sum);
            ParameterSpec p = find(a, json);
            if (p != null) {
                found.add(p);
                Check check = p.check(a);
                checks.add(check);
                if (!check.isMatched()) {
                    violations.add(check.getViolation());
                    if (a.isContainer()) invalidContainers.add(a.getName());
                }
                p.getType().warningFor(a).ifPresent(warnings::add);
            } else if (!a.isContainer()
                    && (a.getLocation() == ArgumentLocation.QUERY
                            || a.getLocation() == ArgumentLocation.BODY)
                    && declares(a.getLocation())
                    && !insideInvalidContainer(a, invalidContainers)) {
                violations.add(
                        new Violation(
                                ViolationKind.EXTRA_ARGUMENT,
                                a.getLocation(),
                                a.getName(),
                                a.getValue(),
                                List.of()));
            } else unchecked.add(a);
        }
        for (ParameterSpec p : parameters) {
            if (p.isRequired()
                    && !(p.getLocation() == ArgumentLocation.BODY && skipBody)
                    && !found.contains(p))
                violations.add(
                        new Violation(
                                ViolationKind.MISSING_ARGUMENT,
                                p.getLocation(),
                                p.getName(),
                                "",
                                List.of()));
        }
        repeated.forEach(
                (key, count) -> {
                    if (count > 1)
                        warnings.add(
                                new Warning(
                                        WarningKind.REPEATED_PARAMETER,
                                        key.location,
                                        key.name,
                                        List.of(Integer.toString(count))));
                });
        return new Verdict(checks, violations, unchecked, warnings);
    }

    private ParameterSpec find(Argument a, boolean json) {
        ParameterSpec match = null;
        for (ParameterSpec p : parameters)
            if (p.accepts(a, json) && (match == null || p.moreSpecificThan(match))) match = p;
        return match;
    }

    private boolean declares(ArgumentLocation location) {
        return parameters.stream().anyMatch(p -> p.getLocation() == location);
    }

    private boolean insideInvalidContainer(Argument a, List<String> containers) {
        if (a.getLocation() != ArgumentLocation.BODY) return false;
        return containers.stream()
                .anyMatch(c -> a.getName().startsWith(c + ".") || a.getName().startsWith(c + "[]"));
    }

    private record RepeatedName(ArgumentLocation location, String name) {}
}
