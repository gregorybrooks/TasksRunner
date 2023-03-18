package edu.umass.ciir;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.nio.file.StandardCopyOption.*;

public class QueryManager {
    private static final int MAX_DOCIDS = 100000;  // max number of scoredHits to look at
    public Map<String, String> queries;
    public Map<String, String> nonTranslatedQueries;

    public Run run;
    private String mode;
    private String language;
    private String queryFileName;
    private String queryFileFullPathName;
    private String queryFileNameOnly;
    private String runFileName;
    private String evaluationTaskLevelFileName;
    private String evaluationRequestLevelFileName;
    private String key;
    private String taskFileNameGeneric;
    private AnalyticTasks tasks;
    private long runTime = 0;
    private String rerankedRunFile;
    private String phase;
    private String submissionId;
    private EventExtractor eventExtractor;
    private static final Logger logger = Logger.getLogger("TasksRunner");

    public QueryManager(String submissionId, String language, String mode, AnalyticTasks tasks, String phase,
                        EventExtractor eventExtractor) {
        try {
            this.tasks = tasks;
            this.phase = phase;
            this.mode = mode;
            this.language = language;
            this.submissionId = submissionId;
            this.eventExtractor = eventExtractor;
            taskFileNameGeneric = tasks.getTaskFileName();
            // When the query formulation name is a Docker image name, it might be qualified
            // with the owner's name and a "/", which confuses things when the formulation name
            // is used in a pathname, so change the "/" to a "-"
//            queryFormulationName = queryFormulationName.replace("/", "-");
//            this.key =  tasks.getMode() + "." + phase + "." + queryFormulationName;
//            this.key =  submissionId + "." + language + "." + phase + "." + queryFormulationName;
            this.key =  submissionId + "." + language + "." + phase;
            queryFileNameOnly = key + ".queries.json";
            queryFileFullPathName = Pathnames.queryFileLocation + queryFileNameOnly;
            queryFileName = queryFileFullPathName; // for historical reasons
            runFileName = Pathnames.runFileLocation + key + ".out";
            evaluationTaskLevelFileName = Pathnames.evaluationFileLocation + "/" + key + ".TASK.csv";
            evaluationRequestLevelFileName = Pathnames.evaluationFileLocation + "/" + key + ".REQUEST.csv";

            queries = getGeneratedQueries(queryFileName);
            /*
            nonTranslatedQueries = getGeneratedQueries(queryFileName + ".NON_TRANSLATED");
             */
            rerankedRunFile = Pathnames.runFileLocation + key + ".RERANKED.out";

            run = new Run();
        } catch (Exception e) {
            throw new TasksRunnerException(e.getMessage());
        }
    }

    private String getTaskLevelKey() {
        return submissionId + "." + language + "." + "Task";
    }

    private String getTaskLevelIndexName(String taskNum) {
        return getTaskLevelKey() + "." + taskNum + ".PARTIAL";
    }

    private void callQueryFormulator(String dockerImageName) {
        String analyticTasksInfoFilename = submissionId + ".analytic_tasks.json";
        // if 4 GPUs, 0 is first one, 1 is second one, etc.
        String gpu_parm = (!Pathnames.gpuDevice.equals("") ? " --gpus device=" + Pathnames.gpuDevice : "");
        String command = "docker run --rm"
                + gpu_parm
                + " --env MODE=" + mode
                // The query formulators expect the language to be all upper-case
                + " --env OUT_LANG=" + (Pathnames.runGetCandidateDocs ? "ENGLISH" : language.toUpperCase(Locale.ROOT))
                + " --env PHASE=" + phase
                + " --env SEARCH_ENGINE=" + Pathnames.searchEngine
                + " --env INPUTFILE=" + analyticTasksInfoFilename
                + " --env QUERYFILE=" + getKey()
                /* For each directory that we want to share between this parent docker container (TasksRunner)
                 and the child docker container (TaskQueryBuilder1 e.g.), we pass the pathname
                 in an environment variable, and we make that path a bind-volume so the child container
                 can actually access it.
                 */
                + " --env eventExtractorFileLocation=" + Pathnames.eventExtractorFileLocation
                + " -v " + Pathnames.eventExtractorFileLocation + ":" + Pathnames.eventExtractorFileLocation
                + " --env queryFileLocation=" + Pathnames.queryFileLocation
                + " -v " + Pathnames.queryFileLocation + ":" + Pathnames.queryFileLocation
                + " --env logFileLocation=" + Pathnames.logFileLocation
                + " -v " + Pathnames.logFileLocation + ":" + Pathnames.logFileLocation
                + " --env galagoLocation=" + Pathnames.galagoLocation
                // must define volume for galago, not galago/bin, so it can see the galago/lib files, too:
                + " -v " + Pathnames.galagoBaseLocation + ":" + Pathnames.galagoBaseLocation
                    + " --env englishIndexLocation=" + Pathnames.indexLocation + Pathnames.searchEngine + "/better-clear-ir-english/"
                    + " -v " + Pathnames.indexLocation + Pathnames.searchEngine + "/better-clear-ir-english"
                    + ":" + Pathnames.indexLocation + Pathnames.searchEngine + "/better-clear-ir-english"
                + " --env targetIndexLocation=" + Pathnames.indexLocation + Pathnames.searchEngine + "/better-clear-ir-"
                + language
                + " -v " + Pathnames.indexLocation + Pathnames.searchEngine + "/better-clear-ir-" + language
                + ":" + Pathnames.indexLocation + Pathnames.searchEngine + "/better-clear-ir-" + language
                + " --env qrelFile=" + Pathnames.qrelFileLocation + Pathnames.qrelFileName
                + " -v " + Pathnames.qrelFileLocation + ":" + Pathnames.qrelFileLocation

                + " " + dockerImageName
                + " sh -c ./runit.sh";
        String logFile = Pathnames.logFileLocation + submissionId + "." + phase + ".query-formulator.out";

        Command.execute(command, logFile);
    }

    public void buildQueries(String queryFormulator) {
        callQueryFormulator(queryFormulator);
    }

    public void readQueryFile() {
        queries = getGeneratedQueries(queryFileName);
    }

    public String getKey() {
        return key;
    }

    public void setRun(String runFileName) {
        run = new Run(runFileName);  // Get new run file into memory
    }

    // file name only, no path
    public String getQueryFileNameOnly() {
        return queryFileNameOnly;
    }

    public AnalyticTasks getTasks() {
        return tasks;
    }

    /* vars needed by the Docker version of the reranker:
    $TASK_FILE $DATA_DIR $QLANG $DLANG $RUNFILE_MASK $DEVICE $NUM_CPU $TOPK $OUTPUT_DIR
    */
    public void callReranker(String dockerImageName, String currentRunFile, String outputFileName) {
        String analyticTasksInfoFilename = submissionId + ".analytic_tasks.json";
        // if 4 GPUs, 0 is first one, 1 is second one, etc.
        // works at Mitre:
        //String gpu_parm = " --gpus 1";
        // doesn't seem to work at Mitre:
        String gpu_parm = (!Pathnames.gpuDevice.equals("") ? " --gpus device=" + Pathnames.gpuDevice : "");

        String deviceParm = Pathnames.rerankerDevice;   // cuda:0 or cpu
        String command = "docker run --rm"
                + gpu_parm
                + " --env MODE=" + mode
                + " --env DEVICE=" + deviceParm
                + " --env TASK_FILE=" + Pathnames.eventExtractorFileLocation + analyticTasksInfoFilename
                + " -v " + Pathnames.eventExtractorFileLocation + ":" + Pathnames.eventExtractorFileLocation
                + " --env DATA_DIR=" + Pathnames.eventExtractorFileLocation
                + " --env OUTPUT_DIR=" + Pathnames.eventExtractorFileLocation
                + " --env QLANG=en --env DLANG=" + (Pathnames.runGetCandidateDocs ? "ENGLISH" : language.toUpperCase(Locale.ROOT))
                + " --env RUNFILE_MASK='" + submissionId + ".[req-num].REQUESTHITS.events.json'"
                + " --env NUM_CPU=8 --env TOPK=100"
                + " --env RUNFILE=" + runFileName
                + " -v " + Pathnames.runFileLocation + ":" + Pathnames.runFileLocation
                + " --env CORPUS_FILE=" + Pathnames.corpusFileLocation + Pathnames.targetCorpusFileName
                + " -v " + Pathnames.corpusFileLocation + ":" + Pathnames.corpusFileLocation

                + " " + dockerImageName
                + " sh -c ./runit.sh";
        String logFile = Pathnames.logFileLocation + submissionId + ".reranker-docker-program.out";

        Command.execute(command, logFile);

        // TBD: add submissionId to this file name (must change it in the Docker, too)
        try {
            Files.copy(new File(Pathnames.eventExtractorFileLocation + "fused.run").toPath(),
                    new File(outputFileName).toPath(), REPLACE_EXISTING);
        } catch (Exception e) {
            throw new TasksRunnerException(e);
        }
    }



