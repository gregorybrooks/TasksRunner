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
import java.util.logging.*;
import java.util.Map;
import java.util.stream.Stream;

public class TasksRunner {

    private static final Logger logger = Logger.getLogger("TasksRunner");

    /**
     * Configures the logger for this program.
     * @param logFileName Name to give the log file.
     */
    private void configureLogger(String logFileName) {
        SimpleFormatter formatterTxt;
        FileHandler fileTxt;
        try {
            // suppress the logging output to the console
            Logger rootLogger = Logger.getLogger("");
            Handler[] handlers = rootLogger.getHandlers();
            if (handlers[0] instanceof ConsoleHandler) {
                rootLogger.removeHandler(handlers[0]);
            }
            logger.setLevel(Level.INFO);
            fileTxt = new FileHandler(logFileName);
            // create a TXT formatter
            formatterTxt = new SimpleFormatter();
            fileTxt.setFormatter(formatterTxt);
            logger.addHandler(fileTxt);
        } catch (Exception cause) {
            throw new TasksRunnerException(cause);
        }
    }

    /**
     * Sets up logging for this program.
     */
    public void setupLogging() {
        String logFileName = Pathnames.logFileLocation + Pathnames.mode + "/tasks-runner.log";
        configureLogger(logFileName);
    }

    /**
     * Processes the analytic tasks file: generates queries for the Tasks and Requests,
     * executes the queries, annotates hits with events.
     */
    private void process() {
        logger.info("Opening the analytic task file, expanding example docs");
        AnalyticTasks tasks = new AnalyticTasks();

        /*
         * We can be executing in one of 3 modes: AUTO, AUTO-HITL, or HITL.
         * Processing is a little different in each mode, but the differences are hidden inside
         * the query formulators. The caller passes in the mode through the env file.
         * The input analytic tasks file should be different for AUTO vs the other 2 modes:
         * the AUTO file should not have anything for the fields like Task Narrative and
         * Request Text. Those are only provided in AUTO-HITL and HITL mode.
         * HITL mode's only difference is that more sample documents are passed in, in the
         * supplemental_info.json file.
         * Once we know the mode, we select the 2 query formulators created for that mode.
         * (There are separate query formulators for the creation of Task-level queries and
         * Request-level queries.)
         */
        String requestLevelFormulator;
        String taskLevelFormulator;
        String mode = tasks.getMode();
        switch (mode) {
            case "HITL":
                requestLevelFormulator = "QF_HITL";
                taskLevelFormulator = "QF_HITLTaskLevel";
                break;
            case "AUTO":
                requestLevelFormulator = "QF_AUTO";
                taskLevelFormulator = "QF_AUTOTaskLevel";
                break;
            case "AUTO-HITL":
                requestLevelFormulator = "QF_AUTO_HITL";
                taskLevelFormulator = "QF_AUTO_HITLTaskLevel";
                break;
            default:
                throw new TasksRunnerException("Invalid mode: " + mode);
        }
        logger.info("Executing in " + mode + " mode");

        EventExtractor eventExtractor = new EventExtractor(tasks, mode);

        boolean runPhase1 = Pathnames.runIRPhase1;
        boolean runPhase2 = Pathnames.runIRPhase2;
        boolean runPhase3 = Pathnames.runIRPhase3;

        if (!runPhase1) {
            System.out.println("Skipping phase 1");
        } else {
            logger.info("PHASE 1: Preparing a file of the example docs for the event extractor");
            eventExtractor.extractExampleEventsPart1();
            logger.info("PHASE 1 COMPLETE");
    	}

        // Extract events from sample docs here...

        if (!runPhase2) {
            System.out.println("Skipping phase 2");
        } else {
            logger.info("PHASE 2: Retrieving the file of example doc events created by the event extractor");
            // TEMP ignore events completely
            // eventExtractor.extractExampleEventsPart2();

            /* write the analytic tasks info file, with event info, for multipartite
            and re-ranking to use */
            tasks.writeJSONVersion();
    
            QueryManager qf = new QueryManager(tasks, taskLevelFormulator);
            qf.resetQueries();  // Clears any existing queries read in from an old file
    
            logger.info("PHASE 2: Building task-level queries");
            NewQueryFormulator queryFormulator = NewQueryFormulatorFactory(tasks, taskLevelFormulator);
            queryFormulator.buildQueries(qf.getQueryFileName());

            qf.readQueryFile();

            logger.info("PHASE 2: Executing the task-level queries");
            qf.execute(2500);
            logger.info("PHASE 2: Execution of task-level queries complete. Run time: " + qf.getRunTime());

            /* Create an input file for the event extractor for the top N task-level hits.
            (We did this to experiment with task-level hits, but it is not needed normally.)
            logger.info("Extracting events from the top task-level hits");
            eventExtractor.createInputForEventExtractorFromTaskHits(qf);
            */

            // Evaluate the task-level results (they are saved into a file as a side effect)
	        if (Pathnames.doTaskLevelEvaluation) {
                logger.info("PHASE 2: Evaluating the task-level results");
                Map<String, QueryManager.EvaluationStats> tstats = qf.evaluateTaskLevel();
            }

            logger.info("PHASE 2: Building a separate index for each task's top hits");
            qf.buildTaskLevelIndexes();
    
            logger.info("PHASE 2: Building request-level queries");
            qf = new QueryManager(tasks, requestLevelFormulator);
            qf.resetQueries();  // Clears any existing queries read in from an old file

            NewQueryFormulator requestQueryFormulator = NewQueryFormulatorFactory(tasks, requestLevelFormulator);
            requestQueryFormulator.buildQueries(qf.getQueryFileName());

            qf.readQueryFile();

            logger.info("PHASE 2: Writing separate query files for the requests in each task");
            qf.writeQueryFiles();
    
            logger.info("PHASE 2: Executing the request queries, using the task-level indexes");
            qf.executeRequestQueries(eventExtractor, taskLevelFormulator);
    
            logger.info("PHASE 2: Execution of request queries complete. Run time: " + qf.getRunTime());
    
            // Evaluate the request-level results (they are saved into a file as a side effect)
	        if (Pathnames.doRequestLevelEvaluation) {
                Map<String, Double> rstats = qf.evaluate("RAW");
                logger.info("PHASE 2: Evaluation of request-level hits complete");
            }

            /* Extract events from the request-level hits, to use when re-ranking the request-level results */
            logger.info("Extracting events from the top request-level hits");
            /* TEMP - no need to do this because we are not doing phase 3
            eventExtractor.createInputForEventExtractorFromRequestHits(qf);
            */

            // [Run the ISI QF event extractor here]
    
            logger.info("PHASE 2 COMPLETE");
        } 

	    if (!runPhase3) {
	        System.out.println("Skipping phase 3");
	    } else {
            QueryManager qf = new QueryManager(tasks, requestLevelFormulator);
    
            logger.info("PHASE 3: Building file with doc text and events for request hits, for reranker");
            eventExtractor.retrieveEventsFromRequestHits(qf);

            logger.info("PHASE 3: Building file with doc text and events for task hits, for experiments");
            /*
            eventExtractor.retrieveEventsFromTaskHits(qf);
             */

            logger.info("PHASE 3: Reranking");
            qf.rerank();
    
            //eventExtractor.writeFileForHITL(qf);  // writes top 10 hits for HITL to judge
    
            // Evaluate the request-level results (they are saved into a file as a side effect)
	        if (Pathnames.doRequestLevelEvaluation) {
                Map<String, Double> rstats = qf.evaluate("RERANKED");
                logger.info("PHASE 3: Evaluation of request-level hits complete");
            }

            logger.info("PHASE 3: Merging IR and IE results");
            qf.writeFinalResultsFile();  // Combined IR and IE results
    
            logger.info("PHASE 3 COMPLETE");
	    }
    }

