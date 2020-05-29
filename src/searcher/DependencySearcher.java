package searcher;

import model.Artifact;
import model.Dependency;

import java.io.IOException;
import java.util.List;

/**
 * To search dependency.
 */
public interface DependencySearcher {


    /**
     * Search dependency.
     */
    List<Artifact> search(String text) throws Exception;

    /**
     * Fetch all version dependency by groupId & articleId.
     */
    List<Dependency> getDependencies(String groupId, String articleId) throws IOException;

}
