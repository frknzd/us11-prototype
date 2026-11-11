/* SPDX-License-Identifier: Apache-2.0 */
// Opt-in acceptance agent, not part of the extension or its production JAR.
import com.sun.net.httpserver.HttpServer;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.lang.instrument.Instrumentation;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

public final class PrototypeUiSmoke {
    private static Container panel;
    private static Path output;
    public static void premain(String directory, Instrumentation instrumentation) {
        output=Path.of(directory);
        Thread worker=new Thread(()->{
            HttpServer fixture=null;
            try {
                Files.createDirectories(output);
                for(int i=0;i<120 && panel==null;i++) {
                    panel=edt(()->{for(Frame frame:Frame.getFrames()){Component p=find(frame,"REST Extraction");if(p instanceof Container c)return c;}return null;});
                    Thread.sleep(500);
                }
                require(panel!=null,"REST Extraction panel was not found");
                fixture=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
                fixture.createContext("/",exchange->{byte[] b="{\"ok\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(200,b.length);exchange.getResponseBody().write(b);exchange.close();});
                fixture.start();
                String base="http://127.0.0.1:"+fixture.getAddress().getPort();
                edt(()->{
                    Component child=(Component)panel;
                    while(child.getParent()!=null){Container parent=child.getParent();if(parent instanceof JTabbedPane tabs)tabs.setSelectedComponent(child);child=parent;}
                    if(child instanceof Frame frame){frame.setSize(1450,1000);frame.toFront();}
                    field("baseUrl").setText(base);click("addNamespace");field("pattern").setText("/posts/[ID]");click("addEndpoint");click("startStop");
                    JTree tree=(JTree)find(panel,"namespaces");var root=(DefaultMutableTreeNode)tree.getModel().getRoot();var ns=(DefaultMutableTreeNode)root.getChildAt(root.getChildCount()-1);var endpoint=(DefaultMutableTreeNode)ns.getChildAt(0);tree.setSelectionPath(new TreePath(endpoint.getPath()));return null;
                });
                int port=Integer.parseInt(System.getenv().getOrDefault("US11_PROXY_PORT","8091"));
                HttpClient client=HttpClient.newBuilder().proxy(ProxySelector.of(new InetSocketAddress("127.0.0.1",port))).connectTimeout(Duration.ofSeconds(5)).build();
                send(client,base+"/posts/1?n=1");waitRows(1);
                edt(()->{require("".equals(table("calls").getValueAt(0,5)),"Empty spec must be neutral");tabs().setSelectedIndex(2);select("specKind","QUERY_PARAMETER");field("specName").setText("n");select("specTYPE","Integer");field("specMIN").setText("1");field("specMAX").setText("10");click("saveItem");require(table("specItems").getRowCount()==2,"Spec item did not save");return null;});
                edt(()->{select("specKind","QUERY_PARAMETER");select("specTYPE","Integer");field("specName").setText("page");return null;});
                snapshot("specification-light");
                edt(()->{tabs().setSelectedIndex(0);return null;});
                send(client,base+"/posts/2?n=x");waitRows(2);
                edt(()->{require("✓".equals(table("calls").getValueAt(0,5)),"Old call was not rejudged valid");require("✗ 1".equals(table("calls").getValueAt(1,5)),"Invalid call not marked");((JCheckBox)find(panel,"onlyInvalid")).doClick();require(table("calls").getRowCount()==1,"Invalid-only filter failed");table("calls").setRowSelectionInterval(0,0);require(((JTextArea)find(panel,"detail")).getText().contains("expected Integer"),"Missing diagnostic");return null;});
                snapshot("checks-light");
                edt(()->{tabs().setSelectedIndex(2);table("specItems").setRowSelectionInterval(1,1);click("editItem");field("specMIN").setText("2");click("saveItem");tabs().setSelectedIndex(0);return null;});
                waitRows(2);
                edt(()->{UIManager.setLookAndFeel("com.formdev.flatlaf.FlatDarkLaf");for(Frame frame:Frame.getFrames())SwingUtilities.updateComponentTreeUI(frame);return null;});
                snapshot("checks-dark");
                edt(()->{tabs().setSelectedIndex(1);return null;});
                snapshot("documentation-dark");
                edt(()->{tabs().setSelectedIndex(2);select("specKind","QUERY_PARAMETER");select("specTYPE","Integer");field("specName").setText("page");for(Frame frame:Frame.getFrames())if(frame.isVisible())frame.setSize(1150,800);return null;});
                snapshot("specification-compact-dark");
                Files.writeString(output.resolve("result.txt"),"PASS: real ZAP GUI, neutral calls, specification save/edit, report-time rejudging, valid/invalid checks, invalid-only filtering, diagnostics, documentation, light/dark and compact layout.\n");
                System.out.println("US11 UI smoke: PASS (loopback-only fixture, results under ignored .local)");
            } catch(Throwable error) {
                try{Files.writeString(output.resolve("result.txt"),"FAIL: "+error+"\n");}catch(Exception ignored){}
                error.printStackTrace();
            } finally {if(fixture!=null)fixture.stop(0);}
        },"US11-UI-smoke");worker.setDaemon(true);worker.start();
    }
    private static void send(HttpClient client,String url)throws Exception{require(client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).build(),HttpResponse.BodyHandlers.discarding()).statusCode()==200,"Fixture request failed");}
    private static void waitRows(int count)throws Exception{for(int i=0;i<100;i++){if(edt(()->table("calls").getRowCount())==count)return;Thread.sleep(100);}throw new AssertionError("Expected "+count+" rows");}
    private static void snapshot(String name)throws Exception{
        Thread.sleep(500);
        try{
            var bounds=edt(()->{Container c=panel;while(c.getParent()!=null)c=c.getParent();return c.getBounds();});
            BufferedImage image=new Robot().createScreenCapture(bounds);ImageIO.write(image,"png",output.resolve(name+".png").toFile());
        }catch(Exception e){System.out.println("US11 preview unavailable: "+e.getClass().getSimpleName());}
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static <T>T edt(Callable<T> action)throws Exception{
        AtomicReference<T> result=new AtomicReference<>();AtomicReference<Throwable> failure=new AtomicReference<>();
        SwingUtilities.invokeAndWait(()->{try{result.set(action.call());}catch(Throwable e){failure.set(e);}});
        if(failure.get()!=null)throw new AssertionError(failure.get());return result.get();
    }
    private static JTabbedPane tabs(){return (JTabbedPane)find(panel,"tabs");}
    private static JTable table(String name){return (JTable)find(panel,name);}
    private static JTextField field(String name){return (JTextField)find(panel,name);}
    private static void click(String name){((JButton)find(panel,name)).doClick();}
    private static void select(String name,String value){JComboBox<?> combo=(JComboBox<?>)find(panel,name);for(int i=0;i<combo.getItemCount();i++)if(combo.getItemAt(i).toString().equals(value)){combo.setSelectedIndex(i);return;}throw new AssertionError("Missing choice "+value);}
    private static Component find(Container parent,String name){
        if(name.equals("REST Extraction") && parent.getClass().getName().equals("org.zaproxy.zap.view.TabbedPanel2")) {
            try {
                var panels=(java.util.List<?>)parent.getClass().getMethod("getPanels").invoke(parent);
                for(Object item:panels) if(item instanceof Component c && name.equals(c.getName())) {
                    parent.getClass().getMethod("setVisible",Component.class,boolean.class).invoke(parent,c,true);
                    return c;
                }
            }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
        }
        for(Component child:parent.getComponents()){if(name.equals(child.getName()))return child;if(child instanceof Container c){Component found=find(c,name);if(found!=null)return found;}}return null;
    }
}
