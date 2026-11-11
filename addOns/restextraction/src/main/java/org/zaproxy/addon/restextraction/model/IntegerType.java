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

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

public final class IntegerType extends ParameterType {
    private static final BigInteger EXACT_LIMIT = BigInteger.valueOf(9007199254740991L);
    private final BigInteger min;
    private final BigInteger max;

    public IntegerType(BigInteger min, BigInteger max) {
        if (min != null && max != null && min.compareTo(max) > 0)
            throw new SpecValidationException("range");
        this.min = min;
        this.max = max;
    }

    public static BigInteger parseBound(String text) {
        if (!NumericGrammar.valid(text, false, false))
            throw new SpecValidationException("integerBound");
        return new BigInteger(text);
    }

    @Override
    public Optional<Violation> check(Argument a) {
        if (a.isMalformedEncoding()) return invalid(a, "encoding");
        if (a.getValue().length() > 10000)
            return invalid(a, "length", Integer.toString(a.getValue().length()));
        if (a.getJsonKind() != null && a.getJsonKind() != JsonKind.NUMBER)
            return invalid(a, "json", a.getJsonKind().name());
        if (!NumericGrammar.valid(a.getValue(), false, a.getJsonKind() != null))
            return invalid(a, a.getJsonKind() == JsonKind.NUMBER ? "fraction" : "grammar");
        BigInteger n = new BigInteger(a.getValue());
        if (min != null && n.compareTo(min) < 0) return range(a, "below", min.toString());
        if (max != null && n.compareTo(max) > 0) return range(a, "above", max.toString());
        return Optional.empty();
    }

    @Override
    public Optional<Warning> warningFor(Argument a) {
        if (a.getJsonKind() == JsonKind.NUMBER
                && NumericGrammar.valid(a.getValue(), false, true)
                && new BigInteger(a.getValue()).abs().compareTo(EXACT_LIMIT) > 0)
            return Optional.of(
                    new Warning(
                            WarningKind.PRECISION,
                            a.getLocation(),
                            a.getName(),
                            List.of(a.getValue())));
        return Optional.empty();
    }

    @Override
    public String getName() {
        return "Integer";
    }

    @Override
    public String describeRange() {
        return bounds(min, max);
    }
}
