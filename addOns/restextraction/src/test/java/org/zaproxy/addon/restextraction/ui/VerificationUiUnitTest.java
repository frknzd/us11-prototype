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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zaproxy.addon.restextraction.ExtensionRestExtraction;
import org.zaproxy.addon.restextraction.model.ApiCall;
import org.zaproxy.addon.restextraction.model.ApiNamespace;
import org.zaproxy.addon.restextraction.model.ApiReport;
import org.zaproxy.addon.restextraction.model.ArgumentLocation;
import org.zaproxy.addon.restextraction.model.BodyState;
import org.zaproxy.addon.restextraction.model.Endpoint;
import org.zaproxy.addon.restextraction.model.EndpointSpec;
import org.zaproxy.addon.restextraction.model.Header;
import org.zaproxy.addon.restextraction.model.HttpMethod;
import org.zaproxy.addon.restextraction.model.IntegerType;
import org.zaproxy.addon.restextraction.model.ParameterSpec;
import org.zaproxy.addon.restextraction.model.RecordedCall;
import org.zaproxy.addon.restextraction.model.RequestData;
import org.zaproxy.addon.restextraction.model.RestExtractor;
import org.zaproxy.addon.restextraction.model.SpecItem;
import org.zaproxy.addon.restextraction.model.SpecItemKind;
import org.zaproxy.addon.restextraction.model.UrlPath;
import org.zaproxy.addon.restextraction.model.Verdict;
import org.zaproxy.addon.restextraction.model.VerdictStatus;
import org.zaproxy.zap.testutils.TestUtils;

class VerificationUiUnitTest extends TestUtils {
    @BeforeEach
    void messages() {
        mockMessages(new ExtensionRestExtraction());
    }

    private SpecItem query(String name) {
        return SpecItem.parameter(
                HttpMethod.GET,
                new ParameterSpec(
                        ArgumentLocation.QUERY,
                        name,
                        new IntegerType(BigInteger.ONE, BigInteger.TEN),
                        true));
    }

    private ApiCall call(String query) {
        return new ApiCall(
                HttpMethod.GET,
                "http://localhost/posts/1",
                200,
                "",
                "",
                new RequestData(query, List.of(), "", "", BodyState.NONE));
    }

    @Test
    void SM1_SM2_SM3_visibleFields() {
        SpecItemForm form = new SpecItemForm("/users/[UID]/posts/[PID]");
        assertEquals(List.of("UID", "PID"), form.pathVariables());
        for (SpecItemKind kind : SpecItemKind.values()) {
            form.setKind(kind);
            form.set(SpecField.TYPE, "String");
            assertTrue(form.visibleFields().contains(SpecField.METHOD));
            assertEquals(
                    kind == SpecItemKind.CONTENT_TYPE,
                    form.visibleFields().contains(SpecField.MEDIA_TYPE));
            assertEquals(
                    kind != SpecItemKind.METHOD && kind != SpecItemKind.CONTENT_TYPE,
                    form.visibleFields().contains(SpecField.TYPE));
            assertFalse(form.visibleFields().contains(SpecField.MIN));
            form.set(SpecField.TYPE, "Integer");
            assertEquals(
                    kind != SpecItemKind.METHOD && kind != SpecItemKind.CONTENT_TYPE,
                    form.visibleFields().contains(SpecField.MIN));
            assertEquals(
                    kind == SpecItemKind.QUERY_PARAMETER
                            || kind == SpecItemKind.BODY_FIELD
                            || kind == SpecItemKind.HEADER,
                    form.visibleFields().contains(SpecField.REQUIRED));
        }
    }

