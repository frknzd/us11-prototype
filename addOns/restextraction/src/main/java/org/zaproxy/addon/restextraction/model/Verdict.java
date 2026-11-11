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

public final class Verdict {
    private final VerdictStatus status;
    private final boolean preflight;
    private final List<Check> checks;
    private final List<Violation> violations;
    private final List<Argument> unchecked;
    private final List<Warning> warnings;

    public Verdict(
            List<Check> checks,
            List<Violation> violations,
            List<Argument> unchecked,
            List<Warning> warnings) {
        this(
                violations.isEmpty() ? VerdictStatus.VALID : VerdictStatus.INVALID,
                false,
                checks,
                violations,
                unchecked,
                warnings);
    }

    private Verdict(
            VerdictStatus status,
            boolean preflight,
            List<Check> checks,
            List<Violation> violations,
            List<Argument> unchecked,
            List<Warning> warnings) {
        this.status = status;
        this.preflight = preflight;
        this.checks = List.copyOf(checks);
        this.violations = List.copyOf(violations);
        this.unchecked = List.copyOf(unchecked);
        this.warnings = List.copyOf(warnings);
    }

    public static Verdict notVerified(boolean preflight) {
        return new Verdict(
                VerdictStatus.NOT_VERIFIED, preflight, List.of(), List.of(), List.of(), List.of());
    }

    public VerdictStatus getStatus() {
        return status;
    }

    public boolean isPreflight() {
        return preflight;
    }

    public List<Check> getChecks() {
        return checks;
    }

    public List<Violation> getViolations() {
        return violations;
    }

    public List<Argument> getUnchecked() {
        return unchecked;
    }

    public List<Warning> getWarnings() {
        return warnings;
    }
}
