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
import java.util.stream.Stream;

public class TasksRunner {

    private static final Logger logger = Logger.getLogger("TasksRunner");
    EventExtractor eventExtractor;
    private AnalyticTasks tasks;
    private String mode;
    private String submissionId;
//    private boolean runIndexBuild = false;
//    private boolean runEnglishIndexBuild = false;
//    private boolean runPreTrain = false;
//    private boolean runGetIEFromFile = false;
//    private boolean runGetPhrases = false;
//    private boolean runGetCandidateDocs = false;
//    private boolean runSearch = false;
//    private boolean runNeuralIndexing = false;
    public enum Action {
        INDEX_BUILD,
        ENGLISH_INDEX_BUILD,
        PRETRAIN,
        GET_IE_FROM_FILE,
        GET_PHRASES,
        GET_CANDIDATE_DOCS,
        SEARCH,
        NEURAL_INDEX_BUILD
    }
    private Set<Action> actions = null;

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
            actions = new HashSet<>();

            Reader reader = new BufferedReader(new InputStreamReader(new FileInputStream(taskSetFile)));
            JSONParser parser = new JSONParser();
            JSONObject topLevelJSON = (JSONObject) parser.parse(reader);
            submissionId = (String) topLevelJSON.get("test-id");
            submissionId = submissionId.replace(" ", "_");
            JSONObject taskSetJSON = (JSONObject) topLevelJSON.get("task-set");

            if (Pathnames.skipPretrain) {
                logger.info("Skipping IE pre-training");
            } else {
                File f = new File(Pathnames.MODELS_BASE_DIR);
                if (!f.exists()) {
                    logger.info(Pathnames.MODELS_BASE_DIR + " does not exist, so we will run IE pre-training");
                    actions.add(Action.PRETRAIN);
                } else {
                    logger.info(Pathnames.MODELS_BASE_DIR + " already exists, so we will NOT run IE pre-training");
                }
            }

            if (Pathnames.skipIndexBuild) {
                logger.info("Skipping target index build");
            } else {
                if ((SearchEngineInterface.getTargetLanguages().size() == 0)) {
                    logger.info("Target indexes do not exist, so we will build them");
                    actions.add(Action.INDEX_BUILD);
                } else {
                    logger.info("Target indexes already exist, so we will NOT build them");
                }
            }

            if (Pathnames.skipNeuralIndexBuild) {
                logger.info("Skipping neural index build");
            } else {
                File f = new File(Pathnames.neuralFilesLocation + "0.pt");
                if (!f.exists()) {
                    logger.info(Pathnames.neuralFilesLocation + "0.pt" + " does not exist, so we will build the neural index");
                    actions.add(Action.NEURAL_INDEX_BUILD);
                } else {
                    logger.info(Pathnames.neuralFilesLocation + "0.pt" + " already exists, so we will NOT build the neural index");
                }
            }

/*
            if (!(SearchEngineInterface.englishIndexExists())) {
                logger.info("English index does not exist, so we will build the English index");
                actions.add(Action.ENGLISH_INDEX_BUILD);
            } else {
                logger.info("English index already exists, so we will NOT build the English index");
            }
*/
            Pathnames.IEAllowed = true;

