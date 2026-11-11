# US11 REST verification: test plan

Version 0.1 · design: `US11 specification.md` (rules SPEC-…, decisions D…), `US11 class diagram.puml`,
`US11 sequence diagrams.puml` · US10 test plan 0.4 stays valid for US10 behaviour.

## How to read this plan

- **ID**: unique test ID (group + number). **Source**: the rule or decision the expected result comes
  from (SPEC-…, D…); every rule in the specification is cited at least once.
- Class names end in `UnitTest` (unit and integration tests) or `ScenarioTest` (end-to-end), as in
  US10, so they can be counted for the sign-off card. JUnit Jupiter, Hamcrest `assertThat`,
  Mockito, `// Given // When // Then` comments, method names `shouldXWhenY`.
- Location: `zap-extensions/addOns/restextraction/src/test/java/org/zaproxy/addon/restextraction/`
  (`model/`, `ui/`, root package for listener and scenarios).

### Levels

| Level | Meaning in this plan | Groups |
|---|---|---|
| Unit | one class; collaborators simple values or mocks | RD, MT, JR, RP, FN, PT-S, PT-I, PT-D, PT-B, PS, OS, ES, SI, VD, UM, PD, UP, SM, CT, CW, WR, MSG |
| Integration | several real domain classes or the listener with real `HttpMessage`s; no Swing, no network | TL, EI, NS, RX, CC |
| End-to-end | a whole session: hand-made messages through the listener → extractor → report → writer, or the real panel driven with a real extractor | SC-V, PN-E |
| Manual | ZAP from source, the Firefox profile `ZAP`, jsonplaceholder.typicode.com and Juice Shop (localhost:3000) | M-V |

### Shared test data and helpers

- `request(query, headers, contentType, body)` builds a `RequestData` (state TEXT, or NONE for `""`).
- `call(method, url, request)` builds an `ApiCall` with status 200 and empty response.
- `arg(name, location, value)` / `json(name, value, kind)` build `Argument`s.
- `spec(item…)` builds an `EndpointSpec` from `SpecItem`s; `query("_limit", integer(1, 100))`,
  `body("title", string(), required)` etc. build items.
- `ScenarioSession` (US10) gets `send(method, url, status, requestHeaders, requestContentType,
  requestBody)` and `verdict(ns, endpoint, call)`.
- Sample requests used by several groups: **S1** `GET /posts/1/comments?_limit=5`; **S2** `POST
  /posts/1/comments` with `{"title":"Hi","userId":3}`; **S3** form `title=Hi&userId=3`;
  **S4** `GET /posts/abc/comments?_limit=500&debug=1`.

---

## 1. Unit tests

### 1.1 RD `RequestData`, `Header`, `ApiCall` (changed)

| ID | Case | Expected | Source |
|---|---|---|---|
| RD1 | getters of `RequestData(q, headers, ct, body, TEXT)` | the constructor values; headers in the given order | SPEC-C01, SPEC-C03 |
| RD2 | `RequestData.none()` | query `""`, no headers, content type `""`, body `""`, state NONE, `hasBody()` false | SPEC-C02, SPEC-C04, SPEC-C11 |
| RD3 | state TEXT with body `""` | `hasBody()` false | SPEC-C11 |
| RD4 | `null` for any argument | `NullPointerException` naming the argument | SPEC-C01 |
| RD5 | the returned header list | cannot be modified; two headers with the same name are both kept | SPEC-C03 |
| RD6 | US10 `ApiCall` constructor without request | `getRequest()` equals `RequestData.none()`; US10 getters unchanged | SPEC-C01 |
| RD7 | `ApiCall` with a URL with a query and a request with that query | URL without query (US10), `getRequest().getQuery()` keeps it, still escaped | SPEC-C02 |

### 1.2 MT `MediaType`

| ID | Case | Expected | Source |
|---|---|---|---|
| MT1 | `normalise(" Application/JSON; charset=UTF-8 ")` | `application/json` | SPEC-C09 |
| MT2 | `normalise("")`, `normalise(";charset=x")` | `""` both | SPEC-C09, SPEC-C12 |
| MT3 | `isJson` of `application/json`, `text/json`, `application/vnd.api+json`, `application/problem+json` | true | SPEC-C10 |
| MT4 | `isJson` of `application/jsonx`, `text/plain`, `application/json-seq`, `""` | false | SPEC-C10 |
| MT5 | `isForm("application/x-www-form-urlencoded")` / of `multipart/form-data` | true / false | SPEC-C10 |
| MT6 | `isValid` of `application/json`, `a/b`, `application/vnd.x+json` | true | SPEC-S06 |
| MT7 | `isValid` of `json`, `a/b/c`, `a /b`, `/b`, `a/`, `""`, `a/b c` | false | SPEC-S06 |

### 1.3 JR `JsonReader`

Each row: `new JsonReader(text, 10).read()` gives the listed arguments (name = value (kind)), in order.

