# US11 REST verification: formal specification

Version 0.1 · builds on US10 (`origin/us10/feat/call-filters`, class diagram v3) · normative:
"must" is a requirement, examples are part of the rule. Reasons are in the plan
(`plans/us11-rest-verification.md`); this document only states what holds.

## 1. Scope

### 1.1 Requirements

| ID | Requirement |
|---|---|
| R1 | The extension captures query parameters, request headers and the request body (`text/json`, `application/json`, `application/x-www-form-urlencoded`). |
| R2 | The GUI and the documentation show all captured request information. |
| R3 | The developer sets a type per argument: String, Integer, Decimal, Base64. |
| R4 | Integer and Decimal arguments can have a range. |
| R5 | Invalid requests are detected and indicated in the GUI. |
| R6 | Unspecified methods, unspecified content types and extra arguments are detected. |
| R7 | The developer defines the valid parameters, and traffic is scanned for compliance. |
| R8 | The extraction report shows, per call and per value, whether it complies with the spec. |

**Out of scope:** checking responses, OpenAPI import/export, saving specs between ZAP sessions,
generating specs from recorded calls, ZAP alerts, changing or blocking traffic.

### 1.2 Terms

| Term | Meaning |
|---|---|
| Operation | an endpoint pattern together with one HTTP method, e.g. `POST /posts/[POST_ID]/comments` |
| Argument | one named value in a request: name, location (PATH, QUERY, BODY, HEADER), value text; for JSON bodies also its JSON kind (STRING, NUMBER, BOOLEAN, NULL, OBJECT, ARRAY) |
| Spec item | one rule the developer added: Method, Content type, Path variable, Query parameter, Body field or Header |
| Parameter item | a spec item of kind Path variable, Query parameter, Body field or Header: name, type, optional range, Required |
| Spec | all spec items of one endpoint, grouped per method |
| Check | the result for one argument against one parameter item: matched or violation |
| Violation | a deviation from the spec; makes the call invalid |
| Warning | worth knowing, not a deviation; never makes a call invalid |
| Verdict | VALID, INVALID or NOT_VERIFIED, with the call's checks, unchecked arguments and warnings |
| Unmatched path | the path (after the base path) of a call that belongs to a namespace but fits none of its patterns |

## 2. Decisions

| ID | Decision |
|---|---|
| D1 | A spec belongs to an operation (endpoint + method). Any operation may have path, query, header and body items. The method is a value, not a class per method. |
| D2 | Calls are verified when a report is made, always against the current spec. |
| D3 | An endpoint without spec shows no indicator. With a spec, valid counts are green, invalid counts red; every checked value is shown with ✓ or ✗ and a message. |
| D4 | Unmatched paths are collected per namespace and shown only in their own panel, with a pattern builder. Secondary feature. |
| D5 | Only headers with a Header item are checked. A header is never extra. |
| D6 | Body field names: `user.id`, `items[].id` (array items), `scores{}.x` (every key of a map); depth limit 10. |
| D7 | JSON values are checked strictly by JSON kind, with messages that say what was expected and what came. |
| D8 | Specs start empty. Path variables are never missing. Query, body and header items have Required, default off. |
| D9 | Number grammars as in SPEC-T03 to SPEC-T06, JSON numbers as JSON defines them. |
| D10 | One Base64 checker for the standard and the URL-safe alphabet in every location. |
| D11 | No spec generation from recorded calls. |
| D12 | Header values are shown in full. |
| D13 | Specs live for the ZAP session only. |
| D14 | Every value of a repeated parameter is checked; one generic warning per repeated name. |
| D15 | CORS preflight requests are not verified and are labelled "preflight". |
| D16 | The spec is edited in a Specification tab; items are added through a kind dropdown. |

## 3. Rules

### 3.1 Capture (SPEC-C)