    @Test
    void SM4_SM5_SM6_SM7_roundTripAndRefusals() {
        SpecItemForm form = new SpecItemForm("/posts/[ID]");
        var item = query("n");
        form.load(item);
        assertEquals("n", form.toItem().getParameter().getName());
        assertEquals("1..10", form.toItem().getParameter().getType().describeRange());
        assertTrue(form.toItem().getParameter().isRequired());
        form.set(SpecField.MIN, "11");
        assertThrows(IllegalArgumentException.class, form::toItem);
        form.set(SpecField.MIN, "1");
        form.set(SpecField.TYPE, "String");
        assertEquals("", form.toItem().getParameter().getType().describeRange());
        form.setKind(SpecItemKind.PATH_VARIABLE);
        form.set(SpecField.NAME, "MISSING");
        assertThrows(IllegalArgumentException.class, form::toItem);
        form.set(SpecField.NAME, "ID");
        assertEquals("ID", form.toItem().getParameter().getName());
        form.setKind(SpecItemKind.BODY_FIELD);
        form.set(SpecField.NAME, "items[].id");
        form.set(SpecField.REQUIRED, "true");
        assertThrows(IllegalArgumentException.class, form::toItem);
        form.load(SpecItem.contentType(HttpMethod.POST, "application/json"));
        assertEquals("application/json", form.toItem().getMediaType());
        form.load(SpecItem.method(HttpMethod.DELETE));
        assertEquals(HttpMethod.DELETE, form.toItem().getMethod());
        SpecItemTableModel model = new SpecItemTableModel();
        model.setSpec(EndpointSpec.empty().with(item));
        assertEquals(2, model.getRowCount());
        assertEquals(6, model.getColumnCount());
        assertEquals("Method", model.getColumnName(0));
        assertEquals("n", model.getValueAt(1, 2));
        assertEquals("Integer", model.getValueAt(1, 3));
        assertEquals("1..10", model.getValueAt(1, 4));
        assertEquals("✓", model.getValueAt(1, 5));
    }

    @Test
    void CT1_CT2_CT3_CT4_rejudgedRowsFireUpdateAndFilter() {
        Endpoint endpoint = new Endpoint("/posts/[ID]");
        endpoint.record(call("n=1"), new UrlPath("/posts/1"));
        endpoint.record(call("n=x"), new UrlPath("/posts/1"));
        CallTableModel model = new CallTableModel();
        model.setEndpoints(List.of(endpoint.createReport()));
        assertEquals("", model.getValueAt(0, 5));
        List<Integer> events = new ArrayList<>();
        model.addTableModelListener(e -> events.add(e.getType()));
        endpoint.addSpecItem(query("n"));
        model.setEndpoints(List.of(endpoint.createReport()));
        assertFalse(events.isEmpty());
        assertEquals("✓", model.getValueAt(0, 5));
        assertEquals("✗ 1", model.getValueAt(1, 5));
        assertTrue(model.hasSpecification());
        assertFalse(model.matches(0, null, "", true));
        assertTrue(model.matches(1, HttpMethod.GET, "query n = x", true));
        assertFalse(model.matches(1, HttpMethod.POST, "", true));
        assertEquals(VerdictStatus.INVALID, model.getVerdictAt(1).getStatus());
    }

    @Test
    void CW1_CW2_CW3_CW4_WR1_WR2_headersEscapingAndCatalog() {
        var request =
                new RequestData(
                        "n=%3Cscript%3E",
                        List.of(new Header("X-Long", "x".repeat(300))),
                        "application/json",
                        "{\"title\":\"<img>\"}",
                        BodyState.TEXT);
        Endpoint endpoint = new Endpoint("/posts/[ID]");
        endpoint.record(
                new ApiCall(HttpMethod.GET, "http://localhost/posts/1", 200, "", "", request),
                new UrlPath("/posts/1"));
        endpoint.addSpecItem(query("n"));
        var report = endpoint.createReport();
        RecordedCall call = report.getCalls().get(0);
        Verdict v = report.getVerdict(call);
        CheckWriter checks = new CheckWriter();
        String html = checks.toHtml(v);
        assertTrue(html.contains("&lt;script&gt;"));
        assertFalse(html.contains("<script>"));
        assertTrue(String.join("\n", checks.lines(v)).contains("x".repeat(300)));
        assertEquals("✗ 1", checks.mark(v));
        assertEquals("", checks.mark(Verdict.notVerified(true)));
        assertTrue(checks.lines(Verdict.notVerified(true)).get(0).contains("preflight"));
        String documentation =
                new ApiDocumentationWriter()
                        .write(new ApiReport("http://localhost", List.of(report)));
        assertTrue(documentation.contains("Specification"));
        assertTrue(documentation.contains("&lt;img&gt;"));
        assertFalse(documentation.contains("<img>"));
        assertTrue(documentation.contains("Request body"));
        assertFalse(documentation.contains("restextraction.verification."));
    }

