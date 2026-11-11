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
package org.zaproxy.addon.restextraction.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zaproxy.addon.restextraction.ExtensionRestExtraction;
import org.zaproxy.addon.restextraction.model.Argument;
import org.zaproxy.addon.restextraction.model.ArgumentLocation;
import org.zaproxy.addon.restextraction.model.Check;
import org.zaproxy.addon.restextraction.model.DecimalType;
import org.zaproxy.addon.restextraction.model.EndpointSpec;
import org.zaproxy.addon.restextraction.model.HttpMethod;
import org.zaproxy.addon.restextraction.model.IntegerType;
import org.zaproxy.addon.restextraction.model.ParameterSpec;
import org.zaproxy.addon.restextraction.model.ParameterType;
import org.zaproxy.addon.restextraction.model.SpecItem;
import org.zaproxy.addon.restextraction.model.SpecItemKind;
import org.zaproxy.addon.restextraction.model.SpecValidationException;
import org.zaproxy.addon.restextraction.model.StringType;
import org.zaproxy.addon.restextraction.model.Verdict;
import org.zaproxy.addon.restextraction.model.Violation;
import org.zaproxy.addon.restextraction.model.ViolationKind;
import org.zaproxy.addon.restextraction.model.Warning;
import org.zaproxy.addon.restextraction.model.WarningKind;
import org.zaproxy.zap.testutils.TestUtils;

class DiagnosticCatalogUnitTest extends TestUtils {
    private final CheckWriter writer = new CheckWriter();

    @BeforeEach
    void messages() {
        mockMessages(new ExtensionRestExtraction());
    }

    private Verdict violation(ViolationKind kind, String value, List<String> details) {
        return new Verdict(
                List.of(),
                List.of(new Violation(kind, ArgumentLocation.QUERY, "n", value, details)),
                List.of(),
                List.of());
    }

    @Test
    void CW1_CW5_CW6_CW7_matchesEscapingAndSymbols() {
        ParameterSpec integer =
                new ParameterSpec(
                        ArgumentLocation.QUERY,
                        "_limit",
                        new IntegerType(BigInteger.ONE, BigInteger.valueOf(100)),
                        false);
        Argument argument = new Argument("_limit", ArgumentLocation.QUERY, "5", null, false);
        Verdict valid =
                new Verdict(
                        List.of(new Check(argument, integer, null)),
                        List.of(),
                        List.of(),
                        List.of());
        assertEquals(List.of("✓ query _limit = 5  (Integer 1..100)"), writer.lines(valid));
        assertEquals("✓", writer.mark(valid));
        assertTrue(writer.toHtml(valid).contains(VerificationText.hex('✓')));
        ParameterSpec string =
                new ParameterSpec(ArgumentLocation.QUERY, "n", new StringType(), false);
        var longValue = new Argument("n", ArgumentLocation.QUERY, "x".repeat(200), null, false);
        String line =
                writer.lines(
                                new Verdict(
                                        List.of(new Check(longValue, string, null)),
                                        List.of(),
                                        List.of(),
                                        List.of()))
                        .get(0);
        assertTrue(line.contains("x".repeat(80) + "…"));
        assertFalse(line.contains("x".repeat(81)));
        assertEquals("preflight (not checked)", writer.lines(Verdict.notVerified(true)).get(0));
        assertTrue(writer.toHtml(Verdict.notVerified(false)).contains("No spec"));
        assertTrue(
                writer.toHtml(violation(ViolationKind.EXTRA_ARGUMENT, "<script>", List.of()))
                        .contains("&lt;script&gt;"));
        assertTrue(
                writer.toHtml(violation(ViolationKind.EXTRA_ARGUMENT, "x", List.of()))
                        .contains(VerificationText.hex('✗')));
    }

