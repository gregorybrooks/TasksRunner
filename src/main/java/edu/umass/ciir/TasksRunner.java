package edu.umass.ciir;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.*;

public class TasksRunner {

    private static final Logger logger = Logger.getLogger("TasksRunner");
    EventExtractor eventExtractor;
    private AnalyticTasks tasks;
    private String mode;
    private String submissionId;

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
        String logFileName = Pathnames.logFileLocation + "/tasks-runner." + submissionId + ".log";
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
            mode = "AUTO";  // the default, even for non-search operations

            Reader reader = new BufferedReader(new InputStreamReader(new FileInputStream(taskSetFile)));
            JSONParser parser = new JSONParser();
            JSONObject topLevelJSON = (JSONObject) parser.parse(reader);
            submissionId = (String) topLevelJSON.get("test-id");
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
                if ((new Index("target")).getTargetLanguages().size() == 0) {
                    logger.info("Target indexes do not exist, so we will build them");
                    Pathnames.runIndexBuild = true;
                } else {
                    logger.info("Target indexes already exist, so we will NOT build them");
                }
            }

            Pathnames.runEnglishIndexBuild = false;
            if (!(new Index("target")).englishIndexExists()) {
                logger.info("English index does not exist, so we will build the English index");
                Pathnames.runEnglishIndexBuild = true;
            } else {
                logger.info("English index already exists, so we will NOT build the English index");
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
                        mode = "AUTO";
                        Pathnames.runSearch = true;
                        break;
                    }
                }
                if (taskSetJSON.containsKey("find-relevant-docs.auto-hitl")) {
                    JSONObject findRelevantDocsAutoHitlJSON = (JSONObject) taskSetJSON.get("find-relevant-docs.auto-hitl");
                    boolean perform = (boolean) findRelevantDocsAutoHitlJSON.get("perform?");
                    if (perform) {
                        mode = "AUTO-HITL";
                        Pathnames.runSearch = true;
                        break;
                    }
                }
                if (taskSetJSON.containsKey("find-relevant-docs.hitl")) {
                    JSONObject findRelevantDocsHitlJSON = (JSONObject) taskSetJSON.get("find-relevant-docs.hitl");
                    boolean perform = (boolean) findRelevantDocsHitlJSON.get("perform?");
                    if (perform) {
                        mode = "HITL";
                        Pathnames.runSearch = true;
                        break;
                    }
                }
                if (taskSetJSON.containsKey("find-candidate-docs.hitl")) {
                    JSONObject findTaskExampleDocsHitlJSON = (JSONObject) taskSetJSON.get("find-candidate-docs.hitl");
                    boolean perform = (boolean) findTaskExampleDocsHitlJSON.get("perform?");
                    if (perform) {
                        mode = "HITL";
                        Pathnames.runGetCandidateDocs = true;
                        //Pathnames.isTargetEnglish = "true";
                        //Pathnames.targetLanguageIsEnglish = true;
                        //Pathnames.targetLanguage = Pathnames.Language.ENGLISH;
                        break;
                    }
                }
                if (taskSetJSON.containsKey("get-phrases.hitl")) {
                    JSONObject findTaskExampleDocsHitlJSON = (JSONObject) taskSetJSON.get("get-phrases.hitl");
                    boolean perform = (boolean) findTaskExampleDocsHitlJSON.get("perform?");
                    if (perform) {
                        mode = "HITL";
                        Pathnames.runGetPhrases = true;
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

    private void updateSentenceIDs (Hit d) {
        List<Event> events = d.events;
        for (Event event : events) {
            event.sentenceID = findSentence(event.anchorSpan.start, Document.getDocumentSentences(d.docid));
        }
    }

    private int findSentence(long start, List<SentenceRange> sentences) {
        for (SentenceRange sentence : sentences) {
            if (start >= sentence.start && start <= sentence.end) {
                return sentence.id;
            }
        }
        return -1;
    }

    private void updateTaskOrRequest(Hit hit) {
//        logger.info("In updateTaskOrRequest, hit.taskID is " + hit.taskID + ", length is " + hit.taskID.length());
        String groupType = hit.hitLevel == HitLevel.REQUEST_LEVEL ? "R" : "T";
        if (groupType.equals("T")) {
            String taskID = hit.taskID;
            String docid = hit.docid;
            Task t = tasks.findTask(taskID);
            if (t != null) {
                for (ExampleDocument d : t.taskExampleDocs) {
                    if (d.getDocid().equals(docid)) {
                        d.setEvents(hit.events);
                    }
                }
            }
        } else {
            String requestID = hit.taskID;
            String docid = hit.docid;
            Request r = tasks.findRequest(requestID);
            if (r != null) {
                for (ExampleDocument d : r.reqExampleDocs) {
                    if (d.getDocid().equals(docid)) {
                        d.setEvents(hit.events);
                    }
                }
            }
        }
    }


    public void annotateExampleDocs() {
        logger.info("Preparing a file of the example docs for the event annotator");

        String fileForEventExtractor = eventExtractor.constructExampleFileToEventExtractorFileName();
        Map<String, SimpleHit> entries = new HashMap<>();

        for (Task task : tasks.getTaskList()) {
            eventExtractor.createInputFileEntriesFromExampleDocs(task, entries);
        }

        eventExtractor.writeInputFileMitreFormat(entries, fileForEventExtractor);

        if (Pathnames.skipExampleDocAnnotation) {
            eventExtractor.copyExampleDocEventFileToResultsFile();  // just copy the file to the expected name
        } else {
            eventExtractor.annotateExampleDocEvents();
        }

        logger.info("Retrieving the file of example doc events created by the event annotator");

        String fileFromEventExtractor = eventExtractor.constructExampleFileFromEventExtractorFileName();

        List<Hit> hits = eventExtractor.readEventFile(fileFromEventExtractor, -1);

        for (Hit hit : hits) {
            updateSentenceIDs(hit);
            updateTaskOrRequest(hit);
        }

    }

    private void neuralProcessingModel() {
        logger.info("NEURAL PROCESSING MODE: Building and executing queries");
        NeuralQueryProcessorDocker docker = new NeuralQueryProcessorDocker(submissionId, mode, tasks);
        docker.callDockerImage();
    }

    public List<SearchHit> getSearchHits() {
        return eventExtractor.getSearchHits();
    }

    private void getPhrases() {
        annotateExampleDocs();
        /* Write out the internal analytic tasks info file, with doc text and event info, for query formulators
           and re-rankers to use */
        tasks.writeJSONVersion();

        logger.info("Building Task-level phrases-to-be-annotated files");
        QueryManager qf = new QueryManager(submissionId, "ENGLISH", mode, tasks,
                 "Task", eventExtractor);
        qf.buildQueries(Pathnames.getPhrasesQueryFormulatorDockerImage);
    }

    private Pathnames.ProcessingModel getProcessingModel() {
        Pathnames.ProcessingModel processingModel = Pathnames.processingModel;
        if (Pathnames.runGetCandidateDocs) {  // force one-step model
            processingModel = Pathnames.ProcessingModel.ONE_STEP;
        }
        return processingModel;
    }

    private void executeQueryFile(Path path, String language) {
        logger.info("Found a query file produced by the query formulator: " + path);

        QueryManager qf = new QueryManager(submissionId, language, mode, tasks, "Request", eventExtractor);

        if (getProcessingModel() == Pathnames.ProcessingModel.TWO_STEP) {
            logger.info("Writing separate query files, one per Task so we can execute them against the Task indexes");
            qf.writeQueryFiles();
            logger.info("Executing the Request queries, using the Task-level indexes");
            qf.executeRequestQueries(Pathnames.RESULTS_CAP);
        } else {
            if (Pathnames.runGetCandidateDocs) {
                qf.execute(10);
            } else {
                qf.execute(Pathnames.RESULTS_CAP);
            }
        }

        if (Pathnames.doRequestLevelEvaluation) {
            logger.info("Evaluating Request-level hits");
            Map<String, Double> rstats = qf.evaluate("RAW");  // before reranking is called "RAW"
        }

        /* Extract events from the request-level scoredHits, to use when re-ranking the request-level results */
        qf.createInputFileForEventExtractorFromRequestHits();

        // Create the input file for my Galago reranker project:
        //eventExtractor.createInputForRerankerFromRequestHits(qf);
    }

    private void doTaskLevelProcessing(String taskLevelFormulator, String language) {
        QueryManager qf = new QueryManager(submissionId, language, mode, tasks, "Task", eventExtractor);
        qf.resetQueries();  // Clears any existing queries read in from an old file

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
        if (Pathnames.doTaskLevelEvaluation) {
            logger.info("Evaluating the task-level results");
            Map<String, QueryManager.EvaluationStats> tstats = qf.evaluateTaskLevel();
        }

        logger.info("Building a separate index for each task's top scoredHits");
        qf.buildTaskLevelIndexes();
    }

    private void doSearch(String taskLevelFormulator, String requestLevelFormulator) {
        annotateExampleDocs();
        /* Write out the internal analytic tasks info file, with doc text and event info, for query formulators and re-rankers to use */
        tasks.writeJSONVersion();

        List<String> filesToMerge = new ArrayList<>();
        for (String language : (new Index("target")).getTargetLanguages()) {
            if (getProcessingModel() == Pathnames.ProcessingModel.TWO_STEP) {
                doTaskLevelProcessing(taskLevelFormulator, language);
            }
            doRequestLevelProcessing(requestLevelFormulator, language, filesToMerge);
        }
        logger.info("Merging multiple language's ranked runfiles into one");
        mergeRerankedRunFiles(filesToMerge);

        logger.info("Merging IR and IE results");
        // writeFinalResultsFile();  // adapt this from QueryManager.writeFinalResultsFile()

    }

    private void doRequestLevelProcessing(String requestLevelFormulator, String language,
                                          List<String> filesToMerge) {
        logger.info("Building request-level queries");

        QueryManager qf = new QueryManager(submissionId, language, mode, tasks, "Request", eventExtractor);
        qf.resetQueries();  // Clears any existing queries read in from an old file
        qf.buildQueries(requestLevelFormulator);

        logger.info("Executing request-level queries");

        // We want to process all query files produced by the Request-level query formulator.
        // Make a filter to filter out all but the query files for this Request:
        DirectoryStream.Filter<Path> filter = file -> (file.toString().startsWith(Pathnames.queryFileLocation + qf.getKey())
                && (!file.toString().startsWith(Pathnames.queryFileLocation + qf.getKey() + ".TASK."))
                && (!file.toString().contains(".PRETTY."))
                && (!file.toString().contains(".NON_TRANSLATED.")));

        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(
                Paths.get(Pathnames.queryFileLocation),
                filter)) {
            dirStream.forEach(path -> executeQueryFile(path, language));
        } catch (IOException cause) {
            throw new TasksRunnerException(cause);
        }

        /* Extract events from the request-level scoredHits, to use when re-ranking the request-level results */
        logger.info("Extracting events from the top Request-level hits");
        if (Pathnames.skipRequestDocAnnotation) {
            eventExtractor.copyRequestEventFilesToResultsFiles(); // just copy the files to the expected names
        } else {
            eventExtractor.annotateRequestDocEvents();
        }
        qf.retrieveEventsFromRequestHits();

        if (Pathnames.skipReranker) {
            qf.copyRunFileToRerankedRunFile();  // just do the file action that rerank() normally does
        } else {
            logger.info("Reranking");
            qf.rerank();
            // Evaluate the (reranked) request-level results (they are saved into a file as a side effect)
            if (Pathnames.doRequestLevelEvaluation) {
                logger.info("Evaluating the Request-level hits");
                Map<String, Double> rstats = qf.evaluate("RERANKED");
            }
        }
        filesToMerge.add(qf.getRerankedRunFileName());
    }

    public void mergeRerankedRunFiles(List<String> filesToMerge) {
        try {
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(Pathnames.runFileLocation + submissionId + ".FINAL.out")));
            Map<String, List<Queue<String>>> requestRankedLists = new HashMap<>();
            for (String fileName : filesToMerge) {
                BufferedReader reader = new BufferedReader(new FileReader(fileName));
                String previousRequestNum = null;
                Queue<String> hits = new LinkedList<>();
                String line = reader.readLine();
                while (line != null) {
                    String[] tokens = line.split(" ");
                    String thisRequestNum = tokens[0];
                    if (!thisRequestNum.equals(previousRequestNum)) {
                        if (previousRequestNum != null) {
                            if (requestRankedLists.containsKey(previousRequestNum)) {
                                requestRankedLists.get(previousRequestNum).add(new LinkedList<String>(hits));
                            } else {
                                List<Queue<String>> hitQueuesList = new ArrayList<>();
                                hitQueuesList.add(new LinkedList<String>(hits));
                                requestRankedLists.put(previousRequestNum, hitQueuesList);
                            }
                        }
                        previousRequestNum = thisRequestNum;
                        hits.clear();
                    }
                    hits.add(line);
                    line = reader.readLine();
                }
                if (previousRequestNum != null) {
                    if (requestRankedLists.containsKey(previousRequestNum)) {
                        requestRankedLists.get(previousRequestNum).add(new LinkedList<String>(hits));
                    } else {
                        List<Queue<String>> hitQueuesList = new ArrayList<>();
                        hitQueuesList.add(new LinkedList<String>(hits));
                        requestRankedLists.put(previousRequestNum, hitQueuesList);
                    }
                }
                reader.close();
            }
            for (Map.Entry<String, List<Queue<String>>> entry : requestRankedLists.entrySet()) {
                List<Queue<String>> rankedQueues = entry.getValue();

                for (int index = 0; index < 1000; ++index) {
                    String line = rankedQueues.get(index % filesToMerge.size()).remove();
                    writer.println(line);
                }
            }
            writer.close();
        } catch (Exception e) {
            throw new TasksRunnerException(e);
        }
    }

    /**
     * Processes the analytic tasks file: generates queries for the Tasks and Requests,
     * executes the queries, annotates scoredHits with events.
     */
    void process()  {
        /* This must be done before setupLogging() because that func uses mode,
        which is set in readTaskSetFile()
         */
        //readTaskSetFile(Pathnames.appFileLocation + "tasks.json");
        setupLogging();

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

        logger.info("Executing in " + mode + " mode");
        logger.info("Opening the analytic task file, expanding example docs");
        tasks = new AnalyticTasks(mode, submissionId);    // this is the external analytic tasks file

        /*
         * We can be executing in one of 3 modes: AUTO, AUTO-HITL, or HITL.
         * Processing is a little different in each mode, but the differences are hidden inside
         * the query formulators. The caller passes in the mode through the task set file.
         * The input analytic tasks file should be different for AUTO vs the other 2 modes:
         * the AUTO file should not have anything for the fields like Task Narrative and
         * Request Text. Those are only provided in AUTO-HITL and HITL modes.
         * In HITL mode, noun phrases and sentences that have been judged by a human are passed in and used.
         * Once we know the mode, we select the 2 query formulators created for that mode.
         * (There are separate query formulators for the creation of Task-level queries and
         * Request-level queries, in the two-stage model.)
         */

        eventExtractor = new EventExtractor(tasks, mode, submissionId);

        if (Pathnames.runPreTrain) {
            eventExtractor.preTrainEventAnnotator();
        }

        if (Pathnames.runGetIEFromFile) {
            eventExtractor.annotateProvidedFileEvents("arabic");  // TBD: make this work with multiple languages
        } else if (Pathnames.runGetPhrases) {
            getPhrases();
        } else if (Pathnames.runGetCandidateDocs) {
            doSearch("", Pathnames.getCandidateDocsQueryFormulatorDockerImage);
        }
        else if (Pathnames.runSearch) {
            Pathnames.ProcessingModel processingModel = getProcessingModel();
            if (processingModel == Pathnames.ProcessingModel.TWO_STEP) {
                doSearch(Pathnames.taskLevelQueryFormulatorDockerImage, Pathnames.requestLevelQueryFormulatorDockerImage);
            } else if (processingModel == Pathnames.ProcessingModel.ONE_STEP) {
                doSearch("", Pathnames.requestLevelQueryFormulatorDockerImage);
            } else if (processingModel == Pathnames.ProcessingModel.NEURAL) {
                neuralProcessingModel();
            } else {
                throw new TasksRunnerException("INVALID PROCESSING MODEL");
            }
        }
        logger.info("PROCESSING COMPLETE");

        for(Handler h:logger.getHandlers())
        {
            h.close();   //must call h.close or a .LCK file will remain.
        }
    }

    public static void main(String[] args) {

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

        TasksRunner betterIR = new TasksRunner();
        betterIR.readTaskSetFile(Pathnames.appFileLocation + "tasks.json");
        betterIR.process();
    }
}
