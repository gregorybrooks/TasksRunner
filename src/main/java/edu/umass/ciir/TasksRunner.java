package edu.umass.ciir;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.springframework.boot.SpringApplication;

import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.*;
import java.util.Map;

public class TasksRunner {

    private AnalyticTasks tasks;
    private String mode;
    private String phase;
    private static final Logger logger = Logger.getLogger("TasksRunner");
    EventExtractor eventExtractor;

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
        String logFileName = Pathnames.logFileLocation + "HITL/tasks-runner.log";
        configureLogger(logFileName);
    }

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
    },
    "find-candidate-docs.hitl": {
      "perform?": true,
      "corpus-location": "/corpus",
      "scratch-storage": "/scratch"
    }

}
*/
    /**
     * Reads the tasks.json file, which tells us which function(s) to perform.
     * @param taskSetFile The full pathname of the tasks.json file.
     */
    public void readTaskSetFile(String taskSetFile) {
        try {
            Reader reader = new BufferedReader(new InputStreamReader(new FileInputStream(taskSetFile)));
            JSONParser parser = new JSONParser();
            JSONObject topLevelJSON = (JSONObject) parser.parse(reader);
            String testIDJSON = (String) topLevelJSON.get("test-id");
            JSONObject taskSetJSON = (JSONObject) topLevelJSON.get("task-set");

            Pathnames.runPreTrain = false;
            if (Pathnames.skipPretrain) {
                logger.info("Skipping IE pre-training");
            } else {
                File f = new File(Pathnames.MODELS_BASE_DIR_ENGLISH);
                if (!f.exists()) {
                    logger.info(Pathnames.MODELS_BASE_DIR_ENGLISH + " does not exist, so we will run IE pre-training");
                    Pathnames.runPreTrain = true;
                } else {
                    logger.info(Pathnames.MODELS_BASE_DIR_ENGLISH + " already exists, so we will NOT run IE pre-training");
                }
            }

            Pathnames.runIndexBuild = false;
            if (Pathnames.skipIndexBuild) {
                logger.info("Skipping target index build");
            } else {
                File f = new File(Pathnames.targetIndexLocation);
                if (!f.exists()) {
                    logger.info("Target index " + Pathnames.targetIndexLocation + " does not exist, so we will build the target index");
                    Pathnames.runIndexBuild = true;
                } else {
                    logger.info("Target index " + Pathnames.targetIndexLocation + " already exists, so we will NOT build the target index");
                }
            }

            Pathnames.runEnglishIndexBuild = false;
            File f = new File(Pathnames.englishIndexLocation);
            if (!f.exists()) {
                logger.info("English index " + Pathnames.englishIndexLocation + " does not exist, so we will build the English index");
                Pathnames.runEnglishIndexBuild = true;
            } else {
                logger.info("English index " + Pathnames.englishIndexLocation + " already exists, so we will NOT build the English index");
            }

            /* Now the following are mutually exclusive operations */
            Pathnames.runSearch = false;
            Pathnames.runGetIEFromFile = false;
            Pathnames.runGetPhrases = false;
            Pathnames.runGetCandidateDocs = false;

            while (true) {
                if (taskSetJSON.containsKey("extract-basic-events")) {
                    JSONObject extractBasicEventsJSON = (JSONObject) taskSetJSON.get("extract-basic-events");
                    Boolean perform = (Boolean) extractBasicEventsJSON.get("perform?");
                    if (perform) {
                        logger.info("extract-basic-events perform true");
                        Pathnames.runGetIEFromFile = true;
                        break;
                    }
                }
                if (taskSetJSON.containsKey("find-relevant-docs.automatic")) {
                    JSONObject findRelevantDocsAutomaticJSON = (JSONObject) taskSetJSON.get("find-relevant-docs.automatic");
                    boolean perform = (boolean) findRelevantDocsAutomaticJSON.get("perform?");
                    if (perform) {
                        Pathnames.mode = "AUTO";
                        Pathnames.runSearch = true;
                        break;
                    }
                }
                if (taskSetJSON.containsKey("find-relevant-docs.auto-hitl")) {
                    JSONObject findRelevantDocsAutoHitlJSON = (JSONObject) taskSetJSON.get("find-relevant-docs.auto-hitl");
                    boolean perform = (boolean) findRelevantDocsAutoHitlJSON.get("perform?");
                    if (perform) {
                        Pathnames.mode = "AUTO-HITL";
                        Pathnames.runSearch = true;
                        break;
                    }
                }
                if (taskSetJSON.containsKey("find-relevant-docs.hitl")) {
                    JSONObject findRelevantDocsHitlJSON = (JSONObject) taskSetJSON.get("find-relevant-docs.hitl");
                    boolean perform = (boolean) findRelevantDocsHitlJSON.get("perform?");
                    if (perform) {
                        Pathnames.mode = "HITL";
                        Pathnames.runSearch = true;
                        break;
                    }
                }
                if (taskSetJSON.containsKey("find-candidate-docs.hitl")) {
                    JSONObject findTaskExampleDocsHitlJSON = (JSONObject) taskSetJSON.get("find-candidate-docs.hitl");
                    boolean perform = (boolean) findTaskExampleDocsHitlJSON.get("perform?");
                    if (perform) {
                        Pathnames.mode = "HITL";
                        Pathnames.runSearch = true;
                        Pathnames.runGetCandidateDocs = true;
                        Pathnames.isTargetEnglish = "true";
                        Pathnames.targetLanguageIsEnglish = true;
                        Pathnames.targetLanguage = Pathnames.Language.ENGLISH;
                        Pathnames.processingModel = Pathnames.ProcessingModel.ONE_STEP;
                        // override the Request-level Docker image name with special one for getting candidate docs
                        Pathnames.requestLevelQueryFormulatorDockerImage = Pathnames.getCandidateDocsQueryFormulatorDockerImage;
                        break;
                    }
                }
                if (taskSetJSON.containsKey("get-phrases.hitl")) {
                    JSONObject findTaskExampleDocsHitlJSON = (JSONObject) taskSetJSON.get("get-phrases.hitl");
                    boolean perform = (boolean) findTaskExampleDocsHitlJSON.get("perform?");
                    if (perform) {
                        Pathnames.mode = "HITL";
                        Pathnames.runSearch = true;
                        Pathnames.runGetPhrases = true;
                        Pathnames.processingModel = Pathnames.ProcessingModel.ONE_STEP;
                        // override the Request-level Docker image name with special one for getting phrases
                        Pathnames.requestLevelQueryFormulatorDockerImage = Pathnames.getPhrasesQueryFormulatorDockerImage;
                        break;
                    }
                }
                if (!(Pathnames.runGetPhrases || Pathnames.runSearch || Pathnames.runGetIEFromFile || Pathnames.runGetCandidateDocs)) {
                    logger.info("No operation to do in tasks.json");
                    throw new TasksRunnerException("No operation specified in tasks.json");
                }
            }
        } catch (Exception e) {
            String msg = "ERROR: Exception reading tasks.json file " + taskSetFile;
            System.out.println(msg);
            throw new TasksRunnerException(e);
        }
    }

    private void twoStepProcessingModel() {
        /* These are used in the file names */
        String requestLevelFormulator = Pathnames.requestLevelQueryFormulatorDockerImage;
        String taskLevelFormulator = Pathnames.taskLevelQueryFormulatorDockerImage;
        phase = "Task";

        QueryManager qf = new QueryManager(tasks, taskLevelFormulator, phase, eventExtractor);
        qf.resetQueries();  // Clears any existing queries read in from an old file

        if (!Pathnames.skipExampleDocAnnotation) {
            qf.annotateExampleDocs();
        }

        /* Write out the internal analytic tasks info file, with doc text and event info, for query formulators
           and re-rankers to use */
        tasks.writeJSONVersion();

        logger.info("Building task-level queries");
        qf.buildQueries(taskLevelFormulator);
        qf.readQueryFile();

        logger.info("Executing the task-level queries");
        qf.execute(2500);
        logger.info("Execution of task-level queries complete.");

        /* Create an input file for the event extractor for the top N task-level scoredHits.
        (We did this to experiment with task-level scoredHits, but it is not needed normally.)
        logger.info("Extracting events from the top task-level scoredHits");
        qf.createInputForEventExtractorFromTaskHits();
        */

        // Evaluate the task-level results (they are saved into a file as a side effect)
        if (Pathnames.doTaskLevelEvaluation) {
            logger.info("Evaluating the task-level results");
            Map<String, QueryManager.EvaluationStats> tstats = qf.evaluateTaskLevel();
        }

        logger.info("Building a separate index for each task's top scoredHits");
        qf.buildTaskLevelIndexes();

        logger.info("Building request-level queries");
        phase = "Request";
        qf = new QueryManager(tasks, requestLevelFormulator, phase, eventExtractor);
        qf.resetQueries();  // Clears any existing queries read in from an old file
        qf.buildQueries(requestLevelFormulator);

        logger.info("Executing request-level queries");
        String queryFileDirectory = Pathnames.queryFileLocation;
        String key = qf.getKey();
        // The key helps us distinguish files produced by this search from those produced by other searches
        // (e.g. HITL.Request.gregorybrooks-better-query-builder-1:3.1.0)

        // We want to process all query files produced by the Request-level query formulator.
        // Make a filter to filter out all but the query files for this Request:
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

        /* Extract events from the request-level scoredHits, to use when re-ranking the request-level results */
        logger.info("Extracting events from the top request-level scoredHits");
        eventExtractor.annotateRequestDocEvents();
        qf.retrieveEventsFromRequestHits();

        if (!Pathnames.skipReranker) {
            logger.info("Reranking");
            qf.rerank();
            // Evaluate the request-level results (they are saved into a file as a side effect)
            if (Pathnames.doRequestLevelEvaluation) {
                logger.info("Evaluating request-level re-ranked hits");
                Map<String, Double> rstats = qf.evaluate("RERANKED");
            }
            logger.info("Merging IR and IE results");
            qf.writeFinalResultsFile();  // Combined IR (reranked) and IE results
        }
    }

    private void getPhrases() {
        /* These are used in the file names */
        String taskLevelFormulator = Pathnames.getPhrasesQueryFormulatorDockerImage;
        phase = "Task";

        QueryManager qf = new QueryManager(tasks, taskLevelFormulator, phase, eventExtractor);

        if (!Pathnames.skipExampleDocAnnotation) {
            qf.annotateExampleDocs();
        }

        /* Write out the internal analytic tasks info file, with doc text and event info, for query formulators
           and re-rankers to use */
        tasks.writeJSONVersion();

        logger.info("Building Task-level phrases-to-be-annotated files");
        qf.buildQueries(Pathnames.getPhrasesQueryFormulatorDockerImage);
    }

    private String getFormulationName(Path path, String key, String queryFileDirectory, String requestLevelFormulator) {
        String pathname = path.toString();
        pathname = pathname.replace(queryFileDirectory + key, "");
        String extra = pathname.replace(".queries.json", "");
        String newQueryFormulationName = requestLevelFormulator + extra;
        return newQueryFormulationName;
    }

    private void executeOne(Path path, String key, String queryFileDirectory, String requestLevelFormulator,
                            String taskLevelFormulator) {
        logger.info("Found a query file produced by the query formulator: " + path);
        String newQueryFormulationName = getFormulationName(path, key, queryFileDirectory, requestLevelFormulator);
        logger.info("  Effective query formulation name is: " + newQueryFormulationName);

        QueryManager qf = new QueryManager(tasks, newQueryFormulationName, phase, eventExtractor);
        logger.info("Writing separate query files, one per Task so we can execute them against the Task indexes");
        qf.writeQueryFiles();

        logger.info("Executing the Request queries, using the Task-level indexes");
        qf.executeRequestQueries(taskLevelFormulator, Pathnames.RESULTS_CAP);

        logger.info("Execution of Request queries complete.");

        // Evaluate the request-level results (the evaluation results are saved into a file as a side effect)
        if (Pathnames.doRequestLevelEvaluation) {
            logger.info("Evaluating Request-level hits");
            Map<String, Double> rstats = qf.evaluate("RAW");  // before reranking is called "RAW"
        }

        qf.createInputFileForEventExtractorFromRequestHits();

        // Create the input file for my Galago reranker project:
        //eventExtractor.createInputForRerankerFromRequestHits(qf);
    }

    private void oneStepExecuteOne(Path path, String key, String queryFileDirectory, String requestLevelFormulator) {
        logger.info("Found a query file produced by the query formulator: " + path);
        String newQueryFormulationName = getFormulationName(path, key, queryFileDirectory, requestLevelFormulator);
        logger.info("  Effective query formulation name is: " + newQueryFormulationName);

        QueryManager qf = new QueryManager(tasks, newQueryFormulationName, phase, eventExtractor);
        if (Pathnames.runGetCandidateDocs) {
            qf.execute(10);
        } else {
            qf.execute(Pathnames.RESULTS_CAP);
        }
        if (Pathnames.doRequestLevelEvaluation) {
            Map<String, Double> rstats = qf.evaluate("RAW");
            logger.info(newQueryFormulationName + " TOTAL nDCG: " + String.format("%.4f", rstats.get("TOTAL")));
        }
        /* Extract events from the request-level scoredHits, to use when re-ranking the request-level results */
        // logger.info("Extracting events from the top request-level scoredHits");
        qf.createInputFileForEventExtractorFromRequestHits();
    }


    private void neuralProcessingModel() {
        logger.info("NEURAL PROCESSING MODE: Building and executing queries");
        NeuralQueryProcessorDocker docker = new NeuralQueryProcessorDocker(tasks);
        docker.callDockerImage();
    }

    public List<SearchHit> getSearchHits() {
        return eventExtractor.getSearchHits();
    }

    private void oneStepProcessingModel() {
        phase = "Request";  // used in the file names
        String requestLevelFormulator = Pathnames.runGetPhrases ? Pathnames.getPhrasesQueryFormulatorDockerImage :
             Pathnames.requestLevelQueryFormulatorDockerImage;

        QueryManager qf = new QueryManager(tasks, requestLevelFormulator, phase, eventExtractor);
        qf.resetQueries();  // Clears any existing queries read in from an old file

        if (!Pathnames.skipExampleDocAnnotation) {
            qf.annotateExampleDocs();
        }

        /* Write out the internal analytic tasks info file, with doc text and event info, for query formulators
           and re-rankers to use */
        tasks.writeJSONVersion();

        logger.info("Building queries");
        qf.buildQueries(Pathnames.requestLevelQueryFormulatorDockerImage);

        logger.info("Executing queries");
        String queryFileDirectory = Pathnames.queryFileLocation;
        DirectoryStream.Filter<Path> filter = file -> (file.toString().startsWith(queryFileDirectory + qf.getKey())
                && (!file.toString().startsWith(queryFileDirectory + qf.getKey() + ".TASK."))
                && (!file.toString().contains(".PRETTY."))
                && (!file.toString().contains(".NON_TRANSLATED.")));

        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(
                Paths.get(Pathnames.queryFileLocation),
                filter)) {
            dirStream.forEach(path -> oneStepExecuteOne(path, qf.getKey(), queryFileDirectory, requestLevelFormulator));
        } catch (IOException cause) {
            throw new TasksRunnerException(cause);
        }

        /* Extract events from the request-level scoredHits, to use when re-ranking the request-level results */
        logger.info("Extracting events from the top Request-level hits");
        eventExtractor.annotateRequestDocEvents();
        qf.retrieveEventsFromRequestHits();

        if (!Pathnames.skipReranker) {
            logger.info("Reranking");
            qf.rerank();
            // Evaluate the (reranked) request-level results (they are saved into a file as a side effect)
            if (Pathnames.doRequestLevelEvaluation) {
                logger.info("Evaluating the Request-level hits");
                Map<String, Double> rstats = qf.evaluate("RERANKED");
            }
            logger.info("Merging IR and IE results");
            qf.writeFinalResultsFile();  // Combined IR and IE results
        }
    }

    /**
     * Processes the analytic tasks file: generates queries for the Tasks and Requests,
     * executes the queries, annotates scoredHits with events.
     */
    void process()  {

        if (Pathnames.runEnglishIndexBuild) {
            Index index = new Index("english");
            index.preprocess();
            index.buildIndex();
        }

        if (Pathnames.runIndexBuild) {
            Index index = new Index("target");
            index.preprocess();
            index.buildIndex();
        }

        logger.info("Opening the analytic task file, expanding example docs");
        tasks = new AnalyticTasks();    // this is the external analytic tasks file

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

        if (Pathnames.runPreTrain) {
            eventExtractor.preTrainEventAnnotator();
        }

        if (Pathnames.runGetIEFromFile) {
            eventExtractor.annotateProvidedFileEvents();
        } else if (Pathnames.runGetPhrases) {
            getPhrases();
        } else if (Pathnames.runSearch) {
            if (Pathnames.processingModel == Pathnames.ProcessingModel.TWO_STEP) {
                twoStepProcessingModel();
            } else if (Pathnames.processingModel == Pathnames.ProcessingModel.ONE_STEP) {
                oneStepProcessingModel();
            } else if (Pathnames.processingModel == Pathnames.ProcessingModel.NEURAL) {
                neuralProcessingModel();
            } else {
                throw new TasksRunnerException("INVALID PROCESSING MODEL");
            }
        }
        logger.info("PROCESSING COMPLETE");
    }

    public void mainProcess() {
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
