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
package org.zaproxy.addon.restextraction.ui;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.zaproxy.addon.restextraction.model.ArgumentLocation;
import org.zaproxy.addon.restextraction.model.Base64Type;
import org.zaproxy.addon.restextraction.model.DecimalType;
import org.zaproxy.addon.restextraction.model.EndpointPattern;
import org.zaproxy.addon.restextraction.model.HttpMethod;
import org.zaproxy.addon.restextraction.model.IntegerType;
import org.zaproxy.addon.restextraction.model.ParameterSpec;
import org.zaproxy.addon.restextraction.model.ParameterType;
import org.zaproxy.addon.restextraction.model.SpecItem;
import org.zaproxy.addon.restextraction.model.SpecItemKind;
import org.zaproxy.addon.restextraction.model.SpecValidationException;
import org.zaproxy.addon.restextraction.model.StringType;

/** Testable form state; all domain validation still goes through the specification. */
public final class SpecItemForm {
    private SpecItemKind kind = SpecItemKind.METHOD;
    private final Map<SpecField, String> values = new EnumMap<>(SpecField.class);
    private final List<String> variables;

    public SpecItemForm(String template) {
        variables = new EndpointPattern(template).getVariableNames();
        values.put(SpecField.METHOD, "GET");
        values.put(SpecField.TYPE, "String");
        values.put(SpecField.REQUIRED, "false");
    }

    public List<String> pathVariables() {
        return variables;
    }

    public void setKind(SpecItemKind kind) {
        this.kind = kind;
    }

    public SpecItemKind getKind() {
        return kind;
    }

    public void set(SpecField field, String value) {
        values.put(field, value);
    }

    public String get(SpecField field) {
        return values.getOrDefault(field, "");
    }

    public List<SpecField> visibleFields() {
        List<SpecField> fields = new ArrayList<>(List.of(SpecField.METHOD));
        if (kind == SpecItemKind.CONTENT_TYPE) fields.add(SpecField.MEDIA_TYPE);
        else if (kind != SpecItemKind.METHOD) {
            fields.add(SpecField.NAME);
            fields.add(SpecField.TYPE);
            if (get(SpecField.TYPE).equals("Integer") || get(SpecField.TYPE).equals("Decimal")) {
                fields.add(SpecField.MIN);
                fields.add(SpecField.MAX);
            }
            if (kind != SpecItemKind.PATH_VARIABLE) fields.add(SpecField.REQUIRED);
        }
        return List.copyOf(fields);
    }

    public void load(SpecItem item) {
        kind = item.getKind();
        set(SpecField.METHOD, item.getMethod().name());
        set(SpecField.MEDIA_TYPE, item.getMediaType());
        set(SpecField.MIN, "");
        set(SpecField.MAX, "");
        set(SpecField.NAME, "");
        set(SpecField.REQUIRED, "false");
        set(SpecField.TYPE, "String");
        if (item.getParameter() != null) {
            var p = item.getParameter();
            set(SpecField.NAME, p.getName());
            set(SpecField.TYPE, p.getType().getName());
            set(SpecField.REQUIRED, Boolean.toString(p.isRequired()));
            String range = p.getType().describeRange();
            if (range.startsWith("≥ ")) set(SpecField.MIN, range.substring(2));
            else if (range.startsWith("≤ ")) set(SpecField.MAX, range.substring(2));
            else if (range.contains("..")) {
                int at = range.indexOf("..");
                set(SpecField.MIN, range.substring(0, at));
                set(SpecField.MAX, range.substring(at + 2));
            }
        }
    }

    public SpecItem toItem() {
        HttpMethod method;
        try {
            method = HttpMethod.valueOf(get(SpecField.METHOD));
        } catch (IllegalArgumentException e) {
            throw new SpecValidationException("method");
        }
        if (kind == SpecItemKind.METHOD) return SpecItem.method(method);
        if (kind == SpecItemKind.CONTENT_TYPE)
            return SpecItem.contentType(method, get(SpecField.MEDIA_TYPE));
        ParameterType type =
                switch (get(SpecField.TYPE)) {
                    case "String" -> new StringType();
                    case "Base64" -> new Base64Type();
                    case "Integer" ->
                            new IntegerType(
                                    get(SpecField.MIN).isEmpty()
                                            ? null
                                            : IntegerType.parseBound(get(SpecField.MIN)),
                                    get(SpecField.MAX).isEmpty()
                                            ? null
                                            : IntegerType.parseBound(get(SpecField.MAX)));
                    case "Decimal" ->
                            new DecimalType(
                                    get(SpecField.MIN).isEmpty()
                                            ? null
                                            : DecimalType.parseBound(get(SpecField.MIN)),
                                    get(SpecField.MAX).isEmpty()
                                            ? null
                                            : DecimalType.parseBound(get(SpecField.MAX)));
                    default -> throw new SpecValidationException("type");
                };
        ArgumentLocation location =
                switch (kind) {
                    case PATH_VARIABLE -> ArgumentLocation.PATH;
                    case QUERY_PARAMETER -> ArgumentLocation.QUERY;
                    case BODY_FIELD -> ArgumentLocation.BODY;
                    case HEADER -> ArgumentLocation.HEADER;
                    default -> throw new IllegalStateException();
                };
        if (location == ArgumentLocation.PATH && !variables.contains(get(SpecField.NAME)))
            throw new SpecValidationException("pathVariable");
        return SpecItem.parameter(
                method,
                new ParameterSpec(
                        location,
                        get(SpecField.NAME),
                        type,
                        location != ArgumentLocation.PATH
                                && Boolean.parseBoolean(get(SpecField.REQUIRED))));
    }
}
