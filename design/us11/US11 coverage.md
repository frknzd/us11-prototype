# US11 REST verification: coverage

Version 0.1 · test plan: `US11 test plan.md` · rules: `US11 specification.md`.
This document says how much of the code and of the specification the tests must cover, how it is
measured, and how every requirement reaches tests.

## 1. Two kinds of coverage

| Kind | Question | Measured by |
|---|---|---|
| **Code coverage** | which lines and branches of the add-on run during the automated tests | JaCoCo (§3) |
| **Specification coverage** | does every rule SPEC-… and every requirement R1–R8 have tests | the check script (§5, §6) |

Both must hold. High code coverage without rule coverage misses behaviour the code never had;
rule coverage without code coverage leaves code nobody asked for.

## 2. Goals

| Package / classes | Line | Branch | Reason |
|---|---|---|---|
| `model` (all domain classes, new and changed) | **≥ 90 %** (aim: keep the US10 level, ≥ 98 %) | **≥ 85 %** (aim ≥ 95 %) | pure Java, no ZAP or Swing: everything is unit-testable |
| `model` parsers and types: `RequestParser`, `JsonReader`, `MediaType`, `FieldName`, `ParameterType` subclasses | **100 %** of lines, **≥ 95 %** branch | | they hold the edge cases of §5–§6 of the specification; every branch is a rule |
| root package: `RestTrafficListener`, `ExtensionRestExtraction` | ≥ 90 % | ≥ 85 % | real `HttpMessage`s in TL tests; the defensive `catch` is accepted (§7) |
| `ui` table models and writers: `CallTableModel`, `SpecItemTableModel`, `SpecItemForm`, `CheckWriter`, `ApiDocumentationWriter` | **≥ 80 %** (aim ≥ 95 %) | **≥ 80 %** | logic kept out of Swing on purpose (US10 AGENTS rule) |
| `ui` Swing layout: `RestExtractionPanel`, `SpecificationPanel`, `UnmatchedPathsPanel`, `PatternBuilderDialog` | measured, **no goal** | measured, no goal | layout code and look and feel are checked by PN-E and the manual tests M-V; the logic they call is in the models above |
| **whole add-on** | must not drop below the US10 baseline minus 5 points (≥ 92 % line, ≥ 88 % branch) | | US11 may not lower the quality US10 reached |

A goal that is missed is either fixed with tests or explained in §7 before the sign-off.

## 3. How to measure

```sh
cd zap-extensions
./gradlew :addOns:restextraction:test :addOns:restextraction:jacocoTestReport
open addOns/restextraction/build/reports/jacoco/test/html/index.html
```

- The HTML report lists packages, then classes, then the source with colours: green = covered,
  yellow = branch partly covered (hover shows "1 of 2 branches missed"), red = not run.
- The XML report `build/reports/jacoco/test/jacocoTestReport.xml` has the same numbers per class;
  the check script reads it for §4 and the sign-off card.
- Test counts: `build/test-results/test/*.xml` (`*ScenarioTest` = end-to-end, the rest unit and
  integration tests, as in US10).

## 4. US10 baseline

Measured on 11 Nov 2026 on `origin/us10/feat/call-filters` (`b0b415d49a`, all US10 MRs !26–!32):
**430 tests, 0 failures** (413 unit, 17 scenario).

| Package | Line | Branch |
|---|---|---|
| `model` | 100.0 % (315/315) | 99.3 % (135/136) |
| root (`restextraction`) | 97.6 % (41/42) | 100.0 % (12/12) |
| `ui` | 95.9 % (660/688) | 89.7 % (252/281) |
| **total** | **97.2 % (1016/1045)** | **93.0 % (399/429)** |

Classes below 100 % line coverage: `RestExtractionPanel` 96.5 % (435/451, branch 88.0 %),
`ApiDocumentationWriter` 98.5 % (branch 94.3 %), `CallTableModel` 98.2 % (branch 97.3 %),
`RestTrafficListener` 96.2 % (the logging line in the defensive `catch`). `ApiNamespace` misses one
branch (97.9 %).

## 5. Test counts per level

| Level | Test cases |
|---|---|
| unit | 202 |
| integration | 24 |
| end-to-end | 22 |
| manual | 10 |
| **total** | **258** |

Unit and integration tests run as `*UnitTest` classes, end-to-end tests as `*ScenarioTest` and the panel tests (PN-E); the sign-off card counts JUnit test methods, which will be more than the rows here because many rows list several inputs.

## 6. Traceability

### 6.1 Requirements → use cases → rules → tests

