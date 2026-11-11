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
import java.util.List;
import java.util.Optional;

public abstract class ParameterType {
    public abstract Optional<Violation> check(Argument argument);

    public Optional<Warning> warningFor(Argument argument) {
        return Optional.empty();
    }

    public abstract String getName();

    public String describeRange() {
        return "";
    }

    protected Optional<Violation> invalid(Argument a, String reason, String... detail) {
        var details = new ArrayList<String>();
        details.add(getName());
        details.add(reason);
        details.addAll(List.of(detail));
        return Optional.of(
                new Violation(
                        ViolationKind.INVALID_TYPE,
                        a.getLocation(),
                        a.getName(),
                        a.getValue(),
                        details));
    }

    protected Optional<Violation> range(Argument a, String direction, String bound) {
        return Optional.of(
                new Violation(
                        ViolationKind.OUT_OF_RANGE,
                        a.getLocation(),
                        a.getName(),
                        a.getValue(),
                        List.of(direction, bound)));
    }

    protected String bounds(Object min, Object max) {
        if (min == null) return max == null ? "" : "≤ " + max;
        return max == null ? "≥ " + min : min + ".." + max;
    }
}
