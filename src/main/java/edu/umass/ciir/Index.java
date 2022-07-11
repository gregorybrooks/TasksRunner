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
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class Index {
    private static final Logger logger = Logger.getLogger("TasksRunner");
    private String corpusFile = Pathnames.corpusFileLocation + Pathnames.targetCorpusFileName;
    private String tempFile = Pathnames.tempFileLocation + Pathnames.targetCorpusFileName + ".jl.out";
    private String trecFile = Pathnames.tempFileLocation + Pathnames.targetCorpusFileName + ".trectext";
    private String indexListFile = Pathnames.indexLocation + "index_list.json";
    private String confFile = Pathnames.indexLocation + Pathnames.targetCorpusFileName + ".conf";
    private String indexPath = Pathnames.targetIndexLocation;
    private String type;
    private Map<String,PrintWriter> printWriterMap = new HashMap<>();

    public Index(String indexType) {
        type = indexType;   // either "target" or "english"
    }

    public void farsiToTrec (String inputFile, String outputFile) {
        try {
            BufferedReader rdr = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile)));
            PrintWriter writer = new PrintWriter(outputFile);
            String line;
            int id = 0;
            while ((line = rdr.readLine()) != null) {
                if (line.startsWith(".DID")) {
                    if (id > 0) {
                        writer.println("</TEXT>\n</DOC>");
                    }
                    ++ id;
                    String[] tokens = line.split("\t");
                    if (tokens.length != 2) {
                        throw new TasksRunnerException(".DID on line " + id
                                + " does not have 2 tokens after split on tab");
                    }
                    String uuid = tokens[1];
                    writer.println("<DOC>\n<DOCNO>" + uuid + "</DOCNO>\n<ID>" + id + "</ID>\n<TEXT>");
                } else if (line.startsWith(".Date")) {
                } else if (line.startsWith(".Cat")) {
                } else {
                    writer.println(line);
                }
            }
            // finish the last document
            if (id > 0) {
                writer.println("</TEXT>\n</DOC>");
            }
            writer.close();
        } catch (Exception cause) {
            throw new TasksRunnerException(cause);
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

    /**
     * @author dfisher (originally)
     * Reads in the corpus file, which is a JSON file that has a BETTER-specific schema,
     * and outputs the documents in the trectext format, suitable for indexing by Galago.
     */
    public void betterToTrec (String inputFile) {
        try {

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

                if (!printWriterMap.containsKey(language)) {
                    printWriterMap.put(language, new PrintWriter(Pathnames.tempFileLocation + language + ".jl.out"));
                }
                PrintWriter writer = printWriterMap.get(language);

                writer.println("<DOC>\n<DOCNO>" + uuid + "</DOCNO>\n<ID>" + lineNo + "</ID>\n<TEXT>");
                writer.println(text);
                writer.println("</TEXT>\n</DOC>");
                ++lineNo;
            }
            for (PrintWriter writer : printWriterMap.values()) {
                writer.close();
            }
        } catch (Exception cause) {
            throw new TasksRunnerException(cause);
        }
    }

    /**
     * In order to use Galago's #reject operator we add a field to each
     * document that contains its unique identifier.
     */
    public void addExid () {
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
    void buildIndex() {
        logger.info("Building indexes");
        Instant start = Instant.now();
        createGalagoConfFiles();
        for (String language : printWriterMap.keySet()) {

            String galagoLogFile = Pathnames.logFileLocation + "galago_" + language + "_indexbuild.log";
            String tempCommand = Pathnames.galagoLocation + "galago build " + Pathnames.indexLocation + language + ".conf"
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
            logger.info("Index build time (minutes):\n" + runTime);
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
                outputQueries.put("indexPath", Pathnames.indexLocation + "better-clear-ir-" + language);
                outputQueries.put("mode", "local");
                outputQueries.put("fieldIndex", true);
                outputQueries.put("tmpdir", Pathnames.tempFileLocation);
                JSONArray stemmerList = new JSONArray();
                JSONObject stemmerClass = new JSONObject();
                if (language.equals("Arabic")) {
                    stemmerList.add("snowball");
                    stemmerClass.put("snowball", "org.lemurproject.galago.core.parse.stem.SnowballArabicStemmer");
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
                        new FileOutputStream(Pathnames.indexLocation + language + ".conf")));
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
    void preprocess() {
        if (type.equals("target")) {
            corpusFile = Pathnames.corpusFileLocation + Pathnames.targetCorpusFileName;
            confFile = Pathnames.indexLocation + "target.conf";
            indexPath = Pathnames.targetIndexLocation;

            logger.info("Preprocessing the target corpus at " + corpusFile);

            /* Convert the target corpus file into a format we can use. Create separate files for each language
             * found in the corpus file.
             */
            if (Pathnames.corpusFileFormat.equals("BETTER")) {
                betterToTrec(corpusFile);
            } /*else if (Pathnames.corpusFileFormat.equals("FARSI")) {
                farsiToTrec(corpusFile, tempFile);
            }*/

            /* Add the EXID field, to store the unique ID (docid) for each document. */
            addExid();
        } else if (type.equals("english")) {
            corpusFile = Pathnames.corpusFileLocation + Pathnames.englishCorpusFileName;
            confFile = Pathnames.indexLocation + "english.conf";
            indexPath = Pathnames.englishIndexLocation;

            logger.info("Preprocessing the English corpus at " + corpusFile);

            /* Convert the English corpus file into a format we can use. */
            betterToTrec(corpusFile);
            /* Add the EXID field, to store the unique ID (docid) for each document. */
            addExid();
        } else {
            throw new TasksRunnerException("Invalid index type:" + type);
        }
    }

}