| ID | Input | Expected | Source |
|---|---|---|---|
| JR1 | `{"title":"Hi","userId":3}` | `title`=Hi (STRING), `userId`=3 (NUMBER) | SPEC-J02 |
| JR2 | `{"user":{"id":3}}` | `user` (OBJECT), `user.id`=3 | SPEC-J02, SPEC-J03 |
| JR3 | `{"items":[{"id":1},{"id":2}]}` | `items` (ARRAY), `items[]` (OBJECT) ×2, `items[].id`=1, `items[].id`=2 | SPEC-J02, SPEC-J03 |
| JR4 | `{"tags":["a","b"]}` | `tags` (ARRAY), `tags[]`=a, `tags[]`=b | SPEC-J02 |
| JR5 | `[{"id":1}]` | `[]` (OBJECT), `[].id`=1 | SPEC-J02 |
| JR6 | `42`, `"x"`, `true`, `null` | one argument `$` with kinds NUMBER, STRING, BOOLEAN, NULL | SPEC-J02 |
| JR7 | `{}`, `{"items":[]}` | none / `items` (ARRAY) only | SPEC-J03 |
| JR8 | `{"n":1.0}`, `{"n":1e3}`, `{"n":-0}`, `{"n":123456789012345678901234567890}` | value text exactly `1.0`, `1e3`, `-0`, `123456789012345678901234567890` | SPEC-J04 |
| JR9 | `{"a":1,"a":2}` | two arguments `a` (1, 2) | SPEC-J05 |
| JR10 | `{"a.b":1,"c[d]":2,"e{f}":3,"g\\h":4}` | names `a\.b`, `c\[d\]`, `e\{f\}`, `g\\h` | SPEC-J06 |
| JR11 | 10 nested objects / 11 nested objects | all read, `isTooDeep()` false / values below level 10 not read, `isTooDeep()` true | SPEC-J07 |
| JR12 | `{"s":"\u00e9\n\"x\""}` | `s` = `é` + newline + `"x"` | SPEC-J01 |
| JR13 | UTF-8 BOM before `{"a":1}` | `a`=1 | SPEC-J01 |
| JR14 | `{"a":1,}` | malformed, reason "trailing comma", position 8 | SPEC-J01 |
| JR15 | `{'a':1}`, `{"a":NaN}`, `{"a":Infinity}`, `{"a":1}//x`, `{"a":1`, `""` | malformed | SPEC-J01 |
| JR16 | numbers `01`, `+1`, `.5`, `5.`, `1e`, `0x10` | malformed (not JSON numbers) | SPEC-J01, D9 |
| JR17 | string with a raw control character, `"\x"` escape, lone `"\ud800"` | malformed / malformed / accepted as U+D800 kept | SPEC-J01 |
| JR18 | `{"a":1} {"b":2}` (two values) | malformed | SPEC-J01 |
| JR19 | 50 000 nested `[` | malformed or too deep within 100 ms, no `StackOverflowError` | SPEC-L04, SPEC-J07 |

### 1.4 RP `RequestParser`

`RequestParser.parse(request, pathArguments)`; arguments listed as location name=value.

| ID | Input | Expected | Source |
|---|---|---|---|
| RP1 | path `{POST_ID=1}`, query `_limit=5`, JSON body S2, header `Accept: */*` | order: PATH POST_ID=1, QUERY _limit=5, BODY title, userId, HEADER Accept | SPEC-C08 |
| RP2 | query `a=1&b=2` | QUERY a=1, b=2 | SPEC-Q01 |
| RP3 | query `a=1&&b=2&`, `&a=1` | a, b / a | SPEC-Q01 |
| RP4 | query `a=1;b=2` | one argument a=`1;b=2` | SPEC-Q01 |
| RP5 | query `a`, `a=`, `a=b=c` | a=`""`, a=`""`, a=`b=c` | SPEC-Q02 |
| RP6 | query `q=a+b%2Bc%20d` | q=`a b+c d` | SPEC-Q03 |
| RP7 | query `n=%zz`, `n=%2` | n=`%zz` / n=`%2`, both marked MALFORMED_ENCODING | SPEC-Q04 |
| RP8 | query `n=%C3%A9` / `n=%FF` | n=`é` / n=`�` marked MALFORMED_ENCODING | SPEC-Q05 |
| RP9 | query `tag=a&tag=b` | two arguments tag, in order | SPEC-Q06 |
| RP10 | query `ids[]=1&user.id=2&Tag=3&tag=4` | names `ids[]`, `user.id`, `Tag`, `tag` (literal, case kept) | SPEC-Q07 |
| RP11 | query `=1` | argument with name `""` | SPEC-Q08 |
| RP12 | form body S3, content type `application/x-www-form-urlencoded; charset=UTF-8` | BODY title=Hi, userId=3 (no JSON kind) | SPEC-C10, SPEC-Q01 |
| RP13 | JSON body with content type `text/json` / `application/hal+json` | parsed as JSON | SPEC-C10 |
| RP14 | body `{"a":1}` with content type `text/plain` / `""` | no BODY arguments, body not checked; content type `""` kept | SPEC-C10, SPEC-C12 |
| RP15 | content type JSON, body `""` | no body, no BODY arguments, not malformed | SPEC-C11 |
| RP16 | body state CUT or BINARY with JSON type | not parsed; `isBodyChecked()` false | SPEC-J08, SPEC-C05 |
| RP17 | malformed JSON body | no BODY arguments; `isBodyMalformed()` true with the reader's reason | SPEC-J01, SPEC-V06 |
| RP18 | headers `Accept: a`, `X-Id: 1`, `x-id: 2` | HEADER Accept=a, X-Id=1, x-id=2 (names as sent) | SPEC-H01, SPEC-H02 |
| RP19 | header `Accept: a, b` | one argument Accept=`a, b` | SPEC-H02 |
| RP20 | 250 query parameters | first 200 kept, warning TOO_MANY_ARGUMENTS | SPEC-L01 |
| RP21 | JSON nested 11 deep | warning TOO_DEEP | SPEC-J07 |
| RP22 | random bytes as query, body and header values (1000 seeds) | never throws | SPEC-C07, SPEC-L04 |
| RP23 | query of 64 KB of `a=1&` | parsed in under 50 ms (linear) | SPEC-L04 |

### 1.5 FN `FieldName`

| ID | Case | Expected | Source |
|---|---|---|---|
| FN1 | parse `user.id`, `items[].id`, `[].id`, `scores{}`, `scores{}.x`, `matrix[][]`, `a\.b` | accepted | SPEC-J09 |
| FN2 | parse `""`, `a..b`, `.a`, `a.`, `a[`, `a[1]`, `{}x`, `a{x}`, `a]`, ` a` | `IllegalArgumentException` with the position | SPEC-J09, SPEC-S09 |
| FN3 | `user.id` matches `user.id` / `user.ID` / `user` | true / false / false | SPEC-J10 |
| FN4 | `items[].id` matches `items[].id` / `items.id` | true / false | SPEC-J10 |
| FN5 | `scores{}` matches `scores.alice`, `scores.bob` / `scores` / `scores.a.b` | true, true / false / false | SPEC-J10 |
| FN6 | `m{}.x` matches `m.a.x` / `m.a.y` | true / false | SPEC-J10 |
| FN7 | `a\.b` matches `a\.b` / `a.b` | true / false | SPEC-J06, SPEC-J10 |
| FN8 | `scores.alice` more specific than `scores{}`; `m.a{}` more specific than `m{}.x` | true; true (and false the other way) | SPEC-J11 |
| FN9 | `isWildcard()` for `items[].id` / `scores{}` / `user.id` | true / true / false | SPEC-S12 |

