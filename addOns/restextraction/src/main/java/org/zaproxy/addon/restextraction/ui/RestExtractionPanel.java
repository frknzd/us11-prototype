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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import javax.swing.text.DefaultCaret;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import org.parosproxy.paros.Constant;
import org.parosproxy.paros.extension.AbstractPanel;
import org.zaproxy.addon.restextraction.model.ApiCall;
import org.zaproxy.addon.restextraction.model.ApiNamespace;
import org.zaproxy.addon.restextraction.model.ApiReport;
import org.zaproxy.addon.restextraction.model.ArgumentLocation;
import org.zaproxy.addon.restextraction.model.EndpointReport;
import org.zaproxy.addon.restextraction.model.ExtractionObserver;
import org.zaproxy.addon.restextraction.model.HttpMethod;
import org.zaproxy.addon.restextraction.model.RecordedCall;
import org.zaproxy.addon.restextraction.model.RestExtractor;
import org.zaproxy.addon.restextraction.model.VerdictStatus;
import org.zaproxy.zap.extension.help.ExtensionHelp;
import org.zaproxy.zap.utils.DisplayUtils;

/**
 * The tab of the add-on. On the left the developer adds APIs and endpoint patterns and starts or
 * stops an API; a tree shows every API with its endpoints, which can be removed or cleared. On the
 * right are the calls of the selected API or endpoint in a table, and its documentation in a second
 * tab.
 *
 * <p>The panel has no rules of its own: it passes every action on to the {@link RestExtractor} and
 * shows the message of the exception when something is refused.
 *
 * <p>It is also the observer of the extractor. A recorded call only marks the report as out of
 * date, a timer writes it again on the Swing thread. So a burst of calls costs only a few redraws
 * and the proxy threads never wait for the GUI.
 */
public class RestExtractionPanel extends AbstractPanel implements ExtractionObserver {
    // AbstractPanel is a Swing panel, so it is serializable.
    private static final long serialVersionUID = 1L;

    private static final String PREFIX = "restextraction.panel.";
    private static final String TAB_ICON = "/resource/icon/fugue/plug-connect.png";
    // The target of the help page in map.jhm.
    private static final String HELP_ID = "restextraction";
    private static final String PLACEHOLDER = "JTextField.placeholderText";
    private static final int GAP = 6;
    private static final int INPUT_WIDTH = 380;
    private static final int MIN_INPUT_WIDTH = 220;
    private static final int TABLE_WIDTH = 400;
    private static final int TABLE_HEIGHT = 150;
    private static final int REDRAW_INTERVAL_MS = 500;
    private static final int DOCUMENTATION_TAB = 1;
    private static final int STATUS_COLUMN = 2;

    private final transient RestExtractor extractor;
    private final transient ApiDocumentationWriter writer = new ApiDocumentationWriter();
    private final transient CheckWriter checkWriter = new CheckWriter();
    private final SpecificationPanel specificationPanel;
    private final JCheckBox onlyInvalidBox = new JCheckBox(VerificationText.text("onlyInvalid"));
    private boolean showFullBody;
    private boolean detailDark;

    private final JTextField baseUrlField = new JTextField();
    private final JTextField patternField = new JTextField();
    // Namespace nodes hold the ApiNamespace, endpoint nodes the template of the endpoint.
    private final DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    private final DefaultTreeModel treeModel = new DefaultTreeModel(root);
    private final JTree namespaceTree = new JTree(treeModel);
    // The last report of every namespace, so the tree can show the counts without asking again.
    private final transient Map<ApiNamespace, ApiReport> reports = new HashMap<>();
    private final JButton addNamespaceButton = new JButton(text("addapi"));
    private final JButton addEndpointButton = new JButton(text("addendpoint"));
    private final JButton startStopButton = new JButton(text("start"));
    private final JButton clearButton = new JButton(text("clear"));
    private final JButton removeButton = new JButton(text("remove"));
    private final JButton unmatchedButton = new JButton(VerificationText.text("unmatchedPaths"));
    private final transient Map<ApiNamespace, JDialog> unmatchedDialogs = new HashMap<>();
    private final JPopupMenu treeMenu = new JPopupMenu();
    private final JButton helpButton = new JButton();
    private final JTextArea messageArea = new JTextArea();
    private final JEditorPane reportPane = new JEditorPane();
    private final JCheckBox fullBodyBox = new JCheckBox(text("fullbody"));
    private final CallTableModel callModel = new CallTableModel();
    private final JTable callTable = new JTable(callModel);
    private final TableRowSorter<CallTableModel> callSorter = new TableRowSorter<>(callModel);
    // The first item stands for every method, the others are the names of HttpMethod.
    private final JComboBox<String> methodFilter = new JComboBox<>();
    private final JTextField textFilter = new JTextField();
    private final JLabel callCount = new JLabel();
    private final JTabbedPane tabs = new JTabbedPane();
    private final JTextPane detailArea = new JTextPane();

    private final AtomicBoolean reportOutOfDate = new AtomicBoolean();
    private final Timer redrawTimer = new Timer(REDRAW_INTERVAL_MS, e -> redrawIfOutOfDate());

