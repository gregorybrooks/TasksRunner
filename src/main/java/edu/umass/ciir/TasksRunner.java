package edu.umass.ciir;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.*;
import java.util.Map;
import java.util.stream.Stream;

public class TasksRunner {

    private AnalyticTasks tasks;
    private String mode;
    private String phase;
    private static final Logger logger = Logger.getLogger("TasksRunner");
    EventExtractor eventExtractor;

/* Here is an example of a tasks.json file:
{
  "test-id": "Evaluation",
  "task-set": {
    "extract-basic-events": {"perform?": true},
    "find-relevant-docs.automatic": {
      "perform?": true,
      "corpus-location": "/corpus",
      "scratch-storage": "/scratch"
    },
    "find-relevant-docs.auto-hitl": {
      "perform?": true,
      "corpus-location": "/corpus",
      "scratch-storage": "/scratch"
    },
    "find-relevant-docs.hitl": {
      "perform?": true,
      "corpus-location": "/corpus",
      "scratch-storage": "/scratch"
    }
}
*/
    /**
     * Reads the tasks.json file, which tells us which function to perform.
     * @param taskSetFile The full pathname of the tasks.json file.
     */
    private void readTaskSetFile(String taskSetFile) {
        try {
            Reader reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(taskSetFile)));
            JSONParser parser = new JSONParser();
            JSONObject topLevelJSON = (JSONObject) parser.parse(reader);
            String testIDJSON = (String) topLevelJSON.get("test-id");
            JSONObject taskSetJSON = (JSONObject) topLevelJSON.get("task-set");

            File f = null;
            if (Pathnames.skipPretrain) {
                Pathnames.runPreTrain = false;
            } else {
                f = new File(Pathnames.MODELS_BASE_DIR_ENGLISH);
                if (!f.exists()) {
//                    logger.info(Pathnames.MODELS_BASE_DIR_ENGLISH + " does not exist, so we will run PRE-TRAINING");
                    Pathnames.runPreTrain = true;
                } else {
                    Pathnames.runPreTrain = false;
                }
            }

            f = new File(Pathnames.targetIndexLocation);
            if (!f.exists()) {
//              logger.info("Target index " + Pathnames.targetIndexLocation + " does not exist, so we will build the target index");
                Pathnames.runIndexBuild = true;
            } else {
                Pathnames.runIndexBuild = false;
	        }

            Pathnames.runEnglishIndexBuild = false;  // no need for a Galago index on the English corpus