    public void createInputFileEntriesFromHits(String docSetType, String taskOrRequestID,
                                               List<String> hits, Map<String,SimpleHit> m) {
        for (String td : hits) {
            List<SentenceRange> sentences = null;
            String docText;
            String translatedDocText;
            if (Pathnames.runGetCandidateDocs || Pathnames.targetLanguageIsEnglish) {
                sentences = Document.getDocumentSentences(td);
                docText = Document.getDocumentWithMap(td);
                translatedDocText = "";
            } else {
                sentences = Document.getTargetDocumentSentences(td);
                docText = Document.getTargetDocumentWithMap(td);
                translatedDocText = Document.getTranslatedTargetDocumentWithMap(td);
            }
            SimpleHit hit = new SimpleHit(td, docText, translatedDocText, sentences, null, null);
            m.put(docSetType + "--" + taskOrRequestID + "--" + td, hit);
        }
    }

    public List<Hit> mergeHits (HitLevel hitLevel, String reqNum, List<Hit> hits, List<String> docids) {
        Map<String, Hit> eventsAdded = new HashMap<>();
        for (Hit hit : hits) {
            // updateSentenceIDs(hit);
            eventsAdded.put(hit.docid, hit);
        }
        List<Hit> finalList = new ArrayList<>();
        for (String docid : docids) {
            if (eventsAdded.containsKey(docid)) {
                finalList.add(eventsAdded.get(docid));
            } else {
                Hit hit = new Hit(hitLevel, reqNum, docid, "", "", new ArrayList<Event>());
                finalList.add(hit);
            }
        }
        return finalList;
    }

    public void retrieveEventsFromRequestHits() {
        String theRunFileName = Pathnames.runFileLocation + getKey() + ".out";
        logger.info("Setting run file to " + theRunFileName);
        setRun(theRunFileName);

        for (Task t : tasks.getTaskList()) {
            for (Request r : t.getRequests().values()) {
                List<Hit> mergedHits = mergeHits(HitLevel.REQUEST_LEVEL, r.reqNum,
                        eventExtractor.readEventFile(eventExtractor.constructRequestLevelFileFromEventExtractorFileName(r), -1),
                        getDocids(r.reqNum, Pathnames.RESULTS_CAP));
                eventExtractor.writeEventsAsJson(mergedHits, "REQUESTHITS", eventExtractor.constructRequestLevelEventFileName(r),
                        Pathnames.REQUEST_HITS_DETAILED);
                logger.info(eventExtractor.constructRequestLevelEventFileName(r) + " written");
            }
        }
    }

    /**
     * Version that does not assume the runfile is the language-specific one (as opposed to the neural runfile).
     * It merges the events file into the current runfile of this QueryManager.
     */
    public void retrieveEventsFromRequestHitsNoRunfileRead() {
        for (Task t : tasks.getTaskList()) {
            for (Request r : t.getRequests().values()) {
                List<Hit> mergedHits = mergeHits(HitLevel.REQUEST_LEVEL, r.reqNum,
                        eventExtractor.readEventFile(eventExtractor.constructRequestLevelFileFromEventExtractorFileName(r), -1),
                        getDocids(r.reqNum, Pathnames.RESULTS_CAP));
                eventExtractor.writeEventsAsJson(mergedHits, "REQUESTHITS", eventExtractor.constructRequestLevelEventFileName(r),
                        Pathnames.REQUEST_HITS_DETAILED);
                logger.info(eventExtractor.constructRequestLevelEventFileName(r) + " written");
            }
        }
    }

    /**
     * Number of scoredHits to have full event details.
     */
    private final int TASK_HITS_DETAILED = Pathnames.RESULTS_CAP;

    /**
     * Creates an input file to give to the event extractor, of the top scoredHits for each task.
     * Keep this method in case we ever want to do this:
     */
    public void createInputForEventExtractorFromTaskHits(QueryManager qf) {
        Map<String, SimpleHit> simpleEntries = new LinkedHashMap<>();
        Map<String, String> entries = new LinkedHashMap<>();
        List<Task> taskList = tasks.getTaskList();

        // Load the document text map in one pass through the corpus file:
        if (Pathnames.runGetCandidateDocs || Pathnames.targetLanguageIsEnglish) {
            Document.buildDocMap(tasks.getTaskList().parallelStream()
                    .flatMap(t -> qf.getDocids(t.taskNum, TASK_HITS_DETAILED).stream())
                    .collect(Collectors.toSet()));
        } else {
            Document.buildTargetDocMap(tasks.getTaskList().parallelStream()
                    .flatMap(t -> qf.getDocids(t.taskNum, TASK_HITS_DETAILED).stream())
                    .collect(Collectors.toSet()));
        }
        /* Create the file to give to the ISI event extractor */
        for (Task task : taskList) {
            List<String> hits = qf.getDocids(task.taskNum, TASK_HITS_DETAILED);
            simpleEntries.clear();
            createInputFileEntriesFromHits("TaskLevelHit", task.taskNum, hits, simpleEntries);
            if (simpleEntries.size() > 0) {
                String fileForEventExtractor = eventExtractor.constructTaskLevelFileFromEventExtractorFileName(task);
                eventExtractor.writeInputFileForEventExtractor(simpleEntries, fileForEventExtractor);
            }
        }
    }