### 1.6 PT `ParameterType` subclasses

`check(argument)` returns no violation (✓) or a violation of the given kind (✗). "text" = location
QUERY without JSON kind, "json" = location BODY with the given JSON kind.

**PT-S `StringType`**

| ID | Case | Expected | Source |
|---|---|---|---|
| PT-S1 | text `""`, `abc`, ` a b ` | ✓ (value unchanged) | SPEC-T02, SPEC-T01 |
| PT-S2 | json STRING `"x"`, NUMBER `3`, BOOLEAN `true` | ✓ | SPEC-T02, D7 |
| PT-S3 | json NULL, OBJECT, ARRAY | ✗ INVALID_TYPE naming the JSON kind | SPEC-T02, D7 |
| PT-S4 | text marked MALFORMED_ENCODING `%zz` | ✓ | SPEC-T12 |

**PT-I `IntegerType`**

| ID | Case | Expected | Source |
|---|---|---|---|
| PT-I1 | text `42`, `-7`, `+3`, `007`, `0` | ✓ | SPEC-T03 |
| PT-I2 | text `""`, `4.0`, `1e3`, `0x10`, ` 42`, `42 `, `4 2`, `١٢`, `+`, `-` | ✗ INVALID_TYPE | SPEC-T03, SPEC-T01 |
| PT-I3 | text `123456789012345678901234567890` without range | ✓ | SPEC-T03, SPEC-T08 |
| PT-I4 | json NUMBER `42`, `-7`, `0`, `-0` | ✓ | SPEC-T04 |
| PT-I5 | json NUMBER `1.0`, `1e3`, `1E+2` | ✗ INVALID_TYPE "a number with a fraction or exponent" | SPEC-T04, D9 |
| PT-I6 | json STRING `"42"`, BOOLEAN, NULL, OBJECT | ✗ INVALID_TYPE "expected Integer (a JSON number), got …" | SPEC-T04, D7 |
| PT-I7 | range 1..100: `1`, `100`, `50` | ✓ (bounds inclusive) | SPEC-T08 |
| PT-I8 | range 1..100: `0`, `101`, `-1` | ✗ OUT_OF_RANGE below the minimum 1 / above the maximum 100 | SPEC-T08 |
| PT-I9 | range min only 0 / max only 10 | `-1` ✗, `10^30` ✓ / `11` ✗, `-10^30` ✓ | SPEC-T08 |
| PT-I10 | range 0..0 with `-0`, `+0`, `000` | ✓ | SPEC-T09, SPEC-T08 |
| PT-I11 | text of 10 001 digits | ✗ INVALID_TYPE "too long", no BigInteger built | SPEC-T10 |
| PT-I12 | json `9007199254740991` / `9007199254740992` / `-9007199254740993` | no warning / warning PRECISION / warning PRECISION (`warningFor`) | SPEC-T11 |
| PT-I13 | text marked MALFORMED_ENCODING `4%2` | ✗ INVALID_TYPE "broken %-escape" | SPEC-T12 |
| PT-I14 | `parseBound` of `10`, `-5`, `+1` / of `1.5`, `abc`, `""` | values / `IllegalArgumentException` | SPEC-S11 |

**PT-D `DecimalType`**

| ID | Case | Expected | Source |
|---|---|---|---|
| PT-D1 | text `9.99`, `-0.5`, `3`, `1e3`, `1.5E-2`, `+2`, `007.50` | ✓ | SPEC-T05 |
| PT-D2 | text `""`, `.5`, `5.`, `NaN`, `Infinity`, `1,5`, `0x1p3`, `1e`, ` 1` | ✗ INVALID_TYPE | SPEC-T05, SPEC-T01 |
| PT-D3 | json NUMBER `9.99`, `1e3`, `-0`, `1E-400` | ✓ | SPEC-T06 |
| PT-D4 | json STRING `"9.99"`, BOOLEAN | ✗ INVALID_TYPE naming the JSON kind | SPEC-T06, D7 |
| PT-D5 | range 0.5..2.5: `0.5`, `2.50`, `1` | ✓ | SPEC-T08 |
| PT-D6 | range max 0.3: `0.30000000000000004` / `0.3000` | ✗ OUT_OF_RANGE / ✓ (exact compare) | SPEC-T08 |
| PT-D7 | range 1.0..1.00: `1` | ✓ (`1.0` = `1.00`) | SPEC-T08 |
| PT-D8 | text of 10 001 characters / `1e10000` / `1e9999` | ✗ / ✗ (exponent too large) / ✓ | SPEC-T10 |
| PT-D9 | json `1e400` / `9007199254740993` / `0.1` | PRECISION / PRECISION / no warning | SPEC-T11 |
| PT-D10 | `parseBound` of `0.5`, `-1e2` / of `.5`, `x` | values / `IllegalArgumentException` | SPEC-S11 |

**PT-B `Base64Type`**

| ID | Case | Expected | Source |
|---|---|---|---|
| PT-B1 | `aGVsbG8=`, `aGVsbG8`, `aGk=`, `aGk`, `YQ==`, `YQ`, `a-_b`, `+/+/` | ✓ | SPEC-T07, D10 |
| PT-B2 | `""` | ✗ INVALID_TYPE | SPEC-T07 |
| PT-B3 | `a+b-` (mixed alphabets) | ✗ | SPEC-T07, D10 |
| PT-B4 | `a===`, `ab=c`, `=abc`, `YQ=` (padded but length not a multiple of 4), `YQ===` | ✗ | SPEC-T07 |
| PT-B5 | `abcde` (length mod 4 = 1) | ✗ | SPEC-T07 |
| PT-B6 | `aGVs bG8=`, `aGVsbG8=\n` | ✗ (no whitespace, no trim) | SPEC-T07, SPEC-T01 |
| PT-B7 | json STRING `"aGk="` / NUMBER `1234` | ✓ / ✗ naming the JSON kind | SPEC-T07, D7 |
| PT-B8 | query value from `img=ab+c` (decoded `ab c`) / from `img=ab%2Bc` | ✗ / ✓ | SPEC-T07, SPEC-Q03 |
| PT-B9 | text marked MALFORMED_ENCODING | ✗ | SPEC-T12 |

