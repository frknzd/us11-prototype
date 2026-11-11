# US11 REST verification: design documents

Design for user story 11 "REST verification" (assignment p.22–23), built on US10 "REST extraction"
as on `origin/us10/feat/call-filters` (MRs !26 to !32) and its class diagram v3
(`../../diagrams/US10 class diagram v3.puml`). No code is part of this folder.

Source of the content: the approved plan `../../plans/us11-rest-verification.md` (draft 0.6,
decisions D1–D16 settled with the team).

## Documents

| # | Document | Version | What it holds | IDs it defines / uses |
|---|---|---|---|---|
| 1 | `README.md` | 0.1 | this index | — |
| 2 | `US11 specification.md` | 0.1 | formal specification: requirements, terms, decisions, 107 numbered rules, message catalogue | defines R1–R8, D1–D16, SPEC-C/Q/J/H/T/S/V/U/G/L, message kinds |
| 3 | `US11 domain model.puml` → `.png` | 0.1 | 25 concepts, 32 associations; new in green, changed in yellow | — |
| 4 | `US11 use case diagram.puml` → `.png`; `US11 use cases.md` | 0.1 | actors, UC-V1 … UC-V5 (V1, V2 fully dressed), operation contracts OC-1 … OC-4 | defines UC-V, OC; uses SPEC, R |
| 5 | `US11 system sequence diagrams.puml` → `US11 SSD-V1 define the spec.png`, `US11 SSD-V2 verify traffic.png` | 0.1 | system operations of UC-V1 and UC-V2 | defines SSD-V1/2; uses OC |
| 6 | `US11 class diagram.puml` (changes only, the source) → `US11 class diagram.png` (whole class diagram, US11 highlighted, made by `diagrams/highlight_us11.py`) | 0.1 | changes only on top of US10 v3: 54 classes (37 new of which 8 enumerations, 11 changed, 6 shown for context), 71 relations | uses D, SPEC |
| 7 | `US11 sequence diagrams.puml` → `US11 SD-V1 … SD-V5 *.png` (one file per page) | 0.1 | SD-V1 record a call, SD-V2 add a spec item, SD-V3 make the report and verify, SD-V4 show the checks, SD-V5 pattern from an unmatched path | defines SD-V; uses class diagram |
| 8 | `US11 test plan.md` | 0.1 | 258 test cases: 202 unit, 24 integration, 22 end-to-end, 10 manual | defines test IDs; cites SPEC, D, R |
| 9 | `US11 coverage.md` | 0.1 | coverage goals and method, US10 baseline (430 tests, 97.2 % line, 93.0 % branch), traceability, check results | uses R, UC, SPEC, test IDs |

## Conventions

- **IDs.** Requirements `R1`–`R8`, decisions `D1`–`D16`, rules `SPEC-<area><nn>` (areas:
  C capture, Q query/form, J JSON and field names, H headers, T types, S defining a spec,
  V verification, U unmatched paths, G GUI, L limits/threading/security), use cases `UC-V<n>`,
  operation contracts `OC-<n>`, diagrams `SSD-V<n>`/`SD-V<n>`, tests as in the test plan
  (e.g. `PT-I7`, `SC-V3`, `M-V2`). The `V` keeps them apart from the US10 IDs (UC1, SC1, M1…).
- **Highlighting in every diagram.** The attention is on US11; US10 is only context:

  | Looks like | Means |
  |---|---|
  | green box, bold green border | new in US11 |
  | yellow box | US10 element that US11 changes |
  | bold green member (in a yellow class) | new or changed member |
  | grey struck-through member | US10 member that US11 replaces or removes |
  | faded grey box, grey member, grey line or message | US10, unchanged |
  | bold green line or message | new or changed relation, association or call |

  The class diagram PNG shows all US10 v3 classes faded and US11 on top. Its source,
  `US11 class diagram.puml`, lists only the changes; a removed US10 member is written there as a
  comment line `' removed: <member>`, which PlantUML ignores and the highlighter strikes through.