            /* The following are mutually exclusive search operations */
            while (true) {
                if (taskSetJSON.containsKey("extract-basic-events")) {
                    JSONObject extractBasicEventsJSON = (JSONObject) taskSetJSON.get("extract-basic-events");
                    Boolean perform = (Boolean) extractBasicEventsJSON.get("perform?");
                    if (perform) {
                        logger.info("extract-basic-events perform true");
                        actions.add(Action.GET_IE_FROM_FILE);
                        break;
                    }
                }
                if (taskSetJSON.containsKey("find-relevant-docs.automatic")) {
                    JSONObject findRelevantDocsJSON = (JSONObject) taskSetJSON.get("find-relevant-docs.automatic");
                    boolean perform = (boolean) findRelevantDocsJSON.get("perform?");
                    if (perform) {
                        mode = "AUTO";
                        actions.add(Action.SEARCH);
                        if (findRelevantDocsJSON.containsKey("ie-allowed")) {
                            Pathnames.IEAllowed = (boolean) findRelevantDocsJSON.get("ie-allowed");
                        }
                        break;
                    }
                }
                if (taskSetJSON.containsKey("find-relevant-docs.auto-hitl")) {
                    JSONObject findRelevantDocsJSON = (JSONObject) taskSetJSON.get("find-relevant-docs.auto-hitl");
                    boolean perform = (boolean) findRelevantDocsJSON.get("perform?");
                    if (perform) {
                        mode = "AUTO-HITL";
                        actions.add(Action.SEARCH);
                        if (findRelevantDocsJSON.containsKey("ie-allowed")) {
                            Pathnames.IEAllowed = (boolean) findRelevantDocsJSON.get("ie-allowed");
                        }
                        break;
                    }
                }
                if (taskSetJSON.containsKey("find-relevant-docs.hitl")) {
                    JSONObject findRelevantDocsJSON = (JSONObject) taskSetJSON.get("find-relevant-docs.hitl");
                    boolean perform = (boolean) findRelevantDocsJSON.get("perform?");
                    if (perform) {
                        mode = "HITL";
                        actions.add(Action.SEARCH);
                        if (findRelevantDocsJSON.containsKey("ie-allowed")) {
                            Pathnames.IEAllowed = (boolean) findRelevantDocsJSON.get("ie-allowed");
                        }
                        break;
                    }
                }
                if (taskSetJSON.containsKey("find-candidate-docs.hitl")) {
                    JSONObject findRelevantDocsJSON = (JSONObject) taskSetJSON.get("find-candidate-docs.hitl");
                    boolean perform = (boolean) findRelevantDocsJSON.get("perform?");
                    if (perform) {
                        mode = "HITL";
                        actions.add(Action.GET_CANDIDATE_DOCS);
                        //Pathnames.targetLanguageIsEnglish = true;
                        //Pathnames.targetLanguage = Pathnames.Language.ENGLISH;
                        break;
                    }
                }
                if (taskSetJSON.containsKey("get-phrases.hitl")) {
                    JSONObject findRelevantDocsJSON = (JSONObject) taskSetJSON.get("get-phrases.hitl");
                    boolean perform = (boolean) findRelevantDocsJSON.get("perform?");
                    if (perform) {
                        mode = "HITL";
                        actions.add(Action.GET_PHRASES);
                        break;
                    }
                }
                if (!(actions.contains(Action.GET_PHRASES) || actions.contains(Action.SEARCH)
                      || actions.contains(Action.GET_IE_FROM_FILE) || actions.contains(Action.GET_CANDIDATE_DOCS))) {
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

    private void updateRelevanceJudgment(Hit hit) {
        String groupType = hit.hitLevel == HitLevel.REQUEST_LEVEL ? "R" : "T";
        String requestID = hit.taskID;
        String docid = hit.docid;
        Request r = tasks.findRequest(requestID);
        if (r != null) {
            for (RelevanceJudgment relevanceJudgment : r.relevanceJudgments) {
                if (relevanceJudgment.getDocid().equals(docid)) {
                    relevanceJudgment.setEvents(hit.events);
                }
            }
        }
    }

    public void annotateSomeCorpusDocs() {
        long linesInOriginalFile;
        try {
            linesInOriginalFile = Files.lines(Paths.get(Pathnames.corpusFileLocation +
                    Pathnames.targetCorpusFileName)).parallel().count();
        } catch (IOException e) {
            throw new TasksRunnerException(e);
        }
        int linesPerFile = 3000;
        // parts 54 and 55 caused errors in the ISI event extractor
        for (int startIndex = 0, part = 108; (part - 1) * linesPerFile < linesInOriginalFile; ++part) {
            logger.info("part " + part);
            startIndex = (part - 1) * linesPerFile;
            try {
                eventExtractor.annotateSomeDocs("CORPUS." + part, startIndex, linesPerFile,
                        Document::getSimpleHitsFromCorpus);
            } catch (Exception e) {
                logger.info("part " + part + " failed");
                throw new TasksRunnerException(e);
            }
        }
    }

    public Map<String,SimpleHit> getSimpleHitsFromRelevantDocs(int startIndex, int numDocsPerFile) {
        Map<String, SimpleHit> m = new HashMap<>();
        for (Task task : tasks.getTaskList()) {
            eventExtractor.createInputFileEntriesFromRelevantDocs(task, m);
        }
        return m;
    }

    public Map<String,SimpleHit> getSimpleHitsFromExampleDocs(int startIndex, int numDocsPerFile) {
        Map<String, SimpleHit> m = new HashMap<>();
        for (Task task : tasks.getTaskList()) {
            eventExtractor.createInputFileEntriesFromExampleDocs(task, m);
        }
        return m;
    }

    public void annotateRelevantDocs() {
        if (Pathnames.skipRelevantDocAnnotation) {
            logger.info("Skipping the event annotator for the relevant docs");
        } else {
            List<Hit> hits = eventExtractor.annotateSomeDocs("RELEVANT", 0, 0,
                    this::getSimpleHitsFromRelevantDocs);

/*
            logger.info("Preparing a file of the relevant docs for the event annotator");

            String fileForEventExtractor = eventExtractor.constructRelevantFileToEventExtractorFileName();
            Map<String, SimpleHit> entries = new HashMap<>();

            for (Task task : tasks.getTaskList()) {
                eventExtractor.createInputFileEntriesFromRelevantDocs(task, entries);
            }
            eventExtractor.writeInputFileMitreFormat(entries, fileForEventExtractor);

            logger.info("Calling the event annotator for relevant docs");
            eventExtractor.annotateRelevantDocEvents();

            logger.info("Retrieving the file of relevant doc events created by the event annotator");
*/
            for (Hit hit : hits) {
                updateRelevanceJudgment(hit);
            }
        }
    }

    public void annotateExampleDocs() {
        if (Pathnames.skipExampleDocAnnotation) {
            logger.info("Skipping the event annotator for the example docs");
        } else {
            List<Hit> hits = eventExtractor.annotateSomeDocs("EXAMPLES", 0, 0,
                    this::getSimpleHitsFromExampleDocs);
/*
            logger.info("Preparing a file of the example docs for the event annotator");

            String fileForEventExtractor = eventExtractor.constructExampleFileToEventExtractorFileName();
            Map<String, SimpleHit> entries = new HashMap<>();

            for (Task task : tasks.getTaskList()) {
                eventExtractor.createInputFileEntriesFromExampleDocs(task, entries);
            }
            eventExtractor.writeInputFileMitreFormat(entries, fileForEventExtractor);

            logger.info("Calling the event annotator for the example docs");
            eventExtractor.annotateExampleDocEvents();

            logger.info("Retrieving the file of example doc events created by the event annotator");
*/
            for (Hit hit : hits) {
                updateTaskOrRequest(hit);
            }
        }

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
        if (actions.contains(Action.GET_CANDIDATE_DOCS)) {  // force one-step model
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
            if (actions.contains(Action.GET_CANDIDATE_DOCS)) {
                qf.execute(10);
            } else {
                qf.execute(Pathnames.RESULTS_CAP);
            }
        }

/*
        if (Pathnames.doRequestLevelEvaluation) {
            logger.info("Evaluating Request-level hits");
            Map<String, Double> rstats = qf.evaluate("RAW");  // before reranking is called "RAW"
        }
*/

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

        /* Create an input file for the event extractor for the top N task-level scoredHits.
        (We did this to experiment with task-level scoredHits, but it is not needed normally.)
        logger.info("Extracting events from the top task-level scoredHits");
        qf.createInputForEventExtractorFromTaskHits();
        */

/*
        if (Pathnames.doTaskLevelEvaluation) {
            logger.info("Evaluating the task-level results");
            Map<String, QueryManager.EvaluationStats> tstats = qf.evaluateTaskLevel();
        }
*/

        logger.info("Building a separate index for each task's top scoredHits");
        qf.buildTaskLevelIndexes();
    }

    private void addRankedListForLanguage(String fileName, String language, Map<String, Map<String, Map<String,
            List<ScoredHit>>>> tasks) {
        File f = new File(fileName);
        if (!f.exists()) {
            throw new TasksRunnerException("No reranked run file for language " + language + "!");
        } else {
            logger.info("Reading reranked run file " + fileName);
            try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] tokens = line.split("[ \t]+");
                    if (tokens.length != 6) {
                        throw new TasksRunnerException("Bad runfile line: " + line);
                    }
                    String requestID = tokens[0];
                    String docid = tokens[2];
                    String score = tokens[4];
                    String taskID = QueryManager.getTaskIDFromRequestID(requestID);
                    ScoredHit scoredHit = new ScoredHit(docid, score);
                    if (tasks.containsKey(taskID)) {
//                        Map<String, List<ScoredHit>> requests = tasks.get(taskID);
                        Map<String, Map<String, List<ScoredHit>>> requests = tasks.get(taskID);
                        if (requests.containsKey(requestID)) {
                            Map<String, List<ScoredHit>> languages = requests.get(requestID);
                            if (languages.containsKey(language)) {
                                List<ScoredHit> hitlist = languages.get(language);
                                if (hitlist.size() < Pathnames.RESULTS_CAP_IN_FINAL_RESULTS_FILE) {
                                    hitlist.add(scoredHit);
                                }
                            } else {
                                List<ScoredHit> hitlist = new ArrayList<>();
                                hitlist.add(scoredHit);
                                languages.put(language, hitlist);
                            }
                        } else {
                            List<ScoredHit> requestScoredHits = new ArrayList<>();
                            requestScoredHits.add(scoredHit);
                            Map<String, List<ScoredHit>> languages = new HashMap<>();
                            languages.put(language,requestScoredHits);
                            requests.put(requestID, languages);
                        }
                    } else {
                        Map<String, Map<String, List<ScoredHit>>> requests = new HashMap<>();
                        List<ScoredHit> requestScoredHits = new ArrayList<>();
                        requestScoredHits.add(scoredHit);
                        Map<String, List<ScoredHit>> languages = new HashMap<>();
                        languages.put(language,requestScoredHits);
                        requests.put(requestID, languages);
                        tasks.put(taskID, requests);
                    }
                }
            } catch (IOException e) {
                throw new TasksRunnerException(e);
            }
        }
    }

    public void writeFinalResultsFile() {
        // Read the run files into memory
        Map<String, Map<String, Map<String, List<ScoredHit>>>> tasks = new HashMap<>();
        List<String> targetLanguages = SearchEngineInterface.getTargetLanguages();
        for (String language : targetLanguages) {
            addRankedListForLanguage(Pathnames.runFileLocation + submissionId + "."
                    + language + ".Request.RERANKED.out", SearchEngineInterface.toThreeCharForm(language), tasks);
        }
        addRankedListForLanguage(Pathnames.runFileLocation + submissionId + ".DPR_Baseline_E2E.out", "combined",
                tasks);

        /* Now write the info to the output file */
        try {
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(Pathnames.appFileLocation + "results.json")));

            writer.println("{");
            writer.println("\"format-type\": \"ir-results\",");
            writer.println("\"format-version\": \"v1\",");
            writer.println("\"corpus-id\": \"release-1\",");
            writer.println("\"search-results\": {" );
            int numTasks = tasks.size();
            int currentTaskIdx = 0;
            for (String taskID : tasks.keySet()) {
                ++currentTaskIdx;
                writer.println("  \"" + taskID + "\": {");
                writer.println("    \"task\": \"" + taskID + "\",");
                writer.println("    \"requests\": {");
                Map<String, Map<String, List<ScoredHit>>> requests = tasks.get(taskID);
                int numRequests = requests.size();
                int currentRequestIdx = 0;
                for (String requestID : requests.keySet()) {
                    ++currentRequestIdx;
                    writer.println("      \"" + requestID + "\": {");
                    writer.println("        \"request\": \"" + requestID + "\",");
                    writer.println("        \"rankings\": {");
                    Map<String, List<ScoredHit>> languages = requests.get(requestID);
                    int currentLanguageIdx = 0;
                    for (String language : languages.keySet()) {
                        ++currentLanguageIdx;
                        List<ScoredHit> scoredHits = languages.get(language);
                        writer.println("           \"" + language + "\": [");
                        int numHits = scoredHits.size();
                        int currentHitIdx = 0;
                        for (ScoredHit scoredHit : scoredHits) {
                            ++currentHitIdx;
                            String hitLine = "            { \"docid\": \"" + scoredHit.docid + "\", \"score\": " + scoredHit.score + " }";
                            if (currentHitIdx < numHits) {
                                hitLine += ",";
                            }
                            writer.println(hitLine);
                        }
                        if (currentLanguageIdx < languages.size()) {
                            writer.println("           ],");
                        } else {
                            writer.println("           ]");
                        }
                        // end of combined ranking
                    }
                    if (Pathnames.includeEventsInFinalResults) {
                        writer.println("        },");
                        //addEvents(requestID, writer);
                    } else {
                        writer.println("        }");  // no comma
                    }

                    if (currentRequestIdx < numRequests) {
                        writer.println("      },");  // end of this request
                    } else {
                        writer.println("      }");  // end of this request
                    }
                }
                writer.println("    }");  // end of requests
                if (currentTaskIdx < numTasks) {
                    writer.println("  },");  // end of this task
                } else {
                    writer.println("  }");  // end of this task
                }
            }
            writer.println("}");  // end of search-results
            writer.println("}");  // end of file
            writer.close();
        } catch (Exception cause) {
            throw new TasksRunnerException(cause);
        }
    }


    private void doSearch(String taskLevelFormulator, String requestLevelFormulator) {
        annotateExampleDocs();  // call ISI event extractor to add events to the example docs
        annotateRelevantDocs();  // call ISI event extractor to add events to the relevant docs
        /* Now write out files with a simpler format for the ISI events. */
/*
        eventExtractor.writeEventsAsJson(
                eventExtractor.readEventFile(eventExtractor.constructExampleFileFromEventExtractorFileName(), -1),
                "EXAMPLES", eventExtractor.constructExampleEventFileName(), 999999);
        eventExtractor.writeEventsAsJson(
                eventExtractor.readEventFile(eventExtractor.constructRelevantFileFromEventExtractorFileName(), -1),
                "RELEVANT", eventExtractor.constructRelevantEventFileName(), 999999);
*/

        /* Write out the internal analytic tasks info file, with doc text and event info, for query formulators and re-rankers to use */
        tasks.writeJSONVersion();

        List<String> filesToMerge = new ArrayList<>();
        for (String language : SearchEngineInterface.getTargetLanguages()) {
            if (getProcessingModel() == Pathnames.ProcessingModel.TWO_STEP) {
                doTaskLevelProcessing(taskLevelFormulator, language);
            }
            doRequestLevelProcessing(requestLevelFormulator, language, filesToMerge);
        }

        QueryManager qf = new QueryManager(submissionId, "combined", mode, tasks, "Request", eventExtractor);

        logger.info("Merging multiple language's ranked runfiles into one");
        mergeRerankedRunFilesRoundRobin(filesToMerge);  // round robin merging
        //qf.mergeRerankedRunFilesByScore(filesToMerge);  // naive score merge
        // If you use these, remember to also change doRequestLevelProcessing() to not do the Z1 reranking,
        // which destroys the scores
        //qf.rescoreRunFilesZScores(filesToMerge);
        //qf.rescoreRunFilesMinMax(filesToMerge);

        logger.info("Second reranking");
        qf.rerank2();

        logger.info("Merging DPR with Baseline");
        qf.mergeDPR_Baseline();

        logger.info("Executing neural search");
        new NeuralQueryProcessorDocker(submissionId, mode, tasks).search();

        logger.info("Merging DPR+Baseline with E2E");
        qf.mergeDPR_Baseline_E2E();

        logger.info("Output the final file");
        writeFinalResultsFile();
    }

    /**
     * Executes a round-robin merge of the runfiles
     * @param filesToMerge the list of runfiles to be merged
     */
    public void mergeRerankedRunFilesRoundRobin(List<String> filesToMerge) {
        try {
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(Pathnames.runFileLocation + submissionId + ".FINAL.out")));
            if (filesToMerge.size() == 1) {
                /* No merge is needed */
                BufferedReader reader = new BufferedReader(new FileReader(filesToMerge.get(0)));
                String line = reader.readLine();
                while (line != null) {
                    writer.println(line);
                    line = reader.readLine();
                }
                reader.close();
                writer.close();
            } else {
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

                    RankedQueuesManager queueManager = new RankedQueuesManager(rankedQueues);
                    for (int written = 0; written < 1000; ++written) {
                        String line = queueManager.getNextLine();
                        if (line == null) {
                            break;
                        }
                        writer.println(setScore(line, written));
                    }
                }
                writer.close();
            }
        } catch (Exception e) {
            throw new TasksRunnerException(e);
        }
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
/*
            if (Pathnames.doRequestLevelEvaluation) {
                logger.info("Evaluating the Request-level hits");
                Map<String, Double> rstats = qf.evaluate("RERANKED");
            }
*/
        }
        filesToMerge.add(qf.getRerankedRunFileName());
        /* TEMP EXPERIMENT - pass the original run files to the merge instead of the Z1 reranked files
        filesToMerge.add(qf.getRunFileName());
         */
    }

    private String setScore(String line, int index) {
        String[] tokens = line.split(" ");
        String thisRequestNum = tokens[0];
        String docno = tokens[2];
        return thisRequestNum + " Q0 " + docno + " " + index + " " + (2000 - index) + " CLEAR";
    }
    /**
     * Executes the analytic tasks file: generates queries for the Tasks and Requests,
     * executes the queries, annotates scoredHits with events.
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
    void process()  {

        setupLogging();
        logger.info("Submission id is: " + submissionId);
        logger.info("Executing in " + mode + " mode");
        logger.info("IE Allowed = " + Pathnames.IEAllowed);

        if (actions.contains(Action.ENGLISH_INDEX_BUILD)) {
            SearchEngineInterface.getSearchEngine().buildIndexes(Pathnames.corpusFileLocation + Pathnames.englishCorpusFileName);
        }

        if (actions.contains(Action.INDEX_BUILD)) {
            SearchEngineInterface.getSearchEngine().buildIndexes(Pathnames.corpusFileLocation + Pathnames.targetCorpusFileName);
        }

        if (actions.contains(Action.NEURAL_INDEX_BUILD)) {
            logger.info("Building the neural index");
            new NeuralQueryProcessorDocker(submissionId, mode, tasks).buildIndex();
        }

        logger.info("Opening the analytic task file, expanding example docs");
        tasks = new AnalyticTasks(mode, submissionId);    // this is the external analytic tasks file

        eventExtractor = new EventExtractor(tasks, mode, submissionId);

        /* One-time action: get events for some documents in the target corpus: */
        annotateSomeCorpusDocs();


        if (actions.contains(Action.PRETRAIN)) {
            eventExtractor.preTrainEventAnnotator();
        }

        if (actions.contains(Action.GET_IE_FROM_FILE)) {
            eventExtractor.annotateProvidedFileEvents();
        } else if (actions.contains(Action.GET_PHRASES)) {
            getPhrases();
        } else if (actions.contains(Action.GET_CANDIDATE_DOCS)) {
            doSearch("", Pathnames.getCandidateDocsQueryFormulatorDockerImage);
        }
        else if (actions.contains(Action.SEARCH)) {
            Pathnames.ProcessingModel processingModel = getProcessingModel();
            if (processingModel == Pathnames.ProcessingModel.TWO_STEP) {
                doSearch(Pathnames.taskLevelQueryFormulatorDockerImage, Pathnames.requestLevelQueryFormulatorDockerImage);
            } else if (processingModel == Pathnames.ProcessingModel.ONE_STEP) {
                doSearch("", Pathnames.requestLevelQueryFormulatorDockerImage);
            } else if (processingModel == Pathnames.ProcessingModel.NEURAL) {
                logger.info("NEURAL PROCESSING MODE: Building and executing queries");
                new NeuralQueryProcessorDocker(submissionId, mode, tasks).search();
            } else {
                throw new TasksRunnerException("INVALID PROCESSING MODEL");
            }
        }

        logger.info("PROCESSING COMPLETE");

        for(Handler h : logger.getHandlers())
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