    @Test
    void CW2_allViolationKindsAndReasons() {
        assertEquals(
                "✗ PUT is not in the spec (allowed: GET, POST)",
                writer.lines(
                                violation(
                                        ViolationKind.UNSPECIFIED_METHOD,
                                        "PUT",
                                        List.of("GET", "POST")))
                        .get(0));
        for (String type : List.of("", "text/plain"))
            assertTrue(
                    writer.lines(
                                    violation(
                                            ViolationKind.UNSPECIFIED_CONTENT_TYPE,
                                            type,
                                            List.of("POST", "application/json")))
                            .get(0)
                            .contains("not allowed"));
        assertEquals(
                "✗ query n = x is not in the spec",
                writer.lines(violation(ViolationKind.EXTRA_ARGUMENT, "x", List.of())).get(0));
        assertEquals(
                "✗ required query n is missing",
                writer.lines(violation(ViolationKind.MISSING_ARGUMENT, "", List.of())).get(0));
        for (String direction : List.of("below", "above"))
            assertTrue(
                    writer.lines(
                                    violation(
                                            ViolationKind.OUT_OF_RANGE,
                                            "1",
                                            List.of(direction, "5")))
                            .get(0)
                            .contains(direction));
        for (String reason : List.of("grammar", "encoding", "fraction", "length", "json")) {
            List<String> details =
                    reason.equals("json")
                            ? List.of("Integer", reason, "STRING")
                            : reason.equals("length")
                                    ? List.of("Integer", reason, "10001")
                                    : List.of("Integer", reason);
            String line = writer.lines(violation(ViolationKind.INVALID_TYPE, "x", details)).get(0);
            assertTrue(line.startsWith("✗"));
            assertFalse(line.contains("restextraction."));
            if (reason.equals("json"))
                assertTrue(line.contains("expected Integer (a JSON number), got a JSON string"));
        }
        for (String reason :
                List.of(
                        "trailing comma",
                        "trailing content",
                        "expected value",
                        "expected :",
                        "expected ,",
                        "expected \"",
                        "control character in string",
                        "unfinished escape",
                        "unfinished unicode escape",
                        "invalid unicode escape",
                        "invalid escape",
                        "unclosed string",
                        "invalid number",
                        "expected digit",
                        "invalid literal",
                        "unknown")) {
            String line =
                    writer.lines(
                                    violation(
                                            ViolationKind.MALFORMED_BODY,
                                            "",
                                            List.of(reason + " at position 8")))
                            .get(0);
            assertTrue(line.contains("at character 8"));
            assertFalse(line.contains("restextraction."));
        }
        assertTrue(
                writer.lines(violation(ViolationKind.MALFORMED_BODY, "", List.of("unknown")))
                        .get(0)
                        .contains("character ?"));
    }

    @Test
    void CW3_CW4_warningsAndUncheckedHeaders() {
        for (Warning warning :
                List.of(
                        new Warning(
                                WarningKind.REPEATED_PARAMETER,
                                ArgumentLocation.QUERY,
                                "tag",
                                List.of("2")),
                        new Warning(
                                WarningKind.PRECISION,
                                ArgumentLocation.BODY,
                                "n",
                                List.of("9007199254740992")),
                        new Warning(WarningKind.TOO_MANY_ARGUMENTS, null, "", List.of()),
                        new Warning(WarningKind.TOO_DEEP, ArgumentLocation.BODY, "", List.of()),
                        new Warning(
                                WarningKind.BODY_NOT_CHECKED,
                                ArgumentLocation.BODY,
                                "",
                                List.of("cut", "")),
                        new Warning(
                                WarningKind.BODY_NOT_CHECKED,
                                ArgumentLocation.BODY,
                                "",
                                List.of("binary", "")),
                        new Warning(
                                WarningKind.BODY_NOT_CHECKED,
                                ArgumentLocation.BODY,
                                "",
                                List.of("type", "text/plain")))) {
            Verdict verdict = new Verdict(List.of(), List.of(), List.of(), List.of(warning));
            assertEquals("⚠", writer.mark(verdict));
            assertTrue(writer.lines(verdict).get(0).startsWith("⚠"));
            assertFalse(writer.lines(verdict).get(0).contains("restextraction."));
            assertTrue(writer.toHtml(verdict).contains(VerificationText.hex('⚠')));
        }
        Verdict unchecked =
                new Verdict(
                        List.of(),
                        List.of(),
                        List.of(
                                new Argument(
                                        "User-Agent",
                                        ArgumentLocation.HEADER,
                                        "Firefox/140",
                                        null,
                                        false)),
                        List.of());
        assertEquals(List.of("header User-Agent = Firefox/140"), writer.lines(unchecked));
        assertTrue(writer.toHtml(unchecked).contains("Firefox/140"));
    }

