package edu.umass.ciir;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
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

public class GalagoSearchEngine implements SearchEngineInterface {
    private static final Logger logger = Logger.getLogger("TasksRunner");
//    private String corpusFile = Pathnames.corpusFileLocation + Pathnames.targetCorpusFileName;
//    private String type;
    /**
     * The key is the normalized language, the value is a PrintWriter ready to write to the file for that language
     */
    private Map<String,PrintWriter> printWriterMap = new HashMap<>();

/*
    public GalagoSearchEngine(String indexType) {
        if (!(indexType.equals("target") || indexType.equals("english"))) {
            throw new TasksRunnerException("Invalid index type " + indexType + ", should be english or target");
        }
        type = indexType;   // either "target" or "english"
    }
*/
    /**
     * Executes the specified queryfile with Galago's batch-search,
     * using the specified number of Galago batch threads,
     * requesting the specified number of scoredHits, producing the specified runfile, using
     * the specified PARTIAL index.
     *
     * @param threadCount
     * @param N
     * @param theQueryFileName
     * @param theRunFileName
     */
    public void executeAgainstPartialIndex(int threadCount, int N, String theQueryFileName,
                                            String theRunFileName, String taskNum, String submissionId, String language,
                                            String taskLevelIndexName) {
        // TBD: change this to use the library instead of the CLI
        Instant start = Instant.now();

        String command = "galago threaded-batch-search";
        if (threadCount == 1) {
            command = "galago batch-search";
        }
        String arabicPart = "";
        if (!Pathnames.runGetCandidateDocs && (language.equals("arabic") || language.equals("russian"))) {
            arabicPart = " --defaultTextPart=postings.snowball";
        }
        String galagoLogFile = Pathnames.logFileLocation + submissionId + ".galago_" + taskNum + "_executeAgainstPartial.log";
        String tempCommand = Pathnames.galagoLocation + command
                + " --outputFile=" + theRunFileName + " --threadCount=" + threadCount
                + " --systemName=CLEAR --trec=true "
                + " --index/partial=" + Pathnames.indexLocation + "galago/" + taskLevelIndexName
                + " --index/full=" + Pathnames.indexLocation + "galago/better-clear-ir-" + language
                + " --defaultIndexPart=partial --backgroundIndex=full"
                + arabicPart
                + " --requested=" + N + " " + theQueryFileName + " >& " + galagoLogFile;

        logger.info("Executing this command: " + tempCommand);

        try {
            Files.delete(Paths.get(galagoLogFile));
        } catch (IOException ignore) {
            ;
        }

        int exitVal = 0;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command("bash", "-c", tempCommand);
            Process process = processBuilder.start();

            exitVal = process.waitFor();
        } catch (Exception cause) {
            logger.log(Level.SEVERE, "Exception doing Galago execution", cause);
            throw new TasksRunnerException(cause);
        } finally {
            StringBuilder builder = new StringBuilder();
            try (Stream<String> stream = Files.lines( Paths.get(galagoLogFile), StandardCharsets.UTF_8))
            {
                stream.forEach(s -> builder.append(s).append("\n"));
                logger.info("Galago output log:\n" + builder.toString());
            } catch (IOException ignore) {
                // logger.info("IO error trying to read Galago output file. Ignoring it");
            }
        }
        if (exitVal != 0) {
            logger.log(Level.SEVERE, "Unexpected ERROR from Galago, exit value is: " + exitVal);
            // TEMP throw new TasksRunnerException("Unexpected ERROR from Galago, exit value is: " + exitVal);
        }

    }

    /**
     * Builds a Galago partial index on the top scoredHits for this task.
     * @param taskID the task ID
     */
    public void buildTaskPartialIndex(String taskID, String submissionId, String indexName, String documentNameList,
                                      String taskLevelIndexName, String language, String taskLevelConfFile) {
        createGalagoPartialIndexConfFile(taskID, language, taskLevelConfFile);

        String galagoLogFile = Pathnames.logFileLocation + submissionId + ".galago_" + taskID + "_indexbuild.log";
        String tempCommand = Pathnames.galagoLocation + "galago build-partial-index --documentNameList=" +
                documentNameList +
                " --index=" + indexName +
                " --partialIndex=" + Pathnames.indexLocation + "galago/" + taskLevelIndexName
                + " " + taskLevelConfFile + " >& " + galagoLogFile;  // this is the way to specify fields for a partial index build

        logger.info("Executing this command: " + tempCommand);

        try {
            Files.delete(Paths.get(galagoLogFile));
        } catch (IOException ignore) {
            ;
        }

        int exitVal = 0;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command("bash", "-c", tempCommand);
            Process process = processBuilder.start();

            exitVal = process.waitFor();
        } catch (Exception cause) {
            logger.log(Level.SEVERE, "Exception doing Galago execution", cause);
            throw new TasksRunnerException(cause);
        } finally {
            StringBuilder builder = new StringBuilder();
            try (Stream<String> stream = Files.lines( Paths.get(galagoLogFile), StandardCharsets.UTF_8))
            {
                stream.forEach(s -> builder.append(s).append("\n"));
                logger.info("Galago output log:\n" + builder.toString());
            } catch (IOException ignore) {
                // logger.info("IO error trying to read Galago output file. Ignoring it");
            }
        }
        if (exitVal != 0) {
            logger.log(Level.SEVERE, "Unexpected ERROR from Galago, exit value is: " + exitVal);
            throw new TasksRunnerException("Unexpected ERROR from Galago, exit value is: " + exitVal);
        }

    }

    /**
     * Creates a Galago config file specifying the parameters for building the index
     * for this task's top scoredHits. This is the version to be used when building a PARTIAL index.
     * @param taskID the task ID
     * @return the name of the Galago config file, specific to this task
     */
    private void createGalagoPartialIndexConfFile(String taskID, String language, String taskLevelConfFile) {
        try {
            JSONObject outputQueries = new JSONObject();
            outputQueries.put("mode", "local" );
            outputQueries.put("fieldIndex", true);
            outputQueries.put("tmpdir", Pathnames.tempFileLocation );

            JSONArray stemmerList = new JSONArray();
            JSONObject stemmerClass = new JSONObject();
            if (Pathnames.runGetCandidateDocs || language.equals("english")) {
                stemmerList.add("krovetz");
                stemmerClass.put("krovetz", "org.lemurproject.galago.core.parse.stem.KrovetzStemmer");
            } else if (language.equals("arabic")) {
                stemmerList.add("krovetz");
                stemmerList.add("snowball");
                stemmerClass.put("krovetz", "org.lemurproject.galago.core.parse.stem.KrovetzStemmer");
                stemmerClass.put("snowball", "org.lemurproject.galago.core.parse.stem.SnowballArabicStemmer");
            } else if (language.equals("russian")) {
                stemmerList.add("snowball");
                stemmerClass.put("snowball", "org.lemurproject.galago.core.parse.stem.SnowballRussianStemmer");
            } else if (language.equals("farsi")) {
            } else if (language.equals("korean")) {
            } else if (language.equals("chinese")) {
            }
            outputQueries.put("stemmer", stemmerList);
            outputQueries.put("stemmerClass", stemmerClass );

            JSONObject tokenizer = new JSONObject();
            JSONArray fields = new JSONArray();
            fields.add("exid");
            tokenizer.put("fields", fields);
            JSONObject formats = new JSONObject();
            formats.put("exid", "string");
            tokenizer.put("formats", formats);
            outputQueries.put("tokenizer", tokenizer);
            outputQueries.put("galagoJobDir", Pathnames.galagoJobDirLocation + taskID);
            outputQueries.put("deleteJobDir", true);
            outputQueries.put("mem", "40g");

            PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(taskLevelConfFile)));
            writer.write(outputQueries.toJSONString());
            writer.close();
        } catch (Exception cause) {
            throw new TasksRunnerException(cause);
        }
    }




    /**
     * Executes the specified queryfile with Galago's batch-search,
     * using the specified number of Galago batch threads,
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
        String command = "galago threaded-batch-search";
        if (threadCount == 1) {
            command = "galago batch-search";
        }
        String galagoLogFile = Pathnames.logFileLocation + submissionId + "." + language + ".galago.log";
        String arabicParm = "";
        if (!Pathnames.runGetCandidateDocs && (language.equals("arabic")) || language.equals("russian")) {
            arabicParm = "--defaultTextPart=postings.snowball ";
        }
        String tempCommand = Pathnames.galagoLocation + command
                + " --outputFile=" + theRunFileName + " --threadCount=" + threadCount
                + " --systemName=CLEAR " + arabicParm + "--trec=true --index=" + Pathnames.indexLocation
                      + "galago/better-clear-ir-" + language
                + " --requested=" + N + " " + theQueryFileName + " >& " + galagoLogFile;

        logger.info("Executing this command: " + tempCommand);
        logger.info("Run file will be  " + theRunFileName);

        try {
            Files.delete(Paths.get(galagoLogFile));
        } catch (IOException ignore) {
            ;
        }

        int exitVal = 0;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command("bash", "-c", tempCommand);
            Process process = processBuilder.start();

            exitVal = process.waitFor();
        } catch (Exception cause) {
            logger.log(Level.SEVERE, "Exception doing Galago execution", cause);
            throw new TasksRunnerException(cause);
        } finally {
            StringBuilder builder = new StringBuilder();
            try (Stream<String> stream = Files.lines( Paths.get(galagoLogFile), StandardCharsets.UTF_8))
            {
                stream.forEach(s -> builder.append(s).append("\n"));
                logger.info("Galago output log:\n" + builder.toString());
            } catch (IOException ignore) {
                // logger.info("IO error trying to read Galago output file. Ignoring it");
            }
        }
        if (exitVal != 0) {
            logger.log(Level.SEVERE, "Unexpected ERROR from Galago, exit value is: " + exitVal);
            throw new TasksRunnerException("Unexpected ERROR from Galago, exit value is: " + exitVal);
        }
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
                Reader reader = new BufferedReader(new InputStreamReader(
                        new FileInputStream(queryFileName)));
                JSONParser parser = new JSONParser();
                JSONObject head = (JSONObject) parser.parse(reader);
                JSONArray queries = (JSONArray) head.get("queries");
                for (Object oRequest : queries) {
                    JSONObject r = (JSONObject) oRequest;
                    String reqNum = (String) r.get("number");
                    String reqText = (String) r.get("text");
                    queriesMap.put(reqNum, reqText);
                }
                reader.close();
            } catch (Exception e) {
                throw new TasksRunnerException(e);
            }
        }
        return queriesMap;
    }


    /**
     * @author dfisher (originally)
     * Reads in the corpus file, which is a JSON file that has a BETTER-specific schema,
     * and outputs the documents in the trectext format, suitable for indexing by Galago.
     */
    private void betterToTrec (String inputFile) {
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
                String language = mustContainString(derived_metadata, "language", lineNo);
                language = toCanonicalForm(language);
                if (!ids.contains(uuid)) {
                    ids.add(uuid);
                    if (!printWriterMap.containsKey(language)) {
                        printWriterMap.put(language, new PrintWriter(Pathnames.tempFileLocation + language + ".jl.out"));
                    }
                    PrintWriter writer = printWriterMap.get(language);

                    writer.println("<DOC>\n<DOCNO>" + uuid + "</DOCNO>\n<ID>" + lineNo + "</ID>\n<TEXT>");

                    if (language.equals("chinese")) {
                        text = bigramIt(text);
                    }
                    writer.println(text);
                    writer.println("</TEXT>\n</DOC>");
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

    public String bigramIt(String rawtext) {
        String[] sentences = rawtext.split("。");
        String newText = "";
        for (String sentence : sentences) {
            // Remove any whitespace and punctuation
            String text = sentence.replaceAll("\\p{Punct}", "");
            text = text.replaceAll("\\s+", "");

            if (text.length() == 1) {
                newText += text.charAt(0);
            } else if (text.length() == 2) {
                newText += text.substring(0, 2);
            } else {
                for (int i = 1; i < text.length(); ++i) {
                    newText += text.substring(i - 1, i + 1);
                    newText += " ";
                }
            }
            newText += " ";
        }
        return newText;
    }

    /**
     * In order to use Galago's #reject operator we add a field to each
     * document that contains its unique identifier.
     */
    private void addExid () {
        try {
            for (String language : printWriterMap.keySet()) {
                PrintWriter writer = new PrintWriter(Pathnames.tempFileLocation + language + ".trectext");
                FileReader reader = new FileReader(Pathnames.tempFileLocation + language + ".jl.out");
                BufferedReader br = new BufferedReader(reader);
                String line;
                int linesRead = 0;
                int linesWritten = 0;
                int docs = 0;
                String save_line = "";
                while ((line = br.readLine()) != null) {
                    ++linesRead;
                    writer.println(line);
                    ++linesWritten;
                    if (line.startsWith("<DOCNO>")) {
                        ++docs;
                        save_line = line;
                        save_line = save_line.replace("DOCNO", "EXID");
                    } else if (line.startsWith("<TEXT>")) {
                        writer.println(save_line);
                        ++linesWritten;
                    }
                }
                writer.close();
                reader.close();
            }
        } catch (Exception cause) {
            throw new TasksRunnerException(cause);
        }
    }

    /**
     * Builds a Galago index for the target corpus.
     */
    public void buildIndexes(String corpusFile) {
        logger.info("Building indexes");
        Instant start = Instant.now();
        preprocess(corpusFile);
        createGalagoConfFiles();
        for (String language : printWriterMap.keySet()) {

            String galagoLogFile = Pathnames.logFileLocation + "galago_" + language + "_indexbuild.log";
            String tempCommand = Pathnames.galagoLocation + "galago build " + Pathnames.indexLocation + "galago/" + language + ".conf"
                    + " >& " + galagoLogFile;

            logger.info("Executing this command: " + tempCommand);

            try {
                Files.delete(Paths.get(galagoLogFile));
            } catch (IOException ignore) {
                // do nothing
            }

            int exitVal;
            try {
                ProcessBuilder processBuilder = new ProcessBuilder();
                processBuilder.command("bash", "-c", tempCommand);
                Process process = processBuilder.start();

                exitVal = process.waitFor();
            } catch (Exception cause) {
                logger.log(Level.SEVERE, "Exception doing Galago execution", cause);
                throw new TasksRunnerException(cause);
            } finally {
                StringBuilder builder = new StringBuilder();
                try (Stream<String> stream = Files.lines(Paths.get(galagoLogFile), StandardCharsets.UTF_8)) {
                    stream.forEach(s -> builder.append(s).append("\n"));
                    logger.info("Galago output log:\n" + builder.toString());
                } catch (IOException ignore) {
                    // logger.info("IO error trying to read Galago output file. Ignoring it");
                }
            }
            if (exitVal != 0) {
                logger.log(Level.SEVERE, "Unexpected ERROR from Galago, exit value is: " + exitVal + ". See galago.log.");
                throw new TasksRunnerException("Unexpected ERROR from Galago, exit value is: " + exitVal + ". See galago.log.");
            }

            Instant end = Instant.now();
            Duration interval = Duration.between(start, end);
            long runTime = interval.toMinutes();
            logger.info("GalagoSearchEngine build time (minutes):\n" + runTime);
        }
    }

    /**
     * Creates a Galago config file specifying the parameters for building the index
     */
    private void createGalagoConfFiles() {
        for (String language : printWriterMap.keySet()) {
            try {
                JSONObject outputQueries = new JSONObject();
                outputQueries.put("fileType", "trectext");
                outputQueries.put("inputPath", Pathnames.tempFileLocation + language + ".trectext");
                outputQueries.put("indexPath", Pathnames.indexLocation + "galago/better-clear-ir-" + language);
                outputQueries.put("mode", "local");
                outputQueries.put("fieldIndex", true);
                outputQueries.put("tmpdir", Pathnames.tempFileLocation);
                JSONArray stemmerList = new JSONArray();
                JSONObject stemmerClass = new JSONObject();
                if (language.equals("arabic")) {
                    stemmerList.add("snowball");
                    stemmerClass.put("snowball", "org.lemurproject.galago.core.parse.stem.SnowballArabicStemmer");
                } else if (language.equals("russian")) {
                        stemmerList.add("snowball");
                        stemmerClass.put("snowball", "org.lemurproject.galago.core.parse.stem.SnowballRussianStemmer");
                } else if (language.equals("chinese")) {
                } else if (language.equals("korean")) {
                } else {
                    /* English training corpus */
                    stemmerList.add("krovetz");
                    stemmerClass.put("krovetz", "org.lemurproject.galago.core.parse.stem.KrovetzStemmer");
                }
                outputQueries.put("stemmer", stemmerList);
                outputQueries.put("stemmerClass", stemmerClass);
                JSONObject tokenizer = new JSONObject();
                JSONArray fields = new JSONArray();
                fields.add("exid");
                tokenizer.put("fields", fields);
                JSONObject formats = new JSONObject();
                formats.put("exid", "string");
                tokenizer.put("formats", formats);
                outputQueries.put("tokenizer", tokenizer);
                outputQueries.put("galagoJobDir", Pathnames.galagoJobDirLocation);
                outputQueries.put("deleteJobDir", true);
                outputQueries.put("mem", "40g");

                PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                        new FileOutputStream(Pathnames.indexLocation + "galago/" + language + ".conf")));
                writer.write(outputQueries.toJSONString());
                writer.close();
            } catch (Exception cause) {
                throw new TasksRunnerException(cause);
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
            betterToTrec(corpusFile);

            /* Add the EXID field, to store the unique ID (docid) for each document. */
            addExid();
    }

}