- **Placement and routing.** The class and domain PNGs use fixed, aligned functional regions and
  orthogonal connectors routed around the class/concept boxes. Region headings are layout guides,
  not additional Java packages or domain concepts. Explanatory notes sit below the diagram, outside
  the routing area. All classes/concepts, members, relationships and multiplicities are retained.
  `diagrams/layout_us11.py` measures the boxes with Graphviz, routes with `neato -n2`, and checks
  that boxes do not overlap and connectors do not pass through unrelated boxes. This does not
  claim that every edge crossing or label collision has been eliminated.

## Prototype implementation refinements

This is the acceptance copy for the separate local prototype, not an assertion that every planned
case has passed. Actual outcomes and implementation refinements are in
`../../docs/implementation-status.md`; a runnable walkthrough is in `../../docs/demo-walkthrough.md`.
The class diagram remains the design baseline; additive implementation helpers and atomic edit APIs
are recorded in the test plan's Implementation notes. Unsafe pattern parts start as variables
under SPEC-U08, resolving the contradictory original whitespace-literal example in PD8.

## Refinements of the plan made while drawing

| Plan said | Documents say | Why |
|---|---|---|
| `RestExtractor.setOperation(ns, template, op)` / `removeOperation(…)` | `addSpecItem(ns, template, item)` / `removeSpecItem(…)` with a `SpecItem` value | matches the system operations of UC-V1 (OC-1, OC-2) and the Specification tab, which edits one item at a time |
| `ApiReport` changed to carry unmatched paths | `ApiReport` unchanged; `RestExtractor.getUnmatchedPaths(ns)` | D4: unmatched paths are never part of the report or the documentation |
| default variable name `VAR` when the cleaned name is empty | removed (SPEC-U06) | cleaning only replaces characters and empty parts are refused, so the case can't happen |
| `SpecItemKind` in the UI | in the model | `SpecItem` (model) uses it; the model must not depend on the UI |

## Rendering

Only the PNG of each diagram is kept here, next to its `.puml` source. Regenerate the published
layouts with PlantUML, Graphviz (`dot`, `neato`) and `rsvg-convert`:

```sh
cd design/us11
PLANTUML_LIMIT_SIZE=12000 plantuml -tpng "US11 use case diagram.puml" \
    "US11 system sequence diagrams.puml" "US11 sequence diagrams.puml"   # one PNG per @startuml block
python3 ../../diagrams/highlight_us11.py   # whole class diagram, highlighted and arranged
python3 ../../diagrams/layout_us11_domain.py   # arranged domain model
```

Intermediate routing files stay in `../../.local/us11-highlight/` and
`../../.local/us11-domain-layout/`. Direct PlantUML renders of the class/domain sources remain
valid, but do not reproduce the published fixed layout. For other formats (only if needed):

```sh
plantuml -tsvg *.puml                              # SVG, if needed (the class diagram's .puml
                                                   # renders the changes-only view)
# editable draw.io files, if needed (uses the SVG for the layout)
for d in "US11 domain model" "US11 class diagram"; do
  python3 ../../diagrams/svg2layout.py "$d.svg" "$d.layout.json"
  python3 ../../diagrams/puml2drawio.py "$d.puml" "$d.layout.json" "$d.drawio"
done
```

Only PNGs and editable PlantUML sources are published here. The workspace's historical
SVG/draw.io/layout backups were not copied into this repository.

## Checks

```sh
python3 ../../scripts/check_design_docs.py          # design traceability only, not application acceptance
python3 ../../scripts/check_design_docs.py --write  # also update US11 coverage.md §5, §6.2
```

Last run (version 0.1): every `.puml` passes PlantUML's syntax check; all 128 messages of the sequence diagrams are methods of the US11 class diagram or of
US10 v3 with the right number of parameters; every class realises a domain concept or is a declared
fabrication/UI class, and every concept is realised; all 107 rules are cited by tests and all 258
tests cite existing rules, decisions or requirements; R1–R8 each reach use cases, rules and tests;
D1–D16 are all in the specification. **0 problems.** The checker was tested by planting a wrong
method, a wrong parameter count and an untested rule; it reported all three.
