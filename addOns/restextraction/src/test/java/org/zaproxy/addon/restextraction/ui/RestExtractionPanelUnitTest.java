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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zaproxy.addon.restextraction.ExtensionRestExtraction;
import org.zaproxy.addon.restextraction.model.ApiCall;
import org.zaproxy.addon.restextraction.model.ApiNamespace;
import org.zaproxy.addon.restextraction.model.BodyState;
import org.zaproxy.addon.restextraction.model.Header;
import org.zaproxy.addon.restextraction.model.HttpMethod;
import org.zaproxy.addon.restextraction.model.RequestData;
import org.zaproxy.addon.restextraction.model.RestExtractor;
import org.zaproxy.zap.testutils.TestUtils;

/** Unit test for {@link RestExtractionPanel}, with a real extractor. */
class RestExtractionPanelUnitTest extends TestUtils {

    private static final String BASE_URL = "https://jsonplaceholder.typicode.com";
    private static final String TEMPLATE = "/posts/[POST_ID]/comments";
    private static final String BODY = "[{\"postId\":1223,\"email\":\"eliseo@gardner.biz\"}]";

    private RestExtractor extractor;
    private RestExtractionPanel panel;

    @BeforeEach
    void setUp() throws Exception {
        mockMessages(new ExtensionRestExtraction());
        extractor = new RestExtractor();
        SwingUtilities.invokeAndWait(() -> panel = new RestExtractionPanel(extractor));
    }

    /**
     * Stops the redraw timer of the panel. Otherwise it can redraw the tree, which reads message
     * texts, while the next test replaces the shared message mock, and that test gets no texts.
     */
    @AfterEach
    void stopRedrawTimer() throws Exception {
        SwingUtilities.invokeAndWait(() -> panel.removeNotify());
    }

    @Test
    void shouldShowLocalisedLabelsAndHintWhenCreated() throws Exception {
        onSwingThread(
                () -> {
                    // Given / When / Then
                    assertThat(panel.getName(), is("REST Extraction"));
                    assertThat(panel.getIcon(), is(notNullValue()));
                    assertThat(button("help").getToolTipText(), is("Help for REST Extraction"));
                    assertThat(button("help").getIcon(), is(notNullValue()));
                    assertThat(button("addNamespace").getText(), is("Add API"));
                    assertThat(button("addEndpoint").getText(), is("Add endpoint"));
                    assertThat(button("startStop").getText(), is("Start"));
                    assertThat(reportText(), containsString("Add the base URL of an API"));
                });
    }

    @Test
    void shouldShowExamplesInEmptyFields() throws Exception {
        onSwingThread(
                () -> {
                    // Given / When / Then
                    assertThat(
                            field("baseUrl").getClientProperty("JTextField.placeholderText"),
                            is("https://api.example.com"));
                    assertThat(
                            field("pattern").getClientProperty("JTextField.placeholderText"),
                            is("/posts/[POST_ID]"));
                });
    }

    @Test
    void shouldDisableEndpointAndStartControlsWhenNoNamespaceIsSelected() throws Exception {
        onSwingThread(
                () -> {
                    // Given / When / Then
                    assertThat(button("addEndpoint").isEnabled(), is(false));
                    assertThat(button("startStop").isEnabled(), is(false));
                    assertThat(field("pattern").isEnabled(), is(false));
                });
    }

    @Test
    void shouldPutInputAndReportSideInResizableSplitPane() throws Exception {
        onSwingThread(
                () -> {
                    // Given
                    JSplitPane split = (JSplitPane) find(panel, "split");

                    // When / Then
                    assertThat(
                            find((Container) split.getLeftComponent(), "baseUrl"),
                            is(notNullValue()));
                    assertThat(
                            find((Container) split.getRightComponent(), "report"),
                            is(notNullValue()));
                    assertThat(split.getResizeWeight(), is(0.0));
                });
    }

    @Test
    void shouldStartDividerAtWidthOfInputSide() throws Exception {
        onSwingThread(
                () -> {
                    // Given
                    JSplitPane split = (JSplitPane) find(panel, "split");

                    // When / Then
                    assertThat(split.getDividerLocation(), is(380));
                });
    }

    // M4: invalid or duplicate input

    @Test
    void shouldAddNamespaceToListAndSelectIt() throws Exception {
        onSwingThread(
                () -> {
                    // Given
                    addNamespace("HTTPS://JSONPlaceholder.typicode.com/");

                    // When / Then
                    assertThat(namespaceNodes().size(), is(1));
                    assertThat(selectedNamespace().getBaseUrl(), is(BASE_URL));
                    assertThat(field("baseUrl").getText(), is(""));
                    assertThat(message().getText(), is(""));
                    assertThat(reportText(), containsString(BASE_URL));
                    assertThat(reportText(), containsString("No endpoints yet."));
                    assertThat(button("addEndpoint").isEnabled(), is(true));
                });
    }

    @Test
    void shouldShowMessageAndAddNothingWhenBaseUrlIsInvalid() throws Exception {
        onSwingThread(
                () -> {
                    // Given
                    addNamespace("jsonplaceholder.typicode.com");

                    // When / Then
                    assertThat(namespaceNodes().size(), is(0));
                    assertThat(message().getText(), containsString("needs a scheme and a host"));
                    assertThat(field("baseUrl").getText(), is("jsonplaceholder.typicode.com"));
                });
    }