### 1.7 PS `ParameterSpec`

| ID | Case | Expected | Source |
|---|---|---|---|
| PS1 | name `""`, `" id"`, `"id "` | `IllegalArgumentException` | SPEC-S08 |
| PS2 | BODY name `a..b` | refused with the FieldName message | SPEC-S09 |
| PS3 | Required on BODY `items[].id` / `scores{}` | refused | SPEC-S12 |
| PS4 | Required on PATH | refused (path variables have no Required) | SPEC-S07, D8 |
| PS5 | `accepts`: QUERY `tag` vs QUERY `tag` / QUERY `Tag` / BODY `tag` | true / false / false | SPEC-V08, SPEC-Q07 |
| PS6 | `accepts`: HEADER `X-Id` vs `x-id` | true | SPEC-H01 |
| PS7 | `accepts`: BODY `user.id` vs JSON `user.id` / form `user.id` (jsonBody false) | true (FieldName) / true (literal) | SPEC-J10, SPEC-J12 |
| PS8 | `accepts`: BODY `scores{}` vs form field `scores.a` | false (literal compare for forms) | SPEC-J12 |
| PS9 | `check` of a matching value / of a wrong value | `Check` matched / `Check` with the type's violation | SPEC-V08, SPEC-V14 |

### 1.8 OS `OperationSpec.verify`

Operation `POST` unless stated; the call's arguments come from the listed request.

| ID | Case | Expected | Source |
|---|---|---|---|
| OS1 | content types `application/json`, body S2 with `application/json; charset=utf-8` | no content-type violation | SPEC-V05, SPEC-C09 |
| OS2 | content types `application/json`, body `text/plain` | UNSPECIFIED_CONTENT_TYPE listing `application/json` | SPEC-V05 |
| OS3 | content types `application/json`, form body S3 with item `title` | UNSPECIFIED_CONTENT_TYPE, and `title` still checked | SPEC-V05 |
| OS4 | no Content type items, body `text/plain` | no content-type violation | SPEC-V05, D3 |
| OS5 | content types listed, GET without body | no content-type violation | SPEC-V05, SPEC-C11 |
| OS6 | Body field items, malformed JSON body | MALFORMED_BODY; no INVALID_TYPE, EXTRA_ARGUMENT or MISSING_ARGUMENT for BODY | SPEC-V06 |
| OS7 | Body field items, body CUT / BINARY / `text/plain` | warning BODY_NOT_CHECKED with the reason; no body checks | SPEC-V07 |
| OS8 | no Body field items, body CUT | no BODY_NOT_CHECKED warning | SPEC-V07 |
| OS9 | items QUERY `_limit` Integer 1..100; S1 | one matched check `_limit` | SPEC-V08 |
| OS10 | items QUERY `_limit` Integer 1..100; S4 (`_limit=500&debug=1`) | OUT_OF_RANGE `_limit`; EXTRA_ARGUMENT `debug` | SPEC-V08, SPEC-V09 |
| OS11 | only a BODY item; query `debug=1` | `debug` unchecked (no query item) | SPEC-V09, D3 |
| OS12 | a HEADER item `X-Id`; request with `X-Id` and 10 other headers | `X-Id` checked, the others unchecked, none extra | SPEC-V09, SPEC-H03, D5 |
| OS13 | a PATH item `POST_ID` only; query and body present | path checked, query and body unchecked | SPEC-V09 |
| OS14 | BODY item `user` Integer; body `{"user":{"id":1,"x":2}}` and BODY item `title` | one INVALID_TYPE for `user`; `user.id`, `user.x` not extra | SPEC-V10 |
| OS15 | BODY item `user.id`; body `{"user":{"id":1}}` | `user` (OBJECT) not extra; `user.id` matched | SPEC-V09 |
| OS16 | required BODY `title`; body without `title` / form body without it | MISSING_ARGUMENT `title` both | SPEC-V11 |
| OS17 | required QUERY `page` present with value `""` | not missing (present, typed check applies) | SPEC-V11, SPEC-Q02 |
| OS18 | QUERY `tag` String; `tag=a&tag=b` | two matched checks, one REPEATED_PARAMETER warning | SPEC-V12, D14 |
| OS19 | QUERY `tag` Integer; `tag=1&tag=x` | one ✓, one INVALID_TYPE, one REPEATED_PARAMETER | SPEC-V12 |
| OS20 | warnings only (PRECISION, REPEATED) | verdict VALID | SPEC-V13, SPEC-V14 |
| OS21 | a mix: wrong content type, extra query, invalid body value, missing header | violations ordered: content type, request-order arguments, missing | SPEC-V15 |
| OS22 | same request with response status 200 / 400 / 500 | same verdict | SPEC-V16 |
| OS23 | two body items `scores.alice` Integer, `scores{}` String; body `{"scores":{"alice":"x","bob":"y"}}` | `scores.alice` checked as Integer (✗), `scores.bob` as String (✓) | SPEC-J11 |
| OS24 | `{"items":[]}` with required `items[].id`… refused at definition; optional `items[].id` | no MISSING (optional) | SPEC-S12, SPEC-V11 |

### 1.9 ES `EndpointSpec`

