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
    private JComboBox<String> typeComboBox;
    private JLabel titleLabel;
    private JTextField textField;
    private JBScrollPane scrollPane;
    private JList<String> versionJList;
    private DefaultListModel<String> versionListModel;
    private JScrollPane listScroller;

    private String selectedText;
    private String groupId;
    private String artifactId;
    private String version;
    private String scope;

    private ThreadPoolExecutor threadPool;

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        //获取当前操作的类文件
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        //获取当前文件的路径
        String filePath = psiFile != null ? psiFile.getVirtualFile().getPath() : "";
        //获取选中的文本
        CaretModel caretModel = Objects.requireNonNull(e.getData(LangDataKeys.EDITOR)).getCaretModel();
        Caret currentCaret = caretModel.getCurrentCaret();
        selectedText = currentCaret.getSelectedText() != null ? currentCaret.getSelectedText() : "";
        dependencies = getDependencies(filePath);
        //初始化线程池
        ThreadFactory threadFactory = Executors.defaultThreadFactory();
        threadPool = new ThreadPoolExecutor(3, 5, 10, TimeUnit.SECONDS, new ArrayBlockingQueue<>(2), threadFactory);
        if (!"".equals(selectedText) && dependencies.size() > 0) {
            //从List<Dependency>中搜索选中的Dependency，并获取其详细信息
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

    /**
     * 获取versions并展示
     */
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

    /**
     * 获取release类型的依赖信息
     *
     * @return Elements
     * @throws IOException IOException
     */
    private Elements getReleaseVersionElements() throws IOException {
        String url = String.format("https://mvnrepository.com/artifact/%s/%s", groupId, artifactId);
        org.jsoup.nodes.Document doc = Jsoup.connect(url).get();
        return doc.getElementsByClass("vbtn release");
    }

    /**
     * 向ListModel中添加version信息
     *
     * @param elementsByClass Elements
     */
    private void addToVersionListModel(Elements elementsByClass) {
        for (org.jsoup.nodes.Element byClass : elementsByClass) {
            String href = byClass.attr("href");
            String version = href.split("/")[1];
            versionListModel.addElement(version);
        }
    }

    /**
     * 初始化界面
     */
    private void initView() {
        frame = new JFrame("Maven Dependency Helper");
        frame.setSize(525, 250);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        //esc键关闭窗口
        frame.getRootPane().registerKeyboardAction(e -> {
            frame.dispose();
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        //窗体打开时textField获取焦点
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                textField.requestFocus();
            }
        });

        copyVersionButton = new JButton("Copy Version");
        copyVersionButton.setFont(new Font(null, Font.PLAIN, 15));

        copyTextAeraButton = new JButton("Copy Content");
        copyTextAeraButton.setFont(new Font(null, Font.PLAIN, 15));

        searchButton = new JButton("Search");
        searchButton.setFont(new Font(null, Font.PLAIN, 15));

        comboBox = new ComboBox<>();
        comboBox.setFont(new Font(null, Font.PLAIN, 15));
        comboBox.addItem(artifactId);

        typeComboBox = new ComboBox<>();
        typeComboBox.setFont(new Font(null, Font.PLAIN, 15));
        typeComboBox.addItem("Maven");
        typeComboBox.addItem("Gradle");

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

        //设置组件的位置和大小
        setLocationAndSize();

        //将组件添加到Container中
        addToContainer();

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
        comboBox.addItemListener(this::comboBoxItemSelectEvent);
    }

    /**
     * 设置组件的位置和大小
     */
    private void setLocationAndSize() {
        comboBox.setBounds(20, 10, 110, 30);
        textField.setBounds(190, 10, 300, 30);
        typeComboBox.setBounds(310, 45, 80, 25);
        copyVersionButton.setBounds(20, 45, 110, 25);
        copyTextAeraButton.setBounds(190, 45, 110, 25);
        searchButton.setBounds(400, 45, 90, 25);
        listScroller.setBounds(20, 77, 110, 122);
        scrollPane.setBounds(190, 77, 300, 122);
    }

    /**
     * 将组件添加到Container中
     */
    private void addToContainer() {
        frame.getContentPane().setLayout(null);
        frame.getContentPane().add(copyVersionButton);
        frame.getContentPane().add(copyTextAeraButton);
        frame.getContentPane().add(searchButton);
        frame.getContentPane().add(comboBox);
        frame.getContentPane().add(typeComboBox);
        frame.getContentPane().add(textField);
        frame.getContentPane().add(scrollPane);
        frame.getContentPane().add(listScroller);
        frame.setVisible(true);
    }

    /**
     * 搜索依赖
     */
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

    /**
     * 读取并解析pom.xml，获取Dependency信息
     *
     * @param filePath 文件名
     * @return List<Dependency>
     */
    private List<Dependency> getDependencies(String filePath) {
        List<Dependency> d = new ArrayList<>();
        if (filePath.contains("pom.xml")) {
            File file = new File(filePath);
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
                    String version = element.element("version") == null ? "" : element.element("version").getText();
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

    /**
     * 选择依赖comboBox Item选中事件
     *
     * @param event event
     */
    private void comboBoxItemSelectEvent(ItemEvent event) {
        if (event.getStateChange() == ItemEvent.SELECTED) {
            String[] itemStringSplit = event.getItem().toString().split("/");
            groupId = itemStringSplit[0];
            artifactId = itemStringSplit[1];
            copyVersionButton.setText("Loading...");
            threadPool.execute(() -> {
                try {
                    Elements elementsByClass = getReleaseVersionElements();
                    //根据第一个依赖信息，在mvnrepository网站该依赖的详情页面查询该依赖是否有scope属性，比如javaee-api有provided属性，junit有test属性，如果没有，则后面生成的dependencyText就不带scope属性
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

    /**
     * JList点击事件
     */
    private MouseAdapter versionJListMouseAdapter = new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
            JList jList = (JList) e.getSource();
            //JList为空时，点击其空白区域仍会触发点击事件
            Object selectedValue = jList.getSelectedValue();
            version = selectedValue == null ? "" : selectedValue.toString();
            String dependencyText;
            String type = typeComboBox.getSelectedItem().toString();
            switch (type) {
                case "Maven":
                    if ("".equals(scope)) {
                        dependencyText = "<dependency>\n" +
                                "    <groupId>" + groupId + "</groupId>\n" +
                                "    <artifactId>" + artifactId + "</artifactId>\n" +
                                "    <version>" + version + "</version>\n" +
                                "</dependency>";
                    } else {
                        dependencyText = "<dependency>\n" +
                                "    <groupId>" + groupId + "</groupId>\n" +
                                "    <artifactId>" + artifactId + "</artifactId>\n" +
                                "    <version>" + version + "</version>\n" +
                                "    <scope>" + scope + "</scope>\n" +
                                "</dependency>";
                    }

                    break;
                case "Gradle":
                    dependencyText = dependencyTextForGradle();
                    break;
                default:
                    dependencyText = "<dependency>\n" +
                            "    <groupId>" + groupId + "</groupId>\n" +
                            "    <artifactId>" + artifactId + "</artifactId>\n" +
                            "    <version>" + version + "</version>\n" +
                            "</dependency>";
                    break;
            }


            textArea.setText(dependencyText);
            //copyVersion按钮点击事件
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
            //copyText按钮点击事件
            copyTextAeraButton.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    StringSelection stringSelection = new StringSelection(textArea.getText());
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
            //依赖类型ComboBox Item事件
            typeComboBox.addItemListener(this::typeComboBoxItemSelectEvent);

        }

        /**
         * 依赖类型ComboBox Item选中事件
         * @param event event
         */
        private void typeComboBoxItemSelectEvent(ItemEvent event) {
            if (event.getStateChange() == ItemEvent.SELECTED) {
                String itemString = event.getItem().toString();
                String dependencyText;
                if ("Maven".equals(itemString)) {
                    if ("".equals(scope)) {
                        dependencyText = "<dependency>\n" +
                                "    <groupId>" + groupId + "</groupId>\n" +
                                "    <artifactId>" + artifactId + "</artifactId>\n" +
                                "    <version>" + version + "</version>\n" +
                                "</dependency>";
                    } else {
                        dependencyText = "<dependency>\n" +
                                "    <groupId>" + groupId + "</groupId>\n" +
                                "    <artifactId>" + artifactId + "</artifactId>\n" +
                                "    <version>" + version + "</version>\n" +
                                "    <scope>" + scope + "</scope>\n" +
                                "</dependency>";
                    }

                } else if ("Gradle".equals(itemString)) {
                    dependencyText = dependencyTextForGradle();
                } else {
                    dependencyText = "";
                }

                textArea.setText(dependencyText);
            }
        }
    };

    //生成gradle的依赖文本
    private String dependencyTextForGradle() {
        String dependencyText;
        switch (scope) {
            case "test":
                dependencyText = String.format("testCompile group: '%s', name: '%s', version: '%s'", groupId, artifactId, version);
                break;
            case "provided":
                dependencyText = String.format("provided group: '%s', name: '%s', version: '%s'", groupId, artifactId, version);
                break;
            default:
                dependencyText = String.format("compile group: '%s', name: '%s', version: '%s'", groupId, artifactId, version);
                break;
        }
        return dependencyText;
    }
}
