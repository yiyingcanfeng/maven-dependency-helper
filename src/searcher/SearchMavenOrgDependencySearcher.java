package searcher;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import model.Artifact;
import model.Dependency;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Search dependencies by https://search.maven.org/
 */
public class SearchMavenOrgDependencySearcher implements DependencySearcher {

    private static final String SEARCH_URL = "https://search.maven.org/solrsearch/select?q=%s&start=0&rows=20";

    @Override
    public List<Artifact> search(String text) throws Exception {
        Document document = Jsoup.connect(String.format(SEARCH_URL, encodeUtf8(text))).ignoreContentType(true).get();
        JsonObject data = new Gson().fromJson(document.body().text(), JsonObject.class);
        JsonArray docs = data.getAsJsonObject("response").getAsJsonArray("docs");

        List<Artifact> artifacts = new ArrayList<>(docs.size());
        for (JsonElement ele : docs) {
            JsonObject doc = ele.getAsJsonObject();
            Artifact artifact = new Artifact();
            artifact.setGroupId(doc.get("g").getAsString());
            artifact.setArtifactId(doc.get("a").getAsString());
            artifacts.add(artifact);
        }

        return artifacts;
    }

    @Override
    public List<Dependency> getDependencies(String groupId, String articleId) throws IOException {
        String text = String.format("g:%s AND a:%s", encodeUtf8(groupId), encodeUtf8(articleId)) + "&core=gav";
        Document document = Jsoup.connect(String.format(SEARCH_URL, text)).ignoreContentType(true).get();
        JsonObject data = new Gson().fromJson(document.body().text(), JsonObject.class);
        JsonArray docs = data.getAsJsonObject("response").getAsJsonArray("docs");

        List<Dependency> dependencies = new ArrayList<>(docs.size());
        for (JsonElement ele : docs) {
            JsonObject doc = ele.getAsJsonObject();
            Dependency dependency = new Dependency();
            dependency.setGroupId(doc.get("g").getAsString());
            dependency.setArtifactId(doc.get("a").getAsString());
            dependency.setVersion(doc.get("v").getAsString());
            dependencies.add(dependency);
        }
        return dependencies;
    }

    private String encodeUtf8(String text) {
        try {
            return URLEncoder.encode(text, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Never happen.", e);
        }

    }
}
