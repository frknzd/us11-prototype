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

/** Shared bounded ASCII scanner for text and JSON numeric grammars. */
final class NumericGrammar {
    private NumericGrammar() {}

    static boolean valid(String s, boolean decimal, boolean json) {
        if (s.isEmpty() || s.length() > 10000) return false;
        int i = 0;
        if (s.charAt(i) == '-' || !json && s.charAt(i) == '+') i++;
        int start = i;
        while (i < s.length() && digit(s.charAt(i))) i++;
        if (i == start || json && i - start > 1 && s.charAt(start) == '0') return false;
        if (decimal && i < s.length() && s.charAt(i) == '.') {
            start = ++i;
            while (i < s.length() && digit(s.charAt(i))) i++;
            if (start == i) return false;
        }
        if (decimal && i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
            i++;
            if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) i++;
            start = i;
            int exponent = 0;
            while (i < s.length() && digit(s.charAt(i))) {
                exponent = exponent * 10 + s.charAt(i++) - '0';
                if (exponent > 9999) return false;
            }
            if (start == i) return false;
        }
        return i == s.length();
    }

    private static boolean digit(char c) {
        return c >= '0' && c <= '9';
    }
}
