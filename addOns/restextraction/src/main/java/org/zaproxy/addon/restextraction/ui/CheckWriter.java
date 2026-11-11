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

import static org.zaproxy.addon.restextraction.ui.VerificationText.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.zaproxy.addon.restextraction.model.Argument;
import org.zaproxy.addon.restextraction.model.ArgumentLocation;
import org.zaproxy.addon.restextraction.model.Check;
import org.zaproxy.addon.restextraction.model.Verdict;
import org.zaproxy.addon.restextraction.model.VerdictStatus;
import org.zaproxy.addon.restextraction.model.Violation;
import org.zaproxy.addon.restextraction.model.Warning;

/** One presentation of checks for both call details and HTML documentation. */
public final class CheckWriter {
    public List<String> lines(Verdict verdict) {
        if (verdict.getStatus() == VerdictStatus.NOT_VERIFIED)
            return List.of(text(verdict.isPreflight() ? "preflight" : "notVerified"));
        List<String> lines = new ArrayList<>();
        for (Check check : verdict.getChecks())
            lines.add(check.isMatched() ? matched(check) : message(check.getViolation()));
        for (Violation violation : verdict.getViolations())
            if (verdict.getChecks().stream().noneMatch(c -> c.getViolation() == violation))
                lines.add(message(violation));
        for (Argument a : verdict.getUnchecked())
            lines.add(
                    text(
                            "unchecked",
                            location(a.getLocation()),
                            a.getName(),
                            value(a.getValue(), a.getLocation())));
        for (Warning warning : verdict.getWarnings()) lines.add(message(warning));
        return List.copyOf(lines);
    }

    public String toHtml(Verdict verdict) {
        StringBuilder html = new StringBuilder();
        for (String line : lines(verdict)) {
            char mark = line.isEmpty() ? ' ' : line.charAt(0);
            String escaped =
                    ApiDocumentationWriter.escape(ApiDocumentationWriter.wrap(line))
                            .replace("\n", "<br>");
            if (mark == '✓' || mark == '✗' || mark == '⚠')
                escaped =
                        "<font color=\"" + VerificationText.hex(mark) + "\">" + escaped + "</font>";
            html.append("<p>").append(escaped).append("</p>");
        }
        return html.toString();
    }

    public String mark(Verdict verdict) {
        return switch (verdict.getStatus()) {
            case NOT_VERIFIED -> "";
            case INVALID -> "✗ " + verdict.getViolations().size();
            case VALID -> verdict.getWarnings().isEmpty() ? "✓" : "⚠";
        };
    }

    private String matched(Check c) {
        Argument a = c.getArgument();
        String range = c.getParameter().getType().describeRange();
        return text(
                "matched",
                location(a.getLocation()),
                a.getName(),
                value(a.getValue(), a.getLocation()),
                c.getParameter().getType().getName(),
                range.isEmpty() ? "" : " " + range);
    }

    private String message(Violation v) {
        List<String> d = v.getDetails();
        String loc = location(v.getLocation()), val = value(v.getValue(), v.getLocation());
        return switch (v.getKind()) {
            case UNSPECIFIED_METHOD ->
                    text("unspecifiedMethod", v.getValue(), String.join(", ", d));
            case UNSPECIFIED_CONTENT_TYPE ->
                    text(
                            "unspecifiedContentType",
                            v.getValue().isEmpty() ? text("none") : v.getValue(),
                            d.get(0),
                            d.get(1));
            case MALFORMED_BODY -> text("malformedBody", jsonReason(d.get(0)));
            case EXTRA_ARGUMENT -> text("extra", loc, v.getName(), val);
            case MISSING_ARGUMENT -> text("missing", loc, v.getName());
            case OUT_OF_RANGE -> text("range." + d.get(0), loc, v.getName(), val, d.get(1));
            case INVALID_TYPE -> {
                String reason = d.get(1);
                String detail =
                        reason.equals("json")
                                ? text("jsonKind." + d.get(2).toLowerCase(Locale.ROOT))
                                : d.size() > 2 ? d.get(2) : "";
                yield text(
                        "invalid." + reason,
                        loc,
                        v.getName(),
                        val,
                        d.get(0),
                        detail,
                        text("expectedKind." + d.get(0)));
            }
        };
    }

    private String message(Warning w) {
        List<String> d = w.getDetails();
        return switch (w.getKind()) {
            case REPEATED_PARAMETER ->
                    text("repeated", location(w.getLocation()), w.getName(), d.get(0));
            case PRECISION -> text("precision", w.getName(), value(d.get(0), w.getLocation()));
            case BODY_NOT_CHECKED ->
                    text("bodyNotChecked", text("bodyReason." + d.get(0), d.get(1)));
            case TOO_MANY_ARGUMENTS -> text("tooMany");
            case TOO_DEEP -> text("tooDeep");
        };
    }

    private static String jsonReason(String diagnostic) {
        int at = diagnostic.lastIndexOf(" at position ");
        String reason = at < 0 ? diagnostic : diagnostic.substring(0, at);
        String position = at < 0 ? "?" : diagnostic.substring(at + 13);
        String key =
                switch (reason) {
                    case "trailing comma" -> "trailingComma";
                    case "trailing content" -> "trailingContent";
                    case "expected value" -> "expectedValue";
                    case "expected :" -> "expectedColon";
                    case "expected ," -> "expectedComma";
                    case "expected \"" -> "expectedString";
                    case "control character in string" -> "control";
                    case "unfinished escape" -> "unfinishedEscape";
                    case "unfinished unicode escape" -> "unfinishedUnicode";
                    case "invalid unicode escape" -> "invalidUnicode";
                    case "invalid escape" -> "invalidEscape";
                    case "unclosed string" -> "unclosedString";
                    case "invalid number" -> "invalidNumber";
                    case "expected digit" -> "expectedDigit";
                    case "invalid literal" -> "invalidLiteral";
                    default -> "invalid";
                };
        return text("jsonError", text("jsonError." + key), position);
    }

    private static String location(ArgumentLocation location) {
        return location == null ? "" : text("location." + location.name().toLowerCase(Locale.ROOT));
    }

    private static String value(String value, ArgumentLocation location) {
        return location != ArgumentLocation.HEADER && value.length() > 80
                ? value.substring(0, 80) + "…"
                : value;
    }
}
