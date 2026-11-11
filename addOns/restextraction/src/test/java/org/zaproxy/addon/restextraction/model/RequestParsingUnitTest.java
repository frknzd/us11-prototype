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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Test IDs refer to the approved US11 plan; fixtures never contact external sites. */
class RequestParsingUnitTest {
    private RequestData request(String query, String type, String body) {
        return new RequestData(
                query, List.of(), type, body, body.isEmpty() ? BodyState.NONE : BodyState.TEXT);
    }

    private ParsedRequest parse(String query, String type, String body) {
        return RequestParser.parse(request(query, type, body), Map.of());
    }

    private List<Argument> json(String text) {
        return new JsonReader(text, 10).read();
    }

    private List<String> names(List<Argument> arguments) {
        return arguments.stream().map(Argument::getName).toList();
    }

    private List<String> values(List<Argument> arguments) {
        return arguments.stream().map(Argument::getValue).toList();
    }

    @Test
    void RD1_RD2_RD3_RD5_immutableRequest() {
        List<Header> source = new ArrayList<>(List.of(new Header("X", "1"), new Header("X", "2")));
        RequestData r = new RequestData("q=%2B", source, "application/json", "{}", BodyState.TEXT);
        source.clear();
        assertEquals("q=%2B", r.getQuery());
        assertEquals("application/json", r.getContentType());
        assertEquals("{}", r.getBody());
        assertEquals(BodyState.TEXT, r.getBodyState());
        assertTrue(r.hasBody());
        assertEquals(List.of("1", "2"), r.getHeaders().stream().map(Header::getValue).toList());
        assertEquals("X", r.getHeaders().get(0).getName());
        assertThrows(UnsupportedOperationException.class, () -> r.getHeaders().clear());
        RequestData none = RequestData.none();
        assertEquals("", none.getQuery());
        assertEquals(List.of(), none.getHeaders());
        assertEquals("", none.getBody());
        assertEquals("", none.getContentType());
        assertEquals(BodyState.NONE, none.getBodyState());
        assertFalse(none.hasBody());
        assertFalse(new RequestData("", List.of(), "", "", BodyState.TEXT).hasBody());
    }

    @Test
    void RD4_nullArgumentsNameTheirParameter() {
        assertEquals(
                "query",
                assertThrows(
                                NullPointerException.class,
                                () -> new RequestData(null, List.of(), "", "", BodyState.NONE))
                        .getMessage());
        assertEquals(
                "headers",
                assertThrows(
                                NullPointerException.class,
                                () -> new RequestData("", null, "", "", BodyState.NONE))
                        .getMessage());
        assertEquals(
                "contentType",
                assertThrows(
                                NullPointerException.class,
                                () -> new RequestData("", List.of(), null, "", BodyState.NONE))
                        .getMessage());
        assertEquals(
                "body",
                assertThrows(
                                NullPointerException.class,
                                () -> new RequestData("", List.of(), "", null, BodyState.NONE))
                        .getMessage());
        assertEquals(
                "bodyState",
                assertThrows(
                                NullPointerException.class,
                                () -> new RequestData("", List.of(), "", "", null))
                        .getMessage());
        assertThrows(NullPointerException.class, () -> new Header(null, ""));
        assertThrows(NullPointerException.class, () -> new Header("", null));
    }

    @Test
    void RD6_RD7_callCompatibilityAndQuery() {
        ApiCall old = new ApiCall(HttpMethod.GET, "http://localhost/x?q=1", 200, "", "");
        assertEquals("", old.getRequest().getQuery());
        assertFalse(old.getRequest().hasBody());
        RequestData r = request("q=%2B", "", "");
        ApiCall call = new ApiCall(HttpMethod.POST, "http://localhost/x?q=%2B", 201, "H", "B", r);
        assertEquals("http://localhost/x", call.getUrl());
        assertEquals("q=%2B", call.getRequest().getQuery());
        assertThrows(
                NullPointerException.class,
                () -> new ApiCall(HttpMethod.GET, "x", 200, "", "", null));
    }

