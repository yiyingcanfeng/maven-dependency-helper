package actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.psi.PsiFile;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import model.Artifact;
import model.Dependency;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.jetbrains.annotations.NotNull;
import searcher.DependencySearcher;
import searcher.SearchMavenOrgDependencySearcher;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

/**
 * @author yiyingcanfeng
 */
public class MavenDependencyHelperAction extends AnAction {

    private JFrame frame;
    private JTextArea textArea;
    private JButton copyVersionButton;
    private JButton copyTextAeraButton;
    private JButton searchButton;
    private JComboBox<String> comboBox;
    private JComboBox<String> typeComboBox;
    private JTextField textField;
    private JBScrollPane scrollPane;
    private JList<String> versionJList;
    private DefaultListModel<String> versionListModel;
    private JScrollPane listScroller;

    private String groupId;
    private String artifactId;
    private String version;
    private String scope;

    private ThreadPoolExecutor threadPool;
    private DependencySearcher dependencySearcher;

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        ThreadFactory threadFactory = Executors.defaultThreadFactory();
        this.threadPool = new ThreadPoolExecutor(3, 5, 10, TimeUnit.SECONDS, new ArrayBlockingQueue<>(2), threadFactory);
        this.dependencySearcher = new SearchMavenOrgDependencySearcher();
        this.groupId = "";
        this.artifactId = "";
        this.version = "";
        this.scope = "";

        // 获取选中文本匹配的依赖
        Editor editor = e.getData(LangDataKeys.EDITOR);
        if (editor != null) {
            CaretModel caretModel = editor.getCaretModel();
            Caret currentCaret = caretModel.getCurrentCaret();
            String selectedText = currentCaret.getSelectedText() != null ? currentCaret.getSelectedText().trim() : "";
            if (selectedText.length() > 0) {
                PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
                String filePath = psiFile != null ? psiFile.getVirtualFile().getPath() : "";
                List<Dependency> dependencies = getDependenciesByPom(filePath);
                Optional<Dependency> matchDependencyOpt = dependencies.stream()
                        .filter(item -> selectedText.equals(item.getArtifactId()))
                        .findFirst();
                if (matchDependencyOpt.isPresent()) {
                    Dependency dependency = matchDependencyOpt.get();
                    this.groupId = dependency.getGroupId() == null ? "" : dependency.getGroupId();
                    this.artifactId = dependency.getArtifactId() == null ? "" : dependency.getArtifactId();
                }
            }
        }

        initView();
        if (this.groupId.length() > 0 && this.artifactId.length() > 0) {
            loadDependencyVersionsView();
        }
    }

    //------------------ UI渲染 ------------------ //

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

    //------------------ 事件 ------------------ //

    /**
     * 搜索依赖
     */
    private void search() {
        String text = textField.getText();
        searchButton.setText("Searching...");
        threadPool.execute(() -> {
            try {
                List<Artifact> artifacts = dependencySearcher.search(text);
                if (artifacts.size() == 0) {
                    SwingUtilities.invokeLater(() -> {
                        comboBox.removeAllItems();
                        versionListModel.removeAllElements();
                        searchButton.setText("Search");
                        textArea.setText("");
                    });
                    return;
                }
                Artifact firstArtifact = artifacts.get(0);
                this.groupId = firstArtifact.getGroupId();
                this.artifactId = firstArtifact.getArtifactId();
                List<Dependency> dependencies = dependencySearcher.getDependencies(this.groupId, this.artifactId);

                SwingUtilities.invokeLater(() -> {
                    comboBox.removeAllItems();
                    for (Artifact one : artifacts) {
                        comboBox.addItem(one.getGroupId() + "/" + one.getArtifactId());
                    }
                    versionListModel.removeAllElements();
                    for (Dependency one : dependencies) {
                        versionListModel.addElement(one.getVersion());
                    }
                    if (versionListModel.size() > 0) {
                        versionJList.setSelectedIndex(0);
                    } else {
                        textArea.setText("");
                    }
                    searchButton.setText("Search");
                });
            } catch (IOException e1) {
                e1.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * 重新加载版本视图
     */
    private void loadDependencyVersionsView() {
        copyVersionButton.setText("Loading...");
        threadPool.execute(() -> {
            try {
                List<Dependency> dependencies = dependencySearcher.getDependencies(this.groupId, this.artifactId);
                SwingUtilities.invokeLater(() -> {
                    versionListModel.removeAllElements();
                    for (Dependency dependency : dependencies) {
                        versionListModel.addElement(dependency.getVersion());
                    }
                    copyVersionButton.setText("Copy Version");
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * 选择依赖comboBox Item选中事件
     *
     * @param event event
     */
    private void comboBoxItemSelectEvent(ItemEvent event) {
        if (event.getStateChange() == ItemEvent.SELECTED) {
            String[] itemStringSplit = event.getItem().toString().split("/");
            this.groupId = itemStringSplit[0];
            this.artifactId = itemStringSplit[1];
            loadDependencyVersionsView();
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
            String type = typeComboBox.getSelectedItem().toString();
            String dependencyText = dependencyTextByType(type);
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
                String buildToolType = event.getItem().toString();
                String dependencyText = dependencyTextByType(buildToolType);
                textArea.setText(dependencyText);
            }
        }
    };

    //------------------ 辅助方法 ------------------ //

    /**
     * 读取并解析pom.xml，获取Dependency信息
     */
    private List<Dependency> getDependenciesByPom(String filePath) {
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
     * 根据不同构建工具, 生成依赖文本
     */
    private String dependencyTextByType(String type) {
        if ("Gradle".equals(type)) {
            return dependencyTextForGradle();
        }
        return dependencyTextForMaven();
    }

    /**
     * 生成gradle的依赖文本
     */
    private String dependencyTextForGradle() {
        String dependencyText;
        switch (scope) {
            case "test":
                dependencyText = String.format("testImplementation '%s:%s:%s'", groupId, artifactId, version);
                break;
            case "provided":
                dependencyText = String.format("compileOnly '%s:%s:%s'", groupId, artifactId, version);
                break;
            default:
                dependencyText = String.format("implementation '%s:%s:%s'", groupId, artifactId, version);
                break;
        }
        return dependencyText;
    }

    /**
     * 生成maven的依赖文本
     */
    private String dependencyTextForMaven() {
        if (scope != null && !scope.equals("")) {
            return "<dependency>\n" +
                    "    <groupId>" + groupId + "</groupId>\n" +
                    "    <artifactId>" + artifactId + "</artifactId>\n" +
                    "    <version>" + version + "</version>\n" +
                    "    <scope>" + scope + "</scope>\n" +
                    "</dependency>";
        } else {
            return "<dependency>\n" +
                    "    <groupId>" + groupId + "</groupId>\n" +
                    "    <artifactId>" + artifactId + "</artifactId>\n" +
                    "    <version>" + version + "</version>\n" +
                    "</dependency>";
        }
    }
}
