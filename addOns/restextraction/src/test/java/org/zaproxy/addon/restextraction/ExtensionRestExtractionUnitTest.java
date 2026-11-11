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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.swing.SwingUtilities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.parosproxy.paros.core.proxy.ProxyListener;
import org.parosproxy.paros.extension.ExtensionHook;
import org.parosproxy.paros.extension.ExtensionHookView;
import org.parosproxy.paros.extension.SessionChangedListener;
import org.parosproxy.paros.network.HttpMessage;
import org.zaproxy.addon.restextraction.ui.RestExtractionPanel;
import org.zaproxy.zap.testutils.TestUtils;

/** Unit test for {@link ExtensionRestExtraction}. */
class ExtensionRestExtractionUnitTest extends TestUtils {

    @BeforeEach
    void setUpExtensionMessages() {
        mockMessages(new ExtensionRestExtraction());
    }

    @Test
    void shouldHaveLocalizedMetadata() {
        // Given
        ExtensionRestExtraction extension = new ExtensionRestExtraction();
        // When / Then
        assertThat(extension.getUIName(), is("REST Extraction"));
        assertThat(
                extension.getDescription(),
                is("Observe REST API traffic and report extracted results."));
    }

    @Test
    void shouldBeUnloadable() {
        // Given / When / Then
        assertThat(new ExtensionRestExtraction().canUnload(), is(true));
    }

    // EX3: without a GUI the add-on only lets traffic pass

    @Test
    void shouldRegisterListenerWithoutView() {
        // Given
        ExtensionHook hook = mock(ExtensionHook.class);

        // When
        extensionWithView(false).hook(hook);

        // Then
        verify(hook).addProxyListener(any(RestTrafficListener.class));
        verify(hook, never()).getHookView();
    }

    @Test
    void shouldLetTrafficPassWithoutView() throws Exception {
        // Given
        ExtensionHook hook = mock(ExtensionHook.class);
        // When
        extensionWithView(false).hook(hook);
        // Then
        ArgumentCaptor<ProxyListener> hooked = ArgumentCaptor.forClass(ProxyListener.class);
        verify(hook).addProxyListener(hooked.capture());
        HttpMessage message = new HttpMessage();
        // When
        message.setRequestHeader(
                "GET https://jsonplaceholder.typicode.com/posts/1/comments HTTP/1.1\r\n\r\n");
        message.setResponseHeader("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n");
        message.setResponseBody("[]");

        // Then
        assertThat(hooked.getValue().onHttpRequestSend(message), is(true));
        assertThat(hooked.getValue().onHttpResponseReceive(message), is(true));
    }

    // EX6: a new ZAP session keeps the data (Q13)

    @Test
    void shouldNotRegisterSessionChangeListener() throws Exception {
        // Given
        ExtensionHook withoutView = mock(ExtensionHook.class);
        ExtensionHook withView = mock(ExtensionHook.class);
        when(withView.getHookView()).thenReturn(mock(ExtensionHookView.class));

        // When
        extensionWithView(false).hook(withoutView);
        SwingUtilities.invokeAndWait(() -> extensionWithView(true).hook(withView));

        // Then
        // Nothing listens for a new session, so a new session can't clear what was recorded.
        verify(withoutView, never()).addSessionListener(any(SessionChangedListener.class));
        verify(withView, never()).addSessionListener(any(SessionChangedListener.class));
    }

    @Test
    void shouldRegisterListenerAndPanelWithView() throws Exception {
        // Given
        ExtensionHook hook = mock(ExtensionHook.class);
        ExtensionHookView hookView = mock(ExtensionHookView.class);
        when(hook.getHookView()).thenReturn(hookView);
        ExtensionRestExtraction extension = extensionWithView(true);

        // When
        SwingUtilities.invokeAndWait(() -> extension.hook(hook));

        // Then
        verify(hook).addProxyListener(any(RestTrafficListener.class));
        verify(hookView).addStatusPanel(any(RestExtractionPanel.class));
    }

    private static ExtensionRestExtraction extensionWithView(boolean viewAvailable) {
        return new ExtensionRestExtraction() {
            @Override
            protected boolean hasView() {
                return viewAvailable;
            }
        };
    }
}
