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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.parosproxy.paros.Constant;
import org.zaproxy.addon.restextraction.model.ApiCall;
import org.zaproxy.addon.restextraction.model.ApiReport;
import org.zaproxy.addon.restextraction.model.EndpointReport;
import org.zaproxy.addon.restextraction.model.HttpMethod;
import org.zaproxy.addon.restextraction.model.RecordedCall;

/**
 * Turns the report of one namespace into the HTML documentation that the panel shows. It only reads
 * the report, never the live domain objects.
 *
 * <p>Every value that comes from traffic or from the user goes through {@link #escape}: the report
 * is HTML, so a {@code <script>} in a response has to be shown as text. Long lines are cut, so the
 * documentation stays readable.
 *
 * <p>The documentation leaves the response bodies out: with many large bodies the page became too
 * slow to write on every redraw. {@link #formatBody} makes the body of one call readable instead.
 */
public class ApiDocumentationWriter {

    private static final String PREFIX = "restextraction.report.";
    // Swing's HTML view only breaks lines at spaces, so a longer line would widen the whole page.
    private static final int MAX_LINE_LENGTH = 100;
    private static final String INDENT = "  ";
    private static final Pattern JSON_WORD =
            Pattern.compile("-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?|true|false|null");

    /**
     * Writes the base URL once as the heading and one section per endpoint, in the order of the
     * report.
     */
    public String write(ApiReport report) {
        StringBuilder html = new StringBuilder("<html><body>");
        html.append("<h2>").append(escape(report.getBaseUrl())).append("</h2>");
        if (report.getEndpoints().isEmpty()) {
            html.append(paragraph(text("noendpoints")));
        }
        for (EndpointReport endpoint : report.getEndpoints()) {
            html.append(writeEndpoint(endpoint));
        }
        return html.append("</body></html>").toString();
    }

    /**
     * Gives the body of one call as plain text, indented when the headers say it is JSON. A body
     * that is not whole JSON, like one that was cut at 64 KB, is given back as it is.
     */
    public String formatBody(String responseHeaders, String body) {
        return isJson(responseHeaders) ? prettyJson(body) : body;
    }

    private String writeEndpoint(EndpointReport endpoint) {
        StringBuilder html = new StringBuilder();
        html.append("<h3>").append(escape(endpoint.getTemplate())).append("</h3>");
        if (endpoint.getCallCounts().isEmpty()) {
            return html.append(paragraph(text("nocalls"))).toString();
        }
        List<String> counts = new ArrayList<>();
        for (Map.Entry<HttpMethod, Integer> count : endpoint.getCallCounts().entrySet()) {
            counts.add(count.getKey() + ": " + count.getValue());
        }
        html.append(paragraph(text("counts", String.join(", ", counts))));
        html.append(writeValues(endpoint));
        for (RecordedCall call : endpoint.getCalls()) {
            html.append(writeCall(call));
        }
        return html.toString();
    }

    private String writeCall(RecordedCall recorded) {
        ApiCall call = recorded.getCall();
        StringBuilder html = new StringBuilder();
        String method = "<b>" + call.getMethod() + "</b>";
        html.append(paragraph(text("call", method, String.valueOf(call.getStatusCode()))));
        if (!recorded.getArguments().isEmpty()) {
            List<String> arguments = new ArrayList<>();
            for (Map.Entry<String, String> argument : recorded.getArguments().entrySet()) {
                arguments.add(escape(argument.getKey()) + " = " + escape(argument.getValue()));
            }
            html.append(paragraph(text("arguments", String.join(", ", arguments))));
        }
        html.append(block(text("headers"), call.getResponseHeaders().strip()));
        return html.toString();
    }

