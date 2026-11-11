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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.parosproxy.paros.Constant;
import org.parosproxy.paros.core.proxy.ProxyListener;
import org.parosproxy.paros.network.HttpHeader;
import org.parosproxy.paros.network.HttpMessage;
import org.parosproxy.paros.network.HttpResponseHeader;
import org.zaproxy.addon.restextraction.model.ApiCall;
import org.zaproxy.addon.restextraction.model.HttpMethod;
import org.zaproxy.addon.restextraction.model.RestExtractor;

/**
 * Where the add-on plugs into the ZAP proxy. It turns every response into an {@link ApiCall} for
 * the extractor and is the only class that knows ZAP's message API.
 *
 * <p>ZAP waits for these callbacks before it forwards the traffic and calls them on several threads
 * at once, so they only observe: they always return true and never let an exception out.
 */
public class RestTrafficListener implements ProxyListener {
    private static final Logger LOGGER = LogManager.getLogger(RestTrafficListener.class);

    // Bodies above this are cut, so one large download can't fill the memory.
    private static final int MAX_BODY_LENGTH = 64 * 1024;

    private final RestExtractor extractor;

    public RestTrafficListener(RestExtractor extractor) {
        this.extractor = extractor;
    }

    /** Requests are not recorded, a call only counts once it got a response. */
    @Override
    public boolean onHttpRequestSend(HttpMessage msg) {
        // we do not block requests being sent
        return true;
    }

    /** Gives the response to the extractor. Always returns true, also when that fails. */
    @Override
    public boolean onHttpResponseReceive(HttpMessage msg) {
        try {
            extractor.record(toApiCall(msg));
        } catch (RuntimeException e) {
            // A failure of ours must never block or break the traffic of the user.
            LOGGER.warn("Could not record a response.", e);
        }
        return true;
    }

    @Override
    public int getArrangeableListenerOrder() {
        return 0; // means we will get it first
    }

    private ApiCall toApiCall(HttpMessage msg) {
        HttpMethod method = HttpMethod.fromString(msg.getRequestHeader().getMethod());
        String url = msg.getRequestHeader().getURI().toString();
        int status = msg.getResponseHeader().getStatusCode();
        String responseHeaders = msg.getResponseHeader().toString();

        return new ApiCall(method, url, status, responseHeaders, bodyOf(msg));
    }

    /** The response body as text, cut when it is large and replaced by a mark when it is binary. */
    private static String bodyOf(HttpMessage msg) {
        if (msg.getResponseBody().length() == 0) {
            return "";
        }
        if (isBinary(msg.getResponseHeader())) {
            return Constant.messages.getString("restextraction.body.binary");
        }
        String body = msg.getResponseBody().toString();
        if (body.length() <= MAX_BODY_LENGTH) {
            return body;
        }
        return body.substring(0, MAX_BODY_LENGTH)
                + "\n"
                + Constant.messages.getString("restextraction.body.cut");
    }

    /**
     * Only a Content-Type that names a type that is not text makes a body binary. Without a
     * Content-Type the body is kept: showing too much is better than hiding the answer of an API.
     */
    private static boolean isBinary(HttpResponseHeader header) {
        return header.getHeader(HttpHeader.CONTENT_TYPE) != null && !header.isText();
    }
}