    public RestExtractionPanel(RestExtractor extractor) {
        super();
        this.extractor = extractor;
        specificationPanel = new SpecificationPanel(extractor);

        // ZAP uses this name for the UI tab label
        setName(text("title"));
        // An icon that ships with ZAP, so the tab looks like the others without a file of our own.
        setIcon(DisplayUtils.getScaledIcon(RestExtractionPanel.class.getResource(TAB_ICON)));
        // F1 opens the help page while something in the panel has the focus.
        ExtensionHelp.enableHelpKey(this, HELP_ID);
        setLayout(new BorderLayout());
        add(createSplitPane(), BorderLayout.CENTER);

        baseUrlField.addActionListener(e -> onAddNamespace());
        addNamespaceButton.addActionListener(e -> onAddNamespace());
        patternField.addActionListener(e -> onAddEndpoint());
        addEndpointButton.addActionListener(e -> onAddEndpoint());
        startStopButton.addActionListener(e -> onStartStop());
        clearButton.addActionListener(e -> onClear());
        unmatchedButton.setName("unmatchedPathsButton");
        unmatchedButton.addActionListener(e -> onShowUnmatched());
        // A refused input stays in its field; once the user changes it, the old message is wrong.
        DocumentListener clearMessage = onChange(this::clearMessage);
        baseUrlField.getDocument().addDocumentListener(clearMessage);
        patternField.getDocument().addDocumentListener(clearMessage);
        removeButton.addActionListener(e -> onRemove());
        fullBodyBox.addActionListener(e -> onToggleFullBody());
        detailArea.addPropertyChangeListener(
                "UI", e -> SwingUtilities.invokeLater(this::onSelectCall));
        namespaceTree.addTreeSelectionListener(e -> onSelectNamespace());
        tabs.addChangeListener(e -> onSelectTab());
        callTable
                .getSelectionModel()
                .addListSelectionListener(
                        e -> {
                            if (!e.getValueIsAdjusting()) {
                                onSelectCall();
                            }
                        });
        onSelectNamespace();

        extractor.addObserver(this);
        redrawTimer.start();
    }

    /** Called on a proxy thread: it only leaves a note for the timer. */
    @Override
    public void callRecorded(ApiNamespace namespace) {
        reportOutOfDate.set(true);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        redrawTimer.start();
    }

    /** ZAP removes the panel when the add-on is unloaded, the timer must not keep it alive. */
    @Override
    public void removeNotify() {
        redrawTimer.stop();
        for (JDialog dialog : List.copyOf(unmatchedDialogs.values())) dialog.dispose();
        unmatchedDialogs.clear();
        super.removeNotify();
    }

    private void redrawIfOutOfDate() {
        if (!reportOutOfDate.getAndSet(false)) {
            return;
        }
        refreshTree();
        ApiNamespace namespace = selectedNamespace();
        if (namespace != null) {
            showReport(namespace);
        }
    }

    private void onAddNamespace() {
        try {
            ApiNamespace namespace = extractor.addNamespace(baseUrlField.getText());
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(namespace);
            treeModel.insertNodeInto(node, root, root.getChildCount());
            reports.put(namespace, extractor.getReport(namespace));
            TreePath path = new TreePath(node.getPath());
            namespaceTree.setSelectionPath(path);
            namespaceTree.scrollPathToVisible(path);
            baseUrlField.setText("");
            showMessage("");
        } catch (IllegalArgumentException e) {
            showMessage(e.getMessage());
        }
    }

    private void onAddEndpoint() {
        DefaultMutableTreeNode namespaceNode = selectedNamespaceNode();
        if (namespaceNode == null) {
            return;
        }
        ApiNamespace namespace = (ApiNamespace) namespaceNode.getUserObject();
        try {
            String template =
                    extractor.addEndpoint(namespace, patternField.getText()).getTemplate();
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(template, false);
            treeModel.insertNodeInto(node, namespaceNode, namespaceNode.getChildCount());
            namespaceTree.expandPath(new TreePath(namespaceNode.getPath()));
            patternField.setText("");
            showMessage("");
            showReport(namespace);
        } catch (IllegalArgumentException e) {
            showMessage(e.getMessage());
        }
    }

    private void onStartStop() {
        ApiNamespace namespace = selectedNamespace();
        if (namespace == null) {
            return;
        }
        if (namespace.isExtracting()) {
            extractor.stopExtraction(namespace);
        } else {
            extractor.startExtraction(namespace);
        }
        updateButtons(namespace);
        refreshTree();
    }