    /** The values seen per variable, like {@code POST_ID: 1 (3 calls), abcd (1 call)}. */
    private String writeValues(EndpointReport endpoint) {
        StringBuilder html = new StringBuilder();
        for (Map.Entry<String, Map<String, Integer>> variable :
                endpoint.getValuesSeen().entrySet()) {
            List<String> seen = new ArrayList<>();
            for (Map.Entry<String, Integer> value : variable.getValue().entrySet()) {
                seen.add(
                        escape(value.getKey()) + " (" + text("valuecalls", value.getValue()) + ")");
            }
            html.append(
                    paragraph(text("values", escape(variable.getKey()), String.join(", ", seen))));
        }
        return html.toString();
    }

    /** A label with the escaped value below it, with its line breaks kept and long lines cut. */
    private String block(String label, String value) {
        return paragraph(label) + "<pre>" + escape(wrap(value)) + "</pre>";
    }

    /** Cuts every line that is longer than {@link #MAX_LINE_LENGTH} into lines of that length. */
    private static String wrap(String text) {
        StringBuilder wrapped = new StringBuilder();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            while (line.length() > MAX_LINE_LENGTH) {
                wrapped.append(line, 0, MAX_LINE_LENGTH).append('\n');
                line = line.substring(MAX_LINE_LENGTH);
            }
            wrapped.append(line);
            if (i < lines.length - 1) {
                wrapped.append('\n');
            }
        }
        return wrapped.toString();
    }

    private static boolean isJson(String responseHeaders) {
        for (String line : responseHeaders.split("\r?\n")) {
            String header = line.toLowerCase(Locale.ROOT);
            if (header.startsWith("content-type:") && header.contains("json")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Indents a JSON body. The tokens stay exactly as they were sent; only white space between them
     * changes. A body that is not whole JSON, like one that was cut at 64 KB, is given back as it
     * is.
     */
    private static String prettyJson(String body) {
        String json = body.strip();
        if (!json.startsWith("{") && !json.startsWith("[")) {
            return body;
        }
        StringBuilder out = new StringBuilder();
        Deque<Character> open = new ArrayDeque<>();
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                out.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (open.isEmpty() && out.length() > 0 && !Character.isWhitespace(c)) {
                // Something after the closing bracket of the top-level value.
                return body;
            }
            switch (c) {
                case '"':
                    inString = true;
                    out.append(c);
                    break;
                case '{':
                case '[':
                    char close = c == '{' ? '}' : ']';
                    int next = nextNonWhitespace(json, i + 1);
                    if (next < json.length() && json.charAt(next) == close) {
                        // An empty object or list stays on one line.
                        out.append(c).append(close);
                        i = next;
                        break;
                    }
                    open.push(c);
                    out.append(c);
                    newLine(out, open.size());
                    break;
                case '}':
                case ']':
                    if (open.isEmpty() || open.pop() != (c == '}' ? '{' : '[')) {
                        return body;
                    }
                    newLine(out, open.size());
                    out.append(c);
                    break;
                case ',':
                    out.append(c);
                    newLine(out, open.size());
                    break;
                case ':':
                    out.append(": ");
                    break;
                default:
                    if (Character.isWhitespace(c)) {
                        break;
                    }
                    int end = endOfWord(json, i);
                    String word = json.substring(i, end);
                    // Outside strings JSON only has numbers, true, false and null.
                    if (!JSON_WORD.matcher(word).matches()) {
                        return body;
                    }
                    out.append(word);
                    i = end - 1;
            }
        }
        return inString || !open.isEmpty() ? body : out.toString();
    }

    private static int endOfWord(String text, int from) {
        int i = from;
        while (i < text.length()
                && !Character.isWhitespace(text.charAt(i))
                && ",:{}[]\"".indexOf(text.charAt(i)) == -1) {
            i++;
        }
        return i;
    }

    private static int nextNonWhitespace(String text, int from) {
        int i = from;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private static void newLine(StringBuilder out, int depth) {
        out.append('\n').append(INDENT.repeat(depth));
    }

    private static String paragraph(String html) {
        return "<p>" + html + "</p>";
    }

    private static String text(String key, Object... arguments) {
        return Constant.messages.getString(PREFIX + key, arguments);
    }

    private String escape(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
