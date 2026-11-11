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
package org.zaproxy.addon.restextraction.ui;

import java.awt.Color;
import org.parosproxy.paros.Constant;
import org.zaproxy.addon.restextraction.model.SpecValidationException;
import org.zaproxy.zap.utils.DisplayUtils;

/** Shared translated labels and theme-aware, symbol-backed semantic colours. */
final class VerificationText {
    private VerificationText() {}

    static String text(String key, Object... args) {
        return Constant.messages.getString("restextraction.verification." + key, args);
    }

    static String validation(IllegalArgumentException error) {
        if (error instanceof SpecValidationException e)
            return Constant.messages.getString(e.getKey(), e.getArguments().toArray());
        return text("refused", error.getMessage());
    }

    static Color colour(char mark) {
        boolean dark = DisplayUtils.isDarkLookAndFeel();
        return switch (mark) {
            case '✓' -> new Color(dark ? 0x81C784 : 0x236B2A);
            case '✗' -> new Color(dark ? 0xFF8080 : 0xB71C1C);
            default -> new Color(dark ? 0xFFD166 : 0x8A6100);
        };
    }

    static String hex(char mark) {
        return String.format("#%06x", colour(mark).getRGB() & 0xffffff);
    }
}