    /**
     * Removes the selected endpoint, or the selected namespace with its endpoints. Asks first when
     * calls would be lost.
     */
    private void onRemove() {
        DefaultMutableTreeNode namespaceNode = selectedNamespaceNode();
        if (namespaceNode == null) {
            return;
        }
        ApiNamespace namespace = (ApiNamespace) namespaceNode.getUserObject();
        String template = selectedTemplate();
        if (template != null) {
            int calls = callsOf(namespace, template);
            if (calls > 0
                    && !confirm(
                            Constant.messages.getString(
                                    PREFIX + "confirm.removeendpoint",
                                    template,
                                    callsText(calls)))) {
                return;
            }
            extractor.removeEndpoint(namespace, template);
            treeModel.removeNodeFromParent(
                    (DefaultMutableTreeNode)
                            namespaceTree.getSelectionPath().getLastPathComponent());
            namespaceTree.setSelectionPath(new TreePath(namespaceNode.getPath()));
            refreshTree();
            showReport(namespace);
            return;
        }
        int calls = callsOf(namespace, null);
        if (calls > 0
                && !confirm(
                        Constant.messages.getString(
                                PREFIX + "confirm.removenamespace",
                                namespace.getBaseUrl(),
                                callsText(calls)))) {
            return;
        }
        extractor.removeNamespace(namespace);
        JDialog unmatchedDialog = unmatchedDialogs.remove(namespace);
        if (unmatchedDialog != null) unmatchedDialog.dispose();
        reports.remove(namespace);
        treeModel.removeNodeFromParent(namespaceNode);
        namespaceTree.clearSelection();
        onSelectNamespace();
    }

    /**
     * Forgets the recorded calls of the selected endpoint, or of the whole namespace when the
     * namespace is selected. Asks first when there are any.
     */
    private void onClear() {
        ApiNamespace namespace = selectedNamespace();
        if (namespace == null) {
            return;
        }
        String template = selectedTemplate();
        int calls = callsOf(namespace, template);
        if (calls == 0) {
            if (template == null) {
                extractor.clear(namespace);
                refreshTree();
                showReport(namespace);
            }
            return;
        }
        String cleared = template != null ? template : namespace.getBaseUrl();
        if (!confirm(
                Constant.messages.getString(PREFIX + "confirm.clear", cleared, callsText(calls)))) {
            return;
        }
        if (template != null) {
            extractor.clearEndpoint(namespace, template);
        } else {
            extractor.clear(namespace);
        }
        refreshTree();
        showReport(namespace);
    }

    /**
     * Asks the user to confirm that recorded calls may be lost. Tests answer it themselves, a
     * dialog can't be clicked in a unit test.
     */
    boolean confirm(String message) {
        return JOptionPane.showConfirmDialog(
                        this,
                        message,
                        text("confirm.title"),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE)
                == JOptionPane.YES_OPTION;
    }

    /** The calls of one endpoint, or of the whole namespace when the template is null. */
    private int callsOf(ApiNamespace namespace, String template) {
        int calls = 0;
        for (EndpointReport endpoint : extractor.getReport(namespace).getEndpoints()) {
            if (template == null || endpoint.getTemplate().equals(template)) {
                calls += endpoint.getCalls().size();
            }
        }
        return calls;
    }

    /** Fills the right-click menu for what is selected in the tree. */
    private void fillTreeMenu() {
        treeMenu.removeAll();
        ApiNamespace namespace = selectedNamespace();
        if (namespace == null) {
            return;
        }
        if (selectedTemplate() == null) {
            treeMenu.add(
                    menuItem(text(namespace.isExtracting() ? "stop" : "start"), this::onStartStop));
        }
        treeMenu.add(menuItem(text("clear"), this::onClear));
        treeMenu.add(menuItem(text("remove"), this::onRemove));
        if (selectedTemplate() == null)
            treeMenu.add(menuItem(VerificationText.text("unmatchedPaths"), this::onShowUnmatched));
    }

