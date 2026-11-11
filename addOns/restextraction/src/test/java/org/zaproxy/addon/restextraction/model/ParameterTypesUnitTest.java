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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ParameterTypesUnitTest {
    private Argument text(String value) {
        return new Argument("n", ArgumentLocation.QUERY, value, null, false);
    }

    private Argument json(String value, JsonKind kind) {
        return new Argument("n", ArgumentLocation.BODY, value, kind, false);
    }

    private final IntegerType integer = new IntegerType(null, null);
    private final DecimalType decimal = new DecimalType(null, null);
    private final Base64Type base64 = new Base64Type();

    @Test
    void PTS1_PTS2_PTS3_PTS4_stringKinds() {
        StringType type = new StringType();
        for (String s : List.of("", "abc", " a b ")) assertTrue(type.check(text(s)).isEmpty());
        for (JsonKind k : List.of(JsonKind.STRING, JsonKind.NUMBER, JsonKind.BOOLEAN))
            assertTrue(type.check(json("x", k)).isEmpty());
        for (JsonKind k : List.of(JsonKind.NULL, JsonKind.OBJECT, JsonKind.ARRAY))
            assertEquals(
                    ViolationKind.INVALID_TYPE, type.check(json("x", k)).orElseThrow().getKind());
        assertTrue(
                type.check(new Argument("n", ArgumentLocation.QUERY, "%zz", null, true)).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"42", "-7", "+3", "007", "0", "123456789012345678901234567890"})
    void PTI1_PTI3_integerText(String s) {
        assertTrue(integer.check(text(s)).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "4.0", "1e3", "0x10", " 42", "42 ", "4 2", "١٢", "+", "-"})
    void PTI2_invalidInteger(String s) {
        assertEquals(ViolationKind.INVALID_TYPE, integer.check(text(s)).orElseThrow().getKind());
    }

    @Test
    void PTI4_PTI5_PTI6_jsonIntegerKinds() {
        for (String s : List.of("42", "-7", "0", "-0"))
            assertTrue(integer.check(json(s, JsonKind.NUMBER)).isEmpty());
        for (String s : List.of("1.0", "1e3", "1E+2"))
            assertEquals(
                    "fraction",
                    integer.check(json(s, JsonKind.NUMBER)).orElseThrow().getDetails().get(1));
        for (JsonKind k :
                List.of(JsonKind.STRING, JsonKind.BOOLEAN, JsonKind.NULL, JsonKind.OBJECT))
            assertEquals("json", integer.check(json("42", k)).orElseThrow().getDetails().get(1));
        assertFalse(integer.check(json("01", JsonKind.NUMBER)).isEmpty());
    }

    @Test
    void PTI7_PTI8_PTI9_PTI10_ranges() {
        IntegerType ranged = new IntegerType(BigInteger.ONE, BigInteger.valueOf(100));
        for (String s : List.of("1", "100", "50")) assertTrue(ranged.check(text(s)).isEmpty());
        for (String s : List.of("0", "101", "-1"))
            assertEquals(ViolationKind.OUT_OF_RANGE, ranged.check(text(s)).orElseThrow().getKind());
        assertFalse(new IntegerType(BigInteger.ZERO, null).check(text("-1")).isEmpty());
        assertTrue(
                new IntegerType(BigInteger.ZERO, null).check(text("1" + "0".repeat(30))).isEmpty());
        assertFalse(new IntegerType(null, BigInteger.TEN).check(text("11")).isEmpty());
        assertTrue(
                new IntegerType(null, BigInteger.TEN).check(text("-1" + "0".repeat(30))).isEmpty());
        for (String s : List.of("-0", "+0", "000"))
            assertTrue(new IntegerType(BigInteger.ZERO, BigInteger.ZERO).check(text(s)).isEmpty());
        assertThrows(
                IllegalArgumentException.class,
                () -> new IntegerType(BigInteger.TEN, BigInteger.ONE));
    }

    @Test
    void PTI11_PTI12_PTI13_PTI14_limitsWarningsBounds() {
        assertEquals(
                "length", integer.check(text("1".repeat(10001))).orElseThrow().getDetails().get(1));
        assertTrue(integer.warningFor(json("9007199254740991", JsonKind.NUMBER)).isEmpty());
        for (String s : List.of("9007199254740992", "-9007199254740993"))
            assertEquals(
                    WarningKind.PRECISION,
                    integer.warningFor(json(s, JsonKind.NUMBER)).orElseThrow().getKind());
        assertEquals(
                "encoding",
                integer.check(new Argument("n", ArgumentLocation.QUERY, "4%2", null, true))
                        .orElseThrow()
                        .getDetails()
                        .get(1));
        for (String s : List.of("10", "-5", "+1"))
            assertEquals(new BigInteger(s), IntegerType.parseBound(s));
        for (String s : List.of("1.5", "abc", ""))
            assertThrows(IllegalArgumentException.class, () -> IntegerType.parseBound(s));
    }

    @ParameterizedTest
    @ValueSource(strings = {"9.99", "-0.5", "3", "1e3", "1.5E-2", "+2", "007.50"})
    void PTD1_decimalText(String s) {
        assertTrue(decimal.check(text(s)).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ".5", "5.", "NaN", "Infinity", "1,5", "0x1p3", "1e", " 1"})
    void PTD2_invalidDecimal(String s) {
        assertFalse(decimal.check(text(s)).isEmpty());
    }

    @Test
    void PTD3_PTD4_jsonDecimalKinds() {
        for (String s : List.of("9.99", "1e3", "-0", "1E-400"))
            assertTrue(decimal.check(json(s, JsonKind.NUMBER)).isEmpty());
        for (JsonKind k : List.of(JsonKind.STRING, JsonKind.BOOLEAN))
            assertEquals("json", decimal.check(json("9.99", k)).orElseThrow().getDetails().get(1));
    }

    @Test
    void PTD5_PTD6_PTD7_exactRanges() {
        DecimalType ranged = new DecimalType(new BigDecimal("0.5"), new BigDecimal("2.5"));
        for (String s : List.of("0.5", "2.50", "1")) assertTrue(ranged.check(text(s)).isEmpty());
        assertEquals(
                ViolationKind.OUT_OF_RANGE, ranged.check(text("0.499")).orElseThrow().getKind());
        assertEquals(
                ViolationKind.OUT_OF_RANGE, ranged.check(text("2.51")).orElseThrow().getKind());
        DecimalType max = new DecimalType(null, new BigDecimal("0.3"));
        assertFalse(max.check(text("0.30000000000000004")).isEmpty());
        assertTrue(max.check(text("0.3000")).isEmpty());
        assertTrue(
                new DecimalType(new BigDecimal("1.0"), new BigDecimal("1.00"))
                        .check(text("1"))
                        .isEmpty());
        assertThrows(
                IllegalArgumentException.class,
                () -> new DecimalType(BigDecimal.TEN, BigDecimal.ONE));
    }

    @Test
    void PTD8_PTD9_PTD10_limitsWarningsBounds() {
        assertFalse(decimal.check(text("1".repeat(10001))).isEmpty());
        assertFalse(decimal.check(text("1e10000")).isEmpty());
        assertFalse(decimal.check(text("1e-10000")).isEmpty());
        assertTrue(decimal.check(text("1e9999")).isEmpty());
        for (String s : List.of("1e400", "9007199254740993"))
            assertEquals(
                    WarningKind.PRECISION,
                    decimal.warningFor(json(s, JsonKind.NUMBER)).orElseThrow().getKind());
        assertTrue(decimal.warningFor(json("0.1", JsonKind.NUMBER)).isEmpty());
        for (String s : List.of("0.5", "-1e2"))
            assertEquals(new BigDecimal(s), DecimalType.parseBound(s));
        for (String s : List.of(".5", "x"))
            assertThrows(IllegalArgumentException.class, () -> DecimalType.parseBound(s));
    }

    @ParameterizedTest
    @ValueSource(strings = {"aGVsbG8=", "aGVsbG8", "aGk=", "aGk", "YQ==", "YQ", "a-_b", "+/+/"})
    void PTB1_base64(String s) {
        assertTrue(base64.check(text(s)).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "a+b-",
                "a===",
                "ab=c",
                "=abc",
                "YQ=",
                "YQ===",
                "abcde",
                "aGVs bG8=",
                "aGVsbG8=\n"
            })
    void PTB2_PTB3_PTB4_PTB5_PTB6_invalidBase64(String s) {
        assertFalse(base64.check(text(s)).isEmpty());
    }

    @Test
    void PTB7_PTB8_PTB9_kindsAndDecoding() {
        assertTrue(base64.check(json("aGk=", JsonKind.STRING)).isEmpty());
        assertFalse(base64.check(json("1234", JsonKind.NUMBER)).isEmpty());
        var r = new RequestData("img=ab+c&img=ab%2Bc", List.of(), "", "", BodyState.NONE);
        var args = RequestParser.parse(r, java.util.Map.of()).getArguments();
        assertFalse(base64.check(args.get(0)).isEmpty());
        assertTrue(base64.check(args.get(1)).isEmpty());
        assertFalse(
                base64.check(new Argument("x", ArgumentLocation.QUERY, "abc", null, true))
                        .isEmpty());
    }
}
