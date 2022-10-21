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
     * @param rawLanguage The value seen in the corpus file, like "ar" or "Arabic" or "zho".
     * @return the normalized form of the language, like "arabic"
     */
    static String toCanonicalForm(String rawLanguage) {
        rawLanguage = rawLanguage.toLowerCase(Locale.ROOT);
        switch (rawLanguage) {
            case "ar": case "arabic":
                rawLanguage = "arabic";
                break;
            case "ko": case "kor":
                rawLanguage = "korean";
                break;
            case "fa": case "farsi":
                rawLanguage = "farsi";
                break;
            case "zh": case "zho":
                rawLanguage = "chinese";
                break;
            case "ru": case "rus": case "russian":
                rawLanguage = "russian";
                break;
            case "en": case "eng": case "english":
                rawLanguage = "english";
                break;
            default:
                throw new TasksRunnerException("Invalid raw language passed to toCanonicalForm: " + rawLanguage);
        }
        return rawLanguage;
    }

    static String toTwoCharForm(String canonicalForm) {
        switch (canonicalForm) {
            case "arabic":
                canonicalForm = "ar";
                break;
            case "korean":
                canonicalForm = "ko";
                break;
            case "farsi":
                canonicalForm = "fa";
                break;
            case "chinese":
                canonicalForm = "zh";
                break;
            case "russian":
                canonicalForm = "ru";
                break;
            case "english":
                canonicalForm = "en";
                break;
            default:
                throw new TasksRunnerException("Invalid language passed to toTwoCharForm: " + canonicalForm);
        }
        return canonicalForm;

    }

    static String toThreeCharForm(String canonicalForm) {
        switch (canonicalForm) {
            case "arabic":
                canonicalForm = "ara";
                break;
            case "korean":
                canonicalForm = "kor";
                break;
            case "farsi":
                canonicalForm = "far";
                break;
            case "chinese":
                canonicalForm = "zho";
                break;
            case "russian":
                canonicalForm = "rus";
                break;
            case "english":
                canonicalForm = "eng";
                break;
            default:
                throw new TasksRunnerException("Invalid language passed to toThreeCharForm: " + canonicalForm);
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