    /**
     * Creates an input file to give to the event extractor, of the top scoredHits for each request.
     */
    public void createInputFileForEventExtractorFromRequestHits() {
        Map<String, SimpleHit> simpleEntries = new LinkedHashMap<>();
        Map<String, String> entries = new LinkedHashMap<>();
        List<Request> requestList = tasks.getRequests();

        // Load the document text map in one pass through the corpus file:
        if (Pathnames.runGetCandidateDocs || Pathnames.targetLanguageIsEnglish) {
            Document.buildDocMap(requestList.parallelStream()
                    .flatMap(r -> getDocids(r.reqNum, Pathnames.REQUEST_HITS_DETAILED).stream())
                    .collect(Collectors.toSet()));
        } else {
            Document.buildTargetDocMap(requestList.parallelStream()
                    .flatMap(r -> getDocids(r.reqNum, Pathnames.REQUEST_HITS_DETAILED).stream())
                    .collect(Collectors.toSet()));
        }
        /* First, build the file to give to the HITL person */
/*
        Map<String,String> queries = getQueries();
        for (Task t : tasks.getTaskList()) {
            for (Request r : t.getRequests().values()) {
                if (r.reqText == null || r.reqText.equals("")) {
                    continue;     // only include the 2 requests with extra HITL info
                }
                List<String> hits = getDocids(r.reqNum, 10);
                simpleEntries.clear();
                for (String td : hits) {
                    String score = getScore(r.reqNum, td);
                    String docid = "RequestLevelHit--" + r.reqNum + "--" + td;
                    String docText = "";
                    if (Pathnames.targetLanguageIsEnglish) {
                        docText = Document.getDocumentWithMap(td);
                    } else {
                        docText = Document.getArabicDocumentWithMap(td);
                    }
                    String query = queries.get(r.reqNum);
                    simpleEntries.put(docid, new SimpleHit(docid, docText, score, query,
                            t.taskNum, t.taskTitle, t.taskNarr, t.taskStmt, r.reqNum, r.reqText));
                }
                if (simpleEntries.size() > 0) {
                    String fileForEventExtractor = eventExtractor.constructRequestLevelSimpleFileName(r);
                    eventExtractor.writeInputFileSimpleFormat(simpleEntries, fileForEventExtractor);
                }
            }
        }
*/
        /* Create the file to give to the ISI event extractor */
        for (Request r : requestList) {
            List<String> hits = getDocids(r.reqNum, Pathnames.REQUEST_HITS_DETAILED);
//            logger.info("qf.getDocids for reqNum " + r.reqNum + " returned " + hits.size() + " entries");
            simpleEntries.clear();
            eventExtractor.createInputFileEntriesFromHits("RequestLevelHit", r.reqNum, hits, simpleEntries);
//            logger.info("createInputFileEntriesFromHits returned " + simpleEntries.size() + " entries");
            if (simpleEntries.size() > 0) {
                String fileForEventExtractor = eventExtractor.constructRequestLevelToEventExtractorFileName(r);
                eventExtractor.writeInputFileForEventExtractor(simpleEntries, fileForEventExtractor);
            }
        }
    }

    /**
     * Creates an input file to give to the event extractor, of the top scoredHits for each request.
     * This is for my Galago re-ranker project.
     */
    public void createInputForRerankerFromRequestHits() {
        Map<String, SimpleHit> simpleEntries = new LinkedHashMap<>();
        List<Request> requestList = tasks.getRequests();

        // Load the document text map in one pass through the corpus file:
        if (Pathnames.runGetCandidateDocs || Pathnames.targetLanguageIsEnglish) {
            Document.buildDocMap(requestList.parallelStream()
                    .flatMap(r -> getDocids(r.reqNum, Pathnames.REQUEST_HITS_DETAILED).stream())
                    .collect(Collectors.toSet()));
        } else {
            Document.buildTargetDocMap(requestList.parallelStream()
                    .flatMap(r -> getDocids(r.reqNum, Pathnames.REQUEST_HITS_DETAILED).stream())
                    .collect(Collectors.toSet()));
        }

        Map<String, String> queries = getQueries();
        for (Task t : tasks.getTaskList()) {
            for (Request r : t.getRequests().values()) {
                if (r.reqText.length() == 0) {
                    continue;     // only include the 2 requests with extra HITL info
                }
                List<String> hits = getDocids(r.reqNum, Pathnames.REQUEST_HITS_DETAILED);
                simpleEntries.clear();
                String query = queries.get(r.reqNum);
                for (String td : hits) {
                    String score = getScore(r.reqNum, td);
                    String docText;
                    String translatedDocText;
                    if (Pathnames.runGetCandidateDocs || Pathnames.targetLanguageIsEnglish) {
                        docText = Document.getDocumentWithMap(td);
                        translatedDocText = "";
                    } else {
                        docText = Document.getTargetDocumentWithMap(td);
                        translatedDocText = Document.getTranslatedTargetDocumentWithMap(td);
                    }
                    simpleEntries.put(td, new SimpleHit(td, docText, translatedDocText, score, null,
                            null, null));
                }
                if (simpleEntries.size() > 0) {
                    String fileName = eventExtractor.constructRequestLevelRerankerFileName(r);
                    eventExtractor.writeInputFileRerankerFormat(simpleEntries, fileName, query, r.reqNum, "CLEAR");
                }
            }
        }
    }


    /* Keep this method in case we ever want to do this: */
    public void retrieveEventsFromTaskHits() {
        /* Get the document texts and sentences for each hit mentioned in the event files */
        /* (which is a side effect of loading the document text map with those docs) */
        if (Pathnames.runGetCandidateDocs || Pathnames.targetLanguageIsEnglish) {
            Document.buildDocMap(tasks.getTaskList().parallelStream()
                    .flatMap(t -> eventExtractor.readEventFile(eventExtractor.constructTaskLevelFileFromEventExtractorFileName(t), -1).stream())
                    .map(hit -> hit.docid)
                    .collect(Collectors.toSet()));
        } else {
            Document.buildTargetDocMap(tasks.getTaskList().parallelStream()
                    .flatMap(t -> eventExtractor.readEventFile(eventExtractor.constructTaskLevelFileFromEventExtractorFileName(t), -1).stream())
                    .map(hit -> hit.docid)
                    .collect(Collectors.toSet()));
        }

        // To be able to get the scoredHits we need the qf to open the runfile
        String theRunFileName = Pathnames.runFileLocation + getKey() + ".out";
        setRun(theRunFileName);

        for (Task t : tasks.getTaskList()) {
            List<Hit> hits = eventExtractor.readEventFile(eventExtractor.constructTaskLevelFileFromEventExtractorFileName(t), -1);
            /* Augment the scoredHits for this task with the info from the events file */
            List<Hit> mergedHits = mergeHits(HitLevel.TASK_LEVEL, t.taskNum, hits, getDocids(t.taskNum, 1000));

            /* Write out a file that has everything about the task scoredHits that is needed by the final re-ranker */
            String taskHitsEventFileName = eventExtractor.constructTaskLevelEventFileName(t);
            eventExtractor.writeEventsAsJson(mergedHits, "TASKHITS", taskHitsEventFileName,
                    Pathnames.REQUEST_HITS_DETAILED);
        }
    }

    public void rerank() {
        callReranker(Pathnames.rerankerDockerImage, runFileName, rerankedRunFile);
        // Refresh the in-memory runfile data
        run = new Run(rerankedRunFile);
    }

    public void rerank2() {
        runFileName = Pathnames.runFileLocation + submissionId + ".FINAL.out";
        callReranker(Pathnames.reranker2DockerImage, Pathnames.runFileLocation + submissionId + ".FINAL.out",
                Pathnames.runFileLocation + submissionId + ".DPR.out");
        // Refresh the in-memory runfile data
        run = new Run(Pathnames.runFileLocation + submissionId + ".DPR.out");
    }

    private static void copyFile(File source, File dest) throws IOException {
        Files.copy(source.toPath(), dest.toPath(), REPLACE_EXISTING);
    }

    public void mergeDPR_Baseline() {
        List<String> filesToMerge = new ArrayList<>();
        filesToMerge.add(Pathnames.runFileLocation + submissionId + ".DPR.out");
        filesToMerge.add(Pathnames.runFileLocation + submissionId + ".FINAL.out");
        reciprocalRankMergeRerankedRunFiles(filesToMerge, Pathnames.runFileLocation + submissionId + ".DPR_Baseline.out");
    }

    public void mergeDPR_Baseline_E2E() {
        List<String> filesToMerge = new ArrayList<>();
        filesToMerge.add(Pathnames.runFileLocation + submissionId + ".DPR_Baseline.out");
        filesToMerge.add(Pathnames.runFileLocation + "hint_e2e.run");
        reciprocalRankMergeRerankedRunFiles(filesToMerge, Pathnames.runFileLocation + submissionId + ".DPR_Baseline_E2E.out");
    }

