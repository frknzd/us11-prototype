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
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.parosproxy.paros.Constant;
import org.zaproxy.addon.restextraction.model.ApiNamespace;
import org.zaproxy.addon.restextraction.model.PatternDraft;
import org.zaproxy.addon.restextraction.model.RestExtractor;
import org.zaproxy.addon.restextraction.model.UrlPath;

/** Modal chip editor; never bypasses normal endpoint registration. */
public final class PatternBuilderDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    private final transient PatternDraft draft;
    private final transient RestExtractor extractor;
    private final transient ApiNamespace namespace;
    private final JPanel chips = new JPanel();
    private final List<JTextField> names = new ArrayList<>();
    private final JTextField preview = new JTextField();
    private final JLabel problem = new JLabel(" ");
    private final JButton add = new JButton(text("addPattern"));
    private final transient Runnable onAdded;

    public PatternBuilderDialog(RestExtractor extractor, ApiNamespace namespace, UrlPath path) {
        this(null, extractor, namespace, path, () -> {});
    }

    public PatternBuilderDialog(
            Component parent,
            RestExtractor extractor,
            ApiNamespace namespace,
            UrlPath path,
            Runnable onAdded) {
        super(
                parent == null ? (Window) null : SwingUtilities.getWindowAncestor(parent),
                text("patternBuilder"),
                ModalityType.APPLICATION_MODAL);
        this.extractor = extractor;
        this.namespace = namespace;
        this.onAdded = onAdded;
        draft = new PatternDraft(path);
        setName("patternBuilder");
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(new JLabel(text("chipHelp")), BorderLayout.NORTH);
        chips.setLayout(new BoxLayout(chips, BoxLayout.Y_AXIS));
        content.add(new JScrollPane(chips), BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(4, 4));
        preview.setName("patternPreview");
        preview.setEditable(false);
        footer.add(preview, BorderLayout.NORTH);
        problem.setName("patternProblem");
        problem.setForeground(VerificationText.colour('✗'));
        footer.add(problem, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING));
        add.setName("confirmPattern");
        add.addActionListener(e -> onAdd());
        buttons.add(add);
        JButton cancel = new JButton(text("cancelEdit"));
        cancel.addActionListener(e -> dispose());
        buttons.add(cancel);
        footer.add(buttons, BorderLayout.SOUTH);
        content.add(footer, BorderLayout.SOUTH);
        setContentPane(content);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        buildChips();
        setMinimumSize(new Dimension(500, 300));
        setSize(700, 450);
        setLocationRelativeTo(parent);
    }

    private void buildChips() {
        chips.removeAll();
        names.clear();
        boolean usable = draft.size() > 0;
        for (int i = 0; i < draft.size(); i++) if (draft.part(i).isEmpty()) usable = false;
        for (int i = 0; i < draft.size(); i++) {
            final int index = i;
            JPanel row = new JPanel(new BorderLayout(8, 4));
            row.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
            JToggleButton chip = new JToggleButton(draft.part(i), draft.isVariable(i));
            chip.setName("chip" + i);
            chip.setEnabled(usable);
            chip.setToolTipText(text(draft.canBeLiteral(i) ? "toggleChip" : "lockedChip"));
            JTextField name = new JTextField(draft.name(i), 18);
            name.setName("chipName" + i);
            name.setEnabled(draft.isVariable(i));
            names.add(name);
            chip.addActionListener(
                    e -> {
                        try {
                            draft.toggle(index);
                            buildChips();
                        } catch (IllegalArgumentException error) {
                            chip.setSelected(draft.isVariable(index));
                            problem.setText(VerificationText.validation(error));
                        }
                    });
            name.getDocument()
                    .addDocumentListener(
                            new DocumentListener() {
                                @Override
                                public void insertUpdate(DocumentEvent e) {
                                    refreshPreview();
                                }

                                @Override
                                public void removeUpdate(DocumentEvent e) {
                                    refreshPreview();
                                }

                                @Override
                                public void changedUpdate(DocumentEvent e) {
                                    refreshPreview();
                                }
                            });
            row.add(chip, BorderLayout.WEST);
            row.add(name, BorderLayout.CENTER);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
            chips.add(row);
        }
        refreshPreview();
        chips.revalidate();
        chips.repaint();
    }

    private boolean refreshPreview() {
        try {
            for (int i = 0; i < draft.size(); i++)
                if (draft.isVariable(i)) draft.rename(i, names.get(i).getText());
            var issue = draft.problem();
            if (issue.isPresent()) {
                String reason = issue.get();
                problem.setText(
                        reason.startsWith("restextraction.")
                                ? Constant.messages.getString(reason)
                                : text("refused", reason));
                preview.setText("");
                add.setEnabled(false);
                return false;
            }
            preview.setText(draft.toTemplate());
            problem.setText(" ");
            add.setEnabled(true);
            return true;
        } catch (IllegalArgumentException error) {
            problem.setText(VerificationText.validation(error));
            add.setEnabled(false);
            return false;
        }
    }

    private void onAdd() {
        if (!refreshPreview()) return;
        try {
            extractor.addEndpoint(namespace, draft.toTemplate());
            onAdded.run();
            dispose();
        } catch (IllegalArgumentException error) {
            problem.setText(VerificationText.validation(error));
        }
    }
}