    public static NewQueryFormulator NewQueryFormulatorFactory(AnalyticTasks tasks, String formulatorVariant) {
       NewQueryFormulator qf;
       switch (formulatorVariant) {
           case "QF_AUTO":
               qf = new QueryFormulatorArabic1(tasks);
               break;
           case "QF_HITL":
               qf = new QueryFormulatorArabic_HITL(tasks);
               break;
           case "QF_AUTO_HITL":
               qf = new QueryFormulatorArabic_AUTO_HITL(tasks);
               break;
           case "QF_AUTOTaskLevel":
               qf = new QueryFormulatorArabic1TaskLevel(tasks);
               break;
           case "QF_HITLTaskLevel":
               qf = new QueryFormulatorArabic_HITLTaskLevel(tasks);
               break;
           case "QF_AUTO_HITLTaskLevel":
               qf = new QueryFormulatorArabic_AUTO_HITLTaskLevel(tasks);
               break;
           default:
               throw new TasksRunnerException("Invalid query formulator type: " + formulatorVariant);
       }
        return qf;
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
     * Builds a Galago index for the Arabic corpus.
     */
    private void buildIndex() {
        String confFile = Pathnames.indexLocation + "better-clear-ir-arabic.conf";
        logger.info("Building an index for arabic data");
        logger.info("Creating Galago config file " + confFile);
        createGalagoConfFile(confFile);
        Instant start = Instant.now();

        String galagoLogFile = Pathnames.logFileLocation + "galago_arabic_indexbuild.log";
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
            String trecFile = Pathnames.tempFileLocation + "BETTER-Arabic-IR-data.v1-uuid.trectext";
            outputQueries.put("inputPath", trecFile );
            outputQueries.put("indexPath", Pathnames.arabicIndexLocation );
            outputQueries.put("mode", "local" );
            outputQueries.put("fieldIndex", true);
            outputQueries.put("tmpdir", Pathnames.tempFileLocation );
            JSONArray stemmerList = new JSONArray();
            stemmerList.add("krovetz");
            stemmerList.add("snowball");
            outputQueries.put("stemmer", stemmerList);
            JSONObject stemmerClass = new JSONObject();
            stemmerClass.put("krovetz", "org.lemurproject.galago.core.parse.stem.KrovetzStemmer");
            stemmerClass.put("snowball", "org.lemurproject.galago.core.parse.stem.SnowballArabicStemmer");
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
     * Pre-processes the Arabic corpus file. Must be done before calling buildIndex().
     */
    private void preprocess() {
        String arabicCorpusFile = Pathnames.dataFileLocation + Pathnames.arabicCorpusFileName;
        String tempFile = Pathnames.tempFileLocation + "BETTER-Arabic-IR-data.v1.jl.out";
        String trecFile = Pathnames.tempFileLocation + "BETTER-Arabic-IR-data.v1-uuid.trectext";
        logger.info("Preprocessing the Arabic corpus at " + arabicCorpusFile);
        logger.info("Output is going to " + trecFile);

        /* Convert the BETTER corpus file into a format we can use. */
        betterToTrec(arabicCorpusFile, tempFile);
        /* Add the EXID field, to store the unique ID (docid) for each document. */
        addExid(tempFile, trecFile);
    }

    /**
     * Public entry point for this class.
     */
    public static void main (String[] args) {
        TasksRunner betterIR = new TasksRunner();
        betterIR.setupLogging();
        if (Pathnames.runPreprocess) {
            betterIR.preprocess();
        } else if (Pathnames.runIndexBuild) {
            betterIR.buildIndex();
        } else {
            betterIR.process();
        }
    }
}