    @Test
    void shouldShowMessageAndAddNothingWhenBaseUrlIsAddedTwice() throws Exception {
        onSwingThread(
                () -> {
                    // Given
                    addNamespace(BASE_URL);
                    // When
                    addNamespace(BASE_URL + "/");

                    // Then
                    assertThat(namespaceNodes().size(), is(1));
                    assertThat(message().getText(), containsString("already added"));
                });
    }

    @Test
    void shouldShowEndpointInReportWhenAdded() throws Exception {
        onSwingThread(
                () -> {
                    // Given
                    addNamespace(BASE_URL);
                    // When
                    addEndpoint(TEMPLATE);

                    // Then
                    assertThat(reportText(), containsString("/posts/[POST_ID]/comments"));
                    assertThat(reportText(), containsString("No calls yet."));
                    assertThat(field("pattern").getText(), is(""));
                    assertThat(message().getText(), is(""));
                });
    }

    @Test
    void shouldShowMessageAndAddNothingWhenPatternIsInvalid() throws Exception {
        onSwingThread(
                () -> {
                    // Given
                    addNamespace(BASE_URL);
                    // When
                    addEndpoint("/posts/[POST_ID");

                    // Then
                    assertThat(message().getText(), containsString("not a valid variable"));
                    assertThat(reportText(), containsString("No endpoints yet."));
                });
    }

    @Test
    void shouldShowMessageWhenPatternIsAddedTwice() throws Exception {
        onSwingThread(
                () -> {
                    // Given
                    addNamespace(BASE_URL);
                    addEndpoint(TEMPLATE);
                    // When
                    addEndpoint(TEMPLATE);

                    // Then
                    assertThat(message().getText(), containsString("already added"));
                });
    }

    @Test
    void shouldClearMessageAfterValidInput() throws Exception {
        onSwingThread(
                () -> {
                    // Given
                    addNamespace("abc");
                    // When / Then
                    assertThat(message().getText(), is(not("")));

                    // When
                    addNamespace(BASE_URL);
                    // Then
                    assertThat(message().getText(), is(""));
                });
    }

    // The tree of namespaces and endpoints

    @Test
    void shouldShowEveryNamespaceWithItsEndpointsInTree() throws Exception {
        onSwingThread(
                () -> {
                    // Given
                    addNamespace(BASE_URL);
                    addEndpoint(TEMPLATE);
                    addEndpoint("/users/[USER_ID]");
                    // When
                    addNamespace("http://localhost:3000");
                    addEndpoint("/api/Products/[ID]");

                    // Then
                    List<DefaultMutableTreeNode> namespaces = namespaceNodes();
                    assertThat(namespaces.size(), is(2));
                    assertThat(
                            childrenOf(namespaces.get(0)).stream()
                                    .map(DefaultMutableTreeNode::getUserObject)
                                    .collect(Collectors.toList()),
                            contains(TEMPLATE, "/users/[USER_ID]"));
                    assertThat(
                            childrenOf(namespaces.get(1)).get(0).getUserObject(),
                            is("/api/Products/[ID]"));
                    assertThat(
                            tree().isExpanded(new TreePath(namespaces.get(0).getPath())), is(true));
                });
    }

    @Test
    void shouldShowFullBaseUrlStateAndCallsInTree() throws Exception {
        onSwingThread(
                () -> {
                    // Given
                    addNamespace(BASE_URL);
                    // When
                    addEndpoint(TEMPLATE);

                    // Then
                    DefaultMutableTreeNode namespace = namespaceNodes().get(0);
                    assertThat(labelOf(namespace), is(BASE_URL + " (stopped, no calls)"));
                    assertThat(labelOf(childrenOf(namespace).get(0)), is(TEMPLATE + " (no calls)"));
                });
    }

    @Test
    void shouldUpdateStateAndCallsInTreeWhenCallsAreRecorded() throws Exception {
        // Given
        onSwingThread(this::addStartedNamespace);

        // When
        extractor.record(call());
        extractor.record(call("/posts/abcd/comments"));

        // Then
        waitForReportWith("GET: 2");
        AtomicReference<String> namespaceLabel = new AtomicReference<>();
        AtomicReference<String> endpointLabel = new AtomicReference<>();
        onSwingThread(
                () -> {
                    namespaceLabel.set(labelOf(namespaceNodes().get(0)));
                    endpointLabel.set(labelOf(childrenOf(namespaceNodes().get(0)).get(0)));
                });
        assertThat(namespaceLabel.get(), is(BASE_URL + " (running, 2 calls)"));
        assertThat(endpointLabel.get(), is(TEMPLATE + " (2 calls)"));
    }

    @Test
    void shouldShowNamespaceWithoutEndpointsAsFolder() throws Exception {
        onSwingThread(
                () -> {
                    // Given / When
                    addNamespace(BASE_URL);

                    // Then
                    assertThat(tree().getModel().isLeaf(namespaceNodes().get(0)), is(false));
                });
    }

