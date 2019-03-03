package actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.psi.PsiFile;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import model.Dependency;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import org.dom4j.*;
import org.dom4j.io.*;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;

import javax.swing.*;

/**
 * @author yiyingcanfeng
 */
public class MavenDependencyHelperAction extends AnAction {

    private List<Dependency> dependencies = new ArrayList<>();

    private JFrame frame;
    private JTextArea textArea;
    private JButton copyVersionButton;
    private JButton copyTextAeraButton;
    private JButton searchButton;
    private JComboBox<String> comboBox;
    private JLabel titleLabel;
    private JTextField textField;
    private JBScrollPane scrollPane;
    private JList<String> versionJList;
    private DefaultListModel<String> versionListModel;
    private JScrollPane listScroller;

    private String selectedText;
    private String groupId;
    private String artifactId;
    private String scope;

    private ThreadPoolExecutor threadPool;

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        //获取当前在操作的工程上下文
        Project project = e.getData(PlatformDataKeys.PROJECT);
        //获取当前操作的类文件
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        //获取当前类文件的路径
        String classPath = psiFile.getVirtualFile().getPath();
        //获取选中的文本
        CaretModel caretModel = Objects.requireNonNull(e.getData(LangDataKeys.EDITOR)).getCaretModel();
        Caret currentCaret = caretModel.getCurrentCaret();
        selectedText = currentCaret.getSelectedText() != null ? currentCaret.getSelectedText() : "";
        dependencies = getDependencies(classPath);
        ThreadFactory threadFactory = Executors.defaultThreadFactory();
        threadPool = new ThreadPoolExecutor(3, 5, 10, TimeUnit.SECONDS, new ArrayBlockingQueue<>(2), threadFactory);
        if (!"".equals(selectedText)) {
            List<Dependency> list = dependencies.stream().filter(item -> item.getArtifactId().equals(selectedText)).collect(Collectors.toList());
            if (list.size() > 0) {
                Dependency dependency = list.get(0);
                groupId = dependency.getGroupId();
                artifactId = dependency.getArtifactId();
                scope = dependency.getScope();
                initView();
                getVersionsAndShow();
            } else {
                groupId = "";
                artifactId = "";
                scope = "";
                initView();
                getVersionsAndShow();
            }
        } else {
            groupId = "";
            artifactId = "";
            scope = "";
            initView();
        }

    }

    private void getVersionsAndShow() {
        copyVersionButton.setText("loading...");
        threadPool.execute(() -> {
            long start = System.currentTimeMillis();
            try {
                Elements elementsByClass = getReleaseVersionElements();
                SwingUtilities.invokeLater(() -> {
                    copyVersionButton.setText("Copy Version");
                    addToVersionListModel(elementsByClass);
                });
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            long end = System.currentTimeMillis();
            System.out.println("网络请求耗时:" + (end - start));

        });
    }

    private Elements getReleaseVersionElements() throws IOException {
        String url = String.format("https://mvnrepository.com/artifact/%s/%s", groupId, artifactId);
        org.jsoup.nodes.Document doc = Jsoup.connect(url).get();
        return doc.getElementsByClass("vbtn release");
    }

    private void addToVersionListModel(Elements elementsByClass) {
        for (org.jsoup.nodes.Element byClass : elementsByClass) {
            String href = byClass.attr("href");
            String version = href.split("/")[1];
            versionListModel.addElement(version);
        }
    }

    private void initView() {
        frame = new JFrame("Maven Dependency Helper");
        frame.setSize(525, 250);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        copyVersionButton = new JButton("Copy Version");
        copyVersionButton.setFont(new Font(null, Font.PLAIN, 15));

        copyTextAeraButton = new JButton("Copy Content");
        copyTextAeraButton.setFont(new Font(null, Font.PLAIN, 15));

        searchButton = new JButton("Search");
        searchButton.setFont(new Font(null, Font.PLAIN, 15));

        comboBox = new ComboBox<>();
        comboBox.setFont(new Font(null, Font.PLAIN, 15));
        comboBox.addItem(artifactId);

        textArea = new JTextArea();
        textArea.setColumns(10);
        textArea.setRows(6);
        textArea.setFont(new Font(null, Font.PLAIN, 16));

        titleLabel = new JLabel(artifactId);
        titleLabel.setFont(new Font(null, Font.PLAIN, 16));
        textField = new JTextField();
        textField.setFont(new Font(null, Font.PLAIN, 16));

        scrollPane = new JBScrollPane(textArea);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

        versionListModel = new DefaultListModel<>();
        versionJList = new JBList<>(versionListModel);
        listScroller = new JBScrollPane(versionJList);
        listScroller.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        listScroller.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        versionJList.addMouseListener(versionJListMouseAdapter);

        setLocationAndSize();

        addToComtainer();

        searchButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!"".equals(textField.getText())) {
                    search();
                }
            }
        });
        textField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !"".equals(textField.getText())) {
                    search();
                }
            }
        });
        comboBox.addItemListener(this::comboBoxItemStateChanged);
    }

    private void setLocationAndSize() {
        comboBox.setBounds(20, 10, 110, 25);
        copyVersionButton.setBounds(20, 40, 110, 25);
        copyTextAeraButton.setBounds(190, 40, 110, 25);
        searchButton.setBounds(400, 40, 90, 25);
        listScroller.setBounds(20, 72, 110, 122);
        textField.setBounds(190, 10, 300, 25);
        scrollPane.setBounds(190, 72, 300, 122);
    }

    private void addToComtainer() {
        frame.getContentPane().setLayout(null);
        frame.getContentPane().add(copyVersionButton);
        frame.getContentPane().add(copyTextAeraButton);
        frame.getContentPane().add(searchButton);
        frame.getContentPane().add(comboBox);
        frame.getContentPane().add(textField);
        frame.getContentPane().add(scrollPane);
        frame.getContentPane().add(listScroller);
        frame.setVisible(true);
    }

    private void search() {
        String text = textField.getText();
        searchButton.setText("Searching...");
        String searchUrl = "https://mvnrepository.com/search?q=" + text;
        threadPool.execute(() -> {
            try {
                org.jsoup.nodes.Document doc = Jsoup.connect(searchUrl).get();
                doc = Jsoup.parse(doc.getElementsByClass("im-subtitle").html());
                String[] split = doc.getElementsByAttribute("href").text().split(" ");
                String firstGroupId = split[0];
                String firstArtifactId = split[1];
                groupId = split[0];
                artifactId = split[1];
                try {
                    String url = String.format("https://mvnrepository.com/artifact/%s/%s", firstGroupId, firstArtifactId);
                    org.jsoup.nodes.Document doc1 = Jsoup.connect(url).get();
                    Elements elementsByClass = doc1.getElementsByClass("vbtn release");
                    SwingUtilities.invokeLater(() -> {
                        comboBox.removeAllItems();
                        for (int i = 0; i < split.length; i += 2) {
                            comboBox.addItem(split[i] + "/" + split[i + 1]);
                        }
                        searchButton.setText("Search");
                        titleLabel.setText(firstArtifactId);
                        versionListModel.removeAllElements();
                        for (org.jsoup.nodes.Element byClass : elementsByClass) {
                            String href = byClass.attr("href");
                            String[] version = href.split("/");
                            versionListModel.addElement(version[1]);
                        }
                    });
                } catch (IOException e1) {
                    e1.printStackTrace();
                }

            } catch (IOException e1) {
                e1.printStackTrace();
            }
        });
    }

    private List<Dependency> getDependencies(String fileName) {
        List<Dependency> d = new ArrayList<>();
        File file = new File(fileName);
        if (fileName.contains("pom.xml")) {
            try {
                SAXReader saxReader = new SAXReader();
                Document document = saxReader.read(file);
                Element rootElement = document.getRootElement();
                Element dependenciesElement = rootElement.element("dependencies");
                for (Iterator it = dependenciesElement.elementIterator(); it.hasNext(); ) {
                    Dependency dependency = new Dependency();
                    Element element = (Element) it.next();
                    String groupId = element.element("groupId").getText();
                    String artifactId = element.element("artifactId").getText();
                    String version = element.element("version").getText();
                    String scope = element.element("scope") == null ? "" : element.element("scope").getText();
                    dependency.setGroupId(groupId);
                    dependency.setArtifactId(artifactId);
                    dependency.setVersion(version);
                    dependency.setScope(scope);
                    d.add(dependency);
                }

            } catch (DocumentException e) {
                e.printStackTrace();
            }
        }
        return d;
    }

    private void comboBoxItemStateChanged(ItemEvent event) {
        if (event.getStateChange() == ItemEvent.SELECTED) {
            String[] split = event.getItem().toString().split("/");
            groupId = split[0];
            artifactId = split[1];
            copyVersionButton.setText("Loading...");
            threadPool.execute(() -> {
                try {
                    Elements elementsByClass = getReleaseVersionElements();
                    String firstVersion = elementsByClass.get(0).attr("href").split("/")[1];
                    String url1 = String.format("https://mvnrepository.com/artifact/%s/%s/%s", groupId, artifactId, firstVersion);
                    org.jsoup.nodes.Document doc2 = Jsoup.connect(url1).get();
                    org.jsoup.nodes.Element element = doc2.getElementById("maven-div");
                    String configContent = element.child(0).html();
                    configContent = configContent.replace("&lt;", "<").replace("&gt;", ">");
                    SAXReader saxReader = new SAXReader();
                    Document document = saxReader.read(new ByteArrayInputStream(configContent.getBytes()));
                    Element rootElement = document.getRootElement();
                    if (rootElement.element("scope") != null) {
                        scope = rootElement.element("scope").getData().toString();
                    } else {
                        scope = "";
                    }
                    SwingUtilities.invokeLater(() -> {
                        versionListModel.removeAllElements();
                        addToVersionListModel(elementsByClass);
                        copyVersionButton.setText("Copy Version");
                    });
                } catch (IOException | DocumentException e) {
                    e.printStackTrace();
                }

            });

        }
    }

    private MouseAdapter versionJListMouseAdapter = new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
            JList jList = (JList) e.getSource();
            String version = jList.getSelectedValue().toString();
            String dependencyText;
            if (!"".equals(scope)) {
                dependencyText = "<dependency>\n" +
                        "    <groupId>" + groupId + "</groupId>\n" +
                        "    <artifactId>" + artifactId + "</artifactId>\n" +
                        "    <version>" + version + "</version>\n" +
                        "    <scope>" + scope + "</scope>\n" +
                        "</dependency>";
            } else {
                dependencyText = "<dependency>\n" +
                        "    <groupId>" + groupId + "</groupId>\n" +
                        "    <artifactId>" + artifactId + "</artifactId>\n" +
                        "    <version>" + version + "</version>\n" +
                        "</dependency>";
            }

            textArea.setText(dependencyText);
            copyVersionButton.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    StringSelection stringSelection = new StringSelection(version);
                    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                    clipboard.setContents(stringSelection, null);
                    copyVersionButton.setText("Copy Success!");
                    ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
                    executorService.schedule(() -> {
                        SwingUtilities.invokeLater(() -> {
                                    copyVersionButton.setText("Copy Version");
                                }

                        );
                    }, 1000, TimeUnit.MILLISECONDS);
                }
            });

            copyTextAeraButton.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    StringSelection stringSelection = new StringSelection(dependencyText);
                    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                    clipboard.setContents(stringSelection, null);
                    ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
                    copyTextAeraButton.setText("Copy Success!");
                    executorService.schedule(() -> {
                        SwingUtilities.invokeLater(() -> {
                                    copyTextAeraButton.setText("Copy Content");
                                }

                        );
                    }, 1000, TimeUnit.MILLISECONDS);
                }
            });

        }
    };
}
