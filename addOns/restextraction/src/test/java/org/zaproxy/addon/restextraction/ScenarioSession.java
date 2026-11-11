/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2026 The ZAP Development Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.zaproxy.addon.restextraction;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.parosproxy.paros.network.HttpMalformedHeaderException;
import org.parosproxy.paros.network.HttpMessage;
import org.zaproxy.addon.restextraction.model.ApiCall;
import org.zaproxy.addon.restextraction.model.ApiNamespace;
import org.zaproxy.addon.restextraction.model.EndpointReport;
import org.zaproxy.addon.restextraction.model.Header;
import org.zaproxy.addon.restextraction.model.RecordedCall;
import org.zaproxy.addon.restextraction.model.RestExtractor;
import org.zaproxy.addon.restextraction.model.SpecItem;
import org.zaproxy.addon.restextraction.model.UrlPath;
import org.zaproxy.addon.restextraction.model.Verdict;
import org.zaproxy.addon.restextraction.ui.ApiDocumentationWriter;

/**
 * One session for the scenario tests: a real extractor, listener and writer, fed with hand-made
 * messages through the listener like ZAP's proxy does. No network and no GUI.
 */
final class ScenarioSession {

    static final String JSON = "application/json; charset=utf-8";

    private final RestExtractor extractor = new RestExtractor();
    private final RestTrafficListener listener = new RestTrafficListener(extractor);
    private final ApiDocumentationWriter writer = new ApiDocumentationWriter();

    /** Adds a namespace with these endpoint patterns. It still has to be started. */
    ApiNamespace namespace(String baseUrl, String... templates) {
        ApiNamespace namespace = extractor.addNamespace(baseUrl);
        for (String template : templates) {
            extractor.addEndpoint(namespace, template);
        }
        return namespace;
    }

    /** Adds a namespace with these endpoint patterns and starts it. */
    ApiNamespace startedNamespace(String baseUrl, String... templates) {
        ApiNamespace namespace = namespace(baseUrl, templates);
        start(namespace);
        return namespace;
    }

    void start(ApiNamespace namespace) {
        extractor.startExtraction(namespace);
    }

    void stop(ApiNamespace namespace) {
        extractor.stopExtraction(namespace);
    }

    /** A JSON response to the request goes through the listener. Its body names the request. */
    void send(String method, String url, int status) {
        send(method, url, status, JSON, bodyFor(method, url));
    }

    void send(String method, String url, int status, String contentType, String body) {
        send(listener, method, url, status, contentType, body);
    }

    void send(
            String method,
            String url,
            int status,
            List<Header> requestHeaders,
            String requestContentType,
            String requestBody) {
        HttpMessage message = message(method, url, status, JSON, "{}");
        for (Header header : requestHeaders)
            message.getRequestHeader().addHeader(header.getName(), header.getValue());
        if (!requestContentType.isEmpty())
            message.getRequestHeader().setHeader("Content-Type", requestContentType);
        message.setRequestBody(requestBody);
        assertThat(listener.onHttpRequestSend(message), is(true));
        assertThat(listener.onHttpResponseReceive(message), is(true));
    }

    void spec(ApiNamespace namespace, String template, SpecItem item) {
        extractor.addSpecItem(namespace, template, item);
    }

    void addEndpoint(ApiNamespace namespace, String template) {
        extractor.addEndpoint(namespace, template);
    }

    Map<UrlPath, Integer> unmatched(ApiNamespace namespace) {
        return extractor.getUnmatchedPaths(namespace);
    }

    Verdict verdict(ApiNamespace namespace, int endpoint, int call) {
        EndpointReport report = endpoint(namespace, endpoint);
        return report.getVerdict(report.getCalls().get(call));
    }

    /** Sends the response through the given listener, which must let the traffic pass. */
    static void send(
            RestTrafficListener listener,
            String method,
            String url,
            int status,
            String contentType,
            String body) {
        assertThat(
                listener.onHttpResponseReceive(message(method, url, status, contentType, body)),
                is(true));
    }

    static String bodyFor(String method, String url) {
        return "{\"request\":\"" + method + " " + url + "\"}";
    }

    /** The report of the endpoint that was added as number {@code index} (from 0). */
    EndpointReport endpoint(ApiNamespace namespace, int index) {
        return extractor.getReport(namespace).getEndpoints().get(index);
    }

    String html(ApiNamespace namespace) {
        return writer.write(extractor.getReport(namespace));
    }

    /** The body of one call as the detail view shows it with Full body ticked. */
    String shownBody(ApiNamespace namespace, int endpoint, int call) {
        ApiCall recorded = endpoint(namespace, endpoint).getCalls().get(call).getCall();
        return writer.formatBody(recorded.getResponseHeaders(), recorded.getResponseBody());
    }

    /** The value of one variable for every call of the endpoint, in arrival order. */
    static List<String> argumentsOf(EndpointReport endpoint, String name) {
        List<String> values = new ArrayList<>();
        for (RecordedCall call : endpoint.getCalls()) {
            values.add(call.getPathArguments().get(name));
        }
        return values;
    }

    static List<Integer> statusCodesOf(EndpointReport endpoint) {
        List<Integer> codes = new ArrayList<>();
        for (RecordedCall call : endpoint.getCalls()) {
            codes.add(call.getCall().getStatusCode());
        }
        return codes;
    }

    static List<String> urlsOf(EndpointReport endpoint) {
        List<String> urls = new ArrayList<>();
        for (RecordedCall call : endpoint.getCalls()) {
            urls.add(call.getCall().getUrl());
        }
        return urls;
    }

    static HttpMessage message(
            String method, String url, int status, String contentType, String body) {
        try {
            HttpMessage message = new HttpMessage();
            message.setRequestHeader(method + " " + url + " HTTP/1.1\r\n\r\n");
            message.setResponseHeader(
                    "HTTP/1.1 " + status + " Status\r\nContent-Type: " + contentType + "\r\n\r\n");
            message.setResponseBody(body);
            return message;
        } catch (HttpMalformedHeaderException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
