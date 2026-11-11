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
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Persistent immutable specification; refused edits cannot change the previous value. */
public final class EndpointSpec {
    private final Map<HttpMethod, OperationSpec> operations;
    private final List<SpecItem> items;

    private EndpointSpec(List<SpecItem> items) {
        this.items =
                items.stream()
                        .sorted(Comparator.comparingInt(i -> i.getMethod().ordinal()))
                        .toList();
        Map<HttpMethod, OperationSpec> ops = new EnumMap<>(HttpMethod.class);
        for (SpecItem item : this.items) {
            HttpMethod method = item.getMethod();
            if (ops.containsKey(method)) continue;
            List<String> types =
                    this.items.stream()
                            .filter(
                                    i ->
                                            i.getMethod() == method
                                                    && i.getKind() == SpecItemKind.CONTENT_TYPE)
                            .map(SpecItem::getMediaType)
                            .toList();
            List<ParameterSpec> parameters =
                    this.items.stream()
                            .filter(i -> i.getMethod() == method && i.getParameter() != null)
                            .map(SpecItem::getParameter)
                            .toList();
            if (!types.isEmpty()
                    && parameters.stream().anyMatch(p -> p.getLocation() == ArgumentLocation.BODY)
                    && types.stream().noneMatch(t -> MediaType.isJson(t) || MediaType.isForm(t)))
                throw new SpecValidationException("bodyMediaType");
            ops.put(method, new OperationSpec(method, types, parameters));
        }
        operations = Collections.unmodifiableMap(ops);
    }

    public static EndpointSpec empty() {
        return new EndpointSpec(List.of());
    }

    public boolean isEmpty() {
        return operations.isEmpty();
    }

    public Optional<OperationSpec> operation(HttpMethod method) {
        return Optional.ofNullable(operations.get(method));
    }

    public List<SpecItem> items() {
        return items;
    }

    public EndpointSpec with(SpecItem item) {
        Objects.requireNonNull(item, "item");
        if (items.stream().anyMatch(i -> i.sameKey(item)))
            throw new SpecValidationException("duplicate");
        List<SpecItem> next = new ArrayList<>(items);
        if (item.getKind() != SpecItemKind.METHOD && !operations.containsKey(item.getMethod()))
            next.add(SpecItem.method(item.getMethod()));
        next.add(item);
        return new EndpointSpec(next);
    }

    public EndpointSpec without(SpecItem item) {
        Objects.requireNonNull(item, "item");
        return new EndpointSpec(
                items.stream()
                        .filter(
                                i ->
                                        item.getKind() == SpecItemKind.METHOD
                                                ? i.getMethod() != item.getMethod()
                                                : !i.sameKey(item))
                        .toList());
    }

    /** Validates the final replacement, never an intermediate removal. */
    public EndpointSpec replacing(SpecItem old, SpecItem replacement) {
        Objects.requireNonNull(old, "old");
        Objects.requireNonNull(replacement, "replacement");
        if (items.stream().noneMatch(i -> i.sameKey(old)))
            throw new SpecValidationException("missingItem");
        List<SpecItem> next =
                new ArrayList<>(
                        items.stream()
                                .filter(
                                        i ->
                                                old.getKind() == SpecItemKind.METHOD
                                                        ? i.getMethod() != old.getMethod()
                                                        : !i.sameKey(old))
                                .toList());
        if (next.stream().anyMatch(i -> i.sameKey(replacement)))
            throw new SpecValidationException("duplicate");
        if (replacement.getKind() != SpecItemKind.METHOD
                && next.stream()
                        .noneMatch(
                                i ->
                                        i.getMethod() == replacement.getMethod()
                                                && i.getKind() == SpecItemKind.METHOD))
            next.add(SpecItem.method(replacement.getMethod()));
        next.add(replacement);
        return new EndpointSpec(next);
    }

    public Verdict verify(RecordedCall call) {
        if (isEmpty()) return Verdict.notVerified(false);
        if (isPreflight(call)) return Verdict.notVerified(true);
        OperationSpec op = operations.get(call.getCall().getMethod());
        if (op == null)
            return new Verdict(
                    List.of(),
                    List.of(
                            new Violation(
                                    ViolationKind.UNSPECIFIED_METHOD,
                                    null,
                                    "",
                                    call.getCall().getMethod().name(),
                                    operations.keySet().stream().map(Enum::name).toList())),
                    List.of(),
                    List.of());
        return op.verify(call);
    }

    private static boolean isPreflight(RecordedCall call) {
        return call.getCall().getMethod() == HttpMethod.OPTIONS
                && call.getCall().getRequest().getHeaders().stream()
                        .anyMatch(
                                h -> h.getName().equalsIgnoreCase("Access-Control-Request-Method"));
    }
}