    @Test
    void PN_E_editorAddsEditsRefusesAndRemovesThroughRealFacade() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    try {
                        RestExtractor ex = new RestExtractor();
                        ApiNamespace ns = ex.addNamespace("http://localhost");
                        ex.addEndpoint(ns, "/posts/[ID]");
                        SpecificationPanel panel =
                                new SpecificationPanel(ex) {
                                    private static final long serialVersionUID = 1L;

                                    @Override
                                    protected boolean confirm(String message) {
                                        return true;
                                    }
                                };
                        panel.showEndpoint(ns, "/posts/[ID]");
                        combo(panel, "specKind").setSelectedItem(SpecItemKind.QUERY_PARAMETER);
                        field(panel, "specName").setText("n");
                        combo(panel, "specTYPE").setSelectedItem("Integer");
                        field(panel, "specMIN").setText("1");
                        field(panel, "specMAX").setText("10");
                        button(panel, "saveItem").doClick();
                        JTable table = (JTable) find(panel, "specItems");
                        assertEquals(2, table.getRowCount());
                        table.setRowSelectionInterval(1, 1);
                        button(panel, "editItem").doClick();
                        field(panel, "specMIN").setText("11");
                        button(panel, "saveItem").doClick();
                        assertTrue(
                                ((JLabel) find(panel, "specError")).getText().contains("minimum"));
                        assertEquals(2, table.getRowCount());
                        field(panel, "specMIN").setText("2");
                        button(panel, "saveItem").doClick();
                        assertEquals("2..10", table.getValueAt(1, 4));
                        table.setRowSelectionInterval(0, 0);
                        button(panel, "removeItem").doClick();
                        assertEquals(0, table.getRowCount());
                        panel.setSize(900, 700);
                        panel.doLayout();
                        assertTrue(panel.getComponentCount() > 0);
                    } catch (Throwable e) {
                        failure.set(e);
                    }
                });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test
    void unmatchedPanelRefreshAndClearNeverChangesEndpointCalls() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    try {
                        RestExtractor ex = new RestExtractor();
                        ApiNamespace ns = ex.addNamespace("http://localhost");
                        ex.addEndpoint(ns, "/posts/[ID]");
                        ex.startExtraction(ns);
                        ex.record(call("n=1"));
                        ex.record(
                                new ApiCall(
                                        HttpMethod.GET,
                                        "http://localhost/private-unmatched",
                                        200,
                                        "",
                                        ""));
                        UnmatchedPathsPanel panel = new UnmatchedPathsPanel(ex, ns);
                        JTable table = (JTable) find(panel, "unmatchedTable");
                        assertEquals(1, table.getRowCount());
                        table.setRowSelectionInterval(0, 0);
                        ex.record(
                                new ApiCall(
                                        HttpMethod.GET,
                                        "http://localhost/private-unmatched",
                                        200,
                                        "",
                                        ""));
                        panel.refresh();
                        assertEquals(2, table.getValueAt(0, 1));
                        assertEquals(0, table.getSelectedRow());
                        assertFalse(
                                new ApiDocumentationWriter()
                                        .write(ex.getReport(ns))
                                        .contains("private-unmatched"));
                        button(panel, "clearUnmatched").doClick();
                        assertEquals(0, table.getRowCount());
                        assertEquals(1, ex.getReport(ns).getEndpoints().get(0).getCalls().size());
                    } catch (Throwable error) {
                        failure.set(error);
                    }
                });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private static Component find(Container parent, String name) {
        for (Component c : parent.getComponents()) {
            if (name.equals(c.getName())) return c;
            if (c instanceof Container child) {
                Component found = find(child, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JButton button(Container p, String n) {
        return (JButton) find(p, n);
    }

    private static JTextField field(Container p, String n) {
        return (JTextField) find(p, n);
    }

    private static JComboBox<?> combo(Container p, String n) {
        return (JComboBox<?>) find(p, n);
    }
}