    @Test
    void shouldAddEndpointToNamespaceOfSelectedEndpoint() throws Exception {
        onSwingThread(
                () -> {
                    // Given
                    addNamespace(BASE_URL);
                    addEndpoint(TEMPLATE);
                    select(childrenOf(namespaceNodes().get(0)).get(0));

                    // When
                    addEndpoint("/users/[USER_ID]");

                    // Then
                    assertThat(childrenOf(namespaceNodes().get(0)).size(), is(2));
                    assertThat(button("startStop").isEnabled(), is(true));
                    assertThat(selectedNamespace().getBaseUrl(), is(BASE_URL));
                });
    }

    @Test
    void shouldShowOnlySelectedEndpointInReport() throws Exception {
        onSwingThread(
                () -> {
                    // Given
                    addNamespace(BASE_URL);
                    addEndpoint(TEMPLATE);
                    addEndpoint("/users/[USER_ID]");

                    // When
                    select(childrenOf(namespaceNodes().get(0)).get(1));

                    // Then
                    assertThat(reportText(), containsString(BASE_URL));
                    assertThat(reportText(), containsString("/users/[USER_ID]"));
                    assertThat(reportText(), not(containsString(TEMPLATE)));

                    // When
                    select(namespaceNodes().get(0));

                    // Then
                    assertThat(reportText(), containsString(TEMPLATE));
                    assertThat(reportText(), containsString("/users/[USER_ID]"));
                });
    }

    @Test
    void shouldStartNamespaceOfSelectedEndpoint() throws Exception {
        onSwingThread(
                () -> {
                    // Given
                    addNamespace(BASE_URL);
                    addEndpoint(TEMPLATE);
                    select(childrenOf(namespaceNodes().get(0)).get(0));

                    // When
                    button("startStop").doClick();

                    // Then
                    assertThat(selectedNamespace().isExtracting(), is(true));
                    assertThat(button("startStop").getText(), is("Stop"));
                });
    }

    @Test
    void shouldUpdateReportOfSelectedEndpointWhenCallIsRecorded() throws Exception {
        // Given
        onSwingThread(
                () -> {
                    addStartedNamespace();
                    addEndpoint("/users/[USER_ID]");
                    select(childrenOf(namespaceNodes().get(0)).get(0));
                });

        // When
        extractor.record(call());

        // Then
        String report = waitForReportWith("GET: 1");
        assertThat(report, containsString("POST_ID = 1223"));
        assertThat(report, not(containsString("/users/[USER_ID]")));
    }

    // The call table

    @Test
    void shouldShowCallsOfSelectedNamespaceInTable() throws Exception {
        // Given
        onSwingThread(
                () -> {
                    addStartedNamespace();
                    addEndpoint("/users/[USER_ID]");
                });

        // When
        extractor.record(call());
        extractor.record(call("/users/7"));

        // Then
        waitForRows(2);
        onSwingThread(
                () -> {
                    JTable table = (JTable) find(panel, "calls");
                    assertThat(((JTabbedPane) find(panel, "tabs")).getSelectedIndex(), is(0));
                    assertThat(table.getValueAt(0, 3), is(TEMPLATE));
                    assertThat(table.getValueAt(0, 4), is("POST_ID = 1223"));
                    assertThat(table.getValueAt(1, 3), is("/users/[USER_ID]"));
                    assertThat(table.getValueAt(1, 4), is("USER_ID = 7"));
                });
    }

    @Test
    void shouldShowOnlyCallsOfSelectedEndpointInTable() throws Exception {
        // Given
        onSwingThread(
                () -> {
                    addStartedNamespace();
                    addEndpoint("/users/[USER_ID]");
                });
        extractor.record(call());
        extractor.record(call("/users/7"));
        waitForRows(2);

        // When
        onSwingThread(() -> select(childrenOf(namespaceNodes().get(0)).get(1)));

        // Then
        onSwingThread(
                () -> {
                    JTable table = (JTable) find(panel, "calls");
                    assertThat(table.getRowCount(), is(1));
                    assertThat(table.getValueAt(0, 4), is("USER_ID = 7"));
                });
    }

    @Test
    void shouldKeepSelectedCallWhenNewCallsArrive() throws Exception {
        // Given
        onSwingThread(this::addStartedNamespace);
        extractor.record(call());
        waitForRows(1);
        onSwingThread(() -> ((JTable) find(panel, "calls")).setRowSelectionInterval(0, 0));

        // When
        extractor.record(call("/posts/abcd/comments"));

        // Then
        waitForRows(2);
        onSwingThread(() -> assertThat(((JTable) find(panel, "calls")).getSelectedRow(), is(0)));
    }

    @Test
    void shouldEmptyTableWhenNothingIsSelected() throws Exception {
        // Given
        onSwingThread(this::addStartedNamespace);
        extractor.record(call());
        waitForRows(1);

        // When
        onSwingThread(() -> tree().clearSelection());

        // Then
        onSwingThread(() -> assertThat(((JTable) find(panel, "calls")).getRowCount(), is(0)));
    }

