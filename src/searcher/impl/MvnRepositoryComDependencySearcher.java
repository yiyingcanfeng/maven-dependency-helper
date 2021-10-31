package searcher.impl;

import model.Artifact;
import model.Dependency;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searcher.DependencySearcher;
import util.OkHttpUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Search dependencies from https://mvnrepository.com
 */
public class MvnRepositoryComDependencySearcher implements DependencySearcher {

    /**
     * 超时毫秒
     */
    private final int timeoutMs;

    /**
     * 网站速度，单位: ms
     */
    private int speed = 0;

    /**
     * 用于测速的url
     */
    public static final String DETECT_SPEED_URL = "https://mvnrepository.com/search?q=commons";

    public MvnRepositoryComDependencySearcher() {
        this(30000);
    }

    public MvnRepositoryComDependencySearcher(int timeoutMs) {
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must grater than 0");
        }
        this.timeoutMs = timeoutMs;
    }

    @Override
    public String getDetectSpeedUrl() {
        return DETECT_SPEED_URL;
    }

    @Override
    public List<Artifact> search(String text) throws IOException {
        String searchUrl = "https://mvnrepository.com/search?q=" + text;
        // jsoup visit mvnrepository.com may return 403, use okhttp instead
        String s = OkHttpUtils.get(searchUrl);
        Document doc = Jsoup.parse(s);
        Elements elements = doc.getElementsByClass("im-subtitle");

        List<Artifact> artifacts = new ArrayList<>(elements.size());
        for (Element ele : elements) {
            Elements hrefs = ele.getElementsByAttribute("href");
            if (hrefs.size() >= 2) {
                Artifact artifact = new Artifact();
                artifact.setGroupId(hrefs.get(0).text());
                artifact.setArtifactId(hrefs.get(1).text());
                artifacts.add(artifact);
            }
        }
        return artifacts;
    }

    @Override
    public List<Dependency> getDependencies(String groupId, String artifactId) throws IOException {
        String url = String.format("https://mvnrepository.com/artifact/%s/%s", groupId, artifactId);
        // jsoup visit mvnrepository.com may return 403, use okhttp instead
        String s = OkHttpUtils.get(url);
        Document doc = Jsoup.parse(s);
        Elements elements = doc.getElementsByClass("vbtn release");

        List<Dependency> dependencies = new ArrayList<>(elements.size());
        for (Element ele : elements) {
            Dependency dependency = new Dependency();
            dependency.setGroupId(groupId);
            dependency.setArtifactId(artifactId);
            dependency.setVersion(ele.text());
            dependencies.add(dependency);
        }
        return dependencies;
    }

    @Override
    public int getSpeed() {
        return speed;
    }

    @Override
    public void setSpeed(int speed) {
        this.speed = speed;
    }
}
