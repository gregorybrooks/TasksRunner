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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class Preprocessor {
    private static final Logger logger = Logger.getLogger("TasksRunner");
    private String targetCorpusFile = Pathnames.corpusFileLocation + Pathnames.targetCorpusFileName;
    private String tempFile = Pathnames.tempFileLocation + Pathnames.targetCorpusFileName + ".jl.out";
    private String trecFile = Pathnames.tempFileLocation + Pathnames.targetCorpusFileName + ".trectext";
    private String confFile = Pathnames.indexLocation + Pathnames.targetCorpusFileName + ".conf";

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

    /**
     * @author dfisher (originally)
     * Reads in the corpus file, which is a JSON file that has a BETTER-specific schema,
     * and outputs the documents in the trectext format, suitable for indexing by Galago.
     */
    public void betterToTrec (String inputFile, String outputFile) {
        try {

            JSONParser parser = new JSONParser();
            BufferedReader rdr = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile)));
            PrintWriter writer = new PrintWriter(outputFile);
            String line;
            int id = 1;
            while ((line = rdr.readLine()) != null) {
                JSONObject json = (JSONObject) parser.parse(line);
                if (!json.containsKey("derived-metadata")) {
                    throw new TasksRunnerException("No derived-metadata field in corpus file, line " + id);
                }
                JSONObject derived_metadata = (JSONObject) json.get("derived-metadata");
                if (!derived_metadata.containsKey("id")) {
                    throw new TasksRunnerException("No id field in the derived-metadata field in corpus file, line " + id);
                }
                if (!derived_metadata.containsKey("text")) {
                    throw new TasksRunnerException("No text field in the derived-metadata field in corpus file, line " + id);
                }
                String uuid = (String) derived_metadata.get("id");
                String text = (String) derived_metadata.get("text");
                writer.println("<DOC>\n<DOCNO>" + uuid + "</DOCNO>\n<ID>" + id + "</ID>\n<TEXT>");
                writer.println(text);
                writer.println("</TEXT>\n</DOC>");
                ++id;
            }
            writer.close();
        } catch (Exception cause) {
            throw new TasksRunnerException(cause);
        }
    }

    /**
     * In order to use Galago's #reject operator we add a field to each
     * document that contains its unique identifier.
     * @param inFileName the trectext format file
     * @param outputFileName the updated trectext format file
     */
    public void addExid (String inFileName, String outputFileName) {
        try {
            PrintWriter writer = new PrintWriter(outputFileName);
            FileReader reader = new FileReader(inFileName);
            BufferedReader br = new BufferedReader(reader);
            String line;
            int linesRead = 0;
            int linesWritten = 0;
            int docs = 0;
            String save_line = "";
            while((line = br.readLine()) != null)
            {
                ++linesRead;
                writer.println(line);
                ++linesWritten;
                if (line.startsWith("<DOCNO>")) {
                    ++docs;
                    save_line = line;
                    save_line = save_line.replace("DOCNO", "EXID");
                }
                else if (line.startsWith("<TEXT>")) {
                    writer.println(save_line);
                    ++linesWritten;
                }
            }
            writer.close();
            reader.close();
        } catch (Exception cause) {
            throw new TasksRunnerException(cause);
        }
    }

    /**
     * Builds a Galago index for the target corpus.
     */
    void buildIndex() {
        logger.info("Building an index for the target corpus");
        logger.info("Creating Galago config file " + confFile);
        createGalagoConfFile(confFile);
        Instant start = Instant.now();

        String galagoLogFile = Pathnames.logFileLocation + "galago_target_indexbuild.log";
        String tempCommand = Pathnames.galagoLocation + "galago build " + confFile + " >& " + galagoLogFile;

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
            try (Stream<String> stream = Files.lines( Paths.get(galagoLogFile), StandardCharsets.UTF_8))
            {
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
        long runTime  = interval.toMinutes();
        logger.info("Index build time (minutes):\n" + runTime);
    }

    /**
     * Creates a Galago config file specifying the parameters for building the index
     * for the Arabic corpus.
     * @param fileName the full pathname for the config file
     */
    private void createGalagoConfFile(String fileName) {
        try {
            JSONObject outputQueries = new JSONObject();
            outputQueries.put("fileType", "trectext");
            outputQueries.put("inputPath", trecFile );
            outputQueries.put("indexPath", Pathnames.targetIndexLocation);
            outputQueries.put("mode", "local" );
            outputQueries.put("fieldIndex", true);
            outputQueries.put("tmpdir", Pathnames.tempFileLocation );
            JSONArray stemmerList = new JSONArray();
            JSONObject stemmerClass = new JSONObject();
            if (Pathnames.targetLanguage.equals("ARABIC")) {
                stemmerList.add("krovetz");
                stemmerList.add("snowball");
                stemmerClass.put("krovetz", "org.lemurproject.galago.core.parse.stem.KrovetzStemmer");
                stemmerClass.put("snowball", "org.lemurproject.galago.core.parse.stem.SnowballArabicStemmer");
            } else if (Pathnames.targetLanguage.equals("ENGLISH")) {
                stemmerList.add("krovetz");
                stemmerClass.put("krovetz", "org.lemurproject.galago.core.parse.stem.KrovetzStemmer");
            } else if (Pathnames.targetLanguage.equals("FARSI")) {
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
            outputQueries.put("galagoJobDir", Pathnames.galagoJobDirLocation);
            outputQueries.put("deleteJobDir", true);
            outputQueries.put("mem", "40g");

            PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(fileName)));
            writer.write(outputQueries.toJSONString());
            writer.close();
        } catch (Exception cause) {
            throw new TasksRunnerException(cause);
        }
    }

    /**
     * Pre-processes the target corpus file. Must be done before calling buildIndex().
     */
    void preprocess() {
        logger.info("Preprocessing the target corpus at " + targetCorpusFile);
        logger.info("Output is going to " + trecFile);

        /* Convert the target corpus file into a format we can use. */
        if (Pathnames.corpusFileFormat.equals("BETTER")) {
            betterToTrec(targetCorpusFile, tempFile);
        } else if (Pathnames.corpusFileFormat.equals("FARSI")) {
            farsiToTrec(targetCorpusFile, tempFile);
        }
        /* Add the EXID field, to store the unique ID (docid) for each document. */
        addExid(tempFile, trecFile);
    }

}