    @Test
    void shouldShowHeadersOfSelectedCallAndBodyOnlyWithFullBody() throws Exception {
        // Given
        onSwingThread(this::addStartedNamespace);
        extractor.record(call());
        waitForRows(1);

        // When
        onSwingThread(() -> ((JTable) find(panel, "calls")).setRowSelectionInterval(0, 0));

        // Then
        onSwingThread(
                () -> {
                    assertThat(detail(), containsString("Content-Type: application/json"));
                    assertThat(detail(), not(containsString(BODY)));
                    assertThat(detail(), containsString("Tick Full body"));
                });

        // When
        onSwingThread(() -> ((JCheckBox) find(panel, "fullBody")).doClick());

        // Then
        onSwingThread(
                () ->
                        assertThat(
                                detail(), containsString("    \"email\": \"eliseo@gardner.biz\"")));
    }

    @Test
    void shouldShowHintInDetailWhenNoCallIsSelected() throws Exception {
        // Given / When
        onSwingThread(this::addStartedNamespace);

        // Then
        onSwingThread(() -> assertThat(detail(), containsString("Select a call")));
    }

    @Test
    void shouldShowScriptInBodyAsPlainText() throws Exception {
        // Given
        onSwingThread(this::addStartedNamespace);
        extractor.record(
                new ApiCall(
                        HttpMethod.GET,
                        BASE_URL + "/posts/1/comments",
                        200,
                        "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n",
                        "<script>alert(1)</script>"));
        waitForRows(1);

        // When
        onSwingThread(
                () -> {
                    ((JCheckBox) find(panel, "fullBody")).doClick();
                    ((JTable) find(panel, "calls")).setRowSelectionInterval(0, 0);
                });

        // Then
        onSwingThread(
                () -> {
                    assertThat(find(panel, "detail") instanceof JTextArea, is(true));
                    assertThat(detail(), containsString("<script>alert(1)</script>"));
                });
    }

    @Test
    void shouldColourStatusCodesOfFailedCalls() throws Exception {
        // Given
        onSwingThread(this::addStartedNamespace);
        for (int status : new int[] {200, 302, 404, 500}) {
            extractor.record(
                    new ApiCall(
                            HttpMethod.GET,
                            BASE_URL + "/posts/1/comments",
                            status,
                            "HTTP/1.1 " + status + "\r\n\r\n",
                            ""));
        }
        waitForRows(4);

        // When
        List<Color> colours = new ArrayList<>();
        AtomicReference<Color> normal = new AtomicReference<>();
        onSwingThread(
                () -> {
                    JTable table = (JTable) find(panel, "calls");
                    normal.set(table.getForeground());
                    for (int row = 0; row < 4; row++) {
                        colours.add(
                                table.getCellRenderer(row, 2)
                                        .getTableCellRendererComponent(
                                                table,
                                                table.getValueAt(row, 2),
                                                false,
                                                false,
                                                row,
                                                2)
                                        .getForeground());
                    }
                });

        // Then
        assertThat(colours.get(0), is(normal.get()));
        assertThat(new HashSet<>(colours).size(), is(4));
    }

    // Sorting the call table

    @Test
    void shouldSortCallsByColumnAndShowDetailOfSelectedSortedRow() throws Exception {
        // Given
        onSwingThread(this::addStartedNamespace);
        for (int status : new int[] {200, 500, 404}) {
            extractor.record(
                    new ApiCall(
                            HttpMethod.GET,
                            BASE_URL + "/posts/" + status + "/comments",
                            status,
                            "HTTP/1.1 " + status + "\r\n\r\n",
                            ""));
        }
        waitForRows(3);

        onSwingThread(
                () -> {
                    JTable table = (JTable) find(panel, "calls");
                    // When
                    table.getRowSorter().toggleSortOrder(2);
                    // Then
                    assertThat(statusesOf(table), contains(200, 404, 500));

                    // When
                    table.getRowSorter().toggleSortOrder(2);
                    table.setRowSelectionInterval(0, 0);
                    // Then
                    assertThat(statusesOf(table), contains(500, 404, 200));
                    assertThat(detail(), containsString("HTTP/1.1 500"));
                });
    }

    @Test
    void shouldKeepSortOrderAndSelectedCallWhenNewCallsArrive() throws Exception {
        // Given
        onSwingThread(this::addStartedNamespace);
        extractor.record(call("/posts/2/comments"));
        waitForRows(1);
        onSwingThread(
                () -> {
                    JTable table = (JTable) find(panel, "calls");
                    table.getRowSorter().toggleSortOrder(4);
                    table.setRowSelectionInterval(0, 0);
                });

        // When
        extractor.record(call("/posts/1/comments"));

        // Then
        waitForRows(2);
        onSwingThread(
                () -> {
                    JTable table = (JTable) find(panel, "calls");
                    assertThat(table.getValueAt(0, 4), is("POST_ID = 1"));
                    assertThat(table.getValueAt(1, 4), is("POST_ID = 2"));
                    assertThat(table.getSelectedRow(), is(1));
                });
    }

    // Filtering the call table and the call count

