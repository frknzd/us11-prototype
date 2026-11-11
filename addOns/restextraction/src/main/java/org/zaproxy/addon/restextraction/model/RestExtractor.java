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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The one door into the domain. The panel and the traffic listener only talk to this class, and it
 * only passes their requests on to the namespaces.
 */
public class RestExtractor {
    private static final Logger LOGGER = LogManager.getLogger(RestExtractor.class);

    // Read on the proxy threads for every call, changed from the Swing thread now and then.
    private final List<ApiNamespace> namespaces = new CopyOnWriteArrayList<>();
    private final List<ExtractionObserver> observers = new CopyOnWriteArrayList<>();

    /**
     * Adds an API. Its extraction still has to be started.
     *
     * @throws IllegalArgumentException if the base URL is not valid or was added before
     */
    public synchronized ApiNamespace addNamespace(String baseUrl) {
        ApiNamespace namespace = new ApiNamespace(baseUrl);
        for (ApiNamespace existing : namespaces) {
            if (existing.getBaseUrl().equals(namespace.getBaseUrl())) {
                throw new IllegalArgumentException(
                        "The API '" + namespace.getBaseUrl() + "' was already added");
            }
        }
        namespaces.add(namespace);
        return namespace;
    }

    /**
     * @throws IllegalArgumentException if the template is not valid or was added before
     */
    public Endpoint addEndpoint(ApiNamespace namespace, String template) {
        Endpoint endpoint = namespace.addEndpoint(template);
        notifyObservers(namespace);
        return endpoint;
    }

    /**
     * Removes the namespace with its endpoints and calls. It is stopped too, so it records nothing
     * any more, and its base URL can be added again. Removing it twice does nothing.
     */
    public synchronized void removeNamespace(ApiNamespace namespace) {
        namespaces.remove(namespace);
        namespace.stop();
        namespace.getUnmatchedPaths().clear();
    }

    /** Removes the endpoint with this template from the namespace, with its recorded calls. */
    public void removeEndpoint(ApiNamespace namespace, String template) {
        namespace.removeEndpoint(template);
    }

    /** Forgets the recorded calls of the namespace, its endpoints and running state stay. */
    public void clear(ApiNamespace namespace) {
        namespace.clear();
    }

    /** Forgets the recorded calls of one endpoint of the namespace, the endpoint stays. */
    public void clearEndpoint(ApiNamespace namespace, String template) {
        namespace.clearEndpoint(template);
    }

    /** From now on calls to this namespace are recorded. */
    public void startExtraction(ApiNamespace namespace) {
        namespace.start();
    }

    /** Calls to this namespace are no longer recorded. What was recorded stays. */
    public void stopExtraction(ApiNamespace namespace) {
        namespace.stop();
    }

    public void addSpecItem(ApiNamespace namespace, String template, SpecItem item) {
        namespace.addSpecItem(template, item);
        notifyObservers(namespace);
    }

    public void removeSpecItem(ApiNamespace namespace, String template, SpecItem item) {
        namespace.removeSpecItem(template, item);
        notifyObservers(namespace);
    }

    /** One edit and one notification; a report can never see an intermediate removal. */
    public void replaceSpecItem(
            ApiNamespace namespace, String template, SpecItem old, SpecItem replacement) {
        namespace.replaceSpecItem(template, old, replacement);
        notifyObservers(namespace);
    }

    public Map<UrlPath, Integer> getUnmatchedPaths(ApiNamespace namespace) {
        return namespace.getUnmatchedPaths().counts();
    }

    public int getUnmatchedOverflow(ApiNamespace namespace) {
        return namespace.getUnmatchedPaths().getOverflow();
    }

    public void clearUnmatchedPaths(ApiNamespace namespace) {
        namespace.getUnmatchedPaths().clear();
        notifyObservers(namespace);
    }

    /** Gives the call to every namespace and tells the observers which ones recorded it. */
    public void record(ApiCall call) {
        for (ApiNamespace namespace : namespaces) {
            long unmatchedBefore = namespace.getUnmatchedPaths().version();
            boolean recorded = namespace.record(call);
            if (recorded || unmatchedBefore != namespace.getUnmatchedPaths().version())
                notifyObservers(namespace);
        }
    }

    /** Gives a snapshot of what the namespace recorded so far, for the presentation layer. */
    public ApiReport getReport(ApiNamespace namespace) {
        return namespace.createReport();
    }

    /** The observer is told on a proxy thread every time a namespace records a call. */
    public void addObserver(ExtractionObserver observer) {
        observers.add(observer);
    }

    private void notifyObservers(ApiNamespace namespace) {
        for (ExtractionObserver observer : observers) {
            try {
                observer.callRecorded(namespace);
            } catch (RuntimeException e) {
                // One broken observer must not stop the others or lose the recorded call.
                LOGGER.warn("An extraction observer failed.", e);
            }
        }
    }
}
