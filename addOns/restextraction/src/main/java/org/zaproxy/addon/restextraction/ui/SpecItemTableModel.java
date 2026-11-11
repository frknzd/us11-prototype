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

import java.util.List;
import javax.swing.table.AbstractTableModel;
import org.zaproxy.addon.restextraction.model.EndpointSpec;
import org.zaproxy.addon.restextraction.model.SpecItem;

public final class SpecItemTableModel extends AbstractTableModel {
    private static final long serialVersionUID = 1L;
    private transient List<SpecItem> rows = List.of();
    private static final String[] COLUMNS = {"method", "kind", "name", "type", "range", "required"};

    public void setSpec(EndpointSpec spec) {
        if (rows.equals(spec.items())) return;
        rows = spec.items();
        fireTableDataChanged();
    }

    public SpecItem getItemAt(int row) {
        return rows.get(row);
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
        return text("column." + COLUMNS[column]);
    }

    @Override
    public Object getValueAt(int row, int column) {
        var item = rows.get(row);
        var p = item.getParameter();
        return switch (column) {
            case 0 -> item.getMethod().name();
            case 1 -> text("kind." + item.getKind().name());
            case 2 -> p == null ? item.getMediaType() : p.getName();
            case 3 -> p == null ? "" : p.getType().getName();
            case 4 -> p == null ? "" : p.getType().describeRange();
            default -> p != null && p.isRequired() ? "✓" : "";
        };
    }
}