    @Test
    void shouldShowOnlyCallsWithChosenMethodAndCountThem() throws Exception {
        // Given
        onSwingThread(this::addStartedNamespace);
        extractor.record(call());
        extractor.record(call(HttpMethod.POST, "/posts/1/comments"));
        extractor.record(call("/posts/2/comments"));
        waitForRows(3);

        onSwingThread(
                () -> {
                    JTable table = (JTable) find(panel, "calls");
                    // Then
                    assertThat(callCount(), is("3 calls"));

                    // When
                    methodFilter().setSelectedItem("POST");
                    // Then
                    assertThat(table.getRowCount(), is(1));
                    assertThat(table.getValueAt(0, 1), is("POST"));
                    assertThat(callCount(), is("1 of 3 calls"));

                    // When
                    methodFilter().setSelectedIndex(0);
                    // Then
                    assertThat(methodFilter().getSelectedItem(), is("All methods"));
                    assertThat(table.getRowCount(), is(3));
                    assertThat(callCount(), is("3 calls"));
                });
    }

    @Test
    void shouldShowOnlyCallsThatContainFilterText() throws Exception {
        // Given
        onSwingThread(this::addStartedNamespace);
        extractor.record(call());
        extractor.record(call("/posts/abcd/comments"));
        waitForRows(2);

        onSwingThread(
                () -> {
                    JTable table = (JTable) find(panel, "calls");
                    // When
                    field("textFilter").setText("post_id = ABCD");
                    // Then
                    assertThat(table.getRowCount(), is(1));
                    assertThat(table.getValueAt(0, 4), is("POST_ID = abcd"));
                    assertThat(callCount(), is("1 of 2 calls"));

                    // When
                    field("textFilter").setText("");
                    // Then
                    assertThat(table.getRowCount(), is(2));
                });
    }

    @Test
    void shouldFilterNewCallsAndCountThemWhenTheyArrive() throws Exception {
        // Given
        onSwingThread(
                () -> {
                    addStartedNamespace();
                    methodFilter().setSelectedItem("GET");
                });
        extractor.record(call());
        waitForRows(1);

        // When
        extractor.record(call(HttpMethod.DELETE, "/posts/1/comments"));
        extractor.record(call("/posts/2/comments"));

        // Then
        waitForRows(2);
        onSwingThread(() -> assertThat(callCount(), is("2 of 3 calls")));
    }

    @Test
    void shouldShowNoCallsInCountWhenNothingIsSelected() throws Exception {
        onSwingThread(
                () -> {
                    // Given / When / Then
                    assertThat(callCount(), is("No calls"));
                    assertThat(
                            field("textFilter").getClientProperty("JTextField.placeholderText"),
                            is("Filter, for example POST_ID = 1 or 404"));
                });
    }

    @Test
    void shouldShowCapturedRequestInPlainText() throws Exception {
        onSwingThread(this::addStartedNamespace);
        var request =
                new RequestData(
                        "tag=a&tag=b",
                        List.of(new Header("X-Token", "<secret>")),
                        "application/json",
                        "{\"title\":\"Hi\"}",
                        BodyState.TEXT);
        extractor.record(
                new ApiCall(
                        HttpMethod.POST,
                        BASE_URL + "/posts/1/comments",
                        200,
                        "HTTP/1.1 200 OK",
                        "",
                        request));
        waitForRows(1);
        onSwingThread(
                () -> {
                    ((JTable) find(panel, "calls")).setRowSelectionInterval(0, 0);
                    assertThat(detail(), containsString("Request query\ntag=a&tag=b"));
                    assertThat(detail(), containsString("X-Token: <secret>"));
                    assertThat(detail(), containsString("Request body\n{\"title\":\"Hi\"}"));
                });
    }

    @Test
    void shouldClearDetailWhenFilterHidesSelectedCall() throws Exception {
        // Given
        onSwingThread(this::addStartedNamespace);
        extractor.record(call());
        waitForRows(1);

        onSwingThread(
                () -> {
                    ((JTable) find(panel, "calls")).setRowSelectionInterval(0, 0);
                    // When
                    methodFilter().setSelectedItem("POST");
                    // Then
                    assertThat(detail(), containsString("Select a call"));
                });
    }

    @SuppressWarnings("unchecked")
    private JComboBox<String> methodFilter() {
        return (JComboBox<String>) find(panel, "methodFilter");
    }

    private String callCount() {
        return ((JLabel) find(panel, "callCount")).getText();
    }

    private static List<Integer> statusesOf(JTable table) {
        List<Integer> statuses = new ArrayList<>();
        for (int row = 0; row < table.getRowCount(); row++) {
            statuses.add((Integer) table.getValueAt(row, 2));
        }
        return statuses;
    }

    private String detail() {
        return ((JTextArea) find(panel, "detail")).getText();
    }

    /** Waits a few timer ticks until the call table has this many rows. */
    private void waitForRows(int rows) throws Exception {
        AtomicReference<Integer> count = new AtomicReference<>(0);
        for (int i = 0; i < 50 && count.get() != rows; i++) {
            Thread.sleep(100);
            onSwingThread(() -> count.set(((JTable) find(panel, "calls")).getRowCount()));
        }
        assertThat(count.get(), is(rows));
    }

    // M12: remove and clear