    @Test
    void SM5_SM6_SM7_SM8_SM9_allFormTypesAndTableCells() {
        SpecItemForm form = new SpecItemForm("/posts/[ID]");
        for (ParameterType type :
                List.of(
                        new IntegerType(null, null),
                        new IntegerType(BigInteger.ZERO, null),
                        new IntegerType(null, BigInteger.TEN),
                        new DecimalType(null, null),
                        new DecimalType(BigDecimal.ZERO, BigDecimal.TEN),
                        new StringType())) {
            SpecItem item =
                    SpecItem.parameter(
                            HttpMethod.POST,
                            new ParameterSpec(ArgumentLocation.QUERY, "n", type, false));
            form.load(item);
            var result = form.toItem();
            assertEquals(type.getName(), result.getParameter().getType().getName());
            assertEquals(type.describeRange(), result.getParameter().getType().describeRange());
        }
        form.set(SpecField.TYPE, "Base64");
        assertEquals("Base64", form.toItem().getParameter().getType().getName());
        form.set(SpecField.TYPE, "Integer");
        form.set(SpecField.MIN, "1.5");
        assertThrows(IllegalArgumentException.class, form::toItem);
        form.set(SpecField.MIN, "");
        form.set(SpecField.TYPE, "unknown");
        assertThrows(IllegalArgumentException.class, form::toItem);
        form.set(SpecField.TYPE, "String");
        form.setKind(SpecItemKind.HEADER);
        assertEquals(ArgumentLocation.HEADER, form.toItem().getParameter().getLocation());
        form.set(SpecField.METHOD, "unknown");
        assertThrows(IllegalArgumentException.class, form::toItem);
        var spec =
                EndpointSpec.empty()
                        .with(
                                SpecItem.parameter(
                                        HttpMethod.GET,
                                        new ParameterSpec(
                                                ArgumentLocation.QUERY,
                                                "n",
                                                new StringType(),
                                                false)))
                        .with(SpecItem.contentType(HttpMethod.POST, "application/json"))
                        .with(
                                SpecItem.parameter(
                                        HttpMethod.POST,
                                        new ParameterSpec(
                                                ArgumentLocation.HEADER,
                                                "X",
                                                new StringType(),
                                                false)));
        SpecItemTableModel table = new SpecItemTableModel();
        table.setSpec(spec);
        table.setSpec(spec);
        assertEquals(5, table.getRowCount());
        for (int row = 0; row < table.getRowCount(); row++)
            for (int col = 0; col < table.getColumnCount(); col++) {
                assertNotNull(table.getValueAt(row, col));
                assertFalse(table.getColumnName(col).contains("restextraction."));
            }
        assertEquals("GET", table.getValueAt(0, 0));
        assertEquals("POST", table.getValueAt(2, 0));
    }

    @Test
    void MSG1_defaultBundleAndValidationAreWellFormed() throws Exception {
        Properties properties = new Properties();
        try (var input =
                getClass()
                        .getResourceAsStream(
                                "/org/zaproxy/addon/restextraction/resources/Messages.properties")) {
            assertNotNull(input);
            properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
        for (String key : properties.stringPropertyNames())
            new MessageFormat(properties.getProperty(key), Locale.ENGLISH);
        for (String key :
                List.of(
                        "range",
                        "integerBound",
                        "decimalBound",
                        "name",
                        "field",
                        "required",
                        "method",
                        "mediaType",
                        "type",
                        "pathVariable",
                        "duplicate",
                        "bodyMediaType",
                        "missingItem",
                        "endpoint",
                        "emptyPath",
                        "emptyPart",
                        "lockedLiteral",
                        "variableName",
                        "pattern"))
            assertTrue(properties.containsKey("restextraction.validation." + key));
        assertTrue(
                VerificationText.validation(new SpecValidationException("field", "2"))
                        .contains("position 2"));
        assertTrue(
                VerificationText.validation(new IllegalArgumentException("legacy"))
                        .contains("legacy"));
    }
}
