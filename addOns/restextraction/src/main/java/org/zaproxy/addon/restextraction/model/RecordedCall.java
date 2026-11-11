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
import java.util.Map;
import java.util.Objects;

/**
 * A call as it was recorded under one endpoint, together with the value of each variable in that
 * endpoint's pattern. For {@code /posts/[POST_ID]} and the call {@code /posts/7}, the arguments are
 * {@code POST_ID=7}. The same call can be recorded under two endpoints, each with its own
 * arguments.
 */
public final class RecordedCall {

    private final ApiCall call;
    private final Map<String, String> arguments;

    /**
     * @param call the call, shared and not copied because it never changes
     * @param arguments the value of each variable, in the order of the template
     * @throws NullPointerException if the call, the arguments or one of their names or values is
     *     null
     */
    public RecordedCall(ApiCall call, Map<String, String> arguments) {
        this.call = Objects.requireNonNull(call, "call");
        Map<String, String> copy =
                new LinkedHashMap<>(Objects.requireNonNull(arguments, "arguments"));
        if (copy.containsKey(null) || copy.containsValue(null)) {
            throw new NullPointerException("arguments");
        }
        this.arguments = Collections.unmodifiableMap(copy);
    }

    public ApiCall getCall() {
        return call;
    }

    /** Returns the value of each variable, in template order. The map can't be changed. */
    public Map<String, String> getArguments() {
        return arguments;
    }
}