    @Test
    void shouldRemoveEndpointWithoutCallsWithoutAsking() throws Exception {
        // Given
        List<String> questions = answerConfirmations(true);
        onSwingThread(
                () -> {
                    addNamespace(BASE_URL);
                    addEndpoint(TEMPLATE);
                    addEndpoint("/users/[USER_ID]");
                    select(childrenOf(namespaceNodes().get(0)).get(0));

                    // When
                    button("remove").doClick();

                    // Then
                    assertThat(questions.size(), is(0));
                    assertThat(
                            childrenOf(namespaceNodes().get(0)).get(0).getUserObject(),
                            is("/users/[USER_ID]"));
                    assertThat(childrenOf(namespaceNodes().get(0)).size(), is(1));
                    assertThat(selectedNamespace().getBaseUrl(), is(BASE_URL));
                    assertThat(reportText(), not(containsString(TEMPLATE)));
                });
    }

    @Test
    void shouldAskBeforeRemovingNamespaceWithCalls() throws Exception {
        // Given
        List<String> questions = answerConfirmations(false);
        onSwingThread(this::addStartedNamespace);
        extractor.record(call());
        onSwingThread(
                () -> {
                    select(namespaceNodes().get(0));

                    // When
                    button("remove").doClick();

                    // Then
                    assertThat(questions.size(), is(1));
                    assertThat(questions.get(0), containsString(BASE_URL));
                    assertThat(questions.get(0), containsString("1 call"));
                    assertThat(namespaceNodes().size(), is(1));
                });
    }

    @Test
    void shouldRemoveNamespaceWhenConfirmedSoItCanBeAddedAgain() throws Exception {
        // Given
        answerConfirmations(true);
        onSwingThread(this::addStartedNamespace);
        extractor.record(call());
        onSwingThread(
                () -> {
                    select(namespaceNodes().get(0));

                    // When
                    button("remove").doClick();

                    // Then
                    assertThat(namespaceNodes().size(), is(0));
                    assertThat(button("startStop").isEnabled(), is(false));
                    assertThat(button("remove").isEnabled(), is(false));

                    // When
                    addNamespace(BASE_URL);

                    // Then
                    assertThat(namespaceNodes().size(), is(1));
                    assertThat(message().getText(), is(""));
                });
    }

    @Test
    void shouldClearCallsOfNamespaceWhenConfirmed() throws Exception {
        // Given
        List<String> questions = answerConfirmations(true);
        onSwingThread(this::addStartedNamespace);
        extractor.record(call());
        waitForRows(1);

        // When
        onSwingThread(
                () -> {
                    select(namespaceNodes().get(0));
                    button("clear").doClick();
                });

        // Then
        onSwingThread(
                () -> {
                    assertThat(questions.size(), is(1));
                    assertThat(((JTable) find(panel, "calls")).getRowCount(), is(0));
                    assertThat(
                            labelOf(namespaceNodes().get(0)),
                            is(BASE_URL + " (running, no calls)"));
                    assertThat(childrenOf(namespaceNodes().get(0)).size(), is(1));
                });
    }

    @Test
    void shouldClearCallsOfOnlySelectedEndpointWhenConfirmed() throws Exception {
        // Given
        List<String> questions = answerConfirmations(true);
        onSwingThread(
                () -> {
                    addStartedNamespace();
                    addEndpoint("/users/[USER_ID]");
                });
        extractor.record(call());
        extractor.record(call("/users/7"));
        waitForRows(2);

        // When
        onSwingThread(
                () -> {
                    select(childrenOf(namespaceNodes().get(0)).get(1));
                    button("clear").doClick();
                });

        // Then
        onSwingThread(
                () -> {
                    assertThat(questions.size(), is(1));
                    assertThat(questions.get(0), containsString("/users/[USER_ID]"));
                    assertThat(questions.get(0), containsString("1 call"));
                    assertThat(((JTable) find(panel, "calls")).getRowCount(), is(0));
                    assertThat(
                            labelOf(childrenOf(namespaceNodes().get(0)).get(0)),
                            is(TEMPLATE + " (1 call)"));
                    assertThat(
                            labelOf(childrenOf(namespaceNodes().get(0)).get(1)),
                            is("/users/[USER_ID] (no calls)"));
                });
    }

    @Test
    void shouldNotAskWhenThereIsNothingToClear() throws Exception {
        // Given
        List<String> questions = answerConfirmations(true);
        onSwingThread(
                () -> {
                    addStartedNamespace();

                    // When
                    button("clear").doClick();

                    // Then
                    assertThat(questions.size(), is(0));
                });
    }