    @Test
    void MT1_MT2_normalization() {
        assertEquals("application/json", MediaType.normalise(" Application/JSON; charset=UTF-8 "));
        assertEquals("", MediaType.normalise(""));
        assertEquals("", MediaType.normalise(";charset=x"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "application/json",
                "text/json",
                "application/vnd.api+json",
                "application/problem+json"
            })
    void MT3_jsonTypes(String t) {
        assertTrue(MediaType.isJson(t));
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/jsonx", "text/plain", "application/json-seq", ""})
    void MT4_otherTypes(String t) {
        assertFalse(MediaType.isJson(t));
    }

    @Test
    void MT5_form() {
        assertTrue(MediaType.isForm("application/x-www-form-urlencoded"));
        assertFalse(MediaType.isForm("multipart/form-data"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/json", "a/b", "application/vnd.x+json"})
    void MT6_valid(String t) {
        assertTrue(MediaType.isValid(t));
    }

    @ParameterizedTest
    @ValueSource(strings = {"json", "a/b/c", "a /b", "/b", "a/", "", "a/b c"})
    void MT7_invalid(String t) {
        assertFalse(MediaType.isValid(t));
    }

    @Test
    void JR1_JR2_JR3_JR4_JR5_nestedContainersAndValues() {
        assertEquals(List.of("title", "userId"), names(json("{\"title\":\"Hi\",\"userId\":3}")));
        List<Argument> nested = json("{\"user\":{\"id\":3}}");
        assertEquals(List.of("user", "user.id"), names(nested));
        assertEquals(JsonKind.OBJECT, nested.get(0).getJsonKind());
        assertEquals(
                List.of("items", "items[]", "items[].id", "items[]", "items[].id"),
                names(json("{\"items\":[{\"id\":1},{\"id\":2}]}")));
        assertEquals(List.of("tags", "tags[]", "tags[]"), names(json("{\"tags\":[\"a\",\"b\"]}")));
        assertEquals(List.of("[]", "[].id"), names(json("[{\"id\":1}]")));
    }

    @Test
    void JR6_JR7_rootsAndEmptyContainers() {
        for (String value : List.of("42", "\"x\"", "true", "null"))
            assertEquals(List.of("$"), names(json(value)));
        assertEquals(JsonKind.NUMBER, json("42").get(0).getJsonKind());
        assertEquals(JsonKind.STRING, json("\"x\"").get(0).getJsonKind());
        assertEquals(JsonKind.BOOLEAN, json("true").get(0).getJsonKind());
        assertEquals(JsonKind.NULL, json("null").get(0).getJsonKind());
        assertTrue(json("{}").isEmpty());
        assertTrue(json("[]").isEmpty());
        assertEquals(JsonKind.ARRAY, json("{\"items\":[]}").get(0).getJsonKind());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.0", "1e3", "-0", "123456789012345678901234567890"})
    void JR8_numberLexemes(String n) {
        assertEquals(n, json("{\"n\":" + n + "}").get(0).getValue());
    }

    @Test
    void JR9_duplicateKeys() {
        assertEquals(List.of("1", "2"), values(json("{\"a\":1,\"a\":2}")));
    }

    @Test
    void JR10_escapedNames() {
        assertEquals(
                List.of("a\\.b", "c\\[d\\]", "e\\{f\\}", "g\\\\h"),
                names(json("{\"a.b\":1,\"c[d]\":2,\"e{f}\":3,\"g\\\\h\":4}")));
    }

    @Test
    void JR11_JR19_depthAndHostileNesting() {
        JsonReader ten = new JsonReader("{\"a\":".repeat(10) + "1" + "}".repeat(10), 10);
        assertEquals(10, ten.read().size());
        assertFalse(ten.isTooDeep());
        JsonReader eleven = new JsonReader("{\"a\":".repeat(11) + "1" + "}".repeat(11), 10);
        assertEquals(10, eleven.read().size());
        assertTrue(eleven.isTooDeep());
        assertTimeout(
                Duration.ofMillis(100),
                () -> {
                    JsonReader hostile = new JsonReader("[".repeat(50000), 10);
                    assertThrows(IllegalArgumentException.class, hostile::read);
                });
    }

    @Test
    void JR12_JR13_escapesBomAndSurrogates() {
        assertEquals("é\n\"x\"", json("{\"s\":\"\\u00e9\\n\\\"x\\\"\"}").get(0).getValue());
        assertEquals("1", json("\ufeff{\"a\":1}").get(0).getValue());
        assertEquals("\ud800", json("\"\\ud800\"").get(0).getValue());
        assertEquals("\b\f\r\t/\\", json("\"\\b\\f\\r\\t\\/\\\\\"").get(0).getValue());
    }

    @Test
    void JR14_trailingCommaPosition() {
        assertEquals(
                "trailing comma at position 8",
                assertThrows(IllegalArgumentException.class, () -> json("{\"a\":1,}"))
                        .getMessage());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{'a':1}",
                "{\"a\":NaN}",
                "{\"a\":Infinity}",
                "{\"a\":1}//x",
                "{\"a\":1",
                "",
                "01",
                "+1",
                ".5",
                "5.",
                "1e",
                "0x10",
                "\"\u0001\"",
                "\"\\x\"",
                "{\"a\":1} {\"b\":2}",
                "[1,]",
                "\"unclosed",
                "\"\\uZZZZ\""
            })
    void JR15_JR16_JR17_JR18_rejectMalformed(String text) {
        assertThrows(IllegalArgumentException.class, () -> json(text));
    }

    @Test
    void RP1_order() {
        var r =
                new RequestData(
                        "_limit=5",
                        List.of(new Header("Accept", "*/*")),
                        "application/json",
                        "{\"title\":\"Hi\",\"userId\":3}",
                        BodyState.TEXT);
        var result = RequestParser.parse(r, new LinkedHashMap<>(Map.of("POST_ID", "1")));
        assertEquals(
                List.of("POST_ID", "_limit", "title", "userId", "Accept"),
                names(result.getArguments()));
        assertEquals(
                List.of(
                        ArgumentLocation.PATH,
                        ArgumentLocation.QUERY,
                        ArgumentLocation.BODY,
                        ArgumentLocation.BODY,
                        ArgumentLocation.HEADER),
                result.getArguments().stream().map(Argument::getLocation).toList());
    }

    @Test
    void RP2_RP3_RP4_RP5_pairs() {
        assertEquals(List.of("a", "b"), names(parse("a=1&b=2", "", "").getArguments()));
        assertEquals(List.of("a", "b"), names(parse("a=1&&b=2&", "", "").getArguments()));
        assertEquals(List.of("a"), names(parse("&a=1", "", "").getArguments()));
        assertEquals(List.of("1;b=2"), values(parse("a=1;b=2", "", "").getArguments()));
        assertEquals(List.of("", "", "b=c"), values(parse("a&a=&a=b=c", "", "").getArguments()));
    }

    @Test
    void RP6_RP7_RP8_encoding() {
        assertEquals("a b+c d", parse("q=a+b%2Bc%20d", "", "").getArguments().get(0).getValue());
        for (String broken : List.of("%zz", "%2")) {
            Argument a = parse("n=" + broken, "", "").getArguments().get(0);
            assertEquals(broken, a.getValue());
            assertTrue(a.isMalformedEncoding());
        }
        assertEquals("é", parse("n=%C3%A9", "", "").getArguments().get(0).getValue());
        var invalid = parse("n=%FF", "", "").getArguments().get(0);
        assertEquals("�", invalid.getValue());
        assertTrue(invalid.isMalformedEncoding());
    }

    @Test
    void RP9_RP10_RP11_duplicatesLiteralNames() {
        assertEquals(List.of("a", "b"), values(parse("tag=a&tag=b", "", "").getArguments()));
        assertEquals(
                List.of("ids[]", "user.id", "Tag", "tag", ""),
                names(parse("ids[]=1&user.id=2&Tag=3&tag=4&=1", "", "").getArguments()));
    }

    @Test
    void RP12_RP13_bodyMediaTypes() {
        assertEquals(
                List.of("title", "userId"),
                names(
                        parse(
                                        "",
                                        "application/x-www-form-urlencoded; charset=UTF-8",
                                        "title=Hi&userId=3")
                                .getArguments()));
        for (String t : List.of("text/json", "application/hal+json"))
            assertEquals(List.of("a"), names(parse("", t, "{\"a\":1}").getArguments()));
    }

    @Test
    void RP14_RP15_RP16_RP17_bodyStates() {
        for (String t : List.of("text/plain", "")) {
            var p = parse("", t, "{\"a\":1}");
            assertTrue(p.getArguments().isEmpty());
            assertFalse(p.isBodyChecked());
            assertEquals(t, p.getContentType());
        }
        assertFalse(parse("", "application/json", "").isBodyMalformed());
        for (BodyState state : List.of(BodyState.CUT, BodyState.BINARY)) {
            var p =
                    RequestParser.parse(
                            new RequestData("", List.of(), "application/json", "cut", state),
                            Map.of());
            assertFalse(p.isBodyChecked());
            assertTrue(p.getArguments().isEmpty());
        }
        var malformed = parse("q=1", "application/json", "{\"a\":1,}");
        assertTrue(malformed.isBodyMalformed());
        assertFalse(malformed.getMalformedBodyReason().isEmpty());
        assertEquals(List.of("q"), names(malformed.getArguments()));
    }

    @Test
    void RP18_RP19_headers() {
        var p =
                RequestParser.parse(
                        new RequestData(
                                "",
                                List.of(
                                        new Header("Accept", "a, b"),
                                        new Header("X-Id", "1"),
                                        new Header("x-id", "2")),
                                "",
                                "",
                                BodyState.NONE),
                        Map.of());
        assertEquals(List.of("Accept", "X-Id", "x-id"), names(p.getArguments()));
        assertEquals(List.of("a, b", "1", "2"), values(p.getArguments()));
    }

    @Test
    void RP20_RP21_limits() {
        var p = parse("a=1&".repeat(250), "", "");
        assertEquals(200, p.getArguments().size());
        assertEquals(WarningKind.TOO_MANY_ARGUMENTS, p.getWarnings().get(0).getKind());
        var deep = parse("", "application/json", "[".repeat(12) + "0" + "]".repeat(12));
        assertEquals(WarningKind.TOO_DEEP, deep.getWarnings().get(0).getKind());
        var jsonLimit = parse("", "application/json", "[" + "1,".repeat(250) + "1]");
        assertEquals(200, jsonLimit.getArguments().size());
        assertEquals(WarningKind.TOO_MANY_ARGUMENTS, jsonLimit.getWarnings().get(0).getKind());
    }

    @Test
    void RP22_randomInputsNeverThrow() {
        Random random = new Random(9);
        for (int i = 0; i < 1000; i++) {
            StringBuilder s = new StringBuilder();
            for (int j = 0; j < 100; j++) s.append((char) random.nextInt(65536));
            var r =
                    new RequestData(
                            s.toString(),
                            List.of(new Header(s.toString(), s.toString())),
                            "application/json",
                            s.toString(),
                            BodyState.TEXT);
            assertNotNull(RequestParser.parse(r, Map.of()));
        }
    }

    @Test
    void RP23_linearQuery() {
        assertTimeout(Duration.ofMillis(50), () -> parse("a=1&".repeat(16384), "", ""));
    }
}