| ID | Case | Expected | Source |
|---|---|---|---|
| ES1 | `EndpointSpec.empty()` verifies any call | NOT_VERIFIED, not preflight, no checks | SPEC-V02, SPEC-S01 |
| ES2 | non-empty spec; `OPTIONS` with `Access-Control-Request-Method: POST` | NOT_VERIFIED, preflight | SPEC-V03, D15 |
| ES3 | `OPTIONS` without that header, no OPTIONS item | UNSPECIFIED_METHOD | SPEC-V03, SPEC-V04 |
| ES4 | items GET, POST; call PUT / call OTHER (`PROPFIND`) | UNSPECIFIED_METHOD listing GET, POST; no other checks | SPEC-V04 |
| ES5 | `with(Method GET)` twice / Method OTHER | refused | SPEC-S03 |
| ES6 | `with(Query item for GET)` on an empty spec | spec has a GET Method item and the query item | SPEC-S04 |
| ES7 | `without(Method POST)` with items | POST and all its items gone | SPEC-S05 |
| ES8 | `with(Content type "application/json")` twice for POST / `"Application/JSON"` after `"application/json"` | refused (duplicate after normalising) | SPEC-S06 |
| ES9 | Path variable item for a name not in the template, or twice for one method | refused | SPEC-S07 |
| ES10 | Body field when the method lists only `text/plain` / removing the last JSON type while body items exist | refused / refused | SPEC-S10 |
| ES11 | Body field on GET / HEAD / DELETE | accepted | SPEC-S13 |
| ES12 | after any `with`/`without` | the old spec is unchanged (immutable), a new object is returned | SPEC-S14, SPEC-L02 |
| ES13 | refused `with` | `IllegalArgumentException` with a message; spec unchanged | SPEC-S14 |
| ES14 | `items()` | every item once, grouped by method in `HttpMethod` order | SPEC-G05 |
| ES15 | changing a type with a range to String through `without` + `with` | new item has no range | SPEC-S11 |
| ES16 | QUERY name `tag` in GET and in POST | both accepted (unique per method) | SPEC-S08, D1 |
| ES17 | HEADER `X-Id` and `x-id` in one method | refused | SPEC-S08, SPEC-H01 |

### 1.10 SI `SpecItem` and VD `Verdict`, `Check`, `Violation`, `Warning`

| ID | Case | Expected | Source |
|---|---|---|---|
| SI1 | `SpecItem.method(GET)`, `contentType(POST, "application/json")`, `parameter(GET, p)` | kinds METHOD, CONTENT_TYPE, kind from the parameter's location | SPEC-S02 |
| SI2 | `contentType(POST, "json")` | refused (not `type/subtype`) | SPEC-S06 |
| VD1 | `Verdict` with no violations / one violation | VALID / INVALID | SPEC-V14 |
| VD2 | `Verdict.notVerified(true)` | NOT_VERIFIED, `isPreflight()` | SPEC-V03 |
| VD3 | returned lists | cannot be modified; checks keep request order | SPEC-V14, SPEC-L02 |
| VD4 | `Check` without violation / with | `isMatched()` true / false | SPEC-V14 |
| VD5 | `Violation`, `Warning` getters and `null` kind | values / `NullPointerException` | SPEC-V14 |

### 1.11 UM `UnmatchedPaths`, PD `PatternDraft`, UP `UrlPath` (changed)

| ID | Case | Expected | Source |
|---|---|---|---|
| UM1 | `add(/a/1)` twice, `add(/b)` | counts `/a/1`=2, `/b`=1 | SPEC-U02 |
| UM2 | 1000 distinct paths, then 5 calls to new paths and 1 to a known path | 1000 entries; overflow 5; the known path counted | SPEC-U02, SPEC-L01 |
| UM3 | `counts()` result | a copy; later adds don't change it | SPEC-U02 |
| UM4 | `clear()` | no entries, overflow 0 | SPEC-U03 |
| UM5 | 8 threads add 1000 each | sum of counts + overflow = 8000 | SPEC-L03 |
| PD1 | `new PatternDraft(/api/42/comments)` | 3 literal chips `api`, `42`, `comments`; `toTemplate()` = `/api/42/comments` | SPEC-U05 |
| PD2 | `toggle(1)` | chip 1 variable with name `42`; template `/api/[42]/comments` | SPEC-U05, SPEC-U06 |
| PD3 | default names for `a-b`, `é`, `x y` | `a_b`, `_`, `x_y` | SPEC-U06 |
| PD4 | path `/42/x/42/y/42`: toggle parts 0, 2 and 4 | names `42`, `42_2`, `42_3`; `rename(0, "")` refused | SPEC-U06 |
| PD5 | `rename(1, "POST_ID")` / `rename(1, "a b")` | template `/api/[POST_ID]/comments` / refused | SPEC-U05, SPEC-U10 |
| PD6 | path `/a//b` / `/` | `problem()` "empty part" / "no parts"; no chips toggleable | SPEC-U07 |
| PD7 | parts `.`, `..`, `a[b`, `50%`, `a b` | `canBeLiteral` false; `toggle` back to literal refused | SPEC-U08 |
| PD8 | literal `my doc` (decoded), `ü`, `a+b`, `x:y@z` | template parts `my%20doc`, `%C3%BC`, `a+b`, `x:y@z` | SPEC-U09 |
| PD9 | toggle a literal to variable and back | template as before | SPEC-U05 |
| PD10 | `problem()` for a valid draft / for two variables renamed to the same name | empty / the US10 refusal message | SPEC-U10 |
| UP1 | `new UrlPath("/a/b").equals(new UrlPath("/a/b/"))`, `hashCode` equal | true, equal | SPEC-U02 |
| UP2 | `toString()` of `/users/John%20Doe` | `/users/John Doe` | SPEC-U02 |

### 1.12 SM `SpecItemForm`, `SpecItemTableModel`

