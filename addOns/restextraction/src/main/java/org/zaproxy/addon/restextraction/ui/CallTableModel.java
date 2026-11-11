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

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.swing.table.AbstractTableModel;
import org.parosproxy.paros.Constant;
import org.zaproxy.addon.restextraction.model.Argument;
import org.zaproxy.addon.restextraction.model.ArgumentLocation;
import org.zaproxy.addon.restextraction.model.EndpointReport;
import org.zaproxy.addon.restextraction.model.HttpMethod;
import org.zaproxy.addon.restextraction.model.RecordedCall;
import org.zaproxy.addon.restextraction.model.Verdict;
import org.zaproxy.addon.restextraction.model.VerdictStatus;

/**
 * The calls of the selected namespace or endpoint as rows of a table: time, method, status code,
 * endpoint and arguments. It only holds values from the report, never live domain objects.
 */
public class CallTableModel extends AbstractTableModel {
    private static final long serialVersionUID = 1L;

    private static final String PREFIX = "restextraction.calls.column.";
    private static final String[] COLUMNS = {
        "time", "method", "status", "endpoint", "arguments", "check"
    };
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private transient List<Row> rows = List.of();
    private boolean hasSpec;
    private final transient CheckWriter checkWriter = new CheckWriter();

    /**
     * Shows the calls of these endpoints. Calls of several endpoints are merged by the time they
     * were made.
     */
    public void setEndpoints(List<EndpointReport> endpoints) {
        hasSpec = endpoints.stream().anyMatch(e -> !e.getSpec().isEmpty());
        List<Row> newRows = new ArrayList<>();
        for (EndpointReport endpoint : endpoints) {
            for (RecordedCall call : endpoint.getCalls()) {
                newRows.add(new Row(endpoint.getTemplate(), call, endpoint.getVerdict(call)));
            }
        }
        // A stable sort: calls of one endpoint keep their arrival order.
        newRows.sort(Comparator.comparing(row -> row.call.getCall().getTime()));
        List<Row> oldRows = rows;
        rows = List.copyOf(newRows);
        // Only telling the table about new rows keeps its selection and scroll position.
        if (startsWith(rows, oldRows)) {
            if (rows.size() > oldRows.size()) {
                fireTableRowsInserted(oldRows.size(), rows.size() - 1);
            }
        } else {
            fireTableDataChanged();
        }
    }

    public boolean hasSpecification() {
        return hasSpec;
    }

    public Verdict getVerdictAt(int row) {
        return rows.get(row).verdict;
    }

    public RecordedCall getCallAt(int row) {
        return rows.get(row).call;
    }

    /** The row that shows the call, or -1 when it is not shown. */
    public int rowOf(RecordedCall call) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).call == call) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Tells whether the call in this row passes the filter.
     *
     * @param method the method the call must have, or null for every method
     * @param text text that one of the columns must contain, ignoring case; empty for every call
     */
    public boolean matches(int row, HttpMethod method, String text) {
        return matches(row, method, text, false);
    }

    public boolean matches(int row, HttpMethod method, String text, boolean onlyInvalid) {
        if (onlyInvalid && getVerdictAt(row).getStatus() != VerdictStatus.INVALID) return false;
        if (method != null && rows.get(row).call.getCall().getMethod() != method) {
            return false;
        }
        String wanted = text.strip().toLowerCase(Locale.ROOT);
        if (wanted.isEmpty()) {
            return true;
        }
        for (int column = 0; column < COLUMNS.length; column++) {
            String value = String.valueOf(getValueAt(row, column)).toLowerCase(Locale.ROOT);
            if (value.contains(wanted)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return Constant.messages.getString(PREFIX + COLUMNS[column]);
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return column == 2 ? Integer.class : String.class;
    }

    @Override
    public Object getValueAt(int row, int column) {
        Row shown = rows.get(row);
        switch (column) {
            case 0:
                return TIME.format(shown.call.getCall().getTime());
            case 1:
                return shown.call.getCall().getMethod().name();
            case 2:
                return shown.call.getCall().getStatusCode();
            case 3:
                return shown.template;
            case 4:
                return argumentsOf(shown.call);
            default:
                return checkWriter.mark(shown.verdict);
        }
    }

    private static String argumentsOf(RecordedCall call) {
        List<String> arguments = new ArrayList<>();
        for (Argument argument : call.getArguments()) {
            if (argument.isContainer()) continue;
            String location =
                    argument.getLocation() == ArgumentLocation.PATH
                            ? ""
                            : VerificationText.text(
                                            "location."
                                                    + argument.getLocation()
                                                            .name()
                                                            .toLowerCase(Locale.ROOT))
                                    + " ";
            arguments.add(location + argument.getName() + " = " + argument.getValue());
        }
        return String.join(", ", arguments);
    }

    private static boolean startsWith(List<Row> rows, List<Row> prefix) {
        if (prefix.size() > rows.size()) {
            return false;
        }
        for (int i = 0; i < prefix.size(); i++) {
            Row current = rows.get(i), previous = prefix.get(i);
            if (current.call != previous.call) return false;
            if (current.verdict != previous.verdict
                    && !(current.verdict.getStatus() == VerdictStatus.NOT_VERIFIED
                            && previous.verdict.getStatus() == VerdictStatus.NOT_VERIFIED
                            && current.verdict.isPreflight() == previous.verdict.isPreflight()))
                return false;
        }
        return true;
    }

    private static final class Row {
        private final String template;
        private final RecordedCall call;
        private final Verdict verdict;

        private Row(String template, RecordedCall call, Verdict verdict) {
            this.template = template;
            this.call = call;
            this.verdict = verdict;
        }
    }
}
