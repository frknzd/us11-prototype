# Rest Extractor

Records the calls to a REST API that go through the ZAP proxy and shows them as documentation: per
endpoint the HTTP methods used, how often each was called, the values in the URL and the status
code, headers and body of every response.

## How to add to ZAP

The `zap-extensions` and `zaproxy` repositories have to sit next to each other. Build with JDK 17.

```bash
./gradlew :addOns:copyMandatoryAddOns :addOns:restextraction:copyZapAddOn
cd ../zaproxy
./gradlew :zap:run
```

The first run takes a few minutes, it compiles ZAP.

## Using the panel

1. Open ZAP and select the **REST Extraction** tab next to `History`, `Search`, `Alerts` and
   `Output`. If it is not there, click the green **+** next to those tabs and select it.
2. Type the base URL of the API, for example `https://jsonplaceholder.typicode.com`, and click
   **Add API**.
3. Select the API in the tree, type an endpoint pattern relative to that base URL, for example
   `/posts/[POST_ID]/comments`, and click **Add endpoint**. `[NAME]` stands for one whole part of
   the path.
4. Click **Start**.
5. Send requests through the ZAP proxy. The tree shows how many calls each API and endpoint
   recorded. On the right, the **Calls** tab lists every call with its method, status code and
   arguments; click a column header to sort by it. Above the table, choose a method or type a text
   like `POST_ID = 1` to see only the matching calls, with their number (`3 of 12 calls`). The
   response headers of the selected call are below the table. The **Documentation** tab shows the same calls as readable documentation.
   Select an endpoint to see only its calls. Tick **Full body** to also see the response body of the selected call, with JSON
   indented. The documentation leaves the bodies out.

The help page of the add-on (ZAP User Guide, under **Add Ons**, **REST Extraction**) has all the
rules for base URLs and patterns.

To fix a typo, select the API or endpoint and click **Remove** (or press Delete, or right-click
it). **Clear calls** forgets what the selected API or endpoint recorded and keeps the endpoints.

## Trying it without a browser

With the API added and started as above, send a request through ZAP (default proxy port 8080):

```bash
curl -k -x http://localhost:8080 https://jsonplaceholder.typicode.com/posts/1/comments
```

The report then shows `GET: 1` and `POST_ID = 1` under `/posts/[POST_ID]/comments`.

## Development

```bash
./gradlew :addOns:restextraction:spotlessApply      # format, run before every commit
./gradlew :addOns:restextraction:test               # unit and scenario tests
./gradlew :addOns:restextraction:jacocoTestReport   # coverage, in build/reports/jacoco
```

The code has three packages:

- `org.zaproxy.addon.restextraction`: the extension and the proxy listener, the only classes that
  know ZAP's message API.
- `org.zaproxy.addon.restextraction.model`: the domain, without ZAP or Swing. `RestExtractor` is the
  one door into it.
- `org.zaproxy.addon.restextraction.ui`: the panel and the writer of the report.

Test classes that end in `ScenarioTest` replay a whole session from the listener to the report, the
other tests are unit tests.
