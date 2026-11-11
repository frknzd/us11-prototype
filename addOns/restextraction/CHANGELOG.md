# Changelog
All notable changes to this add-on will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## Unreleased

### Added
- US11 private prototype: immutable operation specifications, strict request parsing and exact
  String/Integer/Decimal/Base64 checks, ranges, required fields and nested JSON selectors.
- Specification editor, report-time rejudging, Check column, symbolic diagnostics, compliance
  counts, invalid-only filters and request information in details/documentation.
- Bounded unmatched-path counts with a separate panel and literal/variable chip pattern builder.
- Isolated local runtime, loopback demonstration and opt-in real ZAP GUI smoke tooling.
- First version of the add-on. It records the calls to a REST API that go through the ZAP proxy and
  shows them as documentation.
- API namespaces with a base URL (scheme, host, optional port and base path), each started and
  stopped on its own.
- Endpoint patterns relative to the namespace, with `[NAME]` for a variable part of the path, like
  `/posts/[POST_ID]/comments`.
- A report per namespace: per endpoint the HTTP methods used with their counts, and per call the
  values of the variables, the status code and the response headers.
- A REST Extraction tab to add namespaces and patterns, start and stop, and read the report, which
  updates while calls come in. A "Full body" checkbox shows the response body of the selected
  call.
- Messages that say why a base URL or a pattern is refused.
- Help page.

### Security
- Every value from traffic is escaped in the report.
- Response bodies above 64 KB are cut and binary bodies are not shown.
- The proxy listener never blocks or changes traffic, also not when recording fails.
