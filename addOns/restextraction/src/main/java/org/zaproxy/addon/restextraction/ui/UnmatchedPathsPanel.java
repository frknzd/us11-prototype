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

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import org.zaproxy.addon.restextraction.model.ApiNamespace;
import org.zaproxy.addon.restextraction.model.RestExtractor;
import org.zaproxy.addon.restextraction.model.UrlPath;

public final class UnmatchedPathsPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final transient RestExtractor extractor;
    private final transient ApiNamespace namespace;
    private transient List<Map.Entry<UrlPath, Integer>> rows = List.of();
    private final JLabel summary = new JLabel();
    private final transient AbstractTableModel model =
            new AbstractTableModel() {
                private static final long serialVersionUID = 1L;

                @Override
                public int getRowCount() {
                    return rows.size();
                }

                @Override
                public int getColumnCount() {
                    return 2;
                }

                @Override
                public String getColumnName(int column) {
                    return text(column == 0 ? "pathColumn" : "callsColumn");
                }

                @Override
                public Class<?> getColumnClass(int column) {
                    return column == 0 ? String.class : Integer.class;
                }

                @Override
                public Object getValueAt(int row, int column) {
                    return column == 0
                            ? rows.get(row).getKey().toString()
                            : rows.get(row).getValue();
                }
            };
    private final JTable table = new JTable(model);
    private final JButton add = new JButton(text("addPattern"));

    public UnmatchedPathsPanel(RestExtractor extractor, ApiNamespace namespace) {
        super(new BorderLayout(8, 8));
        this.extractor = extractor;
        this.namespace = namespace;
        setName("unmatchedPaths");
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        add(summary, BorderLayout.NORTH);
        table.setName("unmatchedTable");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING));
        JButton clear = new JButton(text("clearUnmatched"));
        clear.setName("clearUnmatched");
        clear.addActionListener(
                e -> {
                    extractor.clearUnmatchedPaths(namespace);
                    refresh();
                });
        buttons.add(clear);
        add.setName("addUnmatchedPattern");
        add.addActionListener(e -> onAddPattern());
        buttons.add(add);
        add(buttons, BorderLayout.SOUTH);
        table.getSelectionModel()
                .addListSelectionListener(e -> add.setEnabled(table.getSelectedRow() >= 0));
        refresh();
    }

    public void refresh() {
        UrlPath selected =
                table.getSelectedRow() < 0
                        ? null
                        : rows.get(table.convertRowIndexToModel(table.getSelectedRow())).getKey();
        var next = List.copyOf(extractor.getUnmatchedPaths(namespace).entrySet());
        if (!rows.equals(next)) {
            rows = next;
            model.fireTableDataChanged();
            if (selected != null)
                for (int i = 0; i < rows.size(); i++)
                    if (rows.get(i).getKey().equals(selected)) {
                        int row = table.convertRowIndexToView(i);
                        if (row >= 0) table.setRowSelectionInterval(row, row);
                    }
        }
        summary.setText(
                text("unmatchedSummary", rows.size(), extractor.getUnmatchedOverflow(namespace)));
        add.setEnabled(table.getSelectedRow() >= 0);
    }

    private void onAddPattern() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        UrlPath path = rows.get(table.convertRowIndexToModel(row)).getKey();
        new PatternBuilderDialog(this, extractor, namespace, path, this::refresh).setVisible(true);
    }
}
