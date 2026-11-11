# US11 REST verification: use cases

Version 0.1 · diagram: `US11 use case diagram.puml` · rules: `US11 specification.md` (SPEC-…).
The system is ZAP with the REST Extraction add-on (as in US10). US10 use cases (Add API namespace,
Register endpoint pattern, Start/stop extraction, View API report, Record matching API call) are
unchanged and only referred to.

## Actors

| Actor | Goal |
|---|---|
| Developer | define what the API accepts and see where the traffic deviates from it |
| API client (browser or own app) | send requests to the API; its traffic goes through the ZAP proxy |
| REST API server (the developer's application) | answer the requests; not checked by US11 |

## Use cases

| ID | Name | Level | Primary actor | Text |
|---|---|---|---|---|
| UC-V1 | Define the spec of an endpoint | user goal | Developer | fully dressed |
| UC-V2 | Verify traffic against the spec | user goal (runs on its own while extracting) | API client (traffic) / Developer (stakeholder) | fully dressed |
| UC-V3 | Inspect the verification result of a call | user goal | Developer | brief |
| UC-V4 | Read the compliance documentation | user goal | Developer | brief |
| UC-V5 | Add an endpoint pattern from an unmatched path | user goal, secondary | Developer | brief |

---

## UC-V1 Define the spec of an endpoint

| Field | Content |
|---|---|
| Scope | ZAP with the REST Extraction add-on |
| Level | user goal |
| Primary actor | Developer |
| Stakeholders and interests | **Developer**: wants to say quickly what the API accepts, per method, without a file format; wants to be told at once when an input makes no sense. **API client authors**: rely on the spec matching the server. **Supervisor**: the spec covers types, ranges, methods, content types and extra arguments (R3, R4, R6). |
| Preconditions | An API namespace exists and has at least one endpoint pattern (US10). |
| Success guarantee | The endpoint's spec contains exactly the accepted items; it was replaced as a whole (SPEC-S14, SPEC-L02); the next report judges all recorded calls of the endpoint against it (SPEC-V01). |

**Main success scenario**

1. The developer selects an endpoint pattern in the tree.
2. The system shows its Specification tab with the current items, or "No spec: calls are not
   checked" (SPEC-G03).
3. The developer chooses a kind in the Add item dropdown (SPEC-G04).
4. The system shows the fields of that kind only (SPEC-G04).
5. The developer fills in the fields (method; name or variable; type; range; Required) and confirms.
6. The system validates the item (SPEC-S03 … SPEC-S13), adds it to the spec, adds the Method item
   if the method had none (SPEC-S04), and replaces the endpoint's spec (SPEC-S14).
7. The system lists the item under its method (SPEC-G05) and, at the next redraw, shows the ✓/✗
   counts of the endpoint (SPEC-G02).

The developer repeats steps 3–7 until the spec is complete.

**Extensions**

- 3a. The developer edits an existing item: the form opens with its values; continue at step 5.
- 3b. The developer removes an item:
  1. If it is a Method item with other items, the system asks for confirmation (SPEC-S05).
  2. The system removes it (and that method's items) and replaces the spec. Continue at step 7.
  3. If the spec is now empty, the endpoint shows no indicator any more (SPEC-G01).
- 5a. The method is OTHER, or a Method item for it exists: the system refuses (SPEC-S03).
- 5b. The name is empty, has leading/trailing whitespace, or is already used for that method and
  location: refused (SPEC-S08).
- 5c. A Body field name is not a valid field name (`a..b`, `a[1]`, …): refused (SPEC-S09, SPEC-J09).
- 5d. A Body field for a method whose content types are neither JSON nor form, or removing the last
  JSON/form content type while Body fields exist: refused (SPEC-S10).
- 5e. The content type is not `type/subtype`, or is already listed: refused (SPEC-S06).
- 5f. A range on String/Base64, a bound outside the type's grammar, or min > max: refused (SPEC-S11).
- 5g. Required on a field name with `[]` or `{}`: refused (SPEC-S12).
- 5h. The type of an item with a range changes to String or Base64: the system asks to drop the
  range (SPEC-S11); if the developer declines, nothing changes.
- 5i. A Body field on GET, HEAD or DELETE: accepted, with a hint (SPEC-S13).
- 6a. On every refusal the form stays open, the message says why, and the spec is unchanged
  (SPEC-S14, SPEC-G05).
- \*a. The developer removes the endpoint: its spec is removed too (SPEC-S15).
- \*b. Calls arrive while the developer edits: they are recorded as usual; recording never waits for
  the spec (SPEC-L03).

**Special requirements:** all texts translatable (SPEC-G11); colours always with ✓/✗/⚠ (SPEC-G08).
**Technology and data variations:** Swing panel inside ZAP; specs live for the session only (SPEC-S16).
**Frequency:** a few times per endpoint, mostly at the start of a session.
**Open issues:** none.

---

## UC-V2 Verify traffic against the spec

| Field | Content |
|---|---|
| Scope | ZAP with the REST Extraction add-on |
| Level | user goal; runs by itself for every recorded call and whenever a report is made |
| Primary actor | API client (its requests are verified); the developer is the one who wants the result |
| Stakeholders and interests | **Developer**: sees every deviation (R5, R6) with a clear message, and valid values marked too (D3); is not disturbed by endpoints without spec. **API client user**: traffic is never blocked or changed (SPEC-C07). **ZAP**: the listener stays fast and never throws. |
| Preconditions | The namespace is started (US10); the endpoint pattern exists; its spec may be empty. |
| Success guarantee | The call is recorded with its request data and parsed arguments (SPEC-C01, SPEC-C08); every report shows for it a verdict computed against the current spec (SPEC-V01, SPEC-V14). |

**Main success scenario**

1. The API client sends a request through the ZAP proxy; the server answers (US10 "Send request
   through proxy").
2. The system records the call under every matching endpoint (US10 "Record matching API call"),
   together with its query, headers, content type and body (SPEC-C01 … SPEC-C06).
3. The system parses the call's arguments once: path, query, body (JSON or form), headers
   (SPEC-C08, SPEC-Q, SPEC-J, SPEC-H).
4. When a report is made (redraw, documentation, selection), the system verifies each call against
   its endpoint's current spec (SPEC-V01):
   1. finds the operation for the call's method (SPEC-V04);
   2. checks the content type when the operation lists any (SPEC-V05);
   3. checks every argument against the item of its location and name: type and range (SPEC-V08,
      SPEC-T);
   4. reports undeclared query and body arguments as extra where that location is declared
      (SPEC-V09) and missing required items (SPEC-V11);
   5. adds warnings (SPEC-V12, SPEC-V13) and orders the violations (SPEC-V15).
5. The system gives the call its verdict VALID or INVALID with all checks (SPEC-V14) and updates the
   counts in the tree and the calls table (SPEC-G02, SPEC-G06).

**Extensions**

- 2a. No pattern matches, but the namespace covers the call: the system counts its path as
  unmatched (SPEC-U01, SPEC-U02); the use case ends (see UC-V5).
- 2b. The request body is binary or larger than 64 KB: kept as a mark or cut (SPEC-C05).
- 2c. Recording fails internally: logged, the traffic passes unchanged (SPEC-C07).
- 3a. The query or form has broken escapes or invalid UTF-8: the argument is kept raw and marked
  (SPEC-Q04, SPEC-Q05).
- 3b. The JSON body is malformed: no body arguments (SPEC-J01); in step 4 the call gets
  MALFORMED_BODY (SPEC-V06).
- 3c. More than 200 arguments or JSON deeper than 10 levels: the rest is skipped with a warning
  (SPEC-L01, SPEC-J07).
- 4a. The spec is empty: the verdict is NOT_VERIFIED, no indicator (SPEC-V02).
- 4b. The call is a CORS preflight: NOT_VERIFIED, "preflight" (SPEC-V03).
- 4c. The method has no Method item: UNSPECIFIED_METHOD, no further checks (SPEC-V04).
- 4d. The body's content type is not listed: UNSPECIFIED_CONTENT_TYPE, body still checked (SPEC-V05).
- 4e. The body cannot be checked (binary, cut, other type): warning BODY_NOT_CHECKED (SPEC-V07).
- 4f. A JSON container where a scalar is expected: one INVALID_TYPE, its contents not extra (SPEC-V10).
- 4g. A repeated query/form parameter: each value checked, one REPEATED_PARAMETER warning (SPEC-V12).
- 4h. A JSON number that clients with doubles can't store exactly: PRECISION warning (SPEC-T11).
- 4i. The developer changed the spec since the last report: every call is judged again (SPEC-V01, SPEC-V17).
- \*a. The response status (also 4xx/5xx) never changes the verdict (SPEC-V16).

**Special requirements:** limits of SPEC-L01; no backtracking regular expressions on traffic
(SPEC-L04); a report never mixes two specs (SPEC-L02); the 500 ms redraw stays responsive with
1000 calls (US10 M6, M-V6).
**Technology and data variations:** JSON per RFC 8259; form per `application/x-www-form-urlencoded`;
UTF-8 by default.
**Frequency:** every proxied response of a started namespace; every redraw (at most every 500 ms).
**Open issues:** none.

---

## UC-V3 Inspect the verification result of a call (brief)

The developer looks at the tree, where endpoints with a spec show green ✓ and red ✗ counts
(SPEC-G02), filters the Calls table with **Only invalid** (SPEC-G06), and selects a call. The detail
view shows its request, then one line per check with ✓, ✗ or ⚠ and a message (catalogue §4 of the
specification), then the response (SPEC-G07). Extends US10 "View API report".

## UC-V4 Read the compliance documentation (brief)

The developer opens the Documentation tab. Per endpoint it shows the spec per method (or "No
spec"), the ✓/✗ counts, the number of violations per kind, and per call its arguments with the same
lines as the detail view (SPEC-G09). Unmatched paths are not part of it (SPEC-U04). Extends US10
"View API report".

## UC-V5 Add an endpoint pattern from an unmatched path (brief, secondary)

The developer selects a namespace and opens its Unmatched paths panel (SPEC-G10), sees the paths
that matched no pattern with their counts (SPEC-U02), selects one and chooses **Add endpoint
pattern**. The pattern builder shows the path as chips (SPEC-U05); the developer turns parts into
variables and names them (SPEC-U06, SPEC-U08) and adds the pattern, which goes through the US10
rules (SPEC-U09, SPEC-U10). Paths with an empty part can't be used (SPEC-U07). Includes US10
"Register endpoint pattern".

---

## Operation contracts

System operations from the system sequence diagrams (`US11 system sequence diagrams.puml`).

### OC-1 `addSpecItem(endpoint, item)`

| | |
|---|---|
| Cross references | UC-V1 step 5–6, SSD-V1 |
| Preconditions | `endpoint` exists in a namespace; `item` has a kind and the fields of that kind |
| Postconditions | if `item` passes SPEC-S03 … SPEC-S13: a new spec was made that has every old item plus `item` (and a Method item for its method when there was none, SPEC-S04); the endpoint's spec was replaced by it (SPEC-S14); the endpoint's verdict cache was emptied (SPEC-V17). Otherwise nothing changed and a refusal message was produced (SPEC-S14). |

### OC-2 `removeSpecItem(endpoint, item)`

| | |
|---|---|
| Cross references | UC-V1 extension 3b, SSD-V1 |
| Preconditions | `item` is in the endpoint's spec |
| Postconditions | a new spec without `item` (and, for a Method item, without that method's items, SPEC-S05) replaced the endpoint's spec; the verdict cache was emptied. Removing the last JSON/form content type while Body fields exist was refused and changed nothing (SPEC-S10). |

`editSpecItem(endpoint, old, new)` is `removeSpecItem` followed by `addSpecItem` as one change: if
the add is refused, the old item stays (SPEC-S14).

### OC-3 `recordCall(message)`

| | |
|---|---|
| Cross references | UC-V2 steps 1–3, SSD-V2 |
| Preconditions | the namespace is started |
| Postconditions | for each matching endpoint a recorded call was created with the request data (SPEC-C01 … C06) and the parsed arguments and parse warnings (SPEC-C08); or, when the namespace covers the call but no pattern matches, the unmatched path's count grew by one (SPEC-U01, U02). The message was passed on unchanged (SPEC-C07). |

### OC-4 `getReport(namespace)`

| | |
|---|---|
| Cross references | UC-V2 steps 4–5, UC-V3, UC-V4, SSD-V2 |
| Preconditions | the namespace exists |
| Postconditions | a report snapshot was created that holds, per endpoint, a copy of its spec, its calls and a verdict per call computed against that one spec (SPEC-V01, SPEC-L02), plus the counts per verdict and per violation kind; verdicts computed for the first time were put in the endpoint's cache (SPEC-V17). Nothing in the domain changed otherwise. |
