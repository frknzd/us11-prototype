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

import java.util.Optional;

public final class Base64Type extends ParameterType {
    @Override
    public Optional<Violation> check(Argument a) {
        if (a.isMalformedEncoding()) return invalid(a, "encoding");
        if (a.getJsonKind() != null && a.getJsonKind() != JsonKind.STRING)
            return invalid(a, "json", a.getJsonKind().name());
        String s = a.getValue();
        boolean standard = false, url = false;
        int padding = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '=') {
                padding++;
                continue;
            }
            if (padding != 0) return invalid(a, "grammar");
            if (c == '+' || c == '/') standard = true;
            else if (c == '-' || c == '_') url = true;
            else if (!(c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9'))
                return invalid(a, "grammar");
        }
        boolean bad =
                s.isEmpty()
                        || standard && url
                        || padding > 2
                        || (padding == 0 ? s.length() % 4 == 1 : s.length() % 4 != 0);
        return bad ? invalid(a, "grammar") : Optional.empty();
    }

    @Override
    public String getName() {
        return "Base64";
    }
}
