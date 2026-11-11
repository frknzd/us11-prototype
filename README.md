# US11 REST verification — local prototype

A complete, runnable ZAP add-on built on the team's US10 baseline. This repository is local-only,
with **no remote**. GitHub hosting can be added later, privately, after a separate request.
Upstream licenses are preserved; provenance and isolation rules are in [BASELINE.md](BASELINE.md).

## What you can try

- Capture path/query/form/JSON/header arguments without changing or blocking traffic.
- Define an operation's specification in a kind-driven editor: methods, content types, typed
  parameters, exact numeric ranges and required values.
- Inspect valid/invalid/neutral calls, per-value checks, warnings, invalid-only filters and
  compliance documentation. Editing the spec rejudges old calls.
- Inspect unmatched paths separately and build an endpoint pattern with literal/variable chips.

Specs and captured calls are session-only. There is no inference, persistence, response validation,
active scanning or OpenAPI integration. Headers are displayed in full; do not use sensitive traffic
when sharing screenshots. Current test/coverage limitations are explicit in
[implementation status](docs/implementation-status.md), not hidden behind a production-ready claim.

## Run (three terminals)

Prerequisites: sibling `../zaproxy` source checkout, JDK 17, Python 3, and existing mandatory
`callhome`/`network` archives in the sibling core's development install. Set `JAVA17_HOME` or
`ZAP_CORE_DIR` for non-default locations. The repository launcher scopes Java 17 to builds;
it does not change the system Java default.

```sh
# Terminal 1: own install, profile and loopback-only proxy (8091)
./scripts/run-prototype.sh

# Terminal 2: local demo REST API (8092)
python3 scripts/demo-server.py

# Terminal 3: first configure the UI as described below, then send local traffic
python3 scripts/demo-traffic.py --case baseline
```

In ZAP, open **REST Extraction** using the green **+** beside the status tabs if necessary.
Add API `http://127.0.0.1:8092`, add `/posts/[POST_ID]/comments`, and click **Start**.
The baseline call is neutral because its spec is empty. Select the endpoint and open
**Specification**. Follow [the step-by-step walkthrough](docs/demo-walkthrough.md) to add checks,
see invalid requests and try the pattern builder.

```sh
python3 scripts/demo-traffic.py --case valid
python3 scripts/demo-traffic.py --case invalid
python3 scripts/demo-traffic.py --case all
# Show available groups/options without sending traffic:
python3 scripts/demo-traffic.py --help
```

The script explicitly connects to the prototype proxy, ignoring `HTTP_PROXY`/`NO_PROXY` settings.
It can target only loopback ports; it does not scan or contact public sites. An equivalent request:

```sh
curl --noproxy '' --proxy http://127.0.0.1:8091 \
  'http://127.0.0.1:8092/posts/1/comments?_limit=5'
```

Port overrides: `US11_PROXY_PORT=8095 ./scripts/run-prototype.sh`,
`python3 scripts/demo-server.py --port 8096`, and
`python3 scripts/demo-traffic.py --proxy-port 8095 --fixture-port 8096`.
A busy proxy/fixture port fails rather than taking over another process.

## Stop safely

```sh
python3 scripts/stop-prototype.py   # verifies this checkout's profile/install before signaling
# Stop the demo server with Ctrl-C in its terminal.
```

Your existing ZAP_D profile, port 8080, Firefox profile, system proxy and certificate stores are
not changed. The prototype has its own ignored `.local/runtime/` and `.local/zap-profile/`.
Do **not** use `copyZapAddOn`, `deployZapAddOn`, `installZapAddOn` or `copyMandatoryAddOns`:
those upstream tasks can affect another installation. Only mandatory add-ons are staged.

## Build and test

```sh
./gradlew :addOns:restextraction:spotlessApply
./gradlew :addOns:restextraction:spotlessCheck :addOns:restextraction:test \
  :addOns:restextraction:jacocoTestReport :addOns:restextraction:jarZapAddOn
```

Archive: `addOns/restextraction/build/zapAddOn/bin/restextraction-alpha-0.0.1.zap`.
Reports: `addOns/restextraction/build/reports/tests/test/` and `build/reports/jacoco/test/`
inside the add-on directory. Test-plan IDs and implementation notes are under `design/us11/`.
The full upstream multi-project scaffolding is retained to avoid an unrelated build migration;
prototype application changes are limited to `addOns/restextraction/`.

Optional real-GUI acceptance (stop the prototype first):

```sh
./scripts/ui-smoke.sh
```

This opt-in test agent fills actual Swing controls, sends generated traffic to a temporary
loopback fixture, checks workflows and records screenshots/results under ignored `.local/ui-smoke/`.
The agent is not packaged in the extension; normal startup does not seed specs or APIs.
Core Help is not installed automatically: the packaged help page can also be read directly in
`addOns/restextraction/src/main/javahelp/.../contents/restextraction.html`.
