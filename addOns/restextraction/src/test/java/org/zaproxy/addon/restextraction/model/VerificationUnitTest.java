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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VerificationUnitTest {
    private ParameterSpec p(
            ArgumentLocation location, String name, ParameterType type, boolean required) {
        return new ParameterSpec(location, name, type, required);
    }

    private ParameterSpec body(String name, ParameterType type, boolean required) {
        return p(ArgumentLocation.BODY, name, type, required);
    }

    private final IntegerType integer = new IntegerType(BigInteger.ONE, BigInteger.valueOf(100));

    private RecordedCall call(
            HttpMethod method,
            String query,
            String type,
            String body,
            List<Header> headers,
            BodyState state) {
        return new RecordedCall(
                new ApiCall(
                        method,
                        "http://localhost/posts/1",
                        200,
                        "",
                        "",
                        new RequestData(query, headers, type, body, state)),
                Map.of("POST_ID", "1"));
    }

    private RecordedCall call(String query, String type, String body) {
        return call(
                HttpMethod.POST,
                query,
                type,
                body,
                List.of(),
                body.isEmpty() ? BodyState.NONE : BodyState.TEXT);
    }

    private List<ViolationKind> kinds(Verdict v) {
        return v.getViolations().stream().map(Violation::getKind).toList();
    }

    private OperationSpec op(List<String> types, ParameterSpec... parameters) {
        return new OperationSpec(HttpMethod.POST, types, List.of(parameters));
    }

    private SpecItem item(ParameterSpec parameter) {
        return SpecItem.parameter(HttpMethod.POST, parameter);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "user.id",
                "items[].id",
                "[].id",
                "scores{}",
                "scores{}.x",
                "matrix[][]",
                "a\\.b"
            })
    void FN1_validNames(String s) {
        FieldName.parse(s);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a..b", ".a", "a.", "a[", "a[1]", "{}x", "a{x}", "a]", " a"})
    void FN2_invalidNames(String s) {
        assertTrue(
                assertThrows(IllegalArgumentException.class, () -> FieldName.parse(s))
                        .getMessage()
                        .contains("field"));
    }

    @Test
    void FN3_FN4_FN5_FN6_FN7_FN8_FN9_matchingSpecificity() {
        assertTrue(FieldName.parse("user.id").matches("user.id"));
        assertFalse(FieldName.parse("user.id").matches("user.ID"));
        assertFalse(FieldName.parse("user.id").matches("user"));
        assertTrue(FieldName.parse("items[].id").matches("items[].id"));
        assertFalse(FieldName.parse("items[].id").matches("items.id"));
        for (String name : List.of("scores.alice", "scores.bob"))
            assertTrue(FieldName.parse("scores{}").matches(name));
        for (String name : List.of("scores", "scores.a.b"))
            assertFalse(FieldName.parse("scores{}").matches(name));
        assertTrue(FieldName.parse("m{}.x").matches("m.a.x"));
        assertFalse(FieldName.parse("m{}.x").matches("m.a.y"));
        assertTrue(FieldName.parse("a\\.b").matches("a\\.b"));
        assertFalse(FieldName.parse("a\\.b").matches("a.b"));
        assertTrue(FieldName.parse("scores.alice").isMoreSpecificThan(FieldName.parse("scores{}")));
        assertTrue(FieldName.parse("m.a{}").isMoreSpecificThan(FieldName.parse("m{}.x")));
        assertFalse(FieldName.parse("m{}.x").isMoreSpecificThan(FieldName.parse("m.a{}")));
        assertTrue(FieldName.parse("items[].id").isWildcard());
        assertTrue(FieldName.parse("scores{}").isWildcard());
        assertFalse(FieldName.parse("user.id").isWildcard());
    }

    @Test
    void PS1_PS2_PS3_PS4_validation() {
        for (String name : List.of("", " id", "id "))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> p(ArgumentLocation.QUERY, name, new StringType(), false));
        assertThrows(IllegalArgumentException.class, () -> body("a..b", new StringType(), false));
        for (String name : List.of("items[].id", "scores{}"))
            assertThrows(IllegalArgumentException.class, () -> body(name, new StringType(), true));
        assertThrows(
                IllegalArgumentException.class,
                () -> p(ArgumentLocation.PATH, "ID", integer, true));
    }

    @Test
    void PS5_PS6_PS7_PS8_PS9_matchingAndCheck() {
        var q = p(ArgumentLocation.QUERY, "tag", integer, false);
        assertTrue(q.accepts(new Argument("tag", ArgumentLocation.QUERY, "1", null, false), false));
        assertFalse(
                q.accepts(new Argument("Tag", ArgumentLocation.QUERY, "1", null, false), false));
        assertFalse(q.accepts(new Argument("tag", ArgumentLocation.BODY, "1", null, false), false));
        assertTrue(
                p(ArgumentLocation.HEADER, "X-Id", integer, false)
                        .accepts(
                                new Argument("x-id", ArgumentLocation.HEADER, "1", null, false),
                                false));
        for (boolean json : List.of(true, false))
            assertTrue(
                    body("user.id", integer, false)
                            .accepts(
                                    new Argument(
                                            "user.id", ArgumentLocation.BODY, "1", null, false),
                                    json));
        assertFalse(
                body("scores{}", integer, false)
                        .accepts(
                                new Argument("scores.a", ArgumentLocation.BODY, "1", null, false),
                                false));
        assertTrue(
                q.check(new Argument("tag", ArgumentLocation.QUERY, "1", null, false)).isMatched());
        assertFalse(
                q.check(new Argument("tag", ArgumentLocation.QUERY, "x", null, false)).isMatched());
    }

    @Test
    void OS1_OS2_OS3_OS4_OS5_contentTypes() {
        assertTrue(
                kinds(
                                op(List.of("application/json"))
                                        .verify(call("", "application/json; charset=UTF-8", "{}")))
                        .isEmpty());
        assertEquals(
                List.of(ViolationKind.UNSPECIFIED_CONTENT_TYPE),
                kinds(op(List.of("application/json")).verify(call("", "text/plain", "x"))));
        var form =
                op(List.of("application/json"), body("title", new StringType(), false))
                        .verify(call("", "application/x-www-form-urlencoded", "title=Hi"));
        assertEquals(List.of(ViolationKind.UNSPECIFIED_CONTENT_TYPE), kinds(form));
        assertEquals(1, form.getChecks().size());
        assertTrue(kinds(op(List.of()).verify(call("", "text/plain", "x"))).isEmpty());
        assertTrue(kinds(op(List.of("application/json")).verify(call("", "", ""))).isEmpty());
    }

    @Test
    void OS6_OS7_OS8_unreadableBodies() {
        var operation = op(List.of(), body("title", integer, true));
        assertEquals(
                List.of(ViolationKind.MALFORMED_BODY),
                kinds(operation.verify(call("", "application/json", "{\"x\":1,}"))));
        for (BodyState state : List.of(BodyState.CUT, BodyState.BINARY)) {
            var v =
                    operation.verify(
                            call(HttpMethod.POST, "", "application/json", "cut", List.of(), state));
            assertEquals(List.of(), kinds(v));
            assertEquals(WarningKind.BODY_NOT_CHECKED, v.getWarnings().get(0).getKind());
            assertTrue(
                    op(List.of())
                            .verify(
                                    call(
                                            HttpMethod.POST,
                                            "",
                                            "application/json",
                                            "cut",
                                            List.of(),
                                            state))
                            .getWarnings()
                            .isEmpty());
        }
        assertEquals(
                WarningKind.BODY_NOT_CHECKED,
                operation.verify(call("", "text/plain", "x")).getWarnings().get(0).getKind());
    }

    @Test
    void OS9_OS10_OS11_OS12_OS13_partialSpecs() {
        var query = p(ArgumentLocation.QUERY, "_limit", integer, false);
        assertTrue(
                op(List.of(), query)
                        .verify(call("_limit=5", "", ""))
                        .getChecks()
                        .get(0)
                        .isMatched());
        assertEquals(
                List.of(ViolationKind.OUT_OF_RANGE, ViolationKind.EXTRA_ARGUMENT),
                kinds(op(List.of(), query).verify(call("_limit=500&debug=1", "", ""))));
        assertTrue(
                kinds(
                                op(List.of(), body("title", new StringType(), false))
                                        .verify(call("debug=1", "", "")))
                        .isEmpty());
        var headers =
                op(List.of(), p(ArgumentLocation.HEADER, "X-Id", integer, false))
                        .verify(
                                call(
                                        HttpMethod.POST,
                                        "",
                                        "",
                                        "",
                                        List.of(new Header("x-id", "1"), new Header("Accept", "a")),
                                        BodyState.NONE));
        assertEquals(1, headers.getChecks().size());
        assertEquals(2, headers.getUnchecked().size());
        assertTrue(headers.getViolations().isEmpty());
        assertTrue(
                kinds(
                                op(List.of(), p(ArgumentLocation.PATH, "POST_ID", integer, false))
                                        .verify(call("debug=1", "application/json", "{\"x\":1}")))
                        .isEmpty());
    }

    @Test
    void OS14_OS15_OS16_OS17_containersAndRequired() {
        var v =
                op(List.of(), body("user", integer, false), body("title", new StringType(), false))
                        .verify(call("", "application/json", "{\"user\":{\"id\":1,\"x\":2}}"));
        assertEquals(List.of(ViolationKind.INVALID_TYPE), kinds(v));
        var nested =
                op(List.of(), body("user.id", integer, false))
                        .verify(call("", "application/json", "{\"user\":{\"id\":1}}"));
        assertEquals(List.of(), kinds(nested));
        assertEquals(1, nested.getChecks().size());
        for (String t : List.of("application/json", "application/x-www-form-urlencoded")) {
            String b = t.endsWith("json") ? "{\"x\":1}" : "x=1";
            assertTrue(
                    kinds(
                                    op(List.of(), body("title", new StringType(), true))
                                            .verify(call("", t, b)))
                            .contains(ViolationKind.MISSING_ARGUMENT));
        }
        var present =
                op(List.of(), p(ArgumentLocation.QUERY, "page", new StringType(), true))
                        .verify(call("page=", "", ""));
        assertEquals(List.of(), kinds(present));
    }

    @Test
    void OS18_OS19_OS20_repeatedAndWarnings() {
        var string =
                op(List.of(), p(ArgumentLocation.QUERY, "tag", new StringType(), false))
                        .verify(call("tag=a&tag=b", "", ""));
        assertEquals(2, string.getChecks().size());
        assertEquals(WarningKind.REPEATED_PARAMETER, string.getWarnings().get(0).getKind());
        assertEquals(VerdictStatus.VALID, string.getStatus());
        var mixed =
                op(List.of(), p(ArgumentLocation.QUERY, "tag", integer, false))
                        .verify(call("tag=1&tag=x", "", ""));
        assertEquals(List.of(ViolationKind.INVALID_TYPE), kinds(mixed));
        assertEquals(1, mixed.getWarnings().size());
        var precision =
                op(List.of(), body("n", new IntegerType(null, null), false))
                        .verify(call("", "application/json", "{\"n\":9007199254740993}"));
        assertEquals(VerdictStatus.VALID, precision.getStatus());
        assertEquals(WarningKind.PRECISION, precision.getWarnings().get(0).getKind());
    }

    @Test
    void OS21_OS22_orderAndResponseIndependence() {
        var operation =
                op(
                        List.of("application/json"),
                        p(ArgumentLocation.QUERY, "page", integer, false),
                        body("title", integer, false),
                        p(ArgumentLocation.HEADER, "X", new StringType(), true));
        assertEquals(
                List.of(
                        ViolationKind.UNSPECIFIED_CONTENT_TYPE,
                        ViolationKind.EXTRA_ARGUMENT,
                        ViolationKind.INVALID_TYPE,
                        ViolationKind.MISSING_ARGUMENT),
                kinds(
                        operation.verify(
                                call("extra=1", "application/x-www-form-urlencoded", "title=x"))));
        var r = new RequestData("page=1", List.of(), "", "", BodyState.NONE);
        for (int status : List.of(200, 400, 500))
            assertEquals(
                    VerdictStatus.VALID,
                    op(List.of(), p(ArgumentLocation.QUERY, "page", integer, false))
                            .verify(
                                    new RecordedCall(
                                            new ApiCall(
                                                    HttpMethod.POST,
                                                    "http://localhost/x",
                                                    status,
                                                    "",
                                                    "",
                                                    r),
                                            Map.of()))
                            .getStatus());
    }

    @Test
    void OS23_OS24_specificityAndOptionalArrays() {
        var v =
                op(
                                List.of(),
                                body("scores.alice", integer, false),
                                body("scores{}", new StringType(), false))
                        .verify(
                                call(
                                        "",
                                        "application/json",
                                        "{\"scores\":{\"alice\":\"x\",\"bob\":\"y\"}}"));
        assertEquals(List.of(ViolationKind.INVALID_TYPE), kinds(v));
        assertFalse(v.getChecks().get(0).isMatched());
        assertTrue(v.getChecks().get(1).isMatched());
        assertTrue(
                op(List.of(), body("items[].id", integer, false))
                        .verify(call("", "application/json", "{\"items\":[]}"))
                        .getViolations()
                        .isEmpty());
    }

    @Test
    void ES1_ES2_ES3_ES4_neutralPreflightAndMethods() {
        assertEquals(
                VerdictStatus.NOT_VERIFIED,
                EndpointSpec.empty().verify(call("", "", "")).getStatus());
        EndpointSpec spec =
                EndpointSpec.empty()
                        .with(SpecItem.method(HttpMethod.GET))
                        .with(SpecItem.method(HttpMethod.POST));
        assertTrue(
                spec.verify(
                                call(
                                        HttpMethod.OPTIONS,
                                        "",
                                        "",
                                        "",
                                        List.of(
                                                new Header(
                                                        "Access-Control-Request-Method", "POST")),
                                        BodyState.NONE))
                        .isPreflight());
        assertFalse(
                spec.verify(call(HttpMethod.OPTIONS, "", "", "", List.of(), BodyState.NONE))
                        .isPreflight());
        for (HttpMethod m : List.of(HttpMethod.OPTIONS, HttpMethod.PUT, HttpMethod.OTHER))
            assertEquals(
                    List.of(ViolationKind.UNSPECIFIED_METHOD),
                    kinds(spec.verify(call(m, "", "", "", List.of(), BodyState.NONE))));
    }

    @Test
    void ES5_ES6_ES7_ES8_ES10_ES11_ES12_ES13_ES14_ES15_ES16_ES17_editsAreAtomicImmutable() {
        EndpointSpec empty = EndpointSpec.empty();
        var method = SpecItem.method(HttpMethod.GET);
        var get = empty.with(method);
        assertTrue(empty.isEmpty());
        assertNotSame(empty, get);
        assertThrows(IllegalArgumentException.class, () -> get.with(method));
        assertThrows(IllegalArgumentException.class, () -> SpecItem.method(HttpMethod.OTHER));
        var q = item(p(ArgumentLocation.QUERY, "tag", new StringType(), false));
        var with = empty.with(q);
        assertEquals(2, with.items().size());
        assertEquals(SpecItemKind.METHOD, with.items().get(0).getKind());
        assertTrue(with.without(SpecItem.method(HttpMethod.POST)).isEmpty());
        var ct = SpecItem.contentType(HttpMethod.POST, "application/json");
        var typed = with.with(ct);
        assertThrows(
                IllegalArgumentException.class,
                () -> typed.with(SpecItem.contentType(HttpMethod.POST, "Application/JSON")));
        var plain = empty.with(SpecItem.contentType(HttpMethod.POST, "text/plain"));
        var b = item(body("title", new StringType(), false));
        assertThrows(IllegalArgumentException.class, () -> plain.with(b));
        var both = typed.with(b).with(SpecItem.contentType(HttpMethod.POST, "text/plain"));
        assertThrows(IllegalArgumentException.class, () -> both.without(ct));
        for (HttpMethod m : List.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.DELETE))
            assertFalse(
                    empty.with(SpecItem.parameter(m, body("title", new StringType(), false)))
                            .isEmpty());
        assertTrue(plain.operation(HttpMethod.POST).orElseThrow().getParameters().isEmpty());
        assertEquals(HttpMethod.GET, with.with(method).items().get(0).getMethod());
        assertEquals(
                4,
                with.with(
                                SpecItem.parameter(
                                        HttpMethod.GET,
                                        p(ArgumentLocation.QUERY, "tag", new StringType(), false)))
                        .items()
                        .size());
        var h = empty.with(item(p(ArgumentLocation.HEADER, "X-Id", new StringType(), false)));
        assertThrows(
                IllegalArgumentException.class,
                () -> h.with(item(p(ArgumentLocation.HEADER, "x-id", new StringType(), false))));
        var old = item(p(ArgumentLocation.QUERY, "n", integer, false));
        var ranged = empty.with(old);
        var replaced =
                ranged.without(old)
                        .with(item(p(ArgumentLocation.QUERY, "n", new StringType(), false)));
        assertEquals(
                "",
                replaced.operation(HttpMethod.POST)
                        .orElseThrow()
                        .getParameters()
                        .get(0)
                        .getType()
                        .describeRange());
    }

    @Test
    void SI1_SI2_VD1_VD2_VD3_VD4_VD5_valueObjects() {
        for (ArgumentLocation l : ArgumentLocation.values())
            assertTrue(
                    SpecItem.parameter(HttpMethod.GET, p(l, "x", new StringType(), false))
                                    .getParameter()
                            != null);
        assertThrows(
                IllegalArgumentException.class,
                () -> SpecItem.contentType(HttpMethod.POST, "json"));
        assertEquals(
                VerdictStatus.VALID,
                new Verdict(List.of(), List.of(), List.of(), List.of()).getStatus());
        var violation =
                new Violation(
                        ViolationKind.EXTRA_ARGUMENT,
                        ArgumentLocation.QUERY,
                        "x",
                        "y",
                        List.of("d"));
        var source = new ArrayList<>(List.of(violation));
        var v = new Verdict(List.of(), source, List.of(), List.of());
        source.clear();
        assertEquals(VerdictStatus.INVALID, v.getStatus());
        assertEquals(1, v.getViolations().size());
        assertThrows(UnsupportedOperationException.class, () -> v.getViolations().clear());
        assertEquals(VerdictStatus.NOT_VERIFIED, Verdict.notVerified(true).getStatus());
        assertTrue(Verdict.notVerified(true).isPreflight());
        assertThrows(
                NullPointerException.class, () -> new Violation(null, null, "", "", List.of()));
        assertThrows(NullPointerException.class, () -> new Warning(null, null, "", List.of()));
        assertEquals("x", violation.getName());
        assertEquals("y", violation.getValue());
        assertEquals(List.of("d"), violation.getDetails());
    }
}
