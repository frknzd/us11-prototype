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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.EnumMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import org.zaproxy.addon.restextraction.model.ApiNamespace;
import org.zaproxy.addon.restextraction.model.EndpointSpec;
import org.zaproxy.addon.restextraction.model.HttpMethod;
import org.zaproxy.addon.restextraction.model.RestExtractor;
import org.zaproxy.addon.restextraction.model.SpecItem;
import org.zaproxy.addon.restextraction.model.SpecItemKind;

/** Specification editor; validation and atomic changes belong to the model facade. */
public class SpecificationPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final transient RestExtractor extractor;
    private transient ApiNamespace namespace;
    private String template;
    private transient SpecItemForm form;
    private transient SpecItem editing;
    private final SpecItemTableModel items = new SpecItemTableModel();
    private final JTable table = new JTable(items);
    private final JComboBox<SpecItemKind> kind = new JComboBox<>(SpecItemKind.values());
    private final JComboBox<String> method = new JComboBox<>();
    private final JComboBox<String> media =
            new JComboBox<>(new String[] {"application/json", "application/x-www-form-urlencoded"});
    private final JTextField name = new JTextField(22);
    private final JComboBox<String> variable = new JComboBox<>();
    private final JPanel nameSlot = new JPanel(new BorderLayout());
    private final JComboBox<String> type =
            new JComboBox<>(new String[] {"String", "Integer", "Decimal", "Base64"});
    private final JTextField min = new JTextField(12), max = new JTextField(12);
    private final JCheckBox required = new JCheckBox();
    private final JLabel explanation = new JLabel(text("selectEndpoint"));
    private final JLabel error = new JLabel(" ");
    private final JLabel hint = new JLabel(" ");
    private final JButton save = new JButton(text("addItem"));
    private final Map<SpecField, JComponent> controls = new EnumMap<>(SpecField.class);
    private final Map<SpecField, JLabel> labels = new EnumMap<>(SpecField.class);
    private boolean updating;
    private String lastType = "String";

    public SpecificationPanel(RestExtractor extractor) {
        super(new BorderLayout(8, 8));
        this.extractor = extractor;
        setName("specification");
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        add(explanation, BorderLayout.NORTH);
        table.setName("specItems");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        JPanel list = new JPanel(new BorderLayout(4, 4));
        list.add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING));
        JButton edit = button("editItem", this::onEdit),
                remove = button("removeItem", this::onRemove),
                fresh = button("newItem", this::newItem);
        actions.add(fresh);
        actions.add(edit);
        actions.add(remove);
        list.add(actions, BorderLayout.SOUTH);
        for (HttpMethod m : HttpMethod.values())
            if (m != HttpMethod.OTHER) method.addItem(m.name());
        media.setEditable(true);
        nameSlot.add(name, BorderLayout.CENTER);
        controls.put(SpecField.METHOD, method);
        controls.put(SpecField.MEDIA_TYPE, media);
        controls.put(SpecField.NAME, nameSlot);
        controls.put(SpecField.TYPE, type);
        controls.put(SpecField.MIN, min);
        controls.put(SpecField.MAX, max);
        controls.put(SpecField.REQUIRED, required);
        JPanel grid = new JPanel(new GridBagLayout());
        kind.setName("specKind");
        kind.setRenderer(
                new javax.swing.DefaultListCellRenderer() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public Component getListCellRendererComponent(
                            javax.swing.JList<?> list,
                            Object value,
                            int index,
                            boolean selected,
                            boolean focus) {
                        return super.getListCellRendererComponent(
                                list,
                                value instanceof SpecItemKind k ? text("kind." + k.name()) : value,
                                index,
                                selected,
                                focus);
                    }
                });
        addRow(grid, 0, new JLabel(text("kindLabel")), kind);
        int row = 1;
        for (SpecField field : SpecField.values()) {
            JLabel label = new JLabel(text("field." + field.name()));
            labels.put(field, label);
            controls.get(field).setName("spec" + field.name());
            addRow(grid, row++, label, controls.get(field));
        }
        name.setName("specName");
        variable.setName("specVariable");
        JPanel editor = new JPanel(new BorderLayout(4, 6));
        editor.setBorder(BorderFactory.createTitledBorder(text("editor")));
        editor.add(grid, BorderLayout.CENTER);
        JPanel lower = new JPanel();
        // A standard vertical BoxLayout keeps messages and buttons independent of form rows.
        lower.setLayout(new javax.swing.BoxLayout(lower, javax.swing.BoxLayout.Y_AXIS));
        hint.setName("specHint");
        error.setName("specError");
        error.setForeground(VerificationText.colour('✗'));
        lower.add(hint);
        lower.add(error);
        JPanel saveRow = new JPanel(new FlowLayout(FlowLayout.LEADING));
        save.setName("saveItem");
        save.addActionListener(e -> onSave());
        saveRow.add(save);
        saveRow.add(button("cancelEdit", this::newItem));
        lower.add(saveRow);
        editor.add(lower, BorderLayout.SOUTH);
        list.setPreferredSize(new Dimension(600, 200));
        list.setMinimumSize(new Dimension(200, 100));
        JScrollPane editorScroll = new JScrollPane(editor);
        editorScroll.setPreferredSize(new Dimension(600, 320));
        editorScroll.setMinimumSize(new Dimension(200, 230));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, list, editorScroll);
        split.setResizeWeight(0.4);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);
        kind.addActionListener(
                e -> {
                    if (!updating && form != null) {
                        readForm();
                        form.setKind((SpecItemKind) kind.getSelectedItem());
                        showFields();
                    }
                });
        type.addActionListener(e -> onTypeSelected());
        method.addActionListener(
                e -> {
                    if (!updating) showHint();
                });
    }

    private static void addRow(JPanel grid, int row, JLabel label, JComponent field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.gridx = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(3, 4, 3, 10);
        grid.add(label, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(3, 0, 3, 4);
        grid.add(field, c);
        label.setLabelFor(field);
    }

    private JButton button(String key, Runnable action) {
        JButton b = new JButton(text(key));
        b.setName(key);
        b.addActionListener(
                e -> {
                    if (namespace != null) action.run();
                });
        return b;
    }

    public void showEndpoint(ApiNamespace namespace, String template) {
        boolean changed =
                this.namespace != namespace || !java.util.Objects.equals(this.template, template);
        this.namespace = namespace;
        this.template = template;
        if (namespace == null || template == null) {
            explanation.setText(text("selectEndpoint"));
            items.setSpec(EndpointSpec.empty());
            return;
        }
        if (changed) newItem();
        else refresh();
    }

    private EndpointSpec spec() {
        return extractor.getReport(namespace).getEndpoints().stream()
                .filter(e -> e.getTemplate().equals(template))
                .findFirst()
                .orElseThrow()
                .getSpec();
    }

    private void refresh() {
        EndpointSpec spec = spec();
        items.setSpec(spec);
        explanation.setText(spec.isEmpty() ? text("noSpec") : text("endpointSpec", template));
    }

    private void newItem() {
        if (template == null) return;
        editing = null;
        form = new SpecItemForm(template);
        updating = true;
        kind.setSelectedItem(SpecItemKind.METHOD);
        method.setSelectedItem("GET");
        type.setSelectedItem("String");
        lastType = "String";
        media.setSelectedItem("application/json");
        name.setText("");
        min.setText("");
        max.setText("");
        required.setSelected(false);
        variable.removeAllItems();
        for (String v : form.pathVariables()) variable.addItem(v);
        updating = false;
        save.setText(text("addItem"));
        error.setText(" ");
        showFields();
        refresh();
    }

    private void readForm() {
        form.set(SpecField.METHOD, (String) method.getSelectedItem());
        form.set(SpecField.MEDIA_TYPE, String.valueOf(media.getEditor().getItem()));
        form.set(
                SpecField.NAME,
                form.getKind() == SpecItemKind.PATH_VARIABLE
                        ? java.util.Objects.toString(variable.getSelectedItem(), "")
                        : name.getText());
        form.set(SpecField.TYPE, (String) type.getSelectedItem());
        form.set(SpecField.MIN, min.getText());
        form.set(SpecField.MAX, max.getText());
        form.set(SpecField.REQUIRED, Boolean.toString(required.isSelected()));
    }

    private void showFields() {
        if (form == null) return;
        form.set(SpecField.TYPE, (String) type.getSelectedItem());
        for (SpecField f : SpecField.values()) {
            boolean visible = form.visibleFields().contains(f);
            controls.get(f).setVisible(visible);
            labels.get(f).setVisible(visible);
        }
        nameSlot.removeAll();
        nameSlot.add(
                form.getKind() == SpecItemKind.PATH_VARIABLE ? variable : name,
                BorderLayout.CENTER);
        showHint();
        revalidate();
        repaint();
    }

    private void showHint() {
        boolean unusual =
                form != null
                        && form.getKind() == SpecItemKind.BODY_FIELD
                        && java.util.Set.of("GET", "HEAD", "DELETE")
                                .contains(method.getSelectedItem());
        hint.setText(unusual ? text("unusualBody", method.getSelectedItem()) : " ");
    }

    private void onTypeSelected() {
        if (updating || form == null) return;
        String selected = (String) type.getSelectedItem();
        if ((selected.equals("String") || selected.equals("Base64"))
                && (!min.getText().isEmpty() || !max.getText().isEmpty())) {
            if (!confirm(text("confirmRange"))) {
                updating = true;
                type.setSelectedItem(lastType);
                updating = false;
                return;
            }
            min.setText("");
            max.setText("");
        }
        lastType = selected;
        showFields();
    }

    private boolean hasDependents(SpecItem item) {
        return item.getKind() == SpecItemKind.METHOD
                && spec().items().stream()
                        .anyMatch(
                                i ->
                                        i.getMethod() == item.getMethod()
                                                && i.getKind() != SpecItemKind.METHOD);
    }

    protected boolean confirm(String message) {
        return JOptionPane.showConfirmDialog(
                        this, message, text("confirmTitle"), JOptionPane.YES_NO_OPTION)
                == JOptionPane.YES_OPTION;
    }

    private void onSave() {
        if (form == null) return;
        try {
            readForm();
            SpecItem item = form.toItem();
            if (editing == null) extractor.addSpecItem(namespace, template, item);
            else {
                if (hasDependents(editing)
                        && (editing.getMethod() != item.getMethod()
                                || item.getKind() != SpecItemKind.METHOD)
                        && !confirm(text("confirmMethod"))) return;
                extractor.replaceSpecItem(namespace, template, editing, item);
            }
            newItem();
        } catch (IllegalArgumentException e) {
            error.setText("✗ " + VerificationText.validation(e));
        }
    }

    private void onEdit() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        editing = items.getItemAt(row);
        form.load(editing);
        updating = true;
        kind.setSelectedItem(form.getKind());
        method.setSelectedItem(form.get(SpecField.METHOD));
        media.setSelectedItem(form.get(SpecField.MEDIA_TYPE));
        name.setText(form.get(SpecField.NAME));
        variable.setSelectedItem(form.get(SpecField.NAME));
        type.setSelectedItem(form.get(SpecField.TYPE));
        lastType = form.get(SpecField.TYPE);
        min.setText(form.get(SpecField.MIN));
        max.setText(form.get(SpecField.MAX));
        required.setSelected(Boolean.parseBoolean(form.get(SpecField.REQUIRED)));
        updating = false;
        save.setText(text("saveItem"));
        error.setText(" ");
        showFields();
    }

    private void onRemove() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        SpecItem item = items.getItemAt(row);
        if (hasDependents(item) && !confirm(text("confirmMethod"))) return;
        try {
            extractor.removeSpecItem(namespace, template, item);
            newItem();
        } catch (IllegalArgumentException e) {
            error.setText("✗ " + VerificationText.validation(e));
        }
    }
}
