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

## Checkpoint 5 — primary UI and real GUI smoke

- Added the kind-driven Specification tab with grouped item table, add/edit/remove, dynamic fields, path-variable choices, numeric ranges, Required, atomic edits, confirmations and localized validation errors.
- Added Check column, invalid-only filtering, filtered ✓/✗ counts, request/check/response details, shared escaped diagnostics and specification/compliance documentation. Full body applies to request and response details; request headers remain fully visible.
- **599 tests passing**, 0 failures/errors/skips; formatter and packaging pass. Existing documentation assertions were updated only where US11 deliberately adds captured request URLs/blocks; response-body exclusion and header wrapping remain asserted.
- Real ZAP UI smoke passed using an opt-in acceptance agent and a temporary loopback fixture, operating through actual Swing fields/buttons. It verified empty neutrality, adding/editing an Integer spec, rejudging old calls, valid/invalid marks, filtering and selected-call diagnostics. It also opened documentation and exercised light/dark themes plus a 1150×800 window.
- Screenshots inspected under `.local/ui-smoke/`: `specification-light.png`, `checks-light.png`, `checks-dark.png`, `documentation-dark.png`, `specification-compact-dark.png`. No real user traffic is in these fixtures.
- Visual testing found and fixed two issues: ZAP disables HTML labels (tree now paints native coloured counts), and the initial splitter starved the editor (explicit editor/table sizing now keeps the complete numeric form usable in the compact window).
- The smoke agent is test tooling only, excluded from the add-on; normal startup never seeds namespaces or specs. Run with `scripts/ui-smoke.sh` only after stopping the prototype instance. Existing ZAP on port 8080 remains untouched.

## Checkpoint 6 — unmatched paths and chip builder

- Added bounded, thread-safe decoded-path counts (1000 distinct paths plus overflow), snapshot access, clear lifecycle and value equality for UrlPath. Unmatched calls do not enter reports, main counts or documentation.
- Added the namespace button/context item, independently refreshed/sortable Path/Calls panel and modal literal/variable chip builder. Builder registration uses the normal facade; newly created patterns appear in the main tree with an empty spec. Clearing unmatched counts leaves recorded calls/specs intact.
- **605 tests passing**, 0 failures/errors/skips. Tests cover bounded concurrent counts, decoded identity, builder naming/encoding/validation, namespace lifecycle, UI refresh/selection/clear and documentation exclusion.
- Extended the real GUI smoke: generated an unmatched request, opened the actual dialog, toggled/renamed a chip, verified `/items/[ITEM_ID]`, registered it, observed it in the tree, captured a neutral call and cleared unmatched counts. Smoke passed; inspected `.local/ui-smoke/pattern-builder-dark.png`.
- Explicit prototype design refinement: U08 forbids whitespace literals, but the old PD8 example expected one. U08 now takes precedence: unsafe parts start as variables; safe Unicode literals are encoded. The prototype spec/test-plan copy records this; workspace/team design originals are unchanged for review.
- Observer refresh signals now include unmatched data/new patterns without changing recorded-call return semantics or counts. Namespace Clear also clears unmatched counts when there are zero matched calls. Associated dialogs are disposed with their namespace/panel.
- Shared test-plan DOCX updated to history 0.1.20 with the refinement and UI acceptance checks after a concurrent-edit check and backup.

## Checkpoint 7 — demonstration and documentation

- Added standard-library loopback fixture (`demo-server.py`, default 8092) and explicit-proxy traffic generator (`demo-traffic.py`, default 8091). Proxy environment bypasses do not affect these requests. Case groups cover neutral/valid/invalid, JSON/form, nested fields, malformed encoding/body, duplicate values/headers, precision, preflight, unmatched paths and limits.
- Ran every demo case through the isolated proxy: fixture requests returned expected 200/201/204 statuses; no public sites were contacted. This checks transport/fixtures, not compliance outcomes unless the walkthrough's spec has been configured in the UI.
- Added a complete hands-on walkthrough, repository/add-on run/build/safe-stop documentation and packaged US11 help. Added a stop helper that refuses to signal processes lacking this checkout's exact runtime/profile markers.
- Copied the required baseline diagram/rendering/checker sources so design documents are self-contained. Prototype design checker reports **0 problems**; it is still only design traceability, not proof of application acceptance.
- The Help add-on is intentionally not installed in the prototype (mandatory add-ons only). The packaged help HTML is available directly; manual Help navigation is pending unless the user requests that extra core add-on.
- Build, formatting, **605 tests** and packaging pass after documentation changes. No remote exists.

## Checkpoint 8 — acceptance handover

- Added `VerificationScenarioTest` for SC-V1–SC-V13, `DiagnosticCatalogUnitTest` for the CW/SM/MSG catalogue and form/table branches, and edge-case tests for grammar boundaries, encoding, warnings and value objects. Panel tests now also cover namespace-disabled specification, sorted selection, coloured diagnostic text, confirmation decline, range removal and PN-E9 contrast in light/dark FlatLaf.
- Details use a plain-text styled pane: traffic remains inert, while generated ✓/✗/⚠ lines receive semantic colour. Documentation specification tables are grouped by method.
- **632 tests passing**, 0 failures/errors/skips; Spotless and packaging pass. Fresh real-ZAP UI smoke passed after the final UI change.
- Coverage: total 92.62% lines / 90.10% branches; model 99.47% / 96.09%; root 98.70% / 90.00%; UI 86.75% / 82.09%. Parser/type classes have 100% lines; branch coverage is at least 95% except NumericGrammar 95.16%, RequestParser 98.15%, JsonReader 99.32% and FieldName 98.39% (all above 95%). Large Swing panels/dialogs remain below the logic-class gates; PatternBuilderDialog is exercised by the real GUI smoke but not by JaCoCo unit tests.
- Design traceability checker: 0 problems. This does not prove that every planned row has a one-to-one JUnit method; several rows are grouped.
- Manual cases are **not** marked passed. Automated real-GUI evidence covers parts of M-V1–M-V5 and M-V7. M-V6 burst responsiveness, M-V8 restart/session loss, M-V9 Juice Shop and M-V10 human review remain pending. Help navigation is pending because the optional core Help add-on is intentionally not installed in the isolated runtime.
- Final archive: `addOns/restextraction/build/zapAddOn/bin/restextraction-alpha-0.0.1.zap`. Isolation confirmed: existing ZAP stays on 127.0.0.1:8080; the prototype listens only on 127.0.0.1:8091; the fixture is loopback-only.

## Remaining limitations

- Pending human/manual checks above.
- UI-heavy Swing coverage is below the aspirational per-class coverage targets.
- Inherited upstream workflows remain in the snapshot. The published branch is `us11-implementation`, not `main`, so main-branch CI/release workflows are not triggered by this push.
