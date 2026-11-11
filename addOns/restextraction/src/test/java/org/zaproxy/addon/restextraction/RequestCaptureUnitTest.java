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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.parosproxy.paros.network.HttpMessage;
import org.zaproxy.addon.restextraction.model.ApiCall;
import org.zaproxy.addon.restextraction.model.BodyState;
import org.zaproxy.addon.restextraction.model.RestExtractor;
import org.zaproxy.zap.testutils.TestUtils;

class RequestCaptureUnitTest extends TestUtils {
    @BeforeEach
    void messages() {
        mockMessages(new ExtensionRestExtraction());
    }

    private ApiCall capture(HttpMessage msg) {
        RestExtractor extractor = mock(RestExtractor.class);
        RestTrafficListener listener = new RestTrafficListener(extractor);
        assertTrue(listener.onHttpRequestSend(msg));
        assertTrue(listener.onHttpResponseReceive(msg));
        ArgumentCaptor<ApiCall> call = ArgumentCaptor.forClass(ApiCall.class);
        verify(extractor).record(call.capture());
        return call.getValue();
    }

    private HttpMessage message(String contentType, String body) throws Exception {
        HttpMessage msg = new HttpMessage();
        msg.setRequestHeader(
                "POST http://127.0.0.1:8092/posts/1?q=a%2Bb HTTP/1.1\r\nHost: 127.0.0.1:8092\r\n"
                        + (contentType.isEmpty() ? "" : "Content-Type: " + contentType + "\r\n")
                        + "X-Id: 1\r\nx-id: 2\r\n\r\n");
        msg.setRequestBody(body);
        msg.setResponseHeader("HTTP/1.1 200 OK\r\n\r\n");
        return msg;
    }

    @Test
    void TL1_TL2_TL7_captureInOrderAndNeverMutate() throws Exception {
        HttpMessage msg = message("application/json; charset=UTF-8", "{\"title\":\"Hi\"}");
        String header = msg.getRequestHeader().toString();
        byte[] bytes = msg.getRequestBody().getBytes().clone();
        ApiCall call = capture(msg);
        assertEquals("q=a%2Bb", call.getRequest().getQuery());
        assertEquals(
                List.of("Host", "Content-Type", "X-Id", "x-id"),
                call.getRequest().getHeaders().stream().map(h -> h.getName()).toList());
        assertEquals("{\"title\":\"Hi\"}", call.getRequest().getBody());
        assertEquals(BodyState.TEXT, call.getRequest().getBodyState());
        assertEquals(header, msg.getRequestHeader().toString());
        org.junit.jupiter.api.Assertions.assertArrayEquals(bytes, msg.getRequestBody().getBytes());
    }

    @Test
    void TL3_cutLargeText() throws Exception {
        var r = capture(message("application/json", "x".repeat(65537))).getRequest();
        assertEquals(BodyState.CUT, r.getBodyState());
        assertTrue(r.getBody().startsWith("x".repeat(65536)));
    }

    @Test
    void TL4_binary() throws Exception {
        assertEquals(
                BodyState.BINARY,
                capture(message("image/png", "data")).getRequest().getBodyState());
    }

    @Test
    void TL5_empty() throws Exception {
        assertEquals(
                BodyState.NONE,
                capture(message("application/json", "")).getRequest().getBodyState());
    }

    @Test
    void TL6_charset() throws Exception {
        HttpMessage msg = message("text/plain; charset=ISO-8859-1", "");
        msg.setRequestBody("é".getBytes(StandardCharsets.ISO_8859_1));
        assertEquals("é", capture(msg).getRequest().getBody());
    }
}