| ID | Case | Expected | Source |
|---|---|---|---|
| SM1 | `visibleFields()` per kind | Method: METHOD; Content type: METHOD, MEDIA_TYPE; Path variable: METHOD, NAME (choice), TYPE, MIN, MAX; Query/Body/Header: METHOD, NAME, TYPE, MIN, MAX, REQUIRED | SPEC-G04 |
| SM2 | type String or Base64 selected | MIN and MAX not visible | SPEC-G04, SPEC-S11 |
| SM3 | Path variable name choices for template `/users/[UID]/posts/[PID]` | `UID`, `PID` only | SPEC-S07 |
| SM4 | `toItem()` with min `5`, max `1` | refused "min must not be above max" | SPEC-S11 |
| SM5 | `toItem()` with min `1.5` for Integer | refused | SPEC-S11 |
| SM6 | `load(item)` then `toItem()` | an equal item | SPEC-G05 |
| SM7 | table rows for a spec with GET (2 items) and POST (3 items) | 5 rows grouped GET then POST; columns Method, Kind, Name, Type, Range, Required | SPEC-G05 |
| SM8 | Range column for 1..100, min only 0, max only 10, none | `1..100`, `≥ 0`, `≤ 10`, empty | SPEC-G05 |
| SM9 | Body field on GET in the form | accepted with the hint text | SPEC-S13 |

### 1.13 CT `CallTableModel` (changed), CW `CheckWriter`, WR `ApiDocumentationWriter` (changed), MSG messages

| ID | Case | Expected | Source |
|---|---|---|---|
| CT1 | Check column for VALID / INVALID with 2 violations / VALID with a warning / NOT_VERIFIED | `✓` / `✗ 2` / `⚠` / empty | SPEC-G06 |
| CT2 | `matches(row, null, "", true)` for VALID / INVALID / NOT_VERIFIED | false / true / false | SPEC-G06 |
| CT3 | only invalid + method POST + text `title` | all three filters must hold | SPEC-G06 |
| CT4 | `getVerdictAt(row)` after sorting | verdict of that row's call | SPEC-G06 |
| CW1 | matched check `_limit=5` Integer 1..100 | `✓ query _limit = 5  (Integer 1..100)` | SPEC-G07, D3 |
| CW2 | every violation kind of the catalogue | the exact template text, with mark ✗ | SPEC-G07, D7 |
| CW3 | every warning kind | the exact template text, with mark ⚠; REPEATED text names no framework | SPEC-G07, D14 |
| CW4 | unchecked header `User-Agent` | `header User-Agent = …` without mark | SPEC-G07, SPEC-G12 |
| CW5 | value `<script>alert(1)</script>` and a 200-character value | escaped; shortened to 80 characters with `…` | SPEC-L05 |
| CW6 | `toHtml` colours | ✓ green, ✗ red, ⚠ yellow, always with the symbol | SPEC-G08 |
| CW7 | NOT_VERIFIED preflight | `preflight (not checked)` | SPEC-V03, SPEC-G07 |
| WR1 | endpoint without spec | `No spec`, no ✓/✗ counts | SPEC-G09, D3 |
| WR2 | endpoint with GET and POST items | a table per method; counts `✓ n ✗ m`; violations per kind | SPEC-G09 |
| WR3 | per call | the same lines as CW for its verdict | SPEC-G09 |
| WR4 | values from traffic in spec names and checks | escaped | SPEC-L05 |
| WR5 | namespace with unmatched paths | not mentioned in the documentation | SPEC-U04 |
| MSG1 | every message key used by the new UI classes | exists in `Messages.properties`; no hard-coded user-facing text in `ui` | SPEC-G11 |

---

## 2. Integration tests

### 2.1 TL `RestTrafficListener` with real `HttpMessage`s (`RestTrafficListenerUnitTest`)

| ID | Case | Expected | Source |
|---|---|---|---|
| TL-V1 | `POST https://h/posts/1?x=1&y=%20` with headers A, B, A and JSON body | request data: query `x=1&y=%20`, headers A, B, A in order, content type as sent, body text, state TEXT | SPEC-C01, SPEC-C02, SPEC-C03, SPEC-C04 |
| TL-V2 | request without query / without Content-Type / with empty body | `""` / `""` / state NONE | SPEC-C02, SPEC-C04, SPEC-C11 |
| TL-V3 | request body `image/png` / 70 KB JSON | state BINARY with mark / CUT, 64 KB kept plus mark | SPEC-C05 |
| TL-V4 | body `café` with `charset=ISO-8859-1` / without charset (UTF-8 bytes) | `café` both | SPEC-C06 |
| TL-V5 | the extractor throws while recording | `onHttpResponseReceive` returns true; message unchanged (headers and body equal before/after) | SPEC-C07 |
| TL-V6 | `onHttpRequestSend` | returns true, records nothing (US10) | SPEC-C07 |

### 2.2 EI `Endpoint` with parser and spec; NS `ApiNamespace`; RX `RestExtractor`

| ID | Case | Expected | Source |
|---|---|---|---|
| EI1 | `record(call, path)` for a matching call | one `RecordedCall`; arguments parsed once (a spy on `RequestParser`: one call even after 3 reports) | SPEC-C08, SPEC-L03 |
| EI2 | spec added after 3 calls were recorded | next `createReport()` has a verdict for all 3 against the new spec | SPEC-V01, D2 |
| EI3 | report, then `addSpecItem`, then report | first report keeps its old verdicts (snapshot); second shows new ones | SPEC-V01, SPEC-L02 |
| EI4 | two reports without spec change | the second reuses cached verdicts (same objects); after `addSpecItem` new objects | SPEC-V17 |
| EI5 | `clear()` of the endpoint | calls gone, spec kept | SPEC-S15 |
| EI6 | `EndpointReport` counts | `getValidCount`, `getInvalidCount`, `getViolationCounts` match the verdicts; NOT_VERIFIED in neither | SPEC-G02, SPEC-G09 |
| NS1 | started namespace, call `/api/x` with patterns `/posts/[ID]` only | not recorded; unmatched `/x` (after base `/api`) count 1 | SPEC-U01 |
| NS2 | stopped namespace / call of another host / call outside the base path | nothing counted | SPEC-U01 |
| NS3 | call matching a pattern | not counted as unmatched | SPEC-U01 |
| NS4 | `clear()` of the namespace / removing the namespace | unmatched paths cleared / gone | SPEC-U03 |
| NS5 | adding a pattern for a counted unmatched path | the count stays | SPEC-U03 |
| NS6 | `removeEndpoint` | its spec is gone; re-adding the template starts with an empty spec | SPEC-S15, SPEC-S01 |
| RX1 | `addSpecItem(ns, template, item)` / `removeSpecItem` | passed on to that endpoint (mock) | SPEC-S14 |
| RX2 | `addSpecItem` for an unknown template | `IllegalArgumentException` | SPEC-S14 |
| RX3 | `getUnmatchedPaths(ns)`, `clearUnmatchedPaths(ns)` | passed on; result is a copy | SPEC-U02, SPEC-U03 |
| RX4 | new namespace and endpoint | `getReport` shows an empty spec and NOT_VERIFIED calls | SPEC-S01, SPEC-V02 |

