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
import java.util.Objects;

public final class ParsedRequest {
    private final List<Argument> arguments;
    private final List<Warning> warnings;
    private final String malformedBody;
    private final String contentType;
    private final boolean bodyChecked;

    public ParsedRequest(
            List<Argument> arguments,
            List<Warning> warnings,
            String malformedBody,
            String contentType,
            boolean bodyChecked) {
        this.arguments = List.copyOf(arguments);
        this.warnings = List.copyOf(warnings);
        this.malformedBody = Objects.requireNonNull(malformedBody, "malformedBody");
        this.contentType = Objects.requireNonNull(contentType, "contentType");
        this.bodyChecked = bodyChecked;
    }

    public List<Argument> getArguments() {
        return arguments;
    }

    public List<Warning> getWarnings() {
        return warnings;
    }

    public boolean isBodyMalformed() {
        return !malformedBody.isEmpty();
    }

    public String getMalformedBodyReason() {
        return malformedBody;
    }

    public String getContentType() {
        return contentType;
    }

    public boolean isBodyChecked() {
        return bodyChecked;
    }
}