    public void copyRunFileToFinalFile(String fileName) {
        String finalFileName = Pathnames.runFileLocation + submissionId + ".DPR_Baseline_E2E.out";
        try {
            copyFile(new File(fileName), new File(finalFileName));
        } catch (Exception e) {
            throw new TasksRunnerException(e);
        }
    }

    public void copyRunFileToRerankedRunFile() {
        try {
            copyFile(new File(runFileName), new File(rerankedRunFile));
        } catch (Exception e) {
            throw new TasksRunnerException(e);
        }
    }

    public String getRerankedRunFileName() {
        return rerankedRunFile;
    }

    public String getRunFileName() {
        return runFileName;
    }

    /**
     * Reads the generated query file for a given query formulation, created by ConvertDryRunTasks,
     * and makes a Map of the generated queries, with request number as key.
     *
     * @return Returns the Map of request number to generated query text.
     */
    private Map<String, String> getGeneratedQueries(String queryFileName) {
        logger.info("Reading query file: " + queryFileName);
        return SearchEngineInterface.getSearchEngine().getQueries(queryFileName);
    }

    public void resetQueries() {
        if (queries.size() > 0)
            queries.clear();
        /*
        if (nonTranslatedQueries.size() > 0)
            nonTranslatedQueries.clear();
         */
    }

    public Map<String,String> getQueries() { return queries; }

    /**
     * Writes a separate query file for each task, containing the queries for the requests
     * belonging to that task. So they can be executed against the task-level index.
     */
    public void writeQueryFiles() {
        for (Task t : tasks.getTaskList()) {
            String queryFileName = Pathnames.queryFileLocation + key + ".TASK." + t.taskNum + ".queries.json";
            logger.info("Writing query file " + queryFileName);
            writeQueryFile(t.taskNum, queryFileName);
        }
    }

    public static String getTaskIDFromRequestID(String requestID) {
        String thisTaskID;
        if (Pathnames.analyticTasksFileFormat.equals("FARSI")) {
            thisTaskID = requestID;
        } else {
//            thisTaskID = requestID.substring(0, 5);
            int x = requestID.lastIndexOf('-');
            thisTaskID = requestID.substring(0, x);
        }
        return thisTaskID;
    }