| ID | Rule |
|---|---|
| SPEC-C01 | For every response the listener records, it builds the call's request data: query, headers, content type, body and body state. The listener does not parse arguments. |
| SPEC-C02 | The query is the escaped query of the request URI, `""` when there is none. The call's URL stays without the query (US10). |
| SPEC-C03 | Headers are kept in the order sent, with their names as sent; a header sent twice is kept twice. |
| SPEC-C04 | The content type is the request's `Content-Type` value as sent, `""` when absent. |
| SPEC-C05 | Body state: NONE when the body is empty; BINARY (text replaced by a mark) when the content type names a non-text type; CUT when the text is longer than 64 KB (first 64 KB kept plus a mark); TEXT otherwise. |
| SPEC-C06 | Body text is decoded with the charset of the request, UTF-8 when none is given. |
| SPEC-C07 | The listener never throws, never changes traffic and always returns true. |
| SPEC-C08 | A call's arguments are parsed once, when it is recorded: path arguments from the pattern, then query, body and header arguments. |
| SPEC-C09 | Media types are compared normalised: trimmed, lower-case, parameters (`; charset=…`) removed. |
| SPEC-C10 | `application/json`, `text/json` and `application/<x>+json` bodies are parsed as JSON; `application/x-www-form-urlencoded` bodies as form; other bodies are not parsed. |
| SPEC-C11 | A body of length 0 counts as no body, whatever the `Content-Type` says. |
| SPEC-C12 | A body without `Content-Type` has the content type `""`, shown as "(none)". |

### 3.2 Query and form (SPEC-Q)

Applies to the query and to `application/x-www-form-urlencoded` bodies.

| ID | Rule | Example |
|---|---|---|
| SPEC-Q01 | Pieces are separated by `&` only; empty pieces are skipped. | `a=1&&b=2&` → `a`, `b`; `a=1;b=2` → `a` = `1;b=2` |
| SPEC-Q02 | A piece is split at its first `=`; without `=` the value is `""`. | `a` → `a` = `""`; `a=b=c` → `a` = `b=c` |
| SPEC-Q03 | `+` becomes a space, then `%XX` escapes are decoded as UTF-8. | `q=a+b%2Bc` → `a b+c` |
| SPEC-Q04 | A broken escape (`%zz`, `%2` at the end) keeps the raw text and marks the argument MALFORMED_ENCODING. | `n=%zz` → `n` = `%zz` (marked) |
| SPEC-Q05 | Invalid UTF-8 bytes become U+FFFD and mark the argument MALFORMED_ENCODING. | `n=%FF` → `n` = `�` (marked) |
| SPEC-Q06 | Order is kept; a repeated name gives one argument per value. | `tag=a&tag=b` → two arguments `tag` |
| SPEC-Q07 | Names are literal and case-sensitive: no dotted or bracket meaning. | `ids[]=1` → name `ids[]`; `user.id=1` → name `user.id` |
| SPEC-Q08 | An empty name is a normal argument with name `""`. | `=1` → name `""` |

### 3.3 JSON bodies and field names (SPEC-J)

