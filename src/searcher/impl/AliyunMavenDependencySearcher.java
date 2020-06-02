package searcher.impl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import model.Artifact;
import model.Dependency;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import searcher.DependencySearcher;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Search dependencies by https://maven.aliyun.com/mvn/search
 */
public class AliyunMavenDependencySearcher implements DependencySearcher {

    /**
     * 超时毫秒
     */
    private final int timeoutMs;

    /**
     * 网站速度，单位: ms
     */
    private int speed = 1;

    /**
     * 用于测速的url
     */
    public static final String DETECT_SPEED_URL = "https://maven.aliyun.com/artifact/aliyunMaven/searchArtifactByWords?_input_charset=utf-8&queryTerm=commons&repoId=central";

    private static final String SEARCH_URL = "https://maven.aliyun.com/artifact/aliyunMaven/searchArtifactByWords?_input_charset=utf-8&queryTerm=%s&repoId=central";
    private static final String EXACT_SEARCH_URL = "https://maven.aliyun.com/artifact/aliyunMaven/searchArtifactByGav?_input_charset=utf-8&groupId=%s&repoId=central&artifactId=%s&version=";

    public AliyunMavenDependencySearcher() {
        this(30000);
    }

    public AliyunMavenDependencySearcher(int timeoutMs) {
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

        Document document = Jsoup.connect(String.format(SEARCH_URL, text))
                .timeout(timeoutMs).ignoreContentType(true).get();
        JsonObject data = new Gson().fromJson(document.body().text(), JsonObject.class);
        List<Artifact> artifacts = new ArrayList<>();
        if (data.get("successful").getAsBoolean()) {
            JsonArray array = data.getAsJsonArray("object");

            for (JsonElement ele : array) {
                JsonObject doc = ele.getAsJsonObject();
                if ("unknown".equals(doc.get("packaging").getAsString())) {
                    continue;
                }
                if ("jar".equals(doc.get("packaging").getAsString())) {
                    Artifact artifact = new Artifact();
                    String groupId = doc.get("groupId").getAsString();
                    if (groupId.startsWith("#") || groupId.startsWith("%23")) {
                        continue;
                    }
                    artifact.setGroupId(groupId);
                    artifact.setArtifactId(doc.get("artifactId").getAsString());
                    artifacts.add(artifact);
                }
            }
        }
        return artifacts.stream().filter(distinctByKey(Artifact::getGroupId)).collect(Collectors.toList());
    }

    private static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Map<Object,Boolean> seen = new ConcurrentHashMap<>();
        return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
    }

    @Override
    public List<Dependency> getDependencies(String groupId, String articleId) throws IOException {
        Document document = Jsoup.connect(String.format(EXACT_SEARCH_URL, groupId, articleId))
                .timeout(timeoutMs)
                .ignoreContentType(true).get();
        JsonObject data = new Gson().fromJson(document.body().text(), JsonObject.class);

        List<Dependency> dependencies = new ArrayList<>();
        if (data.get("successful").getAsBoolean()) {
            JsonArray docs = data.getAsJsonArray("object");
            for (JsonElement ele : docs) {
                JsonObject doc = ele.getAsJsonObject();
                if ("unknown".equals(doc.get("packaging").getAsString())) {
                    continue;
                }
                if ("jar".equals(doc.get("packaging").getAsString())) {
                    Dependency dependency = new Dependency();
                    String groupId1 = doc.get("groupId").getAsString();
                    if (groupId1.startsWith("#")) {
                        continue;
                    }
                    dependency.setGroupId(groupId1);
                    dependency.setArtifactId(doc.get("artifactId").getAsString());
                    dependency.setVersion(doc.get("version").getAsString());
                    dependencies.add(dependency);
                }
            }
        }
        return dependencies.stream().filter(distinctByKey(Dependency::getVersion)).collect(Collectors.toList());
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
