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

import java.util.List;

/** A validation refusal with a localizable key and immutable substitution values. */
public final class SpecValidationException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    private final String key;
    private final List<String> arguments;

    public SpecValidationException(String key, String... arguments) {
        super(
                "restextraction.validation."
                        + key
                        + (arguments.length == 0 ? "" : ": " + String.join(", ", arguments)));
        this.key = "restextraction.validation." + key;
        this.arguments = List.of(arguments);
    }

    public String getKey() {
        return key;
    }

    public List<String> getArguments() {
        return arguments;
    }
}
