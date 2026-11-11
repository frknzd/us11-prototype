# Hands-on US11 walkthrough

Use only the supplied loopback fixture. Start ZAP with `scripts/run-prototype.sh` and the API
with `python3 scripts/demo-server.py`. If the REST Extraction tab is hidden, select it from the
green **+** beside History/Search/Alerts/Output. Drag the splitter to give the editor more room.

## 1. Capture a neutral call

1. Add API **http://127.0.0.1:8092**.
2. Add endpoint **/posts/[POST_ID]/comments** and press **Start**.
3. Run `python3 scripts/demo-traffic.py --case baseline`.
4. Select the endpoint. Calls shows one call with an empty Check column; Specification says
   **No spec: calls are not checked**. This is neutral, not an error.

## 2. Define GET (no traffic inference)

In Specification, choose each item kind and fill its fields; press **Add item** each time.

| Kind | Method | Name/type/range |
|---|---|---|
| Path variable | GET | POST_ID · Integer · min 1 · max 10000 |
| Query parameter | GET | _limit · Integer · min 1 · max 100 · Required off |

Adding the first parameter automatically adds its Method item. Methods are values, not separate
GET/POST classes. Observe that the already-recorded baseline call becomes ✓ without resending it.
The form displays only applicable fields. Bounds are optional and inclusive; values are never
trimmed during verification. Rejected input stays editable and changes nothing in the model.

## 3. Define POST

| Kind | Method | Name/type/range |
|---|---|---|
| Path variable | POST | POST_ID · Integer · min 1 · max 10000 |
| Content type | POST | application/json |
| Content type | POST | application/x-www-form-urlencoded |
| Body field | POST | title · String · Required on |
| Body field | POST | userId · Integer · no range · Required off |

Optional: add Header **X-Request-Id** · Integer for GET to see duplicate case-insensitive header
checking. Other headers are never extra and remain fully visible. Defining body fields on GET,
HEAD or DELETE is allowed and shows an unusual-body hint.

## 4. Compare calls, checks and documentation

```sh
python3 scripts/demo-traffic.py --case valid
python3 scripts/demo-traffic.py --case invalid
python3 scripts/demo-traffic.py --case form
python3 scripts/demo-traffic.py --case malformed
python3 scripts/demo-traffic.py --case duplicates
python3 scripts/demo-traffic.py --case precision
python3 scripts/demo-traffic.py --case preflight
```

Expected with the above spec:

- Valid GET/POST/form requests show ✓; response status codes do not decide compliance.
- `POST_ID=abc`, `_limit=500`, `debug=1`, unsupported PUT and wrong JSON kinds show ✗ diagnostics.
- An unparsed text/plain body has an unsupported content type and a body-not-checked warning.
- Malformed JSON is reported once and does not produce misleading body missing/extra checks.
- Repeated `_limit` values are individually checked; one warning advises consulting server/framework
  behaviour. The extension does not guess a preferred value.
- Large JSON userId values can be valid but show a precision warning. Warnings alone do not invalidate.
- OPTIONS with Access-Control-Request-Method is labelled preflight and remains neutral.

Tick **Only invalid**, combine it with method/text filters, sort the table, and select a call.
The selected row still identifies the correct request and verdict. Details show Request → Checks →
Response. **Full body** controls both request and response body display. Documentation repeats the
same diagnostics, spec, counts and violation totals. Request bodies are included; response bodies
are intentionally omitted. Traffic is escaped in HTML, never executed.

## 5. Rejudge old traffic

Select the `_limit` item, press **Edit**, set maximum to **3**, and **Save changes**.
Old `_limit=5` calls immediately turn invalid on refresh; no traffic needs resending.
An invalid minimum/maximum refuses the edit and preserves the old spec.
Changing a ranged type to String/Base64 asks before removing bounds. Removing/changing a Method
with dependent items asks before removing them. **Clear calls** keeps the spec; removing and
re-adding an endpoint creates an empty spec. Specs are lost when this ZAP session closes.

## 6. Nested fields and exact decimals

Optionally add POST Body fields:

- `user.id` · Integer
- `items[].id` · Integer (Required must stay off for wildcard fields)
- `scores{}` · Decimal · minimum 0 · maximum 0.3

Run `python3 scripts/demo-traffic.py --case nested`. The decimal `0.30000000000000004` is above
0.3: comparisons use exact decimal values, not binary doubles. JSON object/array containers are
not extra; an explicitly scalar-typed container gets one type error without a cascade of extra
errors for its children. Escaped field-name punctuation and concrete-over-wildcard precedence
follow the formal specification.

## 7. Discover an unmatched path

```sh
python3 scripts/demo-traffic.py --case unmatched
```

Select the **namespace**, not an endpoint, and press **Unmatched paths** (also in its context menu).
`/new-resources/42` appears with count 2 but does not increase the main tree or documentation counts.
Select it and **Add endpoint pattern**. Toggle `42` to a variable chip, rename it **RESOURCE_ID**,
and inspect `/new-resources/[RESOURCE_ID]` before adding. New calls can now match that pattern;
its spec starts empty. Adding a pattern leaves the previous unmatched count intact. Clearing this
panel clears only its counts. Clearing namespace calls also clears unmatched counts.

Unsafe literal parts (whitespace, brackets, percent signs, `.`/`..`) start as variables and cannot
be toggled back; empty/root paths cannot become patterns. Safe Unicode literals percent-encode.
The prototype documents the resolution of the old whitespace-literal example in its test plan.

## 8. Boundaries and cleanup

`python3 scripts/demo-traffic.py --case limits` demonstrates the 64-KB body and 200-argument limits.
No traffic is blocked. The parser caps extraction depth at 10; numeric values at 10,000 characters
and decimal exponent magnitude at 9,999; unmatched storage at 1,000 distinct paths plus overflow.

Stop only this prototype with `python3 scripts/stop-prototype.py`, and stop the fixture with Ctrl-C.
Do not change the existing Firefox/system proxy or CA trust store. The fixture and screenshots use
only generated local values; never share actual API keys or private traffic.