    /**
     * Writes a query file for all of the requests for this task.
     * @param taskID the task
     * @param outputFileName the name for the query file
     */
    private void writeQueryFile(String taskID, String outputFileName) {
        // Output the query list as a JSON file,
        // in the format the search engine's search program expects as input
        try {
            JSONArray qlist = new JSONArray();
            for (Map.Entry<String, String> entry : queries.entrySet()) {
                String requestID = entry.getKey();
                String thisTaskID = getTaskIDFromRequestID(requestID);
                if (thisTaskID.equals(taskID)) {
                    JSONObject qentry = new JSONObject();
                    qentry.put("number", entry.getKey());
                    qentry.put("text", entry.getValue());
                    qlist.add(qentry);
                }
            }
            JSONObject outputQueries = new JSONObject();
            outputQueries.put("queries", qlist);
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(outputFileName)));
            writer.write(outputQueries.toJSONString());
            writer.close();
        } catch (Exception cause) {
            throw new TasksRunnerException(cause);
        }
    }

    public long getRunTime() {
        return runTime;
    }

    public List<String> getAllDocids(String requestID) {
        if (run.requestRuns.containsKey(requestID)) {
            return run.requestRuns.get(requestID).docids;
        } else {
            return new ArrayList<String>();
        }
    }

    public List<String> getDocids(String requestID, int size) {
        if (run.requestRuns.containsKey(requestID)) {
            List<String> docids = run.requestRuns.get(requestID).docids;
            if (size < docids.size()) {
                return docids.subList(0, size);
            } else {
                return docids;
            }
        } else {
            return new ArrayList<String>();
        }
    }

    public String getScore(String requestID, String docid) {
        List<ScoredHit> scoredHits = run.requestRuns.get(requestID).scoredHits;
        for (ScoredHit h : scoredHits) {
            if (h.docid.equals(docid)) {
                return h.score;
            }
        }
        return "";
    }

    /**
     * Within the solution are multiple Tasks, and each Task has multiple Requests.
     * This class represents the results of running the query for a Request.
     */
    public class RequestRun {
        public String requestID;
        public List<String> docids;
        public List<ScoredHit> scoredHits;
        public List<MissingDoc> missingDocs;

        RequestRun(String requestID, List<String> docids, List<ScoredHit> scoredHits) {
//            logger.info("Adding a RequestRun for Request " + requestID);
//            logger.info(docids.size() + " docids");
            this.requestID = requestID;
            this.docids = docids;
            this.missingDocs = null;
            this.scoredHits = scoredHits;
        }
    }

    /**
     * Represents the results of a particular query formulation's execution.
     */
    public class Run {
        public Map<String, RequestRun> requestRuns = new HashMap<String, RequestRun>();
        Run(String theRunFileName) {
            readFile(theRunFileName);
        }
        /**
         * Reads in the file that was output from the search engine's search function,
         * which is the top x scoredHits for each of the Requests in the input file.
         */
        Run() {
            readFile(runFileName);
        }

        /**
         * Reads in the file that was output from the search engine's search function,
         * which is the top x scoredHits for each of the Requests in the input file.
         */
        private void readFile(String theRunFileName) {
            File f = new File(theRunFileName);
            if (f.exists()) {
                logger.info("Opening run file " + theRunFileName);
                List<String> docids = new ArrayList<>();
                List<ScoredHit> scoredHits = new ArrayList<>();
                try (BufferedReader br = new BufferedReader(new FileReader(theRunFileName))) {
                    String line;
                    String prevQueryID = "NONE";
                    int docidsAdded = 0;
                    while ((line = br.readLine()) != null) {
                        String queryID = line.split("[ \t]+")[0];
                        if (!prevQueryID.equals(queryID)) {
                            if (!prevQueryID.equals("NONE")) {
                                // Clone the lists
                                List<String> cloned_docid_list
                                        = new ArrayList<String>(docids);
                                List<ScoredHit> cloned_Scored_hit_list
                                        = new ArrayList<ScoredHit>(scoredHits);
                                RequestRun r = new RequestRun(prevQueryID, cloned_docid_list, cloned_Scored_hit_list);
                                requestRuns.put(prevQueryID, r);
                                docids.clear();
                                scoredHits.clear();
                                docidsAdded = 0;
                            }
                        }
                        prevQueryID = queryID;
                        if (docidsAdded < MAX_DOCIDS) {
                            String[] tokens = line.split("[ \t]+");
                            String docid = tokens[2];
                            String score = tokens[4];
                            docids.add(docid);
                            ScoredHit h = new ScoredHit(docid, score);
                            scoredHits.add(h);
                            ++docidsAdded;
                        }
                    }
                    if (!prevQueryID.equalsIgnoreCase("")) {
                        // Clone the lists
                        List<String> cloned_docid_list
                                = new ArrayList<String>(docids);
                        List<ScoredHit> cloned_Scored_hit_list
                                = new ArrayList<ScoredHit>(scoredHits);
                        RequestRun r = new RequestRun(prevQueryID, cloned_docid_list, cloned_Scored_hit_list);
                        requestRuns.put(prevQueryID, r);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                logger.info("Run file requested does not exist: " + theRunFileName);
            }
        }
    }

    public class MissingDoc {
        public String taskID;
        public String relevanceStrength;
        public String docid;
        public String doctext;
        MissingDoc(String taskID, String relevanceStrength, String docid, String doctext) {
            this.taskID = taskID;
            this.relevanceStrength = relevanceStrength;
            this.docid = docid;
            this.doctext = doctext;
        }
        public String getDocid() {
            return docid;
        }

        public void setDocid(String docid) {
            this.docid = docid;
        }

    }

    /**
     * Creates a single CLEAR.out file that contains all of the runfiles from the
     * executions of the requests, which were done one-file-per-task.
     * @param runFiles the names of the runfiles to be merged
     */
    private void mergeRunFiles(List<String> runFiles) {
        String combinedRunFileName = Pathnames.runFileLocation + key + ".out";
        logger.info("Combined run file name is " + combinedRunFileName);
        Path outFile= Paths.get(combinedRunFileName);
        try {
            Files.delete(outFile);
        } catch (IOException ignore) {
            // do nothing
        }
        Charset charset = StandardCharsets.UTF_8;
        try {
            for (String runFile : runFiles) {
                logger.info("Adding task-level run file " + runFile);
                Path inFile=Paths.get(runFile);
                List<String> lines = Files.readAllLines(inFile, charset);
                logger.info(lines.size() + " lines");
                if (lines.size() == 0) {
                    throw new TasksRunnerException("Task-level runfile " + runFile + " is empty! Task-level query bad?");
                }
                Files.write(outFile, lines, charset, StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            }
        } catch (IOException e) {
            throw new TasksRunnerException(e);
        }
        // Refresh the in-memory runfile data
        run = new Run(combinedRunFileName);
    }

    /**
     * For each task, executes the task's request queryfile, getting 1000 scoredHits, producing a task-level
     * runfile, using the task's task-level index built previously from the task's top scoredHits.
     * Also, it creates an event extractor input file for each task, while the scoredHits
     * are in memory.
     */
    public void executeRequestQueries(int numResults) {
        // When the query formulation name is a Docker image name, it might be qualified
        // with the owner's name and a "/", which confuses things when the formulation name
        // is used in a pathname, so change the "/" to a "-"
        //String finalTaskLevelFormulationName = taskLevelFormulationName.replace("/", "-");

        AtomicLong totalRunTime = new AtomicLong(0);
        List<String> runFiles = new CopyOnWriteArrayList<>();
        logger.info("Executing request queries for " + tasks.getTaskList().size());
        tasks.getTaskList().parallelStream().forEach(t ->  {
            String theRunFileName = Pathnames.runFileLocation + key + ".TASK." + t.taskNum + ".out";
            runFiles.add(theRunFileName);
            String queryFileName = Pathnames.queryFileLocation + key + ".TASK." + t.taskNum + ".queries.json";

            logger.info("Executing request queries for task " + t.taskNum);

            GalagoSearchEngine galagoSearchEngine = new GalagoSearchEngine();
            galagoSearchEngine.executeAgainstPartialIndex(1, numResults, queryFileName, theRunFileName,
                    t.taskNum, submissionId, language, getTaskLevelIndexName(t.taskNum));
            run = new Run(theRunFileName);  // Get new run file into memory

            totalRunTime.getAndAdd(runTime);
        });
        logger.info("Merging run files");

        mergeRunFiles(runFiles);

        runTime = totalRunTime.get();
    }
    /**
     * Builds a Galago index on the top scoredHits for each task.
     */
    public void buildTaskLevelIndexes() {
        GalagoSearchEngine galagoSearchEngine = new GalagoSearchEngine();
        tasks.getTaskList().parallelStream().forEach(t ->
                {
                    createTaskDocIDListFromHits(t.taskNum);
                    galagoSearchEngine.buildTaskPartialIndex(t.taskNum, submissionId,
                Pathnames.indexLocation + Pathnames.searchEngine + "/better-clear-ir-" + language,
                            Pathnames.taskCorpusFileLocation + key + "." + t.taskNum + ".DOC_LIST.txt",
                            getTaskLevelIndexName(t.taskNum), language,
                            Pathnames.taskCorpusFileLocation + key + "." + t.taskNum + ".conf");
                }
                );
    }
    /**
     * Asks the search engine to executes the default queryfile,
     * requesting N scoredHits, producing the default runfile, using the target index for the current language.
     * @param N the number of scoredHits to get
     */
    public void execute(int N) {
//        GalagoSearchEngine galagoIndex = new GalagoSearchEngine();
        SearchEngineInterface searchEngine = SearchEngineInterface.getSearchEngine();
        if (queries.size() == 1) {
            searchEngine.search(1, N, queryFileName, runFileName, submissionId, language);
        } else {
            searchEngine.search(3, N, queryFileName, runFileName, submissionId, language);
        }

        run = new Run(runFileName);  // Get new run file into memory
    }

    /**
     * Creates a file containing the docids from the top scoredHits
     * for this task, ready to be indexed by Galago.
     * @param taskID the task ID
     */
    private void createTaskDocIDListFromHits(String taskID) {
        List<String> docids = this.getAllDocids(taskID);
        if (docids.size() == 0) {
            throw new TasksRunnerException("No hits from Task " + taskID + " Task-level query! Query execution probably failed.");
        }
        String outputFile = Pathnames.taskCorpusFileLocation + key + "." + taskID + ".DOC_LIST.txt";
        try {
            PrintWriter writer = new PrintWriter(outputFile);
            for (String docid : docids) {
                writer.println(docid);
            }
            writer.close();
        } catch (Exception cause) {
            logger.log(Level.SEVERE, "Exception creating task-level doc list file", cause);
            throw new TasksRunnerException(cause);
        }
    }

    public Map<String, EvaluationStats> evaluateTaskLevel() {
        return new EvaluatorTaskLevel().evaluate();
    }

    /**
     * Evaluates a run file, request level.
     * @param phase RAW or RERANKED (added to the file name)
     * @return evaluation results
     */
    public Map<String, Double> evaluate(String phase) {
        evaluationRequestLevelFileName = Pathnames.evaluationFileLocation + key + "." + phase + ".REQUEST.csv";
        return new Evaluator().evaluate();
    }

    public class EvaluationStats {
        private String key;
        private int n;
        private Double recallAtN;
        private Double recallAt100;
        private Double precisionAt100;
        int maxRelevantDocRank;

        public Double getPrecisionAt100() {
            return precisionAt100;
        }

        public Double getRecallAt100() {
            return recallAt100;
        }

        public void setPrecisionAt100(Double precisionAt100) {
            this.precisionAt100 = precisionAt100;
        }

        public void setRecallAt100(Double recallAt100) {
            this.recallAt100 = recallAt100;
        }

        public int getMaxRelevantDocRank() {
            return maxRelevantDocRank;
        }

        public void setMaxRelevantDocRank(int maxRelevantDocRank) {
            this.maxRelevantDocRank = maxRelevantDocRank;
        }

        public String getKey() {
            return key;
        }

        public int getN() {
            return n;
        }

        public void setN(int n) {
            this.n = n;
        }

        public Double getRecallAtN() {
            return recallAtN;
        }

        public void setRecallAtN(Double recallAtN) {
            this.recallAtN = recallAtN;
        }

        EvaluationStats(String key, int n, Double recallAtN, int maxRelevantDocRank, Double recallAt100,
                        Double precisionAt100) {
            this.key = key;
            this.n = n;
            this.recallAtN = recallAtN;
            this.maxRelevantDocRank = maxRelevantDocRank;
            this.recallAt100 = recallAt100;
            this.precisionAt100 = precisionAt100;
        }
    }

    public class Evaluator {
        /**
         * Calculates the normalized discounted cumulative gain
         * for this request and this ranked set of docs.
         */

        private Map<String, Double> stats = new TreeMap<>();

        /** This version computes nDCG to the depth equal to the number
         * of known relevant documents ("R"), instead of a hard cutoff of 10
         * as in his original version.
         *
         * @param requestID the request ID
         * @param runDocids the list of docids
         * @return the calculated nDCG@R
         */
        private double calculatenDCG(String requestID, List<String> runDocids) {
            List<RelevanceJudgment> judgments = tasks.getPositiveRelevanceJudgments(requestID);
            int cutoff = judgments.size();
            /* Calculate the ideal discounted cumulative gain for this query */
            judgments.sort(Comparator.comparingInt(RelevanceJudgment::getRelevanceJudgmentValue)
                    .reversed());
            double iDCG = 0.0;
            int index = 1;
            for (RelevanceJudgment j : judgments) {
                if (index == 1) {
                    iDCG += j.getRelevanceJudgmentValueWithMapping();
                } else {
                    iDCG += (j.getRelevanceJudgmentValueWithMapping() / ((Math.log(index + 1) / Math.log(2))));
                }
                ++index;
            }
            /* Calculate discounted cumulative gain of the ranked scoredHits */
            double DCG = 0.0;
            index = 1;
            for (String docid : runDocids) {
                if (index == 1) {
                    DCG += tasks.getRelevanceJudgmentValueWithMapping(requestID, docid);
                } else {
                    DCG += (tasks.getRelevanceJudgmentValueWithMapping(requestID, docid)
                            / ((Math.log(index + 1) / Math.log(2))));
                }
                ++index;
                if (index > cutoff) {
                    break;
                }
            }
            /* Calculate the normalized discounted cumulative gain */
            return DCG / iDCG;
        }

        /**
         * Evaluates the query formulations and outputs a CSV file of evaluation results.
         * This version only calculates nDCG.
         * <p>
         * For averaging the evaluation results, we use the MICRO approach.
         *
         */
        public Map<String,Double> evaluate() {
            try {

                /* Create and open the output CSV file */
                FileWriter csvWriter = new FileWriter(evaluationRequestLevelFileName);
                /* Write the header line */
                csvWriter.append("Solution");
                csvWriter.append(",");
                csvWriter.append("Request");
                csvWriter.append(",");
                csvWriter.append("nDCG@R");
                csvWriter.append("\n");

                List<String> requestIDs = tasks.getRequestIDs();
                ListIterator<String> requestIDIterator = requestIDs.listIterator();

                /* macro averaging approach accumulators */
                double totalTasknCDG = 0.0;

                int totalRequests = 0;
                int totalTasks = 0;  // this will be calculated as we go through the requests
                String prevTaskID = "EMPTY";  // used to detect Task changes
                String taskID = "";  // used to detect Task changes
                int totalRequestsInTask = 0;  // this will be calculated as we go through the requests

                /* macro averaging approach task-level accumulators */
                double tasknCDG = 0.0;

                double totalnDCG = 0.0;

                /* Assumption: all requests for a task are contiguous as we iterate them */
                while (requestIDIterator.hasNext()) {
                    String requestID = requestIDIterator.next();
                    List<String> runDocids = getDocids(requestID, 1000);
                    /* If this solution did not provide a query for this request, skip it */
                    if (runDocids.size() == 0) {
                        continue;
                    }
                    /* If we have no relevance judgments for this request, skip it */
                    if (!tasks.hasRelevanceJudgments(requestID)) {
                        continue;
                    }

                    taskID = getTaskIDFromRequestID(requestID);
                    if (!taskID.equals(prevTaskID) && !prevTaskID.equals("EMPTY")) {
                        ++totalTasks;
                        totalTasknCDG += (tasknCDG / totalRequestsInTask);
                        tasknCDG = 0.0;
                        totalRequestsInTask = 0;
                    }
                    prevTaskID = taskID;

                    ++totalRequests;
                    ++totalRequestsInTask;

                    double nDCG = calculatenDCG(requestID, runDocids);

                    totalnDCG += nDCG;
                    /* Accumulators for macro averaging approach */
                    tasknCDG += nDCG;

                    csvWriter.append(key);
                    csvWriter.append(",");
                    csvWriter.append(requestID);
                    csvWriter.append(",");
                    csvWriter.append(String.format("%.4f", nDCG));
                    csvWriter.append("\n");

                    stats.put(requestID,nDCG);

                }
                /* Flush out that last task */
                if (!prevTaskID.equals("EMPTY")) {
                    ++totalTasks;
                    totalTasknCDG += (tasknCDG / totalRequestsInTask);
                }

                double microAvgnDCG = totalnDCG / totalRequests;

                csvWriter.append(key);
                csvWriter.append(",");
                csvWriter.append("TOTAL");
                csvWriter.append(",");
                csvWriter.append(String.format("%.4f", microAvgnDCG));
                csvWriter.append("\n");
                csvWriter.close();

                stats.put("TOTAL", microAvgnDCG);
            } catch (Exception e) {
                throw new TasksRunnerException(e.getMessage());
            }
            recordRequestLevelMissingDocs();

            return stats;
        }

        public void recordRequestLevelMissingDocs() {
            for (Map.Entry<String, RequestRun> entry : run.requestRuns.entrySet()) {
                RequestRun rr = entry.getValue();
                String requestID = entry.getKey();
                if (rr.missingDocs == null) {
                    rr.missingDocs = new ArrayList<>();
                }
                List<String> hits = getDocids(requestID, 20000);
                for (RelevanceJudgment rj : tasks.getPositiveRelevanceJudgmentObjects(requestID)) {
                    if (!hits.contains(rj.getDocid())) {
                        rr.missingDocs.add(new MissingDoc(requestID, rj.getRelevanceJudgmentAsString(), rj.getDocid(), Document.getDocumentWithMap(rj.getDocid())));
                    }
                }
            }
        }
    }

    public class EvaluatorTaskLevel {
        private Map<String, EvaluationStats> stats = new TreeMap<>();

        private double calculatenRecallAtN(String taskID, List<String> runDocids) {
            Set<String> judgments = tasks.getPositiveTaskLevelRelevanceJudgments(taskID);
            int count = 0;
            for (String j : judgments) {
                if (runDocids.contains(j)) {
                    ++count;
                }
            }
            return (double) count / judgments.size();
        }

        private double calculatenRecallAtN(String taskID, List<String> runDocids, int N) {
            Set<String> judgments = tasks.getPositiveTaskLevelRelevanceJudgments(taskID);
            List<String> subList = runDocids.subList(0,N);
            int count = 0;
            for (String j : judgments) {
                if (subList.contains(j)) {
                    ++count;
                }
            }
            return (double) count / judgments.size();
        }

        private double calculatenPrecisionAtN(String taskID, List<String> runDocids, int N) {
            Set<String> judgments = tasks.getPositiveTaskLevelRelevanceJudgments(taskID);
            List<String> subList = runDocids.subList(0,N);
            int count = 0;
            for (String j : subList) {
                if (judgments.contains(j)) {
                    ++count;
                }
            }
            return (double) count / subList.size();
        }

        private int getMaxRelevantDocRank(String taskID, List<String> runDocids) {
            Set<String> judgments = tasks.getPositiveTaskLevelRelevanceJudgments(taskID);
            int maxRank = 0;
            for (String j : judgments) {
                if (runDocids.contains(j)) {
                    int index = runDocids.indexOf(j);
                    if (index > maxRank) {
                        maxRank = index;
                    }
                }
            }
            if (maxRank == 0) {
                maxRank = runDocids.size();
            }
            return maxRank;
        }

        /**
         * Evaluates the query formulations and outputs a CSV file of evaluation results.
         * This version only calculates recallAtN.
         * <p>
         * For averaging the evaluation results, we use the MICRO approach.
         *
         * @return the Map of task ID to the stats for that task
         */
        public Map<String,EvaluationStats> evaluate() {
            try {
                /* Create and open the output CSV file */
                FileWriter csvWriter = new FileWriter(evaluationTaskLevelFileName);
                /* Write the header line */
                csvWriter.append("Solution");
                csvWriter.append(",");
                csvWriter.append("Request");
                csvWriter.append(",");
                csvWriter.append("recall@N");
                csvWriter.append(",");
                csvWriter.append("N");
                csvWriter.append(",");
                csvWriter.append("Max Relevant Doc Rank");
                csvWriter.append(",");
                csvWriter.append("recall@100");
                csvWriter.append(",");
                csvWriter.append("precision@100");
                csvWriter.append("\n");

                List<String> taskIDs = tasks.getTaskIDs();
                ListIterator<String> taskIDIterator = taskIDs.listIterator();

                int totalTasks = 0;
                double totalScore = 0.0;
                double totalRecallAt100 = 0.0;
                double totalPrecisionAt100 = 0.0;
                int totalN = 0;
                int maxMaxRelevantDocRank = 0;
                while (taskIDIterator.hasNext()) {
                    String taskID = taskIDIterator.next();
                    List<String> runDocids = getDocids(taskID, MAX_DOCIDS);
                    /* If this solution did not provide a query for this task, skip it */
                    if (runDocids.size() == 0) {
                        continue;
                    }
                    /* If we have no relevance judgments for this task, skip it */
                    if (tasks.getPositiveTaskLevelRelevanceJudgments(taskID).size() == 0) {
                        continue;
                    }

                    ++totalTasks;

                    double score = calculatenRecallAtN(taskID, runDocids);
                    int maxRelevantDocRank  = getMaxRelevantDocRank(taskID, runDocids);
                    double recallAt100 = calculatenRecallAtN(taskID, runDocids, 100);
                    double precisionAt100 = calculatenPrecisionAtN(taskID, runDocids, 100);

                    totalScore += score;
                    totalN += runDocids.size();
                    maxMaxRelevantDocRank = Integer.max(maxMaxRelevantDocRank, maxRelevantDocRank);
                    totalRecallAt100 += recallAt100;
                    totalPrecisionAt100 += precisionAt100;

                    csvWriter.append(key);
                    csvWriter.append(",");
                    csvWriter.append(taskID);
                    csvWriter.append(",");
                    csvWriter.append(String.format("%.4f", score));
                    csvWriter.append(",");
                    csvWriter.append(String.format("%d", runDocids.size()));
                    csvWriter.append(",");
                    csvWriter.append(String.format("%d", maxRelevantDocRank ));
                    csvWriter.append(",");
                    csvWriter.append(String.format("%.4f", recallAt100 ));
                    csvWriter.append(",");
                    csvWriter.append(String.format("%.4f", precisionAt100 ));
                    csvWriter.append("\n");

                    stats.put(taskID,new EvaluationStats(taskID, runDocids.size(), score, maxRelevantDocRank,
                            recallAt100, precisionAt100));

                }

                if (totalTasks > 0) {
                    double microAvg = totalScore / totalTasks;
                    int averageN = totalN / totalTasks;
                    double microAvgRecallAt100 = totalRecallAt100 / totalTasks;
                    double microAvgPrecisionAt100 = totalPrecisionAt100 / totalTasks;

                    csvWriter.append(key);
                    csvWriter.append(",");
                    csvWriter.append("TOTAL");
                    csvWriter.append(",");
                    csvWriter.append(String.format("%.4f", microAvg));
                    csvWriter.append(",");
                    csvWriter.append(String.format("%d", averageN));
                    csvWriter.append(",");
                    csvWriter.append(String.format("%d", maxMaxRelevantDocRank ));
                    csvWriter.append(",");
                    csvWriter.append(String.format("%.4f", microAvgRecallAt100));
                    csvWriter.append(",");
                    csvWriter.append(String.format("%.4f", microAvgPrecisionAt100));
                    csvWriter.append("\n");
                    csvWriter.close();

                    stats.put("TOTAL", new EvaluationStats("TOTAL", averageN, microAvg, maxMaxRelevantDocRank,
                            microAvgRecallAt100, microAvgPrecisionAt100));

                }
                recordMissingDocs();

            } catch (Exception e) {
                throw new TasksRunnerException(e.getMessage());
            }
            return stats;
        }

        public void recordMissingDocs() {
            for (Map.Entry<String, RequestRun> entry : run.requestRuns.entrySet()) {
                RequestRun rr = entry.getValue();
                String taskid = entry.getKey();
                if (rr.missingDocs == null) {
                    rr.missingDocs = new ArrayList<>();
                }
                List<String> hits = getDocids(taskid, 20000);
                for (RelevanceJudgment rj : tasks.getPositiveTaskLevelRelevanceJudgmentObjects(taskid)) {
                    if (!hits.contains(rj.docid)) {
                        rr.missingDocs.add(new MissingDoc(taskid, rj.getRelevanceJudgmentAsString(), rj.docid, Document.getDocumentWithMap(rj.docid)));
                    }
                }
            }
        }
    }

    /**
     * Writes the "entries" part of the event file for this request to the PrintWriter.
     * @param requestID the request ID
     * @param writer the PrintWriter
     */
    private void addEvents(String requestID, PrintWriter writer) {
        String fileName = Pathnames.eventExtractorFileLocation + submissionId + "."
            + requestID + ".REQUESTHITS.json.results.json";
        File f = new File(fileName);
        if (f.exists()) {
            logger.info("Reading top scoredHits event file " + fileName);
            int lineCounter = 0;
            try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
                String line;
                while ((line = br.readLine()) != null) {
                    ++lineCounter;
                    if (lineCounter <= 2) {  /* don't output the 1st two lines */
                        continue;
                    }
                    if (line.equals("}")) {
                        break; /* don't output the last line */
                    }
                    writer.println(line);
                }
            } catch (IOException e) {
                throw new TasksRunnerException(e);
            }
        }
    }

    /**
     * Standardize the scores in the runfiles, then merge the rescored runfiles by score.
     * @param filesToMerge the files to be rescored and merged
     */
    public void rescoreRunFilesMinMax(List<String> filesToMerge) {
        try {
            List<String> rescoredFiles = new ArrayList<>();
            for (String fileName : filesToMerge) {
                String rescoredFileName = fileName + ".rescoredMinMax";
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                        new FileOutputStream(rescoredFileName)));
                Run run = new QueryManager.Run(fileName);   // read the runfile into memory
                for (Map.Entry<String, RequestRun> entry: run.requestRuns.entrySet()) {
                    String requestNum = entry.getKey();
                    RequestRun requestRun = entry.getValue();
                    List<ScoredHit> scoredHits = requestRun.scoredHits;

                    double minValue = getMin(scoredHits);
                    double maxValue = getMax(scoredHits);
                    // normalize the scores
                    int rank = 1;
                    for (ScoredHit hit : scoredHits) {
                        writer.println(requestNum + " Q0 " + hit.docid + " " + rank++ + " "
                                + ((Double.parseDouble(hit.score) - minValue) / (maxValue - minValue)) + " CLEAR");
                        if (rank > 1000) {
                            break;
                        }
                    }
                }
                writer.close();
                rescoredFiles.add(rescoredFileName);
            }
            mergeRerankedRunFilesByScore(rescoredFiles);
        } catch (Exception e) {
            throw new TasksRunnerException(e);
        }
    }

    private double getMax(List<ScoredHit> scoredHits) {
        double max = Double.MIN_VALUE;
        for (ScoredHit scoredHit : scoredHits) {
            if (Double.parseDouble(scoredHit.score) > max) {
                max = Double.parseDouble(scoredHit.score);
            }
        }
        return max;
    }

    private double getMin(List<ScoredHit> scoredHits) {
        double min = Double.MAX_VALUE;
        for (ScoredHit scoredHit : scoredHits) {
            if (Double.parseDouble(scoredHit.score) < min) {
                min = Double.parseDouble(scoredHit.score);
            }
        }
        return min;
    }

    private double getMean(List<ScoredHit> scoredHits) {
        double total = 0.0;
        for (ScoredHit scoredHit : scoredHits) {
            total += Double.parseDouble(scoredHit.score);
        }
        return total / scoredHits.size();
    }

    private double getStandardDeviation(List<ScoredHit> scoredHits, double mean) {
        double stddev = 0.0;
        for (ScoredHit scoredHit : scoredHits) {
            stddev += Math.pow(Double.parseDouble(scoredHit.score) - mean, 2);
        }
        return Math.sqrt(stddev / scoredHits.size());
    }

    /**
     * Standardize the scores in the runfiles, then merge the rescored runfiles by score.
     * @param filesToMerge the files to be rescored and merged
     */
    public void rescoreRunFilesZScores(List<String> filesToMerge) {
        try {
            List<String> rescoredFiles = new ArrayList<>();
            for (String fileName : filesToMerge) {
                String rescoredFileName = fileName + ".rescored";
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                        new FileOutputStream(rescoredFileName)));
                Run run = new QueryManager.Run(fileName);   // read the runfile into memory
                for (Map.Entry<String, RequestRun> entry: run.requestRuns.entrySet()) {
                    String requestNum = entry.getKey();
                    RequestRun requestRun = entry.getValue();
                    List<ScoredHit> scoredHits = requestRun.scoredHits;

                    double mean = getMean(scoredHits);
                    double stddev = getStandardDeviation(scoredHits, mean);
                    // standardize the scores
                    int rank = 1;
                    for (ScoredHit hit : scoredHits) {
                        writer.println(requestNum + " Q0 " + hit.docid + " " + rank++ + " "
                                + ((Double.parseDouble(hit.score) - mean) / stddev) + " CLEAR");
                        if (rank > 1000) {
                            break;
                        }
                    }
                }
                writer.close();
                rescoredFiles.add(rescoredFileName);
            }
            mergeRerankedRunFilesByScore(rescoredFiles);
        } catch (Exception e) {
            throw new TasksRunnerException(e);
        }
    }

    private List<Run> readRunFiles(List<String> filesToMerge) {
        List<Run> runs = new ArrayList<>();
        for (String fileName : filesToMerge) {
            runs.add(new QueryManager.Run(fileName));
        }
        if (runs.size() == 0) {
            throw new TasksRunnerException("Must be >0 run files to merge");
        }
        return runs;
    }

    private class RrmHit {
        public String docid;
        public Double score;
        RrmHit(String docid, Double score) {
            this.docid = docid;
            this.score = score;
        }
        RrmHit(RrmHit other) {
            this.docid = other.docid;
            this.score = other.score;
        }
    }

    private Map<String, List<RrmHit>> reciprocalRankMerge(List<Run> runs) {
        Map<String, List<RrmHit>> requestHits = new HashMap<>();
        for (String requestNum : tasks.getRequestIDs()) {
            Map<String, Double> sums = new HashMap<>();
            for (Run run : runs) {
                if (run.requestRuns.containsKey(requestNum)) {
                    List<ScoredHit> hits = run.requestRuns.get(requestNum).scoredHits;
                    int rank = 1;
                    for (ScoredHit hit : hits) {
                        sums.merge(hit.docid, (1.0 / (60 + rank)), Double::sum);
                        ++rank;
                    }
                }
            }
            List<RrmHit> hitList = new ArrayList<>();
            for (Map.Entry<String, Double> entry : sums.entrySet()) {
                hitList.add(new RrmHit(entry.getKey(), entry.getValue()));
            }
            hitList.sort((lhs, rhs) -> {
                // -1 - less than, 1 - greater than, 0 - equal, inverted to get descending
                return lhs.score > rhs.score ? -1 : (lhs.score < rhs.score ? 1 : 0);
            });
            requestHits.put(requestNum, hitList);
        }
        return requestHits;
    }

    private Map<String, List<ScoredHit>> mergeRequestHits(List<Run> runs) {
        Map<String, List<ScoredHit>> requestHits = new HashMap<>();
        for (String requestNum : tasks.getRequestIDs()) {
            requestHits.put(requestNum, new ArrayList<>());
        }
        mergeHits(requestHits, runs);
        sortHits(requestHits);
        return requestHits;
    }

    /* Sort the ScoredHit lists in place, by score ascending */
    private void sortHits(Map<String, List<ScoredHit>> requestHits) {
        for (Map.Entry<String, List<ScoredHit>> entry : requestHits.entrySet()) {
            List<ScoredHit> hits = entry.getValue();
            hits.sort((lhs, rhs) -> {
                // -1 - less than, 1 - greater than, 0 - equal, inverted to get descending
                return Float.parseFloat(lhs.score) > Float.parseFloat(rhs.score) ? -1
                        : (Float.parseFloat(lhs.score) < Float.parseFloat(rhs.score)) ? 1 : 0;
            });
        }
    }

    private void mergeHits(Map<String, List<ScoredHit>> requestHits, List<Run> runs) {
        for (String requestNum : tasks.getRequestIDs()) {
            for (Run run : runs) {
                if (run.requestRuns.containsKey(requestNum)) {
                    requestHits.get(requestNum).addAll(run.requestRuns.get(requestNum).scoredHits);
                }
            }
        }
    }

    private void writeHitsToMergedRunFile(Map<String, List<ScoredHit>> requestHits, PrintWriter writer) {
        for (Map.Entry<String, List<ScoredHit>> entry : requestHits.entrySet()) {
            String requestNum = entry.getKey();
            List<ScoredHit> hits = entry.getValue();
            int rank = 1;
            for (ScoredHit hit : hits) {
                writer.println(requestNum + " Q0 " + hit.docid + " " + rank++ + " " + hit.score + " CLEAR");
                if (rank > 1000) {
                    break;
                }
            }
        }
        writer.close();
    }

    private void copySingleRunFileToMergedRunFile(String singleFile, String mergedFile) {
        try {
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(mergedFile)));
            BufferedReader reader = new BufferedReader(new FileReader(singleFile));
            String line = reader.readLine();
            while (line != null) {
                writer.println(line);
                line = reader.readLine();
            }
            reader.close();
            writer.close();
        } catch (Exception e) {
            throw new TasksRunnerException(e);
        }
    }

    public void mergeRerankedRunFilesByScore(List<String> filesToMerge) {
        try {
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(Pathnames.runFileLocation + submissionId + ".FINAL.out")));
            if (filesToMerge.size() == 1) {
                /* No merge is needed */
                copySingleRunFileToMergedRunFile(filesToMerge.get(0),
                        Pathnames.runFileLocation + submissionId + ".FINAL.out");
            } else {
                writeHitsToMergedRunFile(mergeRequestHits(readRunFiles(filesToMerge)), writer);
            }
            } catch (Exception e) {
                throw new TasksRunnerException(e);
            }
        }

    private void writeRrmHitsToMergedRunFile(Map<String, List<RrmHit>> requestHits, PrintWriter writer) {
        for (Map.Entry<String, List<RrmHit>> entry : requestHits.entrySet()) {
            String requestNum = entry.getKey();
            List<RrmHit> hits = entry.getValue();
            int rank = 1;
            for (RrmHit hit : hits) {
                writer.println(requestNum + " Q0 " + hit.docid + " " + rank++ + " " + (2000 - rank) + " CLEAR");
                if (rank > 1000) {
                    break;
                }
            }
        }
        writer.close();
    }

    public void reciprocalRankMergeRerankedRunFiles(List<String> filesToMerge, String outFile) {
        try {
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(outFile)));
            if (filesToMerge.size() == 1) {
                /* No merge is needed */
                copySingleRunFileToMergedRunFile(filesToMerge.get(0),
                        Pathnames.runFileLocation + submissionId + ".FINAL.out");
            } else {
                writeRrmHitsToMergedRunFile(reciprocalRankMerge(readRunFiles(filesToMerge)), writer);
            }
        } catch (Exception e) {
            throw new TasksRunnerException(e);
        }
    }

}
