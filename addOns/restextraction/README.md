# REST Extraction and Verification

Observes REST traffic through ZAP and checks it against a session-only, developer-defined
specification. It never blocks or changes traffic, validates responses or infers a specification.

## Use

1. Open **REST Extraction** from the status-tab green **+** if hidden.
2. Add an API base URL and endpoint pattern such as `/posts/[POST_ID]/comments`; click **Start**.
3. Select the endpoint and use **Specification** to add methods/content types/typed parameters.
4. Send authorized traffic through the proxy. Inspect the Check column, invalid-only filter,
   request/check/response details and compliance documentation.
5. Edit the spec to rejudge old calls. Clear calls keeps it; removing an endpoint removes it.
6. Select a namespace for **Unmatched paths**, which has its own counts and chip-based pattern builder.

Types: String, Integer, Decimal and Base64. Numbers support optional inclusive ranges and exact
comparisons. JSON field selectors support nested objects/arrays/maps; Required is not allowed
for wildcards or path variables. Repeated values are checked individually. Empty specs and CORS
preflights are neutral; warnings do not invalidate a request. Headers are shown in full.

## Local prototype

Use the [repository README](../../README.md) and [demo walkthrough](../../docs/demo-walkthrough.md).
Build with JDK 17:

```sh
./gradlew :addOns:restextraction:spotlessCheck :addOns:restextraction:test \
  :addOns:restextraction:jacocoTestReport :addOns:restextraction:jarZapAddOn
./scripts/run-prototype.sh
```

Do not use upstream copy/deploy/install tasks for this prototype: its launcher stages an isolated
installation, profile and mandatory add-ons. The existing development installation is not changed.
The complete design and acceptance inventory are under `design/us11/`; actual outcomes and gaps
are in `docs/implementation-status.md`. Normal startup never seeds APIs/specs. No remote exists.
