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

import java.util.Objects;

public final class ParameterSpec {
    private final ArgumentLocation location;
    private final String name;
    private final FieldName fieldName;
    private final ParameterType type;
    private final boolean required;

    public ParameterSpec(
            ArgumentLocation location, String name, ParameterType type, boolean required) {
        this.location = Objects.requireNonNull(location, "location");
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
        if (name.isEmpty() || !name.equals(name.strip())) throw new SpecValidationException("name");
        fieldName = location == ArgumentLocation.BODY ? FieldName.parse(name) : null;
        if (required
                && (location == ArgumentLocation.PATH
                        || fieldName != null && fieldName.isWildcard()))
            throw new SpecValidationException("required");
        this.required = required;
    }

    public ArgumentLocation getLocation() {
        return location;
    }

    public String getName() {
        return name;
    }

    public ParameterType getType() {
        return type;
    }

    public boolean isRequired() {
        return required;
    }

    public boolean accepts(Argument argument, boolean jsonBody) {
        if (argument.getLocation() != location) return false;
        if (location == ArgumentLocation.HEADER) return name.equalsIgnoreCase(argument.getName());
        return location == ArgumentLocation.BODY && jsonBody
                ? fieldName.matches(argument.getName())
                : name.equals(argument.getName());
    }

    public Check check(Argument argument) {
        return new Check(argument, this, type.check(argument).orElse(null));
    }

    boolean moreSpecificThan(ParameterSpec other) {
        return fieldName != null
                && other.fieldName != null
                && fieldName.isMoreSpecificThan(other.fieldName);
    }
}
