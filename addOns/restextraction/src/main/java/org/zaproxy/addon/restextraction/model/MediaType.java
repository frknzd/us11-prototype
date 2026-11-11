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

import java.util.Locale;
import java.util.Objects;

public final class MediaType {
    private MediaType() {}

    public static String normalise(String text) {
        Objects.requireNonNull(text, "text");
        int end = text.indexOf(';');
        return (end < 0 ? text : text.substring(0, end)).trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isValid(String type) {
        int slash = type.indexOf('/');
        if (slash <= 0 || slash == type.length() - 1) return false;
        for (int i = 0; i < type.length(); i++) {
            char c = type.charAt(i);
            if (i == slash) continue;
            if (!(c >= 'a' && c <= 'z'
                    || c >= 'A' && c <= 'Z'
                    || c >= '0' && c <= '9'
                    || "!#$%&'*+-.^_`|~".indexOf(c) >= 0)) return false;
        }
        return true;
    }

    public static boolean isJson(String type) {
        String t = normalise(type);
        return t.equals("application/json")
                || t.equals("text/json")
                || t.startsWith("application/") && t.endsWith("+json") && isValid(t);
    }

    public static boolean isForm(String type) {
        return normalise(type).equals("application/x-www-form-urlencoded");
    }
}
