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
import org.parosproxy.paros.extension.ExtensionAdaptor;
import org.parosproxy.paros.extension.ExtensionHook;
import org.zaproxy.addon.restextraction.model.RestExtractor;
import org.zaproxy.addon.restextraction.ui.RestExtractionPanel;

/**
 * The entry point of the add-on for ZAP. It only wires the parts together: one {@link
 * RestExtractor}, the proxy listener that feeds it and, when ZAP has a GUI, the panel that shows
 * it.
 */
public class ExtensionRestExtraction extends ExtensionAdaptor {

    // Copied from https://www.zaproxy.org/docs/developer/creating-new-addon-in-zap-extensions/
    // The name is public so that other extensions can access it
    public static final String NAME = "RestExtraction";

    // The i18n prefix, by default the package name - defined in one place to make it easier
    // to copy and change this example
    protected static final String PREFIX = "restextraction";

    private static final Logger LOGGER = LogManager.getLogger(ExtensionRestExtraction.class);

    private RestExtractor extractor;
    private RestTrafficListener trafficListener;
    private RestExtractionPanel panel;

    public ExtensionRestExtraction() {
        super(NAME);
        setI18nPrefix(PREFIX);
    }

    @Override
    public String getUIName() {
        return Constant.messages.getString("restextraction.name");
    }

    @Override
    public String getDescription() {
        return Constant.messages.getString("restextraction.desc");
    }

    /** ZAP removes the hooked listener and panel itself, so the add-on can be unloaded. */
    @Override
    public boolean canUnload() {
        return true;
    }

    /**
     * Registers the listener, also without a GUI, and the panel only when there is one. Without a
     * GUI traffic passes and nothing is recorded, because namespaces are added in the panel.
     */
    @Override
    public void hook(ExtensionHook extensionHook) {
        super.hook(extensionHook);

        this.extractor = new RestExtractor();

        this.trafficListener = new RestTrafficListener(extractor);
        extensionHook.addProxyListener(trafficListener);

        if (hasView()) {
            this.panel = new RestExtractionPanel(extractor);
            extensionHook.getHookView().addStatusPanel(panel);
        }

        LOGGER.debug("REST Extraction add-on loaded.");
    }
}
