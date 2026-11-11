# Implementation status

## Checkpoint 1 — isolated US10 baseline

- Independent local repository on `us11-implementation`, no remote.
- Baseline: `b0b415d49aaa71247f1ba3806c2998fccddf7024`; all upstream licenses retained.
- `:addOns:restextraction:test`: **430 tests, 0 failures/errors/skips** (Gradle reused matching baseline cache).
- JaCoCo: **1016/1045 lines (97.2%)**, **399/429 branches (93.0%)**.
- Packaged with `:addOns:restextraction:jarZapAddOn`; archive under `addOns/restextraction/build/zapAddOn/bin/`.
- Separate GUI process successfully started with home `.local/zap-profile/`, staged install `.local/runtime/`, proxy listening only on `127.0.0.1:8091`.
- Startup inventory: exactly `callhome 0.24.0`, `network 0.33.0`, `restextraction 0.0.1`; no optional scanners installed. Core emits the expected no-vulnerability-provider message without scanning add-ons.
- Existing ZAP continues listening on `127.0.0.1:8080` (same pre-existing process). Existing repository status unchanged: Java-17 wrapper modifications only.
- One init-script fix was required: skip the included `buildSrc` build before locating `:zap:run`.

## Checkpoint 2 — request capture and parsing

- Added immutable wire/parsed request models, ordered arguments, media types and structured parser warnings.
- Strict iterative JSON scanner preserves duplicate keys, original number lexemes, container paths, escaped names and root scalar/array semantics. Storage is capped; deeper structures are validated iteratively without recursive stack growth.
- Query/form parsing preserves duplicates and literal names, implements UTF-8/percent-decoding diagnostics and shares the global 200-argument limit.
- Listener captures escaped query, original ordered header occurrences, body state and charset-decoded body without mutating messages; existing response behaviour remains covered.
- Request query/headers/body now appear as plain text in the call detail panel; headers are not masked. Added a real EDT-based panel test for this.
- Regression fixtures adapted to the designed ordered `getArguments()` API using `getPathArguments()` for the old path-map assertions; no old assertions dropped.
- **498 tests passing**, 0 failures/errors/skips; package built successfully. RD/MT/JR/RP groups and request TL checks are implemented; test-plan implementation notes identify grouped methods vs invocation counts.
- Fixed a discovered ZAP API trap: base `HttpHeader.isText()` defaults to true for requests, so request binary detection uses normalized media types instead of silently classifying images as text.
- Shared OneDrive test plan updated with a prototype acceptance annex/history row after SHA-256 concurrency check and backup (`.local/testplan/before-us11-prototype-20261111-223921.docx` in the workspace). Existing US10 status was not overwritten.

## Checkpoint 3 — specification and verification model

- Added immutable endpoint/operation specifications and item factories, strict parameter types, exact BigInteger/BigDecimal ranges, token-based field selectors and structured verdict/check/violation values.
- Covered partial specifications, required values, nested containers, wildcard specificity, duplicate items/headers, content-type/body compatibility, malformed bodies, warning-only validity, preflight/empty neutrality and violation ordering.
- Added package-private `NumericGrammar` to share bounded non-regex numeric scanning and `SpecValidationException` to carry localization keys/arguments instead of hard-coding UI validation messages.
- **586 tests passing**, 0 failures/errors/skips. Current aggregate model coverage: 1083/1114 lines (97.2%), 762/819 branches (93.0%). Stricter per-parser/type gates are not yet all met; final acceptance must report and address those gaps rather than treating aggregate coverage as sufficient.
- Specification editing has not yet been connected to endpoints or the UI; ES9 path-name validation belongs to the next integration checkpoint.

## Checkpoint 4 — report integration and concurrency

- Endpoints now atomically replace immutable spec/cache state. Recording parses once and never takes the edit monitor; reports retain one state reference for every verdict.
- Report snapshots expose spec, verdicts, valid/invalid counts and violation-kind counts. Existing reports retain old results; unchanged specs reuse cached verdict objects; changes invalidate them. Clearing calls retains specs; removing/re-adding endpoints starts empty.
- Namespace/facade methods validate path variable names and notify the existing coalesced UI observer after accepted changes.
- Added atomic `replaceSpecItem` and `EndpointSpec.replacing`: validates the final edited spec without exposing/removing an intermediate spec. This is necessary for correct form edits, including replacing the sole parsable content type while other content types exist.
- **593 tests passing**, 0 failures/errors/skips. Concurrency test records 8,000 calls on eight threads while replacing the spec 200 times and verifies exact report/check parameter identity. Two additional checks show recording does not acquire the edit monitor and editing finishes while recording is deliberately blocked.
- All changes remain in the local prototype; no remote or uploads.

## Pending

Complete primary UI, unmatched paths, deterministic demo and full US11 acceptance are not yet implemented. Manual GUI acceptance and final per-class coverage gates remain pending.
