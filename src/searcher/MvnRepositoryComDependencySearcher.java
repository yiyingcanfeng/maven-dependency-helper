package searcher;

import model.Artifact;
import model.Dependency;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Search dependencies from https://mvnrepository.com
 * TODO: handle http 503
 */
public class MvnRepositoryComDependencySearcher implements DependencySearcher {

    /**
     * 超时毫秒
     */
    private final int timeoutMs;

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
    public List<Artifact> search(String text) throws IOException {
        String searchUrl = "https://mvnrepository.com/search?q=" + text;
        Document doc = Jsoup.connect(searchUrl).timeout(timeoutMs).get();
        Elements elements = doc.getElementsByClass("im-subtitle");

        List<Artifact> artifacts = new ArrayList<>(elements.size());
        for (Element ele : elements) {
            Elements hrefs = ele.getElementsByAttribute("href");
            if (hrefs.size() >= 2) {
                Artifact artifact = new Artifact();
                artifact.setGroupId(hrefs.get(0).text());
                artifact.setGroupId(hrefs.get(1).text());
            }
        }
        return artifacts;
    }

    @Override
    public List<Dependency> getDependencies(String groupId, String articleId) throws IOException {
        String url = String.format("https://mvnrepository.com/artifact/%s/%s", groupId, articleId);
        Document doc = Jsoup.connect(url).timeout(timeoutMs)
//                .cookie("__cfduid", "d2056c86f8ed420230b9bc81de716eec31590713938")
                .cookie("cf_clearance", "3285a877e4974e241bbaeed2b19b1d7a51bde0ff-1590713923-0-150")
//                .cookie("MVN_SESSION", "eyJhbGciOiJIUzI1NiJ9.eyJkYXRhIjp7InVpZCI6IjkzZjFmOGMxLWExNDctMTFlYS1iY2RmLWIxN2NkNTgxZDlkOCJ9LCJleHAiOjE2MjIyNTAzNDQsIm5iZiI6MTU5MDcxNDM0NCwiaWF0IjoxNTkwNzE0MzQ0fQ.ChWhbOQGiKQMKSZG_NPzT462B32M79RZ4mWqxnU4y18")
                .header("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36")
                .get();
        Elements elements = doc.getElementsByClass("vbtn release");

        List<Dependency> dependencies = new ArrayList<>(elements.size());
        for (Element ele : elements) {
            Dependency dependency = new Dependency();
            dependency.setGroupId(groupId);
            dependency.setArtifactId(articleId);
            dependency.setVersion(ele.text());
            dependencies.add(dependency);
        }
        return dependencies;
    }
}
