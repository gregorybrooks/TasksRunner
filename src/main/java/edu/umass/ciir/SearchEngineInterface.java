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

    /**
     * Returns the normalized form of a language that is in a corpus file's "language" field.
     * We convert everything to an all-lowercase, English word like "arabic".
     * @param rawLanguage The value seen in the corpus file, like "ar" or "Arabic".
     * @return the normalized form of the language, like "arabic"
     */
    default String toCanonicalForm(String rawLanguage) {
        rawLanguage = rawLanguage.toLowerCase(Locale.ROOT);
        if (rawLanguage.equals("ar")) {
            rawLanguage = "arabic";
        } else if (rawLanguage.equals("ko")) {
            rawLanguage = "korean";
        } else if (rawLanguage.equals("fa")) {
            rawLanguage = "farsi";
        } else if (rawLanguage.equals("zh")) {
            rawLanguage = "chinese";
        } else if (rawLanguage.equals("ru")) {
            rawLanguage = "russian";
        } else if (rawLanguage.equals("en")) {
            rawLanguage = "english";
        }
        List<String> allowedLanguages = Arrays.asList("arabic", "korean", "farsi", "chinese", "russian", "english");
        if (!allowedLanguages.contains(rawLanguage)) {
            throw new TasksRunnerException("Unsupported language: " + rawLanguage);
        }
        return rawLanguage;
    }
    default String toTwoCharForm(String canonicalForm) {
        if (canonicalForm.equals("arabic")) {
            canonicalForm = "ar";
        } else if (canonicalForm.equals("korean")) {
            canonicalForm = "ko";
        } else if (canonicalForm.equals("farsi")) {
            canonicalForm = "fa";
        } else if (canonicalForm.equals("chinese")) {
            canonicalForm = "zh";
        } else if (canonicalForm.equals("russian")) {
            canonicalForm = "ru";
        } else if (canonicalForm.equals("english")) {
            canonicalForm = "en";
        }
        return canonicalForm;

    }

    Map<String, String> getQueries(String queryFileName);

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