    private void onShowUnmatched() {
        ApiNamespace namespace = selectedNamespace();
        if (namespace == null) return;
        JDialog existing = unmatchedDialogs.get(namespace);
        if (existing != null) {
            existing.toFront();
            return;
        }
        JDialog dialog =
                new JDialog(
                        SwingUtilities.getWindowAncestor(this),
                        VerificationText.text("unmatchedPaths"));
        dialog.setName("unmatchedDialog");
        dialog.setContentPane(new UnmatchedPathsPanel(extractor, namespace));
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setSize(700, 450);
        dialog.setLocationRelativeTo(this);
        dialog.addWindowListener(
                new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent event) {
                        unmatchedDialogs.remove(namespace);
                    }
                });
        unmatchedDialogs.put(namespace, dialog);
        dialog.setVisible(true);
    }

    private static JMenuItem menuItem(String label, Runnable action) {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(e -> action.run());
        return item;
    }

    private void onSelectNamespace() {
        ApiNamespace namespace = selectedNamespace();
        updateButtons(namespace);
        tabs.setEnabledAt(2, namespace != null && selectedTemplate() != null);
        specificationPanel.showEndpoint(namespace, selectedTemplate());
        if (namespace == null) {
            callModel.setEndpoints(List.of());
            updateCallCount();
            onSelectCall();
            Color hint = hintColour();
            reportPane.setText(
                    String.format(
                            "<html><body><p><font color=\"#%02x%02x%02x\">%s</font></p></body></html>",
                            hint.getRed(), hint.getGreen(), hint.getBlue(), text("empty")));
        } else {
            showReport(namespace);
        }
    }

    /** The documentation is only written while its tab is shown, so write it when it appears. */
    private void onSelectTab() {
        onSelectNamespace();
    }

    /**
     * Shows the headers of the selected call, and its body when Full body is ticked, with JSON
     * indented. Plain text, so nothing from traffic is ever rendered as HTML here.
     */
    private void onSelectCall() {
        RecordedCall selected = selectedCall();
        if (selected == null) {
            detailArea.setForeground(hintColour());
            detailArea.setText(text("detail.empty"));
            return;
        }
        detailArea.setForeground(UIManager.getColor("TextArea.foreground"));
        ApiCall call = selected.getCall();
        StringBuilder detail = new StringBuilder();
        var request = call.getRequest();
        if (!request.getQuery().isEmpty() || !request.getHeaders().isEmpty() || request.hasBody()) {
            String decodedQuery =
                    selected.getArguments().stream()
                            .filter(a -> a.getLocation() == ArgumentLocation.QUERY)
                            .map(a -> a.getName() + "=" + a.getValue())
                            .collect(java.util.stream.Collectors.joining("&"));
            detail.append(call.getMethod())
                    .append(' ')
                    .append(call.getUrl())
                    .append(request.getQuery().isEmpty() ? "" : "?" + decodedQuery)
                    .append("\n\n");
            detail.append(text("detail.request.query"))
                    .append('\n')
                    .append(decodedQuery)
                    .append("\n\n");
            detail.append(text("detail.request.headers")).append('\n');
            for (var header : request.getHeaders()) {
                detail.append(header.getName()).append(": ").append(header.getValue()).append('\n');
            }
            detail.append('\n')
                    .append(text("detail.request.body"))
                    .append('\n')
                    .append(
                            showFullBody
                                    ? writer.formatBody(
                                            "Content-Type: " + request.getContentType(),
                                            request.getBody())
                                    : text("detail.nobody"))
                    .append("\n\n");
        }
        int checkStart = -1, checkEnd = -1;
        int selectedRow = callTable.getSelectedRow();
        if (selectedRow >= 0) {
            detail.append(VerificationText.text("checks")).append('\n');
            checkStart = detail.length();
            for (String line :
                    checkWriter.lines(
                            callModel.getVerdictAt(callTable.convertRowIndexToModel(selectedRow))))
                detail.append(line).append('\n');
            checkEnd = detail.length();
            detail.append('\n');
        }
        detail.append(Constant.messages.getString("restextraction.report.headers")).append('\n');
        detail.append(call.getResponseHeaders().strip()).append("\n\n");
        detail.append(Constant.messages.getString("restextraction.report.body")).append('\n');
        detail.append(
                showFullBody
                        ? writer.formatBody(call.getResponseHeaders(), call.getResponseBody())
                        : text("detail.nobody"));
        String shown = detail.toString();
        // Setting the same text again would move the view back to the top on every redraw.
        boolean changed = !shown.equals(detailArea.getText());
        if (changed) {
            detailArea.setText(shown);
            detailArea.setCaretPosition(0);
        }
        boolean dark = DisplayUtils.isDarkLookAndFeel();
        if (changed || dark != detailDark) {
            styleChecks(shown, checkStart, checkEnd);
            detailDark = dark;
        }
    }

    /**
     * Styled plain text: traffic stays inert; only generated check lines receive semantic colours.
     */
    private void styleChecks(String shown, int start, int end) {
        SimpleAttributeSet normal = new SimpleAttributeSet();
        StyleConstants.setForeground(normal, UIManager.getColor("TextArea.foreground"));
        detailArea.getStyledDocument().setCharacterAttributes(0, shown.length(), normal, true);
        for (int at = start; at >= 0 && at < end; ) {
            int next = shown.indexOf('\n', at);
            if (next < 0 || next > end) next = end;
            char mark = shown.charAt(at);
            if (mark == '✓' || mark == '✗' || mark == '⚠') {
                SimpleAttributeSet colour = new SimpleAttributeSet();
                StyleConstants.setForeground(colour, VerificationText.colour(mark));
                detailArea.getStyledDocument().setCharacterAttributes(at, next - at, colour, false);
            }
            at = next + 1;
        }
    }

    private void onToggleFullBody() {
        showFullBody = fullBodyBox.isSelected();
        onSelectCall();
    }

    /** Shows the whole namespace, or only the selected endpoint when one is selected. */
    private void showReport(ApiNamespace namespace) {
        ApiReport report = extractor.getReport(namespace);
        reports.put(namespace, report);
        String template = selectedTemplate();
        specificationPanel.showEndpoint(namespace, template);
        if (template != null) {
            List<EndpointReport> selected = new ArrayList<>();
            for (EndpointReport endpoint : report.getEndpoints()) {
                if (endpoint.getTemplate().equals(template)) {
                    selected.add(endpoint);
                }
            }
            report = new ApiReport(report.getBaseUrl(), selected);
        }
        showCalls(report);
        onSelectCall();
        // Writing the HTML costs time for every call, so only do it when someone looks at it.
        if (tabs.getSelectedIndex() == DOCUMENTATION_TAB) {
            reportPane.setText(writer.write(report));
        }
    }

    /** Fills the table and keeps the selected call selected when the rows change. */
    private void showCalls(ApiReport report) {
        RecordedCall selected = selectedCall();
        callModel.setEndpoints(report.getEndpoints());
        int modelRow = selected == null ? -1 : callModel.rowOf(selected);
        int newRow = modelRow == -1 ? -1 : callTable.convertRowIndexToView(modelRow);
        if (newRow != -1 && callTable.getSelectedRow() != newRow) {
            callTable.setRowSelectionInterval(newRow, newRow);
        }
        updateCallCount();
    }

    /** The call of the selected row. The table may be sorted, so its rows are not the model's. */
    private RecordedCall selectedCall() {
        int row = callTable.getSelectedRow();
        return row == -1 ? null : callModel.getCallAt(callTable.convertRowIndexToModel(row));
    }

    /** The template of the selected endpoint, or null when a namespace or nothing is selected. */
    private String selectedTemplate() {
        TreePath path = namespaceTree.getSelectionPath();
        if (path == null || path.getPathCount() < 3) {
            return null;
        }
        return (String) ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
    }

    /**
     * Asks every namespace for its report again, so the tree shows the current state and counts.
     */
    private void refreshTree() {
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) root.getChildAt(i);
            ApiNamespace namespace = (ApiNamespace) node.getUserObject();
            ApiReport report = extractor.getReport(namespace);
            reports.put(namespace, report);
            for (EndpointReport endpoint : report.getEndpoints()) {
                boolean exists = false;
                for (int j = 0; j < node.getChildCount(); j++)
                    if (((DefaultMutableTreeNode) node.getChildAt(j))
                            .getUserObject()
                            .equals(endpoint.getTemplate())) exists = true;
                if (!exists)
                    treeModel.insertNodeInto(
                            new DefaultMutableTreeNode(endpoint.getTemplate(), false),
                            node,
                            node.getChildCount());
            }
            for (int j = node.getChildCount() - 1; j >= 0; j--) {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(j);
                if (report.getEndpoints().stream()
                        .noneMatch(e -> e.getTemplate().equals(child.getUserObject())))
                    treeModel.removeNodeFromParent(child);
            }
            JDialog dialog = unmatchedDialogs.get(namespace);
            if (dialog != null) ((UnmatchedPathsPanel) dialog.getContentPane()).refresh();
            treeModel.nodeChanged(node);
            for (int j = 0; j < node.getChildCount(); j++) {
                treeModel.nodeChanged(node.getChildAt(j));
            }
        }
    }

    /** The node of the selected namespace, also when one of its endpoints is selected. */
    private DefaultMutableTreeNode selectedNamespaceNode() {
        TreePath path = namespaceTree.getSelectionPath();
        if (path == null) {
            return null;
        }
        // The path is root, namespace and maybe endpoint.
        return (DefaultMutableTreeNode) path.getPathComponent(1);
    }

    private ApiNamespace selectedNamespace() {
        DefaultMutableTreeNode node = selectedNamespaceNode();
        return node == null ? null : (ApiNamespace) node.getUserObject();
    }

    /** The Start/Stop button always tells what a click will do for the selected namespace. */
    private void updateButtons(ApiNamespace selected) {
        addEndpointButton.setEnabled(selected != null);
        patternField.setEnabled(selected != null);
        startStopButton.setEnabled(selected != null);
        clearButton.setEnabled(selected != null);
        removeButton.setEnabled(selected != null);
        unmatchedButton.setEnabled(selected != null && selectedTemplate() == null);
        boolean extracting = selected != null && selected.isExtracting();
        startStopButton.setText(text(extracting ? "stop" : "start"));
    }

    private static DocumentListener onChange(Runnable action) {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                action.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                action.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                action.run();
            }
        };
    }

    /** Shows only the calls with the chosen method that contain the filter text. */
    private void onFilter() {
        int index = methodFilter.getSelectedIndex();
        HttpMethod method = index <= 0 ? null : HttpMethod.values()[index - 1];
        String text = textFilter.getText();
        if (method == null && text.isBlank() && !onlyInvalidBox.isSelected()) {
            callSorter.setRowFilter(null);
        } else {
            callSorter.setRowFilter(
                    new RowFilter<CallTableModel, Integer>() {
                        @Override
                        public boolean include(
                                Entry<? extends CallTableModel, ? extends Integer> entry) {
                            return callModel.matches(
                                    entry.getIdentifier(),
                                    method,
                                    text,
                                    onlyInvalidBox.isSelected());
                        }
                    });
        }
        updateCallCount();
        onSelectCall();
    }

    /** How many calls the table shows, and of how many when a filter hides some. */
    private void updateCallCount() {
        int all = callModel.getRowCount();
        int shown = callTable.getRowCount();
        if (callModel.hasSpecification()) {
            int valid = 0, invalid = 0;
            for (int row = 0; row < shown; row++) {
                VerdictStatus status =
                        callModel.getVerdictAt(callTable.convertRowIndexToModel(row)).getStatus();
                if (status == VerdictStatus.VALID) valid++;
                if (status == VerdictStatus.INVALID) invalid++;
            }
            callCount.setText(VerificationText.text("counts", shown, all, valid, invalid));
            return;
        }
        callCount.setText(
                shown == all
                        ? Constant.messages.getString(PREFIX + "count.all", all)
                        : Constant.messages.getString(PREFIX + "count.filtered", shown, all));
    }

    private void clearMessage() {
        if (!messageArea.getText().isEmpty()) {
            showMessage("");
        }
    }

    /** Shows why something was refused, in the error colour, or clears the message. */
    private void showMessage(String message) {
        messageArea.setForeground(errorColour());
        messageArea.setText(message);
    }

    /** The same red as server errors in the call table, with a lighter one for dark themes. */
    private static Color errorColour() {
        return DisplayUtils.isDarkLookAndFeel() ? new Color(0xFF6B6B) : new Color(0xC62828);
    }

    /** The colour ZAP uses for disabled labels, for hints that are not data. */
    private static Color hintColour() {
        Color colour = UIManager.getColor("Label.disabledForeground");
        return colour != null ? colour : Color.GRAY;
    }

    /** The user can drag the divider, the report gets the extra width when the tab grows. */
    private JSplitPane createSplitPane() {
        JSplitPane split =
                new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createInputSide(), createReportSide());
        split.setName("split");
        split.setResizeWeight(0);
        split.setContinuousLayout(true);
        // Without this the call table, which asks for a lot of width, squeezes the input side.
        split.setDividerLocation(INPUT_WIDTH);
        split.setBorder(null);
        return split;
    }

    private JPanel createInputSide() {
        namespaceTree.setName("namespaces");
        // An API without endpoints yet still shows as a folder, like the others.
        treeModel.setAsksAllowsChildren(true);
        namespaceTree.setRootVisible(false);
        namespaceTree.setShowsRootHandles(true);
        namespaceTree
                .getSelectionModel()
                .setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        namespaceTree.setCellRenderer(new NamespaceTreeRenderer());
        baseUrlField.setName("baseUrl");
        patternField.setName("pattern");
        // Shown while a field is empty by FlatLaf, the look and feel ZAP uses; others ignore it.
        baseUrlField.putClientProperty(PLACEHOLDER, text("baseurl.placeholder"));
        patternField.putClientProperty(PLACEHOLDER, text("pattern.placeholder"));
        addNamespaceButton.setName("addNamespace");
        addEndpointButton.setName("addEndpoint");
        startStopButton.setName("startStop");
        clearButton.setName("clear");
        removeButton.setName("remove");
        treeMenu.setName("treeMenu");
        treeMenu.addPopupMenuListener(
                new PopupMenuListener() {
                    @Override
                    public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                        fillTreeMenu();
                    }

                    @Override
                    public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {}

                    @Override
                    public void popupMenuCanceled(PopupMenuEvent e) {}
                });
        namespaceTree.setComponentPopupMenu(treeMenu);
        // A right click selects the node under the mouse first, so the menu acts on that node.
        namespaceTree.addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        if (SwingUtilities.isRightMouseButton(e)) {
                            TreePath path = namespaceTree.getPathForLocation(e.getX(), e.getY());
                            if (path != null) {
                                namespaceTree.setSelectionPath(path);
                            }
                        }
                    }
                });
        namespaceTree
                .getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "remove");
        namespaceTree
                .getActionMap()
                .put(
                        "remove",
                        new AbstractAction() {
                            private static final long serialVersionUID = 1L;

                            @Override
                            public void actionPerformed(ActionEvent e) {
                                onRemove();
                            }
                        });
        messageArea.setName("message");
        // A plain text area and not an HTML label: the message repeats what the user typed.
        messageArea.setEditable(false);
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        messageArea.setOpaque(false);
        messageArea.setFont(new JLabel().getFont());

        JPanel side = new JPanel(new GridBagLayout());
        side.setBorder(BorderFactory.createEmptyBorder(GAP, GAP, GAP, GAP));
        side.setPreferredSize(new Dimension(INPUT_WIDTH, 0));
        side.setMinimumSize(new Dimension(MIN_INPUT_WIDTH, 0));
        addRow(side, 0, new JLabel(text("baseurl")), null, 0);
        addRow(side, 1, baseUrlField, addNamespaceButton, 0);
        addRow(side, 2, new JScrollPane(namespaceTree), null, 1);
        addRow(side, 3, leftAligned(startStopButton, clearButton, removeButton), null, 0);
        addRow(side, 4, leftAligned(unmatchedButton), null, 0);
        addRow(side, 5, new JLabel(text("pattern")), null, 0);
        addRow(side, 6, patternField, addEndpointButton, 0);
        addRow(side, 7, messageArea, null, 0);
        return side;
    }

    /**
     * Puts one row in the input side: a component that takes the free width and an optional button
     * to the right of it.
     *
     * @param weighty 1 for the one row that takes the free height, 0 for the others
     */
    private static void addRow(JPanel side, int row, Component main, JButton button, int weighty) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.gridx = 0;
        c.gridwidth = button == null ? 2 : 1;
        c.weightx = 1;
        c.weighty = weighty;
        c.fill = weighty == 1 ? GridBagConstraints.BOTH : GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(0, 0, GAP, 0);
        side.add(main, c);
        if (button != null) {
            GridBagConstraints b = new GridBagConstraints();
            b.gridy = row;
            b.gridx = 1;
            b.fill = GridBagConstraints.HORIZONTAL;
            b.anchor = GridBagConstraints.NORTHWEST;
            b.insets = new Insets(0, GAP, GAP, 0);
            side.add(button, b);
        }
    }

    private static JPanel leftAligned(JButton... buttons) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        for (JButton button : buttons) {
            if (row.getComponentCount() > 0) {
                row.add(Box.createHorizontalStrut(GAP));
            }
            row.add(button);
        }
        return row;
    }

    private JPanel createReportSide() {
        callTable.setName("calls");
        // A click on a column header sorts by that column, a second click the other way round.
        callTable.setRowSorter(callSorter);
        callTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        callTable.setFillsViewportHeight(true);
        callTable.setPreferredScrollableViewportSize(new Dimension(TABLE_WIDTH, TABLE_HEIGHT));
        int[] widths = {70, 70, 60, 220, 320, 65};
        for (int i = 0; i < widths.length; i++) {
            callTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        callTable.getColumnModel().getColumn(STATUS_COLUMN).setCellRenderer(new StatusRenderer());
        callTable
                .getColumnModel()
                .getColumn(5)
                .setCellRenderer(
                        new DefaultTableCellRenderer() {
                            private static final long serialVersionUID = 1L;

                            @Override
                            public Component getTableCellRendererComponent(
                                    JTable table,
                                    Object value,
                                    boolean selected,
                                    boolean focused,
                                    int row,
                                    int column) {
                                Component cell =
                                        super.getTableCellRendererComponent(
                                                table, value, selected, focused, row, column);
                                String mark = String.valueOf(value);
                                if (!selected && !mark.isEmpty())
                                    cell.setForeground(VerificationText.colour(mark.charAt(0)));
                                return cell;
                            }
                        });
        tabs.setName("tabs");
        detailArea.setName("detail");
        detailArea.setEditable(false);
        detailArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, new JLabel().getFont().getSize()));
        JSplitPane calls =
                new JSplitPane(
                        JSplitPane.VERTICAL_SPLIT,
                        new JScrollPane(callTable),
                        new JScrollPane(detailArea));
        calls.setResizeWeight(0.5);
        calls.setBorder(null);
        fullBodyBox.setName("fullBody");
        methodFilter.setName("methodFilter");
        methodFilter.addItem(text("filter.allmethods"));
        for (HttpMethod method : HttpMethod.values()) {
            methodFilter.addItem(method.name());
        }
        methodFilter.addActionListener(e -> onFilter());
        textFilter.setName("textFilter");
        textFilter.putClientProperty(PLACEHOLDER, text("filter.placeholder"));
        textFilter.getDocument().addDocumentListener(onChange(this::onFilter));
        callCount.setName("callCount");
        JPanel countAndBody = new JPanel(new FlowLayout(FlowLayout.TRAILING, GAP, 0));
        countAndBody.add(callCount);
        // Full body only changes the detail view, so it sits in the Calls tab.
        countAndBody.add(fullBodyBox);
        onlyInvalidBox.setName("onlyInvalid");
        onlyInvalidBox.addActionListener(e -> onFilter());
        JPanel filterInputs = new JPanel(new BorderLayout(GAP, 0));
        filterInputs.add(methodFilter, BorderLayout.WEST);
        filterInputs.add(textFilter, BorderLayout.CENTER);
        filterInputs.add(onlyInvalidBox, BorderLayout.EAST);
        JPanel filters = new JPanel(new BorderLayout(GAP, 0));
        filters.setBorder(BorderFactory.createEmptyBorder(GAP, 0, GAP, 0));
        filters.add(filterInputs, BorderLayout.CENTER);
        filters.add(countAndBody, BorderLayout.SOUTH);
        JPanel callsTab = new JPanel(new BorderLayout());
        callsTab.add(filters, BorderLayout.NORTH);
        callsTab.add(calls, BorderLayout.CENTER);
        tabs.addTab(text("tab.calls"), callsTab);
        reportPane.setName("report");
        reportPane.setContentType("text/html");
        reportPane.setEditable(false);
        // Keep the scroll position when the report is written again.
        ((DefaultCaret) reportPane.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);

        helpButton.setName("help");
        helpButton.setIcon(ExtensionHelp.getHelpIcon());
        helpButton.setToolTipText(text("help"));
        helpButton.addActionListener(e -> ExtensionHelp.showHelp(HELP_ID));
        JPanel top = new JPanel(new BorderLayout());
        top.add(helpButton, BorderLayout.EAST);

        JPanel side = new JPanel(new BorderLayout());
        side.add(top, BorderLayout.NORTH);
        tabs.addTab(text("tab.documentation"), new JScrollPane(reportPane));
        tabs.addTab(VerificationText.text("specification"), specificationPanel);
        side.add(tabs, BorderLayout.CENTER);
        return side;
    }

    private static String text(String key) {
        return Constant.messages.getString(PREFIX + key);
    }

    /**
     * Gives redirects, client errors and server errors their own colour, so failed calls stand out.
     * There is a second set for ZAP's dark themes, so the text stays readable.
     */
    private static class StatusRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        private static final Color[] LIGHT = {
            new Color(0x1F5FAF), new Color(0xA65200), new Color(0xC62828)
        };
        private static final Color[] DARK = {
            new Color(0x6FA8FF), new Color(0xFFB050), new Color(0xFF6B6B)
        };

        @Override
        public Component getTableCellRendererComponent(
                JTable table,
                Object value,
                boolean selected,
                boolean focused,
                int row,
                int column) {
            Component cell =
                    super.getTableCellRendererComponent(
                            table, value, selected, focused, row, column);
            if (!selected) {
                cell.setForeground(colourOf((Integer) value, table.getForeground()));
            }
            return cell;
        }

        private static Color colourOf(int status, Color normal) {
            Color[] colours = DisplayUtils.isDarkLookAndFeel() ? DARK : LIGHT;
            if (status >= 500) {
                return colours[2];
            }
            if (status >= 400) {
                return colours[1];
            }
            if (status >= 300) {
                return colours[0];
            }
            return normal;
        }
    }

    /**
     * Shows a namespace with its state and number of calls, like {@code https://api.example.com
     * (running, 12 calls)}, and an endpoint with its template and number of calls.
     */
    private class NamespaceTreeRenderer extends DefaultTreeCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            String label = getText();
            int valid = label == null ? -1 : label.lastIndexOf("✓ ");
            int invalid = label == null ? -1 : label.lastIndexOf("✗ ");
            if (valid < 0 || invalid < valid) return;
            var metrics = graphics.getFontMetrics(getFont());
            Rectangle icon = new Rectangle(), text = new Rectangle();
            Insets insets = getInsets();
            Rectangle view =
                    new Rectangle(
                            insets.left,
                            insets.top,
                            getWidth() - insets.left - insets.right,
                            getHeight() - insets.top - insets.bottom);
            SwingUtilities.layoutCompoundLabel(
                    this,
                    metrics,
                    label,
                    getIcon(),
                    getVerticalAlignment(),
                    getHorizontalAlignment(),
                    getVerticalTextPosition(),
                    getHorizontalTextPosition(),
                    view,
                    icon,
                    text,
                    getIconTextGap());
            int baseline = text.y + metrics.getAscent();
            graphics.setColor(VerificationText.colour('✓'));
            graphics.drawString(
                    label.substring(valid, invalid),
                    text.x + metrics.stringWidth(label.substring(0, valid)),
                    baseline);
            graphics.setColor(VerificationText.colour('✗'));
            graphics.drawString(
                    label.substring(invalid),
                    text.x + metrics.stringWidth(label.substring(0, invalid)),
                    baseline);
        }

        @Override
        public Component getTreeCellRendererComponent(
                JTree tree,
                Object value,
                boolean selected,
                boolean expanded,
                boolean leaf,
                int row,
                boolean focused) {
            return super.getTreeCellRendererComponent(
                    tree,
                    labelOf((DefaultMutableTreeNode) value),
                    selected,
                    expanded,
                    leaf,
                    row,
                    focused);
        }
    }

    private String labelOf(DefaultMutableTreeNode node) {
        if (node.getUserObject() instanceof ApiNamespace) {
            ApiNamespace namespace = (ApiNamespace) node.getUserObject();
            int calls = 0;
            for (EndpointReport endpoint : reportOf(namespace).getEndpoints()) {
                calls += endpoint.getCalls().size();
            }
            String state = text(namespace.isExtracting() ? "tree.running" : "tree.stopped");
            String label =
                    Constant.messages.getString(
                            PREFIX + "tree.namespace",
                            namespace.getBaseUrl(),
                            state,
                            callsText(calls));
            return verificationLabel(label, reportOf(namespace).getEndpoints());
        }
        if (node.getUserObject() instanceof String) {
            String template = (String) node.getUserObject();
            ApiNamespace namespace =
                    (ApiNamespace) ((DefaultMutableTreeNode) node.getParent()).getUserObject();
            int calls = 0;
            for (EndpointReport endpoint : reportOf(namespace).getEndpoints()) {
                if (endpoint.getTemplate().equals(template)) {
                    calls = endpoint.getCalls().size();
                }
            }
            String label =
                    Constant.messages.getString(
                            PREFIX + "tree.endpoint", template, callsText(calls));
            return verificationLabel(
                    label,
                    reportOf(namespace).getEndpoints().stream()
                            .filter(e -> e.getTemplate().equals(template))
                            .toList());
        }
        return "";
    }

    private String verificationLabel(String label, List<EndpointReport> endpoints) {
        if (endpoints.stream().allMatch(e -> e.getSpec().isEmpty())) return label;
        int valid = endpoints.stream().mapToInt(EndpointReport::getValidCount).sum();
        int invalid = endpoints.stream().mapToInt(EndpointReport::getInvalidCount).sum();
        // ZAP disables HTML rendering for labels. Paint semantic count colours in the
        // tree renderer rather than displaying markup literally or enabling unsafe HTML.
        return label + "  ✓ " + valid + "  ✗ " + invalid;
    }

    private ApiReport reportOf(ApiNamespace namespace) {
        return reports.computeIfAbsent(namespace, extractor::getReport);
    }

    private static String callsText(int calls) {
        return Constant.messages.getString(PREFIX + "tree.calls", calls);
    }
}
