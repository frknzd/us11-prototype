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

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Pure request parsing, bounded storage and linear scanning; no regular expressions. */
public final class RequestParser {
    private RequestParser() {}

    public static ParsedRequest parse(RequestData request, Map<String, String> pathArguments) {
        Collector out = new Collector();
        pathArguments.forEach(
                (name, value) ->
                        out.add(new Argument(name, ArgumentLocation.PATH, value, null, false)));
        parsePairs(request.getQuery(), ArgumentLocation.QUERY, out);
        String type = MediaType.normalise(request.getContentType());
        boolean checked = !request.hasBody();
        String malformed = "";
        List<Warning> warnings = new ArrayList<>();
        if (request.hasBody() && request.getBodyState() == BodyState.TEXT) {
            if (MediaType.isJson(type)) {
                JsonReader reader = new JsonReader(request.getBody(), 10);
                try {
                    reader.read().forEach(out::add);
                    if (reader.isTooDeep())
                        warnings.add(
                                new Warning(
                                        WarningKind.TOO_DEEP,
                                        ArgumentLocation.BODY,
                                        "",
                                        List.of("10")));
                } catch (IllegalArgumentException e) {
                    malformed = e.getMessage();
                }
                checked = true;
            } else if (MediaType.isForm(type)) {
                parsePairs(request.getBody(), ArgumentLocation.BODY, out);
                checked = true;
            }
        }
        for (Header header : request.getHeaders()) {
            out.add(
                    new Argument(
                            header.getName(),
                            ArgumentLocation.HEADER,
                            header.getValue(),
                            null,
                            false));
        }
        if (out.overflow)
            warnings.add(new Warning(WarningKind.TOO_MANY_ARGUMENTS, null, "", List.of("200")));
        return new ParsedRequest(out.values, warnings, malformed, type, checked);
    }

    private static void parsePairs(String text, ArgumentLocation location, Collector out) {
        int start = 0;
        while (start < text.length()) {
            int end = text.indexOf('&', start);
            if (end < 0) end = text.length();
            if (end > start) {
                if (out.values.size() == 200) {
                    out.overflow = true;
                    return;
                }
                int equal = text.indexOf('=', start);
                if (equal < 0 || equal > end) equal = end;
                Decoded name = decode(text.substring(start, equal));
                Decoded value = decode(equal == end ? "" : text.substring(equal + 1, end));
                out.add(
                        new Argument(
                                name.text,
                                location,
                                value.text,
                                null,
                                name.malformed || value.malformed));
            }
            start = end + 1;
        }
    }

    private static Decoded decode(String text) {
        // A syntactically broken escape preserves this entire raw component.
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != '%') continue;
            if (i + 2 >= text.length()
                    || JsonReader.hex(text.charAt(i + 1)) < 0
                    || JsonReader.hex(text.charAt(i + 2)) < 0) return new Decoded(text, true);
            i += 2;
        }
        StringBuilder result = new StringBuilder();
        boolean malformed = false;
        for (int i = 0; i < text.length(); ) {
            char c = text.charAt(i++);
            if (c == '+') result.append(' ');
            else if (c != '%') result.append(c);
            else {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                do {
                    bytes.write(
                            JsonReader.hex(text.charAt(i)) * 16
                                    + JsonReader.hex(text.charAt(i + 1)));
                    i += 2;
                    if (i >= text.length() || text.charAt(i) != '%') break;
                    i++;
                } while (true);
                byte[] raw = bytes.toByteArray();
                try {
                    result.append(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw)));
                } catch (CharacterCodingException e) {
                    result.append(new String(raw, StandardCharsets.UTF_8));
                    malformed = true;
                }
            }
        }
        return new Decoded(result.toString(), malformed);
    }

    private record Decoded(String text, boolean malformed) {}

    private static final class Collector {
        private final List<Argument> values = new ArrayList<>();
        private boolean overflow;

        private void add(Argument value) {
            if (values.size() < 200) values.add(value);
            else overflow = true;
        }
    }
}