### 2.3 CC concurrency

| ID | Case | Expected | Source |
|---|---|---|---|
| CC1 | 8 threads record 1000 calls each while the Swing thread replaces the spec 200 times and takes reports | 8000 calls; no exception; in every report all verdicts were made against one spec (checked by a spec ID inside the verdicts) | SPEC-L02, SPEC-L03 |
| CC2 | recording thread blocked in a test listener while `addSpecItem` runs | `addSpecItem` finishes; recording never waits for it | SPEC-L03 |

---

## 3. End-to-end tests

### 3.1 SC-V scenario tests (`VerificationScenarioTest`, hand-made messages through the listener)

Base URL `https://jsonplaceholder.typicode.com`, endpoint `/posts/[POST_ID]/comments` unless stated.

| ID | Scenario | Expected | Source |
|---|---|---|---|
| SC-V1 | story example: spec POST, `application/json`, path `POST_ID` Integer 1..10000, body `title` String required, body `userId` Integer; send `POST /posts/1/comments` with `{"title":"Hi","userId":3}` | VALID; checks ✓ for POST_ID, title, userId; tree ✓ 1 | R3, R4, R7, SPEC-V14 |
| SC-V2 | spec of SC-V1 plus query `_limit` Integer 1..100; send `POST /posts/abc/comments?_limit=500` with `{"title":"Hi","userId":"3","x":1}` | INVALID with, in this order: INVALID_TYPE `POST_ID`, OUT_OF_RANGE `_limit`, INVALID_TYPE `userId` (got a string), EXTRA_ARGUMENT body `x`; `title` ✓ | R5, R6, SPEC-V08, SPEC-V09, SPEC-V15 |
| SC-V3 | spec GET with `application/json`; send `PUT`, then `POST` with `text/plain` (POST added) | first UNSPECIFIED_METHOD; second UNSPECIFIED_CONTENT_TYPE | R6, SPEC-V04, SPEC-V05 |
| SC-V4 | record 5 calls without spec; add items; report | the 5 old calls are judged against the new spec | R8, SPEC-V01, D2 |
| SC-V5 | endpoint without spec, 10 varied calls | all NOT_VERIFIED; tree `(10 calls)` plain; documentation `No spec` | SPEC-V02, SPEC-G01, D3 |
| SC-V6 | browser preflight `OPTIONS` + `Access-Control-Request-Method: POST`, then the POST | first NOT_VERIFIED "preflight", second verified | SPEC-V03, D15 |
| SC-V7 | namespace started, call `/users/7` with only the posts pattern; build `/users/[7]`, rename to `USER_ID`, add; send `/users/8` | `/users/7` unmatched 1; pattern added with empty spec; `/users/8` recorded under it; unmatched count still 1 | SPEC-U01, SPEC-U03, SPEC-U10 |
| SC-V8 | query item `tag` String; send `?tag=a&tag=b` | VALID with one REPEATED_PARAMETER warning, both values ✓ | SPEC-V12, D14 |
| SC-V9 | body items `items[].id` Integer, `scores{}` Integer, `scores.bonus` Decimal; body `{"items":[{"id":1},{"id":"2"}],"scores":{"a":1,"bonus":0.5}}` | `items[].id` 1 ✓, "2" ✗; `scores.a` ✓ Integer; `scores.bonus` ✓ Decimal | SPEC-J02, SPEC-J10, SPEC-J11, D6 |
| SC-V10 | spec with only a Header item `X-Api-Key` String required (GET); send GET with query `a=1`, JSON body, 8 headers, no `X-Api-Key` | MISSING_ARGUMENT `X-Api-Key` only; query, body and other headers unchecked; no content-type check | SPEC-V09, SPEC-V11, SPEC-H03, D5 |
| SC-V11 | form body S3 with spec POST `application/x-www-form-urlencoded`, body `userId` Integer | VALID; `userId` ✓ | R1, SPEC-C10, SPEC-J12 |
| SC-V12 | malformed JSON body with body items | MALFORMED_BODY, documentation shows the message | SPEC-V06, SPEC-G09 |
| SC-V13 | 1000 calls with a 6-item spec | report in under 500 ms (warm); all verdicts present | SPEC-L03, SPEC-V17 |

### 3.2 PN-E panel tests (`RestExtractionPanelUnitTest`, real extractor, headless)

| ID | Case | Expected | Source |
|---|---|---|---|
| PN-E1 | select an endpoint | Specification tab enabled with the empty hint; namespace selected → tab disabled | SPEC-G03 |
| PN-E2 | add a Query parameter through the form | row listed; spec of the endpoint changed; ✓/✗ shown in the tree after the redraw | SPEC-G05, SPEC-G02 |
| PN-E3 | refused input (min > max) | form stays open, message shown, spec unchanged | SPEC-G05, SPEC-S14 |
| PN-E4 | remove the POST Method item with items | confirmation asked (test answers yes) and items gone; answer no: nothing changes | SPEC-S05 |
| PN-E5 | tree labels | without spec `(n calls)`; with spec `✓ a ✗ b` with green/red renderer colours | SPEC-G01, SPEC-G02 |
| PN-E6 | Only invalid ticked | table shows invalid calls only; count label `x of y calls · ✓ a · ✗ b` | SPEC-G06 |
| PN-E7 | select an invalid call | detail view has Request (with all headers), Checks with ✗ lines, Response, in that order | SPEC-G07, SPEC-G12, D12 |
| PN-E8 | Unmatched paths button / tree menu item on a namespace | opens its panel with Path and Calls; Clear empties it | SPEC-G10, SPEC-U03 |
| PN-E9 | dark look and feel (FlatDarkLaf) | the green, red and yellow differ from the background by contrast ≥ 3:1 | SPEC-G08 |

