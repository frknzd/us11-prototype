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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Additional grammar boundaries and value-object contracts found during the acceptance audit. */
class VerificationEdgeCasesUnitTest {
    @Test
    void mediaTokenAlphabetBoundaries() {
        for (String type : List.of("A/Z", "a/0", "a/9", "a/!#$%&'*+-.^_`|~", "a/a", "a/z"))
            assertTrue(MediaType.isValid(type), type);
        for (String type : List.of("a/é", "a/@", "a/[", "a/:"))
            assertFalse(MediaType.isValid(type), type);
        assertFalse(MediaType.isJson("application/ba d+json"));
    }

    @Test
    void jsonTerminalAndExponentStates() {
        var values = new JsonReader("[false,1E+2,-2e-3,0.1,\"\\u00AF\"]", 10).read();
        assertEquals("false", values.get(0).getValue());
        assertEquals("1E+2", values.get(1).getValue());
        assertEquals("-2e-3", values.get(2).getValue());
        assertEquals("¯", values.get(4).getValue());
        for (String invalid :
                List.of(
                        "\"" + "\\",
                        "\"" + "\\u1",
                        "fals",
                        "tru",
                        "nul",
                        "-",
                        "[1 2]",
                        "{\"x\" 1}"))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new JsonReader(invalid, 10).read(),
                    invalid);
        assertEquals(0, JsonReader.hex('0'));
        assertEquals(15, JsonReader.hex('F'));
        assertEquals(-1, JsonReader.hex('G'));
        assertEquals(-1, JsonReader.hex('/'));
        assertEquals(-1, JsonReader.hex(':'));
    }

    @Test
    void fieldEscapesAndNonKeyWildcardBoundaries() {
        for (String name : List.of("a\\", "a\\q"))
            assertThrows(IllegalArgumentException.class, () -> FieldName.parse(name));
        assertFalse(FieldName.parse("a").matches("a..b"));
        assertFalse(FieldName.parse("a{}").matches("a[]"));
        assertFalse(FieldName.parse("a").isMoreSpecificThan(FieldName.parse("a")));
        assertFalse(FieldName.parse("a").isMoreSpecificThan(FieldName.parse("a.b")));
        assertFalse(FieldName.parse("a").isMoreSpecificThan(FieldName.parse("b")));
        assertFalse(FieldName.parse("a[]").isMoreSpecificThan(FieldName.parse("a{}")));
    }

    @Test
    void base64AsciiBoundaries() {
        Base64Type type = new Base64Type();
        for (String value : List.of("0000", "9999", "ZZZZ", "zzzz"))
            assertTrue(
                    type.check(new Argument("a", ArgumentLocation.QUERY, value, null, false))
                            .isEmpty());
        for (String value : List.of("::::", "[[[[", "{{{{", "@@@@"))
            assertFalse(
                    type.check(new Argument("a", ArgumentLocation.QUERY, value, null, false))
                            .isEmpty());
    }

    @Test
    void decimalWarningsKindsRangesAndMalformedEncoding() {
        DecimalType type = new DecimalType(null, null);
        assertFalse(
                type.check(new Argument("n", ArgumentLocation.QUERY, "1", null, true)).isEmpty());
        assertTrue(
                type.warningFor(new Argument("n", ArgumentLocation.QUERY, "1", null, false))
                        .isEmpty());
        assertTrue(
                type.warningFor(
                                new Argument(
                                        "n", ArgumentLocation.BODY, "x", JsonKind.NUMBER, false))
                        .isEmpty());
        assertEquals("", type.describeRange());
        assertEquals("≥ 0", new DecimalType(BigDecimal.ZERO, null).describeRange());
        assertEquals("≤ 10", new DecimalType(null, BigDecimal.TEN).describeRange());
        assertEquals("0..10", new DecimalType(BigDecimal.ZERO, BigDecimal.TEN).describeRange());
        assertTrue(
                type.warningFor(
                                new Argument(
                                        "n",
                                        ArgumentLocation.BODY,
                                        "1E-400",
                                        JsonKind.NUMBER,
                                        false))
                        .isEmpty());
        assertTrue(
                new IntegerType(null, null)
                        .warningFor(
                                new Argument(
                                        "n", ArgumentLocation.BODY, "x", JsonKind.NUMBER, false))
                        .isEmpty());
        assertEquals("", new IntegerType(null, null).describeRange());
        assertEquals("≥ 0", new IntegerType(BigInteger.ZERO, null).describeRange());
        assertEquals("≤ 10", new IntegerType(null, BigInteger.TEN).describeRange());
    }

    @Test
    void parsedAndWarningValueObjects() {
        ParsedRequest parsed =
                RequestParser.parse(
                        new RequestData("x=%zz", List.of(), "", "", BodyState.NONE),
                        Map.of("ID", "1"));
        ApiCall call = new ApiCall(HttpMethod.GET, "http://localhost/x", 200, "", "");
        RecordedCall recorded = new RecordedCall(call, parsed);
        assertSame(parsed, recorded.getRequest());
        assertSame(call, recorded.getCall());
        assertThrows(NullPointerException.class, () -> new RecordedCall(null, parsed));
        assertThrows(
                NullPointerException.class, () -> new RecordedCall(call, (ParsedRequest) null));
        Warning warning =
                new Warning(WarningKind.TOO_DEEP, ArgumentLocation.BODY, "n", List.of("10"));
        assertEquals(ArgumentLocation.BODY, warning.getLocation());
        assertEquals("n", warning.getName());
        assertEquals(List.of("10"), warning.getDetails());
        assertThrows(UnsupportedOperationException.class, () -> warning.getDetails().clear());
    }
}
