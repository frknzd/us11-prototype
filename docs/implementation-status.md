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

## Pending

Request parsing, verification, report integration, primary UI, unmatched paths, deterministic demo and full US11 acceptance are not yet implemented. The baseline opens the unchanged US10 UI, not US11. No claims about passing US11 tests or manual UI acceptance yet.