	        if (taskSetJSON.containsKey("extract-basic-events")) {
                JSONObject extractBasicEventsJSON = (JSONObject) taskSetJSON.get("extract-basic-events");
                Boolean perform = (Boolean) extractBasicEventsJSON.get("perform?");
                if (perform) {
                    Pathnames.runIEPhase = true;
                    Pathnames.runIRPhase1 = false;
                    Pathnames.runIRPhase2 = false;
                    Pathnames.runIRPhase3 = false;
                    return;    // EARLY EXIT FROM FUNCTION
                }
            }
            if (taskSetJSON.containsKey("find-relevant-docs.automatic")) {
                JSONObject findRelevantDocsAutomaticJSON = (JSONObject) taskSetJSON.get("find-relevant-docs.automatic");
                boolean perform = (boolean) findRelevantDocsAutomaticJSON.get("perform?");
                if (perform) {
                    String corpusLocationJSON = (String) findRelevantDocsAutomaticJSON.get("corpus-location");
                    String scratchLocationJSON = (String) findRelevantDocsAutomaticJSON.get("scratch-location");
                    Pathnames.mode = "AUTO";
                    if (Pathnames.skipPhase1) {
                        Pathnames.runIRPhase1 = false;
                    } else {
                        Pathnames.runIRPhase1 = true;
                    }
                    if (Pathnames.skipPhase2) {
                        Pathnames.runIRPhase2 = false;
                    } else {
                        Pathnames.runIRPhase2 = true;
                    }
                    Pathnames.runIRPhase3 = Pathnames.skipPhase3 ? false : true;
                    Pathnames.runIEPhase = false;
                    return;    // EARLY EXIT FROM FUNCTION
                }
            }
            if (taskSetJSON.containsKey("find-relevant-docs.auto-hitl")) {
                JSONObject findRelevantDocsAutoHitlJSON = (JSONObject) taskSetJSON.get("find-relevant-docs.auto-hitl");
                boolean perform = (boolean) findRelevantDocsAutoHitlJSON.get("perform?");
                if (perform) {
                    String corpusLocationJSON = (String) findRelevantDocsAutoHitlJSON.get("corpus-location");
                    String scratchLocationJSON = (String) findRelevantDocsAutoHitlJSON.get("scratch-location");
                    Pathnames.mode = "AUTO-HITL";
                    if (Pathnames.skipPhase1) {
                        Pathnames.runIRPhase1 = false;
                    } else {
                        Pathnames.runIRPhase1 = true;
                    }
                    if (Pathnames.skipPhase2) {
                        Pathnames.runIRPhase2 = false;
                    } else {
                        Pathnames.runIRPhase2 = true;
                    }
                    Pathnames.runIRPhase3 = Pathnames.skipPhase3 ? false : true;
                    Pathnames.runIEPhase = false;
                    return;    // EARLY EXIT FROM FUNCTION
                }
            }
	        if (taskSetJSON.containsKey("find-relevant-docs.hitl")) {
                JSONObject findRelevantDocsHitlJSON = (JSONObject) taskSetJSON.get("find-relevant-docs.hitl");
                boolean perform = (boolean) findRelevantDocsHitlJSON.get("perform?");
                if (perform) {
                    String corpusLocationJSON = (String) findRelevantDocsHitlJSON.get("corpus-location");
                    String scratchLocationJSON = (String) findRelevantDocsHitlJSON.get("scratch-location");
                    Pathnames.mode = "HITL";
                    if (Pathnames.skipPhase1) {
                        Pathnames.runIRPhase1 = false;
                    } else {
                        Pathnames.runIRPhase1 = true;
                    }
                    if (Pathnames.skipPhase2) {
                        Pathnames.runIRPhase2 = false;
                    } else {
                        Pathnames.runIRPhase2 = true;
                    }
                    Pathnames.runIRPhase3 = Pathnames.skipPhase3 ? false : true;
                    Pathnames.runIEPhase = false;
                    return;    // EARLY EXIT FROM FUNCTION
                }
            }
        } catch (Exception e) {
            String msg = "ERROR: Exception reading tasks.json file " + taskSetFile;
            System.out.println(msg);
            throw new TasksRunnerException(msg);

        }
        String msg = "ERROR: tasks.json says to perform NOTHING!";
        System.out.println(msg);
        throw new TasksRunnerException(msg);
    }

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

    private void twoStepProcessingModel() {
        /* These are used in the file names */
        String requestLevelFormulator = Pathnames.requestLevelQueryFormulatorDockerImage;
        String taskLevelFormulator = Pathnames.taskLevelQueryFormulatorDockerImage;
        phase = "Task";

        QueryManager qf = new QueryManager(tasks, taskLevelFormulator, phase);
        qf.resetQueries();  // Clears any existing queries read in from an old file

        logger.info("PHASE 2: Building task-level queries");
        QueryFormulator queryFormulator = NewQueryFormulatorFactory(tasks);
        queryFormulator.buildQueries(phase, qf.getKey());

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
        phase = "Request";
        qf = new QueryManager(tasks, requestLevelFormulator, phase);
        qf.resetQueries();  // Clears any existing queries read in from an old file

        QueryFormulator requestQueryFormulator = NewQueryFormulatorFactory(tasks);
//        requestQueryFormulator.buildQueries(phase, qf.getQueryFileNameOnly());
        String key = qf.getKey();
        requestQueryFormulator.buildQueries(phase, key);

        String queryFileDirectory = Pathnames.queryFileLocation;

        DirectoryStream.Filter<Path> filter = file -> (file.toString().startsWith(queryFileDirectory + key)
                && (!file.toString().startsWith(queryFileDirectory + key + ".TASK."))
                && (!file.toString().contains(".PRETTY."))
                && (!file.toString().contains(".NON_TRANSLATED.")));

        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(
                Paths.get(Pathnames.queryFileLocation),
                filter)) {
            dirStream.forEach(path -> executeOne(path, key, queryFileDirectory, requestLevelFormulator,
                    taskLevelFormulator));
        } catch (IOException cause) {
            throw new TasksRunnerException(cause);
        }
        logger.info("PHASE 2: Execution of all request queries complete.");
    }

    private void executeOne(Path path, String key, String queryFileDirectory, String requestLevelFormulator,
                            String taskLevelFormulator) {
        String pathname = path.toString();
        logger.info("PHASE 2: Found a query file produced by the query formulator: " + path);
        pathname = pathname.replace(queryFileDirectory + key, "");
        String extra = pathname.replace(".queries.json", "");
        String newQueryFormulationName = requestLevelFormulator + extra;
        logger.info("  Effective query formulation name is: " + newQueryFormulationName);

        QueryManager qf = new QueryManager(tasks, newQueryFormulationName, phase);
        logger.info("PHASE 2: Writing separate query files for the requests in each task");
        qf.writeQueryFiles();

        logger.info("PHASE 2: Executing the request queries, using the task-level indexes");
        qf.executeRequestQueries(taskLevelFormulator);

        logger.info("PHASE 2: Execution of request queries complete. Run time: " + qf.getRunTime());

        // Evaluate the request-level results (they are saved into a file as a side effect)
        if (Pathnames.doRequestLevelEvaluation) {
            Map<String, Double> rstats = qf.evaluate("RAW");
            logger.info("PHASE 2: Evaluation of request-level hits complete");
        }

        /* Extract events from the request-level hits, to use when re-ranking the request-level results */
        logger.info("Extracting events from the top request-level hits");

        eventExtractor.createInputForEventExtractorFromRequestHits(qf);

        // Create the input file for my Galago reranker project:
        //eventExtractor.createInputForRerankerFromRequestHits(qf);
    }

    private void oneStepExecuteOne(Path path, String key, String queryFileDirectory, String requestLevelFormulator) {
        String pathname = path.toString();
        logger.info("PHASE 2: Found a query file produced by the query formulator: " + path);
        pathname = pathname.replace(queryFileDirectory + key, "");
        String extra = pathname.replace(".queries.json", "");
        String newQueryFormulationName = requestLevelFormulator + extra;
        logger.info("  Effective query formulation name is: " + newQueryFormulationName);

        QueryManager qf = new QueryManager(tasks, newQueryFormulationName, phase);
        qf.readQueryFile();
        qf.execute(1000);
        if (Pathnames.doRequestLevelEvaluation) {
            Map<String, Double> rstats = qf.evaluate("RAW");
            logger.info(newQueryFormulationName + " TOTAL nDCG: " + String.format("%.4f", rstats.get("TOTAL")));
        }
        /* Extract events from the request-level hits, to use when re-ranking the request-level results */
        // To save time, temporarily skipping this:
        // logger.info("Extracting events from the top request-level hits");
        eventExtractor.createInputForEventExtractorFromRequestHits(qf);

        // Create the input file for my Galago reranker project:
        // eventExtractor.createInputForRerankerFromRequestHits(qf);
    }


    private void neuralProcessingModel() {
        logger.info("PHASE 2: NEURAL PROCESSING MODE: Building and executing queries");
        NeuralQueryProcessorDocker docker = new NeuralQueryProcessorDocker(tasks);
        docker.callDockerImage();

    }

    private void oneStepProcessingModel() {
        /* These are used in the file names */
        String requestLevelFormulator = Pathnames.requestLevelQueryFormulatorDockerImage;
        phase = "Request";

        QueryManager qf = new QueryManager(tasks, requestLevelFormulator, phase);
        qf.resetQueries();  // Clears any existing queries read in from an old file

        logger.info("PHASE 2: Building queries");
        QueryFormulator queryFormulator = NewQueryFormulatorFactory(tasks);
        String key = qf.getKey();
        queryFormulator.buildQueries(phase, key);

        String queryFileDirectory = Pathnames.queryFileLocation;
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(
                Paths.get(Pathnames.queryFileLocation),
                qf.getKey() + "*.queries.json")) {
            dirStream.forEach(path -> files.add(path)
            );
        } catch (IOException cause) {
            throw new TasksRunnerException(cause);
        }
        files.parallelStream().forEach(path -> oneStepExecuteOne(path, key, queryFileDirectory, requestLevelFormulator));
    }

    /**
     * Processes the analytic tasks file: generates queries for the Tasks and Requests,
     * executes the queries, annotates hits with events.
     */
    void process()  {

        logger.info("skipPretrain: " + Pathnames.skipPretrain);
        logger.info("skipPhase1: " + Pathnames.skipPhase1);
        logger.info("skipIndexBuild: " + Pathnames.skipIndexBuild);
        logger.info("skipRequestDocAnnotation: " + Pathnames.skipRequestDocAnnotation);

        logger.info("Opening the analytic task file, expanding example docs");
        tasks = new AnalyticTasks();

        /*
         * We can be executing in one of 3 modes: AUTO, AUTO-HITL, or HITL.
         * Processing is a little different in each mode, but the differences are hidden inside
         * the query formulators. The caller passes in the mode through the env file.
         * The input analytic tasks file should be different for AUTO vs the other 2 modes:
         * the AUTO file should not have anything for the fields like Task Narrative and
         * Request Text. Those
         *  are only provided in AUTO-HITL and HITL mode.
         * HITL mode's only difference is that more sample documents are passed in, in the
         * supplemental_info.json file.
         * Once we know the mode, we select the 2 query formulators created for that mode.
         * (There are separate query formulators for the creation of Task-level queries and
         * Request-level queries.)
         */
        mode = tasks.getMode();
        logger.info("Executing in " + mode + " mode");

        eventExtractor = new EventExtractor(tasks, mode);

        if (Pathnames.runIEPhase) {
            logger.info("Calling event annotator for test_data.bp.json file");
            eventExtractor.annotateProvidedFileEvents();
        } else {
            logger.info("Skipping IE on provided file operation");
        }

        if (Pathnames.runEnglishIndexBuild) {
            Index index = new Index("english");
            index.preprocess();
            index.buildIndex();
        } else {
            logger.info("Skipping English index building");
        }

        if (Pathnames.runIndexBuild) {
            Index index = new Index("target");
            index.preprocess();
            index.buildIndex();
        } else {
            logger.info("Skipping target language index building");
        }

        if (!Pathnames.runPreTrain) {
            logger.info("Skipping event annotator pre-training");
        } else {
            logger.info("PRE-TRAINING: Pre-training the event annotator");
            eventExtractor.preTrainEventAnnotator();
            logger.info("PRE-TRAINING COMPLETE");
        }

        if (!Pathnames.runIRPhase1) {
            logger.info("Skipping phase 1");
        } else {
            logger.info("PHASE 1: Preparing a file of the example docs for the event annotator");
            eventExtractor.extractExampleEventsPart1();
            eventExtractor.annotateExampleDocEvents();
            logger.info("PHASE 1 COMPLETE");
    	}

        if (!Pathnames.runIRPhase2) {
            logger.info("Skipping phase 2");
        } else {
            logger.info("PHASE 2: Retrieving the file of example doc events created by the event annotator");
            eventExtractor.extractExampleEventsPart2();

            /* write the analytic tasks info file, with event info, for multipartite
            and re-ranking to use */
            tasks.writeJSONVersion();

            if (Pathnames.processingModel == Pathnames.ProcessingModel.TWO_STEP) {
                twoStepProcessingModel();
            } else if (Pathnames.processingModel == Pathnames.ProcessingModel.ONE_STEP) {
                oneStepProcessingModel();
            } else if (Pathnames.processingModel == Pathnames.ProcessingModel.NEURAL) {
                neuralProcessingModel();
            } else {
                throw new TasksRunnerException("INVALID PROCESSING MODEL");
            }

            if (!Pathnames.skipRequestDocAnnotation) {
                logger.info("PHASE 2: Calling the ISI event annotators for the request hits");
                eventExtractor.annotateRequestDocEvents();
            } else {
                logger.info("PHASE 2: SKIPPING the ISI event annotators for the request hits");
            }

            logger.info("PHASE 2 COMPLETE");
        } 

	    if (!Pathnames.runIRPhase3) {
            logger.info("Skipping phase 3");
	    } else {
	        phase = "Request";
            String requestLevelFormulator = Pathnames.requestLevelQueryFormulatorDockerImage;
            QueryManager qf = new QueryManager(tasks, requestLevelFormulator, phase);
    
            logger.info("PHASE 3: Building file with doc text and events for request hits, for reranker");
            eventExtractor.retrieveEventsFromRequestHits(qf);

            //logger.info("PHASE 3: Building file with doc text and events for task hits, for experiments");
            //eventExtractor.retrieveEventsFromTaskHits(qf);

            logger.info("PHASE 3: Reranking");
            qf.rerank();
    
            //NOT NEEDED eventExtractor.writeFileForHITL(qf);  // writes top 10 hits for HITL to judge
    
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

    public static QueryFormulator NewQueryFormulatorFactory(AnalyticTasks tasks) {
        QueryFormulator qf = new QueryFormulatorDocker(tasks);
        return qf;
    }

    /**
     * Public entry point for this class.
     */
    public static void main (String[] args) {
/*
        tasksrunner@d5435cba053a:~$ id

                uid=1000(tasksrunner) gid=1000(tasksrunner) groups=1000(tasksrunner),27(sudo)
*/
        if (Pathnames.checkForSudo) {
            boolean hasSudo = false;
            try {
                Process p = Runtime.getRuntime().exec("id");
                BufferedReader sin = new BufferedReader(
                        new InputStreamReader(p.getInputStream()));
                String line;
                while ((line = sin.readLine()) != null) {
                    if (line.contains("sudo")) {
                        hasSudo = true;
                        break;
                    }
                }
            } catch (Exception cause) {
                throw new TasksRunnerException(cause);
            }
            if (!hasSudo) {
                throw new TasksRunnerException("ERROR: This Docker container must be run by a user with SUDO privileges");
            }
        }

        File f = new File(Pathnames.corpusFileLocation + Pathnames.targetCorpusFileName);
        if (!f.exists()) {
            String errorMessage = "ERROR: corpus file (FARSI) " + Pathnames.corpusFileLocation
                    + Pathnames.targetCorpusFileName
                    + " does not exist! Check your environment file for the corpusFileLocation and targetCorpusFileName settings.";
            System.out.println(errorMessage);
            throw new TasksRunnerException(errorMessage);
        }
        f = new File(Pathnames.corpusFileLocation + Pathnames.englishCorpusFileName);
        if (!f.exists()) {
            String errorMessage = "ERROR: corpus file (ENGLISH) " + Pathnames.corpusFileLocation
                    + Pathnames.englishCorpusFileName
                    + " does not exist! Check your environment file for the corpusFileLocation and englishCorpusFileName settings.";
            System.out.println(errorMessage);
            throw new TasksRunnerException(errorMessage);
        }

        TasksRunner betterIR = new TasksRunner();
        if (Pathnames.useTaskSetFile) {
            /* This must be done before setupLogging() because that func uses mode,
            which is set in readTaskSetFile()
             */
            betterIR.readTaskSetFile(Pathnames.appFileLocation + "tasks.json");
        }
        betterIR.setupLogging();

        betterIR.process();
    }
}