| Req. | Use case | Rules | Tests (examples, full list in the test plan) |
|---|---|---|---|
| R1 capture query, headers, body | UC-V2 | SPEC-C01 … C12, SPEC-Q01 … Q08, SPEC-J01 … J08, SPEC-H01 … H02 | RD1–RD7, MT1–MT5, JR1–JR19, RP1–RP23, TL-V1–TL-V6, SC-V11 |
| R2 show it in GUI and documentation | UC-V3, UC-V4 | SPEC-G06, G07, G09, G12, SPEC-L05 | CW1–CW7, WR1–WR4, PN-E7, M-V2, M-V5 |
| R3 type per argument | UC-V1, UC-V2 | SPEC-T01 … T07, SPEC-T12, SPEC-S02, S07 … S09 | PT-S1–4, PT-I1–6, PT-D1–4, PT-B1–9, PS1–PS9, SM1–SM3, SC-V1 |
| R4 integer/decimal range | UC-V1, UC-V2 | SPEC-T08 … T11, SPEC-S11 | PT-I7–PT-I14, PT-D5–PT-D10, SM4, SM5, SM8, SC-V2 |
| R5 detect invalid requests, show in GUI | UC-V2, UC-V3 | SPEC-V01 … V17, SPEC-G01, G02, G06, G08 | OS1–OS24, ES1–ES4, EI2–EI6, CT1–CT4, PN-E5, PN-E6, SC-V2, M-V2, M-V3 |
| R6 unspecified method, content type, extra arguments | UC-V2 | SPEC-V04, V05, V09, SPEC-S03, S06 | ES3, ES4, OS2, OS3, OS10, OS11, SC-V2, SC-V3, M-V9 |
| R7 define parameters, then scan traffic | UC-V1, UC-V2 | SPEC-S01 … S16, SPEC-G03 … G05, SPEC-L02, L03 | ES5–ES17, SI1–SI2, SM1–SM9, PN-E1–PN-E4, CC1, CC2, SC-V4, M-V1 |
| R8 report shows compliance per call and value | UC-V3, UC-V4 | SPEC-V01, V14, SPEC-G07, G09 | EI2, EI3, SC-V4, WR2, WR3, M-V5 |
| D4 (secondary) unmatched paths | UC-V5 | SPEC-U01 … U10, SPEC-G10 | UM1–UM5, PD1–PD10, UP1–UP2, NS1–NS5, RX3, SC-V7, PN-E8, M-V4 |

### 6.2 Rule coverage (reverse check)

Result of `check_us11_docs.py`: **107 rules**, each cited by at least one test (107/107); **258 tests**, each citing an existing rule, decision or requirement; 16 decisions present; 128 sequence-diagram messages match the class diagrams; problems found: **0**.

## 7. Code we accept to leave uncovered

| Where | Why it may stay uncovered |
|---|---|
| `RestTrafficListener`: the `catch (RuntimeException)` logging line | only reached when recording itself fails; TL-V5 forces a failure through the extractor, but the log call's own branches (log level off) are not worth a test |
| Swing layout code in the four panels and the dialog (anonymous listeners, `GridBagConstraints`, renderers for colours) | checked by PN-E and manual tests; JaCoCo counts many tiny lambdas |
| `JsonReader` defensive "unexpected state" branch | unreachable with a correct state machine; kept to fail loudly if the reader is changed later |
| `equals`/`hashCode` branches for `other == null` / other class in value classes | trivial; covered by one test each, not every combination |

## 8. Expected tests per new or changed class

| Class | Test groups | Goal |
|---|---|---|
| `RequestData`, `Header`, `ApiCall` | RD, TL | model |
| `MediaType` | MT | parser 100 % |
| `JsonReader` | JR | parser 100 % |
| `RequestParser`, `ParsedRequest` | RP, EI1 | parser 100 % |
| `FieldName` | FN, OS23, SC-V9 | parser 100 % |
| `StringType`, `IntegerType`, `DecimalType`, `Base64Type` | PT-S, PT-I, PT-D, PT-B | parser 100 % |
| `ParameterSpec` | PS | model |
| `OperationSpec` | OS | model |
| `EndpointSpec`, `SpecItem` | ES, SI | model |
| `Verdict`, `Check`, `Violation`, `Warning` | VD | model |
| `Endpoint`, `EndpointReport`, `RecordedCall` (changed) | EI, CC | model |
| `ApiNamespace`, `RestExtractor` (changed) | NS, RX | model |
| `UnmatchedPaths`, `PatternDraft`, `UrlPath` (changed) | UM, PD, UP | model |
| `SpecItemForm`, `SpecItemTableModel` | SM | ui models |
| `CallTableModel` (changed), `CheckWriter`, `ApiDocumentationWriter` (changed) | CT, CW, WR, MSG | ui models |
| `RestTrafficListener` (changed) | TL | root |
| `RestExtractionPanel` (changed), `SpecificationPanel`, `UnmatchedPathsPanel`, `PatternBuilderDialog` | PN-E, M-V | no goal (§2) |