    @Test
    void shouldRemoveSelectedNodeWithDeleteKey() throws Exception {
        // Given
        answerConfirmations(true);
        onSwingThread(
                () -> {
                    addNamespace(BASE_URL);
                    addEndpoint(TEMPLATE);
                    select(childrenOf(namespaceNodes().get(0)).get(0));
                    Object name =
                            tree().getInputMap(JComponent.WHEN_FOCUSED)
                                    .get(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));

                    // When
                    tree().getActionMap().get(name).actionPerformed(null);

                    // Then
                    assertThat(childrenOf(namespaceNodes().get(0)).size(), is(0));
                });
    }

    @Test
    void shouldOfferStartClearAndRemoveForNamespaceAndClearAndRemoveForEndpointInMenu()
            throws Exception {
        onSwingThread(
                () -> {
                    // Given
                    addNamespace(BASE_URL);
                    addEndpoint(TEMPLATE);
                    select(namespaceNodes().get(0));

                    // When
                    List<String> namespaceItems = menuItems();
                    select(childrenOf(namespaceNodes().get(0)).get(0));
                    List<String> endpointItems = menuItems();

                    // Then
                    assertThat(namespaceItems, contains("Start", "Clear calls", "Remove"));
                    assertThat(endpointItems, contains("Clear calls", "Remove"));
                });
    }

    /** Opens the right-click menu of the tree the way Swing does and gives its item texts. */
    private List<String> menuItems() {
        JPopupMenu menu = tree().getComponentPopupMenu();
        for (PopupMenuListener listener : menu.getPopupMenuListeners()) {
            listener.popupMenuWillBecomeVisible(new PopupMenuEvent(menu));
        }
        List<String> items = new ArrayList<>();
        for (Component item : menu.getComponents()) {
            items.add(((JMenuItem) item).getText());
        }
        return items;
    }

    /**
     * Replaces the panel with one that answers every confirmation itself.
     *
     * @return the questions it was asked
     */
    private List<String> answerConfirmations(boolean answer) throws Exception {
        List<String> questions = new ArrayList<>();
        onSwingThread(
                () ->
                        panel =
                                new RestExtractionPanel(extractor) {
                                    private static final long serialVersionUID = 1L;

                                    @Override
                                    boolean confirm(String message) {
                                        questions.add(message);
                                        return answer;
                                    }
                                });
        return questions;
    }

    // Messages and hints

    @Test
    void shouldShowRefusedInputInErrorColour() throws Exception {
        onSwingThread(
                () -> {
                    // Given
                    Color normal = new JLabel().getForeground();

                    // When
                    addNamespace("abc");

                    // Then
                    assertThat(message().getText(), is(not("")));
                    assertThat(message().getForeground(), is(not(normal)));
                });
    }

    @Test
    void shouldClearMessageWhenRefusedInputIsChanged() throws Exception {
        onSwingThread(
                () -> {
                    // Given
                    addNamespace("abc");

                    // When
                    field("baseUrl").setText("https://");

                    // Then
                    assertThat(message().getText(), is(""));
                    assertThat(field("baseUrl").getText(), is("https://"));
                });
    }

    @Test
    void shouldClearMessageWhenRefusedPatternIsChanged() throws Exception {
        onSwingThread(
                () -> {
                    // Given
                    addNamespace(BASE_URL);
                    addEndpoint("/posts/[POST_ID");

                    // When
                    field("pattern").setText("/posts/[POST_ID]");

                    // Then
                    assertThat(message().getText(), is(""));
                });
    }

    @Test
    void shouldShowHintInMutedColourAndCallInNormalColour() throws Exception {
        // Given
        onSwingThread(this::addStartedNamespace);
        AtomicReference<Color> hint = new AtomicReference<>();
        onSwingThread(() -> hint.set(find(panel, "detail").getForeground()));
        extractor.record(call());
        waitForRows(1);

        // When
        AtomicReference<Color> shown = new AtomicReference<>();
        onSwingThread(
                () -> {
                    ((JTable) find(panel, "calls")).setRowSelectionInterval(0, 0);
                    shown.set(find(panel, "detail").getForeground());
                });

        // Then
        assertThat(hint.get(), is(not(shown.get())));
    }

    // M5: Start/Stop state and label

    @Test
    void shouldStartAndStopSelectedNamespace() throws Exception {
        onSwingThread(
                () -> {
                    // Given
                    addNamespace(BASE_URL);
                    ApiNamespace namespace = selectedNamespace();

                    // When
                    button("startStop").doClick();
                    // Then
                    assertThat(namespace.isExtracting(), is(true));
                    assertThat(button("startStop").getText(), is("Stop"));

                    // When
                    button("startStop").doClick();
                    // Then
                    assertThat(namespace.isExtracting(), is(false));
                    assertThat(button("startStop").getText(), is("Start"));
                });
    }

    @Test
    void shouldShowStateOfSelectedNamespaceOnStartStopButton() throws Exception {
        onSwingThread(
                () -> {
                    // Given
                    addNamespace(BASE_URL);
                    button("startStop").doClick();
                    // When
                    addNamespace("http://localhost:3000");
                    // Then
                    assertThat(button("startStop").getText(), is("Start"));

                    // When
                    select(namespaceNodes().get(0));
                    // Then
                    assertThat(button("startStop").getText(), is("Stop"));
                    assertThat(reportText(), containsString(BASE_URL));

                    // When
                    select(namespaceNodes().get(1));
                    // Then
                    assertThat(button("startStop").getText(), is("Start"));
                    assertThat(reportText(), containsString("http://localhost:3000"));
                });
    }

    // M10: the Full body checkbox

    @Test
    void shouldNeverShowBodiesInDocumentation() throws Exception {
        onSwingThread(
                () -> {
                    // Given
                    addStartedNamespace();
                    // When
                    extractor.record(call());
                    JCheckBox fullBody = (JCheckBox) find(panel, "fullBody");
                    // Then
                    assertThat(fullBody.getText(), is("Full body"));
                    assertThat(fullBody.isSelected(), is(false));
                    assertThat(reportText(), not(containsString("eliseo@gardner.biz")));

                    // When
                    fullBody.doClick();
                    // Then
                    assertThat(reportText(), not(containsString("eliseo@gardner.biz")));
                    assertThat(reportText(), containsString("Content-Type: application/json"));
                });
    }

    // M6: live updates

    @Test
    void shouldRegisterItselfAsObserver() throws Exception {
        // Given
        RestExtractor mocked = mock(RestExtractor.class);
        AtomicReference<RestExtractionPanel> created = new AtomicReference<>();

        // When
        onSwingThread(() -> created.set(new RestExtractionPanel(mocked)));

        // Then
        verify(mocked).addObserver(created.get());
    }

    @Test
    void shouldUpdateReportWhenCallIsRecordedOnAnotherThread() throws Exception {
        // Given
        onSwingThread(this::addStartedNamespace);

        // When
        // The test thread plays the proxy thread here.
        extractor.record(call());

        // Then
        assertThat(waitForReportWith("GET: 1"), containsString("POST_ID = 1223"));
    }

    @Test
    void shouldNotUpdateReportWhenNothingWasRecorded() throws Exception {
        // Given
        onSwingThread(this::addStartedNamespace);

        // When
        extractor.record(call("/users/1"));
        Thread.sleep(3 * 500);

        AtomicReference<String> text = new AtomicReference<>();
        onSwingThread(() -> text.set(reportText()));
        // Then
        assertThat(text.get(), containsString("No calls yet."));
    }

    private void addStartedNamespace() {
        addNamespace(BASE_URL);
        addEndpoint(TEMPLATE);
        button("startStop").doClick();
    }

    /** Waits a few timer ticks for the report to show the text and gives the report back. */
    private String waitForReportWith(String expected) throws Exception {
        AtomicReference<String> text = new AtomicReference<>("");
        for (int i = 0; i < 50 && !text.get().contains(expected); i++) {
            Thread.sleep(100);
            onSwingThread(() -> text.set(reportText()));
        }
        assertThat(text.get(), containsString(expected));
        return text.get();
    }

    private static ApiCall call() {
        return call("/posts/1223/comments");
    }

    private static ApiCall call(String path) {
        return call(HttpMethod.GET, path);
    }

    private static ApiCall call(HttpMethod method, String path) {
        return new ApiCall(
                method,
                BASE_URL + path,
                200,
                "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n",
                BODY);
    }

    private void addNamespace(String baseUrl) {
        field("baseUrl").setText(baseUrl);
        button("addNamespace").doClick();
    }

    private void addEndpoint(String template) {
        field("pattern").setText(template);
        button("addEndpoint").doClick();
    }

    private JButton button(String name) {
        return (JButton) find(panel, name);
    }

    private JTextField field(String name) {
        return (JTextField) find(panel, name);
    }

    private JTextArea message() {
        return (JTextArea) find(panel, "message");
    }

    /** The text of the Documentation tab as the user reads it, without the HTML tags. */
    private String reportText() {
        ((JTabbedPane) find(panel, "tabs")).setSelectedIndex(1);
        Document document = ((JEditorPane) find(panel, "report")).getDocument();
        try {
            return document.getText(0, document.getLength());
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    private JTree tree() {
        return (JTree) find(panel, "namespaces");
    }

    private List<DefaultMutableTreeNode> namespaceNodes() {
        return childrenOf((DefaultMutableTreeNode) tree().getModel().getRoot());
    }

    private static List<DefaultMutableTreeNode> childrenOf(DefaultMutableTreeNode node) {
        List<DefaultMutableTreeNode> children = new ArrayList<>();
        for (int i = 0; i < node.getChildCount(); i++) {
            children.add((DefaultMutableTreeNode) node.getChildAt(i));
        }
        return children;
    }

    private ApiNamespace selectedNamespace() {
        TreePath path = tree().getSelectionPath();
        return (ApiNamespace) ((DefaultMutableTreeNode) path.getPathComponent(1)).getUserObject();
    }

    private void select(DefaultMutableTreeNode node) {
        tree().setSelectionPath(new TreePath(node.getPath()));
    }

    /** The text the tree shows for the node. */
    private String labelOf(DefaultMutableTreeNode node) {
        Component label =
                tree().getCellRenderer()
                        .getTreeCellRendererComponent(
                                tree(), node, false, false, node.isLeaf(), 0, false);
        return ((JLabel) label).getText();
    }

    private static Component find(Container container, String name) {
        for (Component component : container.getComponents()) {
            if (name.equals(component.getName())) {
                return component;
            }
            if (component instanceof Container) {
                Component found = find((Container) component, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** Runs the checks on the Swing thread and passes a failed assertion on to JUnit. */
    private static void onSwingThread(Runnable checks) throws Exception {
        try {
            SwingUtilities.invokeAndWait(checks);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Error) {
                throw (Error) e.getCause();
            }
            throw e;
        }
    }
}
