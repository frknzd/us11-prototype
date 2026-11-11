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

/** One occurrence of a value, never a name-to-last-value map. */
public record Argument(
        String name,
        ArgumentLocation location,
        String value,
        JsonKind jsonKind,
        boolean malformedEncoding) {
    public Argument {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(value, "value");
    }

    public String getName() {
        return name;
    }

    public ArgumentLocation getLocation() {
        return location;
    }

    public String getValue() {
        return value;
    }

    public JsonKind getJsonKind() {
        return jsonKind;
    }

    public boolean isMalformedEncoding() {
        return malformedEncoding;
    }

    public boolean isContainer() {
        return jsonKind == JsonKind.OBJECT || jsonKind == JsonKind.ARRAY;
    }
}