| ID | Rule | Example |
|---|---|---|
| SPEC-J01 | JSON is read strictly per RFC 8259; a leading BOM is ignored; anything else (trailing comma, single quotes, `NaN`, comments, unclosed) makes the body malformed. | `{"a":1,}` → malformed |
| SPEC-J02 | Every scalar value is an argument. Its name is the path from the root: object member `.key` (no dot at the start), array item `[]`; a root array starts with `[]`; a root scalar is named `$`. | `{"user":{"id":3}}` → `user.id`; `{"items":[{"id":1}]}` → `items[].id`; `[{"id":1}]` → `[].id`; `42` → `$` |
| SPEC-J03 | Every object and array is also an argument of kind OBJECT or ARRAY with its own path, so an item typed on it can be checked. | `{"items":[]}` → `items` (ARRAY) |
| SPEC-J04 | Number text is kept exactly as written. | `1.0`, `1e3`, `-0`, `123456789012345678901234567890` |
| SPEC-J05 | Duplicate keys give one argument per occurrence. | `{"a":1,"a":2}` → two `a` |
| SPEC-J06 | A key containing `.`, `[`, `]`, `{`, `}` or `\` is written with `\` before that character. | `{"a.b":1}` → `a\.b` |
| SPEC-J07 | Values deeper than 10 levels are not read; the call gets the warning TOO_DEEP. | |
| SPEC-J08 | A JSON body in state CUT is not parsed; the call gets the warning BODY_NOT_CHECKED when its operation has Body field items. | |
| SPEC-J09 | A field name (Body field item) is parts separated by `.`; each part is a key followed by any number of `[]` (every array item) or `{}` (every key of an object); a part may also be only `[]` (root array). Valid: `user.id`, `items[].id`, `[].id`, `scores{}`, `scores{}.x`, `matrix[][]`, `a\.b`. Refused: `""`, `a..b`, `.a`, `a.`, `a[`, `a[1]`, `{}x`, `a{x}`. | |
| SPEC-J10 | A field name matches an argument name when each part matches: keys equal, `[]` matches one array step, `{}` matches any one key. | `scores{}` matches `scores.alice`; `m{}.x` matches `m.a.x` |
| SPEC-J11 | When several items match one argument, the one with a concrete key at the leftmost position where they differ wins. | `scores.alice` beats `scores{}` for `scores.alice`; `m.a{}` beats `m{}.x` for `m.a.x` |
| SPEC-J12 | In form bodies a Body field item's name is compared literally with the argument name (SPEC-Q07). | item `user.id` matches form field `user.id` |

### 3.4 Headers (SPEC-H)

| ID | Rule |
|---|---|
| SPEC-H01 | Header names are compared case-insensitively and shown as sent. |
| SPEC-H02 | A repeated header gives one argument per occurrence; values are not split on commas. |
| SPEC-H03 | Only headers with a Header item are checked; no header is ever extra. |

### 3.5 Types and ranges (SPEC-T)

| ID | Rule |
|---|---|
| SPEC-T01 | Checks never trim or change the value. |
| SPEC-T02 | **String**: valid for any text, also `""`. For JSON: valid for kinds STRING, NUMBER, BOOLEAN (value = the JSON text), invalid for NULL, OBJECT, ARRAY. No range. |
| SPEC-T03 | **Integer**, text values (path, query, form, header): `[+-]?[0-9]+` with ASCII digits; leading zeros allowed. Valid: `42`, `-7`, `+3`, `007`. Invalid: `""`, `4.0`, `1e3`, `0x10`, ` 42`, `4 2`, `١٢`. |
| SPEC-T04 | **Integer**, JSON values: kind NUMBER and written as a whole number `-?(0\|[1-9][0-9]*)`. Valid: `42`, `-7`, `0`, `-0`. Invalid: `1.0`, `1e3`, `1E+2`, `"42"`. |
| SPEC-T05 | **Decimal**, text values: `[+-]?[0-9]+(\.[0-9]+)?([eE][+-]?[0-9]+)?`. Valid: `9.99`, `-0.5`, `3`, `1e3`, `1.5E-2`, `+2`. Invalid: `""`, `.5`, `5.`, `NaN`, `Infinity`, `1,5`, `0x1p3`. |
| SPEC-T06 | **Decimal**, JSON values: kind NUMBER (any JSON number). Invalid: other kinds, e.g. `"9.99"`. |
| SPEC-T07 | **Base64**: characters from the standard alphabet (`A–Z a–z 0–9 + /`) or the URL-safe alphabet (`A–Z a–z 0–9 - _`), not both in one value; optional `=` padding only at the end, at most two, and with padding the length is a multiple of 4; without padding the length mod 4 is not 1; no whitespace; `""` invalid. For JSON: kind STRING only. Valid: `aGVsbG8=`, `aGVsbG8`, `a-_b`. Invalid: `a+b-`, `a===`, `ab=c`, `abcde`, `aGVs bG8=`. |
| SPEC-T08 | A range has an optional minimum and an optional maximum, both inclusive. Integer ranges compare as whole numbers of any size; Decimal ranges compare the exact written value (`1.0` = `1.00`; max `0.3` rejects `0.30000000000000004`). |
| SPEC-T09 | `-0` equals `0`. |
| SPEC-T10 | For Integer and Decimal a value longer than 10 000 characters is invalid; for Decimal an exponent larger than 9 999 in size is invalid. |
| SPEC-T11 | A JSON number checked as Integer or Decimal whose whole value lies outside ±(2^53 − 1), or a Decimal beyond the double range (about ±1.8 × 10^308), gets the warning PRECISION. |
| SPEC-T12 | An argument marked MALFORMED_ENCODING (SPEC-Q04, Q05) is invalid for Integer, Decimal and Base64 and valid for String. |

### 3.6 Defining a spec (SPEC-S)

| ID | Rule |
|---|---|
| SPEC-S01 | A new namespace and a new endpoint pattern have an empty spec. |
| SPEC-S02 | Spec item kinds: Method, Content type, Path variable, Query parameter, Body field, Header. Every item except Method belongs to one method. |
| SPEC-S03 | Method items take a method of `HttpMethod` except OTHER; the same method twice is refused. |
| SPEC-S04 | Adding a non-Method item for a method without Method item also adds that Method item. |
| SPEC-S05 | Removing a Method item removes all items of that method, after a confirmation when there are any. |
| SPEC-S06 | Content type items take `type/subtype` (each side one or more of the token characters of RFC 9110), normalised by SPEC-C09; duplicates per method are refused. |
| SPEC-S07 | Path variable items take one of the pattern's variable names; at most one item per variable and method; they have no Required. |
| SPEC-S08 | Parameter item names are not empty and have no leading or trailing whitespace; per method and location a name is used once (Header names case-insensitive). |
| SPEC-S09 | Body field names must be valid field names (SPEC-J09). |
| SPEC-S10 | If a method has Content type items and Body field items, at least one of its content types must be JSON or form; adding a Body field or removing a content type that breaks this is refused. |
| SPEC-S11 | A range is allowed only for Integer and Decimal; bounds are written in the type's text grammar (SPEC-T03, T05); min ≤ max (equal allowed). Changing the type to String or Base64 removes the range after a confirmation. |
| SPEC-S12 | Query parameter, Body field and Header items have Required, default off. Required is refused on a field name containing `[]` or `{}`. |
| SPEC-S13 | A Body field item on GET, HEAD or DELETE is allowed and shown with a hint. |
| SPEC-S14 | A refused input changes nothing and gives a message saying why. Every accepted change replaces the endpoint's spec as a whole. |
| SPEC-S15 | Removing an endpoint removes its spec; clearing calls keeps it. |
| SPEC-S16 | Specs are not saved; they are gone when ZAP closes. |

### 3.7 Verification (SPEC-V)

A call is verified against its endpoint's spec in these steps; a step that ends verification says so.

| ID | Rule |
|---|---|
| SPEC-V01 | Verification happens when a report is made, against the spec at that moment; after a spec change every recorded call is judged again. |
| SPEC-V02 | Empty spec: the verdict is NOT_VERIFIED, without checks or indicator. End. |
| SPEC-V03 | A call with method OPTIONS and an `Access-Control-Request-Method` header is a preflight: NOT_VERIFIED, labelled "preflight". End. |
| SPEC-V04 | No Method item for the call's method (OTHER included): violation UNSPECIFIED_METHOD. End. |
| SPEC-V05 | When the operation has Content type items and the call has a body (SPEC-C11) whose type is not among them: violation UNSPECIFIED_CONTENT_TYPE. A JSON or form body is still parsed and checked. Without Content type items the content type is not checked. |
| SPEC-V06 | A JSON body that is malformed (SPEC-J01): violation MALFORMED_BODY; no body checks (type, extra, missing) for that call. |
| SPEC-V07 | A body in state BINARY or CUT, or of a type that is not parsed (SPEC-C10), while the operation has Body field items: warning BODY_NOT_CHECKED; no body checks. |
| SPEC-V08 | Every argument, in request order, is looked up among the operation's items of its location: Path variable by name, Query parameter by literal name, Body field by field name (JSON, SPEC-J10/J11) or literal name (form), Header case-insensitively. Found: the item's type and range are checked, giving a matched check or INVALID_TYPE / OUT_OF_RANGE. |
| SPEC-V09 | Not found: violation EXTRA_ARGUMENT when the location is QUERY or BODY and the operation has at least one item of that location; otherwise the argument is unchecked. OBJECT and ARRAY arguments are never extra. |
| SPEC-V10 | An OBJECT or ARRAY argument found for an item of a scalar type gives one INVALID_TYPE; the arguments inside it are then not reported as extra. |
| SPEC-V11 | Every Required item without a matching argument: violation MISSING_ARGUMENT. |
| SPEC-V12 | A name that appears more than once as a query or form argument gets one warning REPEATED_PARAMETER; each value is checked on its own. |
| SPEC-V13 | Warnings (REPEATED_PARAMETER, PRECISION, BODY_NOT_CHECKED, TOO_MANY_ARGUMENTS, TOO_DEEP) never make a call invalid. |
| SPEC-V14 | The verdict is INVALID when there is at least one violation, VALID otherwise. It lists every check (matched and violated), the unchecked arguments and the warnings. |
| SPEC-V15 | Violations are ordered: method, content type, malformed body, arguments in request order, missing items in spec order. |
| SPEC-V16 | The response status code does not influence the verdict. |
| SPEC-V17 | Verdicts may be cached per recorded call; a new spec drops the cache. Caching never changes a verdict. |

### 3.8 Unmatched paths and the pattern builder (SPEC-U)

| ID | Rule |
|---|---|
| SPEC-U01 | When a started namespace covers a call (same origin, under its base path) but none of its patterns matches, the path after the base path is counted as an unmatched path. Calls the namespace does not cover are not counted. |
| SPEC-U02 | Unmatched paths are kept as distinct decoded paths with a call count; at most 1000 distinct paths, after that only a total of further calls grows. |
| SPEC-U03 | Clearing the namespace's calls clears its unmatched paths; removing the namespace removes them; adding a pattern leaves them unchanged. |
| SPEC-U04 | Unmatched paths never influence verdicts, tree counts or the documentation. |
| SPEC-U05 | The pattern builder shows one chip per path part, all literal at first; a click toggles literal ↔ variable. A literal chip is not editable; a variable chip has an editable name. |
| SPEC-U06 | A variable's default name is the part's text with every character outside `A–Z a–z 0–9 _` replaced by `_`; a name already used gets `_2`, `_3`, …; a variable can't be renamed to an empty name. |
| SPEC-U07 | A path with an empty part (`//`) or without parts (`/`) cannot become a pattern; the builder says why. |
| SPEC-U08 | A part that is `.` or `..`, or contains `[`, `]`, `%` or whitespace, can only be a variable chip. |
| SPEC-U09 | Literal parts are percent-encoded when the template is built (every character outside `A–Z a–z 0–9 - . _ ~ ! $ & ' ( ) * + , ; = : @`). |
| SPEC-U10 | The built template goes through the US10 pattern rules; a refusal is shown and nothing is added; an added endpoint starts with an empty spec (SPEC-S01). |

### 3.9 GUI (SPEC-G)

| ID | Rule |
|---|---|
| SPEC-G01 | Tree, endpoint without spec: `<template> (<n> calls)` in the normal colour (US10). |
| SPEC-G02 | Tree, endpoint with spec: `<template>  ✓ <valid>  ✗ <invalid>`, valid in green, invalid in red; NOT_VERIFIED calls are only in the plain total. A namespace shows the sums of its endpoints. |
| SPEC-G03 | The Specification tab is enabled only when an endpoint is selected; with an empty spec it shows "No spec: calls are not checked". |
| SPEC-G04 | The Add item dropdown offers the six kinds; the form shows only the fields of that kind: Method → method; Content type → method, media type (presets JSON, Form); Path variable → method, variable, type, min, max; Query parameter, Body field, Header → method, name, type, min, max, Required. Min and max are shown only for Integer and Decimal. |
| SPEC-G05 | Items are listed grouped by method (Method, Kind, Name, Type, Range, Required) with Edit and Remove. A refused input keeps the form open and shows the message. |
| SPEC-G06 | The calls table has a Check column: `✓` VALID, `✗ <n>` INVALID with n violations, `⚠` VALID with warnings, empty for NOT_VERIFIED. An Only invalid checkbox joins the method and text filters. The count label reads `<shown> of <all> calls · ✓ <valid> · ✗ <invalid>`. |
| SPEC-G07 | The detail view shows, in order: Request (method, URL with decoded query, headers, body formatted like response bodies, Full body applies), Checks (one line per check, warning and unchecked argument, catalogue §4), Response (US10). |
| SPEC-G08 | Green, red and yellow always come with ✓, ✗ or ⚠; colours are readable in light and dark look and feel. |
| SPEC-G09 | The documentation shows per endpoint the spec per method (or "No spec"), the ✓/✗ counts, the number of violations per kind, and per call its arguments with the same lines as the detail view. |
| SPEC-G10 | An Unmatched paths button and tree context-menu item on a namespace open its Unmatched paths panel: Path, Calls, Clear, Add endpoint pattern; it refreshes with the panel's redraw. |
| SPEC-G11 | All new user-facing texts come from `Messages.properties`. |
| SPEC-G12 | Header values are shown in full. |

### 3.10 Limits, threading, security (SPEC-L)

| ID | Rule |
|---|---|
| SPEC-L01 | Limits: at most 200 arguments are kept per call (the rest dropped, warning TOO_MANY_ARGUMENTS); JSON depth 10 (SPEC-J07); value length 10 000 (SPEC-T10); body 64 KB (SPEC-C05); 1000 unmatched paths (SPEC-U02). |
| SPEC-L02 | Specs are immutable; a change replaces the endpoint's spec in one step; a report uses one spec from start to end. |
| SPEC-L03 | Recording never waits for a spec change; arguments are parsed on the proxy thread, once per call. |
| SPEC-L04 | Traffic data is scanned by hand-written code, never by backtracking regular expressions. |
| SPEC-L05 | All text from traffic is escaped before it is shown as HTML. |

## 4. Message catalogue

`<x>` are filled in; values longer than 80 characters are shortened with `…`. Every line starts
with its mark: ✓ matched, ✗ violation, ⚠ warning, no mark for unchecked.

### 4.1 Checks

| Kind | Template | Example |
|---|---|---|
| matched | `✓ <location> <name> = <value>  (<type>[ <range>])` | `✓ query _limit = 5  (Integer 1..100)` |
| unchecked | `<location> <name> = <value>` | `header User-Agent = Firefox/140` |

Ranges are written `min..max`, `≥ min` or `≤ max`.

### 4.2 Violations

| Kind | Template | Example |
|---|---|---|
| UNSPECIFIED_METHOD | `✗ <method> is not in the spec (allowed: <methods>)` | `✗ PUT is not in the spec (allowed: GET, POST)` |
| UNSPECIFIED_CONTENT_TYPE | `✗ content type <type> is not allowed for <method> (allowed: <types>)` | `✗ content type text/plain is not allowed for POST (allowed: application/json)` |
| MALFORMED_BODY | `✗ the <kind> body can't be read: <reason> at character <n>` | `✗ the JSON body can't be read: trailing comma at character 9` |
| EXTRA_ARGUMENT | `✗ <location> <name> = <value> is not in the spec` | `✗ query debug = 1 is not in the spec` |
| INVALID_TYPE (text) | `✗ <location> <name> = <value>: expected <type>` | `✗ path POST_ID = abc: expected Integer` |
| INVALID_TYPE (JSON kind) | `✗ body <name> = <value>: expected <type> (a JSON <kind>), got <a JSON kind>` | `✗ body userId = "42": expected Integer (a JSON number), got a string` |
| INVALID_TYPE (not whole) | `✗ <location> <name> = <value>: expected Integer, got a number with a fraction or exponent` | `✗ body count = 1.0: expected Integer, got a number with a fraction or exponent` |
| INVALID_TYPE (encoding) | `✗ <location> <name> = <value>: broken %-escape or UTF-8` | `✗ query n = %zz: broken %-escape or UTF-8` |
| INVALID_TYPE (too long) | `✗ <location> <name>: expected <type>, the value has <n> characters (at most 10 000)` | |
| OUT_OF_RANGE | `✗ <location> <name> = <value>: below the minimum <min>` / `above the maximum <max>` | `✗ query _limit = 500: above the maximum 100` |
| MISSING_ARGUMENT | `✗ required <location> <name> is missing` | `✗ required body title is missing` |

### 4.3 Warnings

| Kind | Template |
|---|---|
| REPEATED_PARAMETER | `⚠ <location> <name> appears <n> times. Check which value your HTTP server or framework uses.` |
| PRECISION | `⚠ body <name> = <value> can't be stored exactly by clients that use double-precision numbers (such as JavaScript).` |
| BODY_NOT_CHECKED | `⚠ the body was not checked: <reason>` (reasons: `it is larger than 64 KB`, `it is binary`, `its type <type> is not JSON or form`) |
| TOO_MANY_ARGUMENTS | `⚠ only the first 200 arguments were checked` |
| TOO_DEEP | `⚠ the JSON body is nested deeper than 10 levels; deeper values were not checked` |

### 4.4 Other labels

| Where | Text |
|---|---|
| verdict NOT_VERIFIED, preflight | `preflight (not checked)` |
| Specification tab, empty spec | `No spec: calls are not checked` |
| Body field on GET/HEAD/DELETE | `Unusual: many servers ignore a body in a <method> request` |
| Pattern builder, empty part | `The path has an empty part (//), so it can't become a pattern` |
| Pattern builder, locked literal | `This part can only be a variable: <reason>` |
