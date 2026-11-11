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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** One endpoint's immutable view of a call, parsed exactly once when recorded. */
public final class RecordedCall {
    private final ApiCall call;
    private final ParsedRequest request;

    /** Compatibility construction for existing US10 clients and regression fixtures. */
    public RecordedCall(ApiCall call, Map<String, String> arguments) {
        this.call = Objects.requireNonNull(call, "call");
        Map<String, String> copy =
                new LinkedHashMap<>(Objects.requireNonNull(arguments, "arguments"));
        if (copy.containsKey(null) || copy.containsValue(null))
            throw new NullPointerException("arguments");
        this.request = RequestParser.parse(call.getRequest(), copy);
    }

    public RecordedCall(ApiCall call, ParsedRequest request) {
        this.call = Objects.requireNonNull(call, "call");
        this.request = Objects.requireNonNull(request, "request");
    }

    public ApiCall getCall() {
        return call;
    }

    public ParsedRequest getRequest() {
        return request;
    }

    public List<Argument> getArguments() {
        return request.getArguments();
    }

    public Map<String, String> getPathArguments() {
        Map<String, String> path = new LinkedHashMap<>();
        for (Argument argument : getArguments()) {
            if (argument.getLocation() == ArgumentLocation.PATH)
                path.put(argument.getName(), argument.getValue());
        }
        return Collections.unmodifiableMap(path);
    }
}