---

## 4. Manual tests

ZAP run from source (`./gradlew :zap:run`) with the add-on built from the US11 branch; Firefox profile
`ZAP` (`./firefox-zap.sh`). Screenshots are saved as `m-v<n>-<k>-<short-name>.png`.

| ID | Steps | Expected | Source |
|---|---|---|---|
| M-V1 | Add `https://jsonplaceholder.typicode.com`, pattern `/posts/[POST_ID]/comments`, Start. In the Specification tab add one item of every kind (GET; POST + `application/json`; path `POST_ID` Integer 1..100; query `_limit` Integer; body `title` String required; header `X-Test` String). | each form shows only its fields; every item is listed under its method; a min > max input is refused with a message | SPEC-G04, SPEC-G05, SPEC-S11 |
| M-V2 | Open `/posts/1/comments?_limit=2` and `/posts/abc/comments?_limit=x&debug=1` in Firefox. | first call ✓ with green lines; second ✗ with red lines for POST_ID, `_limit`, `debug`, and green ✓ where values fit | SPEC-G07, D3 |
| M-V3 | Look at the tree for the endpoint with spec and for a second endpoint `/posts/[POST_ID]` without spec. | with spec `✓ 1 ✗ 1` in green/red; without spec plain `(n calls)` | SPEC-G01, SPEC-G02 |
| M-V4 | Visit `/users/1`; open Unmatched paths; Add endpoint pattern; toggle `1`, rename to `USER_ID`, Add; visit `/users/2`. | `/users/1` listed with 1 call; preview `/users/[USER_ID]`; the new endpoint records `/users/2` | SPEC-G10, SPEC-U05, SPEC-U10 |
| M-V5 | Open the Documentation tab. | spec table per method, counts, violation kinds, per call the same ✓/✗ lines | SPEC-G09 |
| M-V6 | Send 1000 calls in a burst (`curl` loop through the proxy, as US10 M6) with the spec of M-V1. | panel stays responsive; counts reach 1000 | SPEC-L03 |
| M-V7 | Switch ZAP to a dark look and feel (Options → Display). | ✓ ✗ ⚠ colours readable; symbols visible | SPEC-G08 |
| M-V8 | Close and restart ZAP. | namespaces and specs are gone (session only) | SPEC-S16, D13 |
| M-V9 | Juice Shop (`http://localhost:3000`): spec `POST /rest/user/login` with `application/json`, body `email` String required, `password` String required. With curl through the proxy send `{"email":"a@b.c"}`, then `{"email":"a@b.c","password":"x","admin":true}`. | first INVALID with MISSING_ARGUMENT `password`; second INVALID with EXTRA_ARGUMENT `admin`, `email` and `password` ✓ | SPEC-V09, SPEC-V11 |
| M-V10 | Send a POST with `Content-Type: application/x-www-form-urlencoded` body `a=1&a=2` to an endpoint with item `a`. | both values checked; ⚠ "appears 2 times" warning | SPEC-V12, D14 |

---

## Implementation notes (private prototype)

- RD1–RD7, MT1–MT7, JR1–JR19 and RP1–RP23 are implemented in `RequestParsingUnitTest`; some planned rows are grouped into a single method, while parameterized rows run several invocations. Planned-case counts and executed invocation counts are deliberately distinct.
- `RequestCaptureUnitTest` adds TL1–TL7 coverage using real `HttpMessage`s, alongside the existing listener regression tests. `RestExtractionPanelUnitTest.shouldShowCapturedRequestInPlainText` exercises the early request-detail UI on the EDT.
- Legacy path-map assertions now use `RecordedCall.getPathArguments()`; `getArguments()` is the designed ordered argument list. The legacy map constructor remains as a compatibility convenience, with the same null/order/immutability assertions.
- `JsonReader` uses iterative frames instead of recursive descent: 50,000 levels cannot overflow the Java stack; validation continues beyond the extraction-depth limit without retaining unbounded path strings.
- `ParameterTypesUnitTest` implements PT-S/I/D/B groups; `VerificationUnitTest` implements FN/PS/OS, ES except endpoint-level ES9, SI and VD. Tests intentionally retain exact decimal comparisons and original JSON number spellings.
- Two implementation helpers refine the class design without changing rules: package-private `NumericGrammar` (shared linear numeric scanner) and `SpecValidationException` (structured localization key/arguments). Field names use tokenized steps, never regex matching.
- Actual outcomes and limitations are in `docs/implementation-status.md`. Unimplemented groups remain planned, not passed.

## 5. Counts (for the sign-off card)

| Level | Test cases | Test classes (planned) |
|---|---|---|
| Unit | see `US11 coverage.md` §5 (counted by the check script) | `RequestDataUnitTest`, `MediaTypeUnitTest`, `JsonReaderUnitTest`, `RequestParserUnitTest`, `FieldNameUnitTest`, `StringTypeUnitTest`, `IntegerTypeUnitTest`, `DecimalTypeUnitTest`, `Base64TypeUnitTest`, `ParameterSpecUnitTest`, `OperationSpecUnitTest`, `EndpointSpecUnitTest`, `SpecItemUnitTest`, `VerdictUnitTest`, `UnmatchedPathsUnitTest`, `PatternDraftUnitTest`, `UrlPathUnitTest` (extended), `SpecItemFormUnitTest`, `SpecItemTableModelUnitTest`, `CallTableModelUnitTest` (extended), `CheckWriterUnitTest`, `ApiDocumentationWriterUnitTest` (extended), `MessagesUnitTest` |
| Integration | see coverage §5 | `RestTrafficListenerUnitTest` (extended), `EndpointUnitTest`, `ApiNamespaceUnitTest`, `RestExtractorUnitTest` (extended), `ConcurrencyUnitTest` |
| End-to-end | see coverage §5 | `VerificationScenarioTest`, `RestExtractionPanelUnitTest` (extended) |
| Manual | see coverage §5 | — |
