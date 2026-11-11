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
package org.zaproxy.addon.restextraction.model;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The base URL of one API, like {@code https://jsonplaceholder.typicode.com} or {@code
 * https://host/api/v1}, with its endpoints. It decides if a call belongs to this API and gives its
 * endpoints only the path after the base path.
 */
public class ApiNamespace {
    private final String baseUrl;
    private final String scheme;
    private final String host;
    private final int port;
    private final UrlPath basePath;
    // Read on the proxy threads for every call, changed from the Swing thread now and then.
    private final List<Endpoint> endpoints = new CopyOnWriteArrayList<>();
    private volatile boolean extracting;

    /**
     * @param baseUrl an absolute URL with a scheme and a host, optionally a port and a base path
     * @throws IllegalArgumentException if the base URL has no scheme or host, or has a query or
     *     fragment
     */
    public ApiNamespace(String baseUrl) {
        URI base = parse(Objects.requireNonNull(baseUrl, "baseUrl"));
        if (base.getScheme() == null || base.getHost() == null) {
            throw invalid(baseUrl, "it needs a scheme and a host, like https://api.example.com");
        }
        if (base.getRawQuery() != null || base.getRawFragment() != null) {
            throw invalid(baseUrl, "it can't have a query or fragment (? or #)");
        }
        this.scheme = base.getScheme().toLowerCase(Locale.ROOT);
        this.host = base.getHost().toLowerCase(Locale.ROOT);
        this.port = portOf(base);
        this.basePath = new UrlPath(rawPathOf(base));
        this.baseUrl = normalised(rawPathOf(base));
    }

    /** The base URL in one fixed form: lower-case scheme and host, no trailing slash. */
    public String getBaseUrl() {
        return baseUrl;
    }

    /** Tells if calls are being recorded, for the Start/Stop button of the panel. */
    public boolean isExtracting() {
        return extracting;
    }

    /**
     * Adds an endpoint to this API.
     *
     * @param template the pattern relative to this namespace, like {@code /posts/[POST_ID]}
     * @throws IllegalArgumentException if the template is not valid or was added before
     */
    public synchronized Endpoint addEndpoint(String template) {
        Endpoint endpoint = new Endpoint(template);
        for (Endpoint existing : endpoints) {
            // One trailing slash is dropped everywhere, so /posts and /posts/ are the same.
            if (withoutTrailingSlash(existing.getTemplate())
                    .equals(withoutTrailingSlash(template))) {
                throw new IllegalArgumentException(
                        "The endpoint '" + template + "' was already added to " + baseUrl);
            }
        }
        endpoints.add(endpoint);
        return endpoint;
    }

    /**
     * Removes the endpoint with this template, with its recorded calls. Does nothing when the
     * namespace has no such endpoint.
     */
    public synchronized void removeEndpoint(String template) {
        // The same rule as for a duplicate: /posts and /posts/ are the same endpoint.
        endpoints.removeIf(
                endpoint ->
                        withoutTrailingSlash(endpoint.getTemplate())
                                .equals(withoutTrailingSlash(template)));
    }

    public void addSpecItem(String template, SpecItem item) {
        endpoint(template).addSpecItem(item);
    }

    public void removeSpecItem(String template, SpecItem item) {
        endpoint(template).removeSpecItem(item);
    }

    public void replaceSpecItem(String template, SpecItem old, SpecItem replacement) {
        endpoint(template).replaceSpecItem(old, replacement);
    }

    private Endpoint endpoint(String template) {
        return endpoints.stream()
                .filter(
                        e ->
                                withoutTrailingSlash(e.getTemplate())
                                        .equals(withoutTrailingSlash(template)))
                .findFirst()
                .orElseThrow(() -> new SpecValidationException("endpoint"));
    }

    /** Starts extracting. Starting twice does nothing. */
    public void start() {
        extracting = true;
    }

    /** Stops extracting. Calls recorded so far are kept, so a later {@link #start()} resumes. */
    public void stop() {
        extracting = false;
    }

    /**
     * Gives the call to every endpoint if it belongs to this API. Never throws: a URL that can't be
     * read is simply not recorded.
     *
     * @return true if at least one endpoint recorded the call
     */
    public boolean record(ApiCall call) {
        if (!extracting) {
            return false;
        }
        UrlPath rest = pathAfterBasePath(call.getUrl());
        if (rest == null) {
            return false;
        }
        boolean recorded = false;
        for (Endpoint endpoint : endpoints) {
            // Every endpoint gets the call, also after a first one recorded it.
            if (endpoint.record(call, rest)) {
                recorded = true;
            }
        }
        return recorded;
    }

    /** Forgets the recorded calls of every endpoint. The endpoints and the running state stay. */
    public void clear() {
        for (Endpoint endpoint : endpoints) {
            endpoint.clear();
        }
    }

    /**
     * Forgets the recorded calls of the endpoint with this template and keeps the endpoint. Does
     * nothing when the namespace has no such endpoint.
     */
    public void clearEndpoint(String template) {
        for (Endpoint endpoint : endpoints) {
            if (withoutTrailingSlash(endpoint.getTemplate())
                    .equals(withoutTrailingSlash(template))) {
                endpoint.clear();
            }
        }
    }

    /** Gives the report of every endpoint, in the order the endpoints were added. */
    public ApiReport createReport() {
        List<EndpointReport> reports = new ArrayList<>();
        for (Endpoint endpoint : endpoints) {
            reports.add(endpoint.createReport());
        }
        return new ApiReport(baseUrl, reports);
    }

    /** The path of the URL after the base path, or null if the URL is not of this API. */
    private UrlPath pathAfterBasePath(String url) {
        try {
            URI uri = new URI(url);
            if (!sameOrigin(uri)) {
                return null;
            }
            UrlPath path = new UrlPath(rawPathOf(uri));
            return path.startsWith(basePath) ? path.after(basePath) : null;
        } catch (URISyntaxException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Compares the parsed parts and never the text: {@code https://api.com.evil.com} starts with
     * {@code https://api.com} but is another host.
     */
    private boolean sameOrigin(URI url) {
        return scheme.equalsIgnoreCase(url.getScheme())
                && host.equalsIgnoreCase(url.getHost())
                && port == portOf(url);
    }

    private String normalised(String rawPath) {
        String shownPort = port == defaultPort(scheme) ? "" : ":" + port;
        return scheme + "://" + host + shownPort + withoutTrailingSlash(rawPath);
    }

    private static URI parse(String baseUrl) {
        try {
            return new URI(baseUrl);
        } catch (URISyntaxException e) {
            throw invalid(baseUrl, "it is not a valid URL");
        }
    }

    private static int portOf(URI uri) {
        return uri.getPort() != -1 ? uri.getPort() : defaultPort(uri.getScheme());
    }

    private static int defaultPort(String scheme) {
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }

    private static String rawPathOf(URI uri) {
        return uri.getRawPath() == null ? "" : uri.getRawPath();
    }

    private static String withoutTrailingSlash(String path) {
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private static IllegalArgumentException invalid(String baseUrl, String reason) {
        return new IllegalArgumentException("Invalid base URL '" + baseUrl + "': " + reason);
    }
}
