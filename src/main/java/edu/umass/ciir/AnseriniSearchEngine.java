package edu.umass.ciir;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class AnseriniSearchEngine implements SearchEngineInterface {
    private static final Logger logger = Logger.getLogger("TasksRunner");
    /**
     * The key is the normalized language, the value is a PrintWriter ready to write to the file for that language
     */
    private Map<String,PrintWriter> printWriterMap = new HashMap<>();

    /**
     * Executes the specified queryfile with Anserini's SearchCollection program,
     * using the specified number of threads,
     * requesting the specified number of scoredHits, producing the specified runfile, using
     * the specified PARTIAL index.
     */
    public void executeAgainstPartialIndex(int threadCount, int N, String theQueryFileName,
                                           String theRunFileName, String taskNum, String submissionId, String language,
                                           String taskLevelIndexName) {
        throw new TasksRunnerException("executeAgainstPartialIndex not supported for Anserini indexes");
    }

    /**
     * Builds a Galago partial index on the top scoredHits for this task.
     * @param taskID the task ID
     */
    public void buildTaskPartialIndex(String taskID, String submissionId, String indexName, String documentNameList,
                                      String taskLevelIndexName, String language, String taskLevelConfFile) {
        throw new TasksRunnerException("buildTaskPartialIndex not supported for Anserini indexes");
    }

    /**
     * Executes the specified queryfile with Anserini's SearchCollection program,
     * using the specified number of threads,
     * requesting the specified number of scoredHits, producing the specified runfile, using
     * the specified index.
     *
     * @param threadCount
     * @param N
     * @param theQueryFileName
     * @param theRunFileName
     */
    public void search(int threadCount, int N, String theQueryFileName, String theRunFileName,
                       String submissionId, String language) {
        /*
        target/appassembler/bin/SearchCollection
        -index ./openresearch_data/lucene-index-openresearch
        -topics ./openresearch_data/anserini_format/queries.small.test.tsv
        -topicreader TsvString
        -language ar
        -output ./openresearch_data/anserini_format/run.small.test
        -bm25
         */
        /* Note: the HELP text says the -parallelism defaults to 8 */

        /* TBD: pass the name of the custom Analyzer that understands our BETTER query files */

        String command = "SearchCollection";
        String anseriniLogFile = Pathnames.logFileLocation + submissionId + "." + language + ".anserini.log";
        String languageParm = Language.toTwoCharForm(language);
        String tempCommand = Pathnames.anseriniLocation + command
                + " -output " + theRunFileName
                + " -index " + Pathnames.indexLocation + "anserini/better-clear-ir-" + language
                + " -topics " + theQueryFileName
                + " -topicreader TsvString"
                + " -language " + languageParm
                + " -qld "
//                + " -bm25 "
                + " -sdm ";

        logger.info("Run file will be  " + theRunFileName);

        Command.execute(tempCommand, anseriniLogFile);
    }

    private String mustContainString(JSONObject json, String objectName, int lineNo) {
        if (!json.containsKey(objectName)) {
            throw new TasksRunnerException("No " + objectName + " field in corpus file, line " + lineNo);
        }
        return (String) json.get(objectName);
    }

    private JSONObject mustContainObject(JSONObject json, String objectName, int lineNo) {
        if (!json.containsKey(objectName)) {
            throw new TasksRunnerException("No " + objectName + " field in corpus file, line " + lineNo);
        }
        return (JSONObject) json.get(objectName);
    }

    public Map<String, String> getQueries(String queryFileName) {
        Map<String, String> queriesMap = new HashMap<>();
        File f = new File(queryFileName);
        if (f.exists()) {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        new FileInputStream(queryFileName)));
                String line = reader.readLine();
                while (line != null) {
                    String[] tokens = line.split("\t");
                    String reqNum = tokens[0];
                    String query = tokens[1];
                    queriesMap.put(reqNum, query);
                    line = reader.readLine();
                }
                reader.close();
            } catch (Exception e) {
                throw new TasksRunnerException(e);
            }
        }
        return queriesMap;
    }

    private void betterToAnserini (String inputFile) {
        try {
            // Some corpora have duplicates, index only one
            Set<String> ids = new HashSet<>();

            JSONParser parser = new JSONParser();
            BufferedReader rdr = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile)));
            String line;
            int lineNo = 1;
            while ((line = rdr.readLine()) != null) {
                JSONObject json = (JSONObject) parser.parse(line);
                JSONObject derived_metadata = mustContainObject(json, "derived-metadata", lineNo);
                String uuid = mustContainString(derived_metadata, "id", lineNo);
                String text = mustContainString(derived_metadata, "text", lineNo);
                text = JSONValue.escape(text);
                String language = mustContainString(derived_metadata, "language", lineNo);
                language = Language.toCanonicalForm(language);
                if (!ids.contains(uuid)) {
                    ids.add(uuid);
                    if (!printWriterMap.containsKey(language)) {
                        new File(Pathnames.tempFileLocation + language).mkdirs();
                        printWriterMap.put(language, new PrintWriter(Pathnames.tempFileLocation + language +
                                "/" + language + ".json"));
                    }
                    PrintWriter writer = printWriterMap.get(language);

                    writer.println("{\"id\": \"" + uuid + "\", \"contents\": \"" + text + "\"}");
                    ++lineNo;
                }
            }
            for (PrintWriter writer : printWriterMap.values()) {
                writer.close();
            }
        } catch (Exception cause) {
            throw new TasksRunnerException(cause);
        }
    }

    /**
     * Builds an Anserini index for the target corpus.
     */
    public void buildIndexes(String corpusFile) {
        logger.info("Building indexes");
        Instant start = Instant.now();
        preprocess(corpusFile);
        for (String language : printWriterMap.keySet()) {
            /*
            sh ./target/appassembler/bin/IndexCollection -collection JsonCollection  -generator DefaultLuceneDocumentGenerator -threads 8
            -input ./openresearch_data/anserini_format/corpus  -index ./openresearch_data/lucene-index-openresearch -optimize -storePositions -storeDocvectors -storeRaw
             */
            String anseriniLogFile = Pathnames.logFileLocation + "anserini_" + language + "_indexbuild.log";
            String tempCommand = Pathnames.anseriniLocation + "IndexCollection "
                   + " -collection JsonCollection"
                   + " -generator DefaultLuceneDocumentGenerator"
                    + " -threads 8"
                    + " -input " + Pathnames.tempFileLocation + language
                    + " -index " + Pathnames.indexLocation + "anserini/better-clear-ir-" + language
                    + " -language " + Language.toTwoCharForm(language)
                    + " -optimize -storePositions -storeDocvectors -storeRaw";

            Command.execute(tempCommand, anseriniLogFile);

            Instant end = Instant.now();
            Duration interval = Duration.between(start, end);
            long runTime = interval.toMinutes();
            logger.info("Anserini build time (minutes):\n" + runTime);
            /* create a file in the index directory that indicates that this language is part of the corpus */
            try {
                Files.createFile(Paths.get(Pathnames.indexLocation + "anserini/" + language + ".conf"));
            } catch (Exception e) {
                throw new TasksRunnerException(e);
            }
        }
    }

    /**
     * Pre-processes the target corpus file. Must be done before calling buildIndex().
     */
    private void preprocess(String corpusFile) {
        logger.info("Preprocessing the corpus file at " + corpusFile);

        /* Convert the target corpus file into a format we can use. Create separate files for each language
         * found in the corpus file. Create the printWriterMap of which languages are in use.
         */
        betterToAnserini(corpusFile);
    }

}
