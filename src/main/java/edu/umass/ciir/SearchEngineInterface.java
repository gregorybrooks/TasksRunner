package edu.umass.ciir;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public interface SearchEngineInterface {
    void buildIndexes(String corpusFileName);
    void search(int threadCount, int maxHits, String queryFileName, String runFileName,
                       String submissionId, String language);
    Map<String, String> getQueries(String queryFileName);

    /**
     * Returns a list of the languages included in this environments target corpus file.
     * The target corpus file must have been processed already and indexes built for each
     * of the languages in that file, since this method just checks for the configuration files that are
     * created during the indexing process.
     * Does NOT return "english", ever.
     * @return the list of languages, which will be in our "canonical" form (lower-case like "arabic")
     */
    static List<String> getTargetLanguages() {
        List<String> languages = new ArrayList<>();

        DirectoryStream.Filter<Path> filter = file -> (file.toString().endsWith(".conf"));

        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(
                Paths.get(Pathnames.indexLocation + Pathnames.searchEngine + "/"),
                filter)) {
            dirStream.forEach(path -> {
                String x = path.toString();
                x = x.replace(Pathnames.indexLocation + Pathnames.searchEngine + "/","");
                String language = x.replace(".conf", "");
                if (!language.equals("english")) {
                    languages.add(language);
                }
            });
        } catch (IOException cause) {
            throw new TasksRunnerException(cause);
        }
        return languages;
    }

    static boolean englishIndexExists() {
        return (new File(Pathnames.indexLocation + Pathnames.searchEngine + "/english.conf").exists());
    }

    public static SearchEngineInterface getSearchEngine() {
        SearchEngineInterface searchEngine = null;
        if (Pathnames.searchEngine.equals("anserini")) {
            searchEngine = new AnseriniSearchEngine();
        } else if (Pathnames.searchEngine.equals("galago")) {
            searchEngine = new GalagoSearchEngine();
        } else {
            throw new TasksRunnerException("Invalid search engine type: " + Pathnames.searchEngine);
        }
        return searchEngine;
    }

}
