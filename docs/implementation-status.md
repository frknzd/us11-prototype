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

## Pending

Verification, report integration, complete primary UI, unmatched paths, deterministic demo and full US11 acceptance are not yet implemented. Manual GUI acceptance and final coverage gates remain pending.
