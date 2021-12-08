package edu.umass.ciir;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Functions to interact with ISI's basic event extractor.
 */
public class EventExtractor {
    private static final Logger logger = Logger.getLogger("TasksRunner");
    private AnalyticTasks tasks;
    private String mode;

    public EventExtractor(AnalyticTasks tasks, String mode) {
        this.tasks = tasks;
        this.mode = mode;
    }

    public class SimpleHit {
        String taskID;
        String taskTitle;
        String taskStmt;
        String taskNarr;
        String reqNum;
        String reqText;
        String docid;
        String docText;
        String score;
        List<SentenceRange> sentences;
        String query;

        SimpleHit(String docid, String docText, String score, String query, String taskID, String taskTitle, String taskNarr,
                  String taskStmt, String reqNum, String reqText) {
            this.docid = docid;
            this.docText = docText;
            this.score = score;
            this.taskID = taskID;
            this.taskTitle = taskTitle;
            this.taskStmt = taskStmt;
            this.taskNarr = taskNarr;
            this.reqNum = reqNum;
            this.reqText = reqText;
        }

        SimpleHit(String docid, String docText, String score, String query) {
            this.docid = docid;
            this.docText = docText;
            this.score = score;
            this.query = query;
        }

        SimpleHit(String docid, String docText, String score) {
            this.docid = docid;
            this.docText = docText;
            this.score = score;
        }

        SimpleHit(String docid, String docText, String score, List<SentenceRange> sentences) {
            this.docid = docid;
            this.docText = docText;
            this.score = score;
            this.sentences = new ArrayList<>(sentences);
        }

        public void setSentences(List<SentenceRange> sentences) {
            this.sentences = new ArrayList<>(sentences);
        }
    }

    /**
     * Creates a JSON file containing the entries, in the format that the
     * Mitre evaluation expects.
     * @param entries a map of key to text-to-be-processed-for-events
     * @return the total number of characters in the text fields
     */
    public int writeInputFileMitreFormat(Map<String,SimpleHit> entries, String fileForExtractor)  {
        int tot_chars = 0;
        try {
            JSONObject outermostEntry = new JSONObject();
            outermostEntry.put("corpus-id", "Basic English, V1.8, Provided Analysis Ref (Obfuscated)");
            outermostEntry.put("format-type", "bp-corpus");
            outermostEntry.put("format-version", "v10");

            JSONObject docEntries = new JSONObject();
            for (Map.Entry<String, SimpleHit> entry : entries.entrySet()) {
                String key = entry.getKey();
                SimpleHit hit = entry.getValue();
                String text = hit.docText;
                tot_chars += text.length();
                JSONObject docEntry = new JSONObject();

                JSONObject events = new JSONObject();
                JSONObject spansets = new JSONObject();
                JSONObject basicEvents = new JSONObject();
                basicEvents.put("events", events);
                basicEvents.put("span-sets", spansets);
                JSONObject annotationSets = new JSONObject();
                annotationSets.put("basic-events", basicEvents);
                docEntry.put("annotation-sets", annotationSets);
                docEntry.put("doc-id", key);
                docEntry.put("entry-id", key);
                JSONArray segmentSections = new JSONArray();
                List<SentenceRange> sentences = hit.sentences;
                for (SentenceRange sentence : sentences) {
                    JSONObject segmentSection = new JSONObject();
                    segmentSection.put("start", sentence.start);
                    segmentSection.put("end", sentence.end);
                    segmentSection.put("structural-element", "Sentence");
                    segmentSections.add(segmentSection);
                }
                docEntry.put("segment-sections", segmentSections);
                docEntry.put("segment-text", text);
                docEntry.put("segment-type", "document");
                docEntries.put(key, docEntry);
            }
            outermostEntry.put("entries", docEntries);

            Path pathToFile = Paths.get(fileForExtractor); // make sure all dirs in path exist
            Files.createDirectories(pathToFile.getParent());
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(fileForExtractor)));
            writer.write(outermostEntry.toJSONString());
            writer.close();
        } catch (Exception cause) {
            throw new TasksRunnerException(cause);
        }
        return tot_chars;
    }

    public int writeInputFileSimpleFormat(Map<String,SimpleHit> entries, String fileForExtractor)  {
        int tot_chars = 0;
        try {
            List<String> exampleDocs = tasks.getAllExampleDocIDs();
            JSONObject outermostEntry = new JSONObject();

            JSONArray docEntries = new JSONArray();
            for (Map.Entry<String, SimpleHit> entry : entries.entrySet()) {
                String key = entry.getKey();
                if (exampleDocs.contains(key))
                    continue;
                SimpleHit hit = entry.getValue();
                tot_chars += hit.docText.length();
                JSONObject docEntry = new JSONObject();

                docEntry.put("task-num", hit.taskID);
                docEntry.put("task-title", hit.taskTitle);
                docEntry.put("task-narr", hit.taskNarr);
                docEntry.put("task-stmt", hit.taskStmt);
                docEntry.put("req-num", hit.reqNum);
                docEntry.put("req-text", hit.reqText);

                docEntry.put("doc-id", key);
                docEntry.put("task-type", "umass");
                docEntry.put("doc-text", hit.docText);
                docEntry.put("doc-score", hit.score);
                docEntry.put("doc-query", hit.query);
                docEntries.add(docEntry);
            }
            outermostEntry.put("hits", docEntries);

            PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(fileForExtractor)));
            writer.write(outermostEntry.toJSONString());
            writer.close();
        } catch (Exception cause) {
            throw new TasksRunnerException(cause);
        }
        return tot_chars;
    }

    /**
     * Writes out a file for each request, in the same format that batch-search-with-reranker uses to
     * call a reranker
     * @param entries the hits
     * @param outputFileName the output file path
     * @param query the text of the query
     * @param queryNumber the request number
     * @param sysName a name for Galago to use as "system name" in its output run files
     */
    public void writeInputFileRerankerFormat(Map<String,SimpleHit> entries, String outputFileName, String query,
                                             String queryNumber, String sysName)  {
        try {
            JSONObject outermostEntry = new JSONObject();
            JSONObject resultsEntry = new JSONObject();

            JSONArray docEntries = new JSONArray();
            for (Map.Entry<String, SimpleHit> entry : entries.entrySet()) {
                String key = entry.getKey();
                SimpleHit hit = entry.getValue();
                JSONObject docEntry = new JSONObject();

                docEntry.put("docid", key);
                docEntry.put("doctext", hit.docText);
                docEntry.put("score", hit.score);
                docEntries.add(docEntry);
            }
            outermostEntry.put("results", docEntries);
            outermostEntry.put("query", query);
            outermostEntry.put("queryNumber", queryNumber);
            outermostEntry.put("sysName", sysName);
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(outputFileName)));
            writer.write(outermostEntry.toJSONString());
            writer.close();
        } catch (Exception cause) {
            throw new TasksRunnerException(cause);
        }
    }

    public int writeInputFileSimpleFormatTask(Map<String,SimpleHit> entries, String fileForExtractor)  {
        int tot_chars = 0;
        try {
            JSONObject outermostEntry = new JSONObject();

            JSONArray docEntries = new JSONArray();
            for (Map.Entry<String, SimpleHit> entry : entries.entrySet()) {
                String key = entry.getKey();
                SimpleHit hit = entry.getValue();
                tot_chars += hit.docText.length();
                JSONObject docEntry = new JSONObject();

                docEntry.put("task-num", hit.taskID);
/*
                docEntry.put("task-title", hit.taskTitle);
                docEntry.put("task-narr", hit.taskNarr);
                docEntry.put("req-num", hit.reqNum);
                docEntry.put("req-text", hit.reqText);
*/

                docEntry.put("doc-id", key);
//                docEntry.put("task-type", "umass");
                docEntry.put("doc-text", hit.docText);
                docEntry.put("doc-score", hit.score);
//                docEntry.put("doc-query", hit.query);
                docEntries.add(docEntry);
            }
            outermostEntry.put("hits", docEntries);

            PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(fileForExtractor)));
            writer.write(outermostEntry.toJSONString());
            writer.close();
        } catch (Exception cause) {
            throw new TasksRunnerException(cause);
        }
        return tot_chars;
    }

    private void createInputFileEntriesFromHits(String docSetType, String taskOrRequestID,
                                                List<String> hits, Map<String,SimpleHit> m) {
        for (String td : hits) {
            List<SentenceRange> sentences = Document.getArabicDocumentSentences(td);
            String docText = Document.getArabicDocumentWithMap(td);
            SimpleHit hit = new SimpleHit(td, docText, "", sentences);
            m.put(docSetType + "--" + taskOrRequestID + "--" + td, hit);
        }
    }

    /**
     * Number of hits to have full event details.
     */
    private final int TASK_HITS_DETAILED = 1000;

    /**
     * Creates an input file to give to the event extractor, of the top hits for each task.
     */
    public void createInputForEventExtractorFromTaskHits(QueryManager qf) {
        Map<String, SimpleHit> simpleEntries = new LinkedHashMap<>();
        Map<String, String> entries = new LinkedHashMap<>();
        List<Task> taskList = tasks.getTaskList();

        // Load the document text map in one pass through the corpus file:
        Document.buildArabicDocMap(tasks.getTaskList().parallelStream()
                .flatMap(t -> qf.getDocids(t.taskNum, TASK_HITS_DETAILED).stream())
                .collect(Collectors.toSet()));
        /* First, the simple version we used during development, to give to ISI */
        /**
         Map<String,String> queries = qf.getQueries();
         for (Task task : taskList) {
         List<String> hits = qf.getDocids(task.taskNum, 500);
         simpleEntries.clear();
         for (String td : hits) {
         String score = qf.getScore(task.taskNum, td);
         String docid = "TaskLevelHit--" + task.taskNum + "--" + td;
         String docText = Document.getArabicDocumentWithMap(td);
         String query = queries.get(task.taskNum);
         simpleEntries.put(docid, new SimpleHit(docid, docText, score, query,
         task.taskNum, task.taskTitle, task.taskNarr, null, null));
         }
         if (simpleEntries.size() > 0) {
         String fileForEventExtractor = constructTaskLevelSimpleFileName(task);
         writeInputFileSimpleFormatTask(simpleEntries, fileForEventExtractor);
         }
         }
         **/

        /* Create the file to give to the ISI event extractor */
        for (Task task : taskList) {
            List<String> hits = qf.getDocids(task.taskNum, TASK_HITS_DETAILED);
            simpleEntries.clear();
            createInputFileEntriesFromHits("TaskLevelHit", task.taskNum, hits, simpleEntries);
            if (simpleEntries.size() > 0) {
                String fileForEventExtractor = constructTaskLevelFileFromEventExtractorFileName(task);
                writeInputFileMitreFormat(simpleEntries, fileForEventExtractor);
            }
        }
    }

    public void retrieveEventsFromTaskHits(QueryManager qf) {
        /* Get the document texts and sentences for each hit mentioned in the event files */
        /* (which is a side effect of loading the document text map with those docs) */
        Document.buildArabicDocMap(tasks.getTaskList().parallelStream()
                .flatMap(t -> readEventFile(constructTaskLevelFileFromEventExtractorFileName(t), -1).stream())
                .map(hit -> hit.docid)
                .collect(Collectors.toSet()));

        // To be able to get the hits we need the qf to open the runfile
        String theRunFileName = Pathnames.runFileLocation + qf.getKey() + ".out";
        qf.setRun(theRunFileName);

        for (Task t : tasks.getTaskList()) {
            List<Hit> hits = readEventFile(constructTaskLevelFileFromEventExtractorFileName(t), -1);
            /* Augment the hits for this task with the info from the events file */
            List<Hit> mergedHits = mergeHits(t.taskNum, hits, qf.getDocids(t.taskNum, 1000));

            /* Write out a file that has everything about the task hits that is needed by the final re-ranker */
            String taskHitsEventFileName = constructTaskLevelEventFileName(t);
            writeEventsAsJson(mergedHits, "TASKHITS", taskHitsEventFileName);
        }
    }

    private String constructTaskLevelFileFromEventExtractorFileName(Task t) {
        return Pathnames.eventExtractorFileLocation + mode + "." + t.taskNum + ".TASKHITS.json.results.json";
    }
    private String constructTaskLevelToFromEventExtractorFileName(Task t) {
        return Pathnames.eventExtractorFileLocation + mode + "." + t.taskNum + ".TASKHITS.json";
    }
    private String constructTaskLevelEventFileName(Task t) {
        return Pathnames.eventExtractorFileLocation + mode + "." + t.taskNum + ".TASKHITS.events.json";
    }
    private String constructTaskLevelSimpleFileName(Task t) {
        return Pathnames.eventExtractorFileLocation + mode + "." + t.taskNum + ".TASKHITS.SIMPLE.json";
    }

    private String constructRequestLevelFileFromEventExtractorFileName(Request r) {
        return Pathnames.eventExtractorFileLocation + mode + "." + r.reqNum + ".REQUESTHITS.json.results.json";
    }
    private String constructRequestLevelToEventExtractorFileName(Request r) {
        return Pathnames.eventExtractorFileLocation + mode + "." + r.reqNum + ".REQUESTHITS.json";
    }
    private String constructRequestLevelEventFileName(Request r) {
        return Pathnames.eventExtractorFileLocation + mode + "." + r.reqNum + ".REQUESTHITS.events.json";
    }
    private String constructRequestLevelSimpleFileName(Request r) {
        return Pathnames.eventExtractorFileLocation + mode + "." + r.reqNum + ".REQUESTHITS.SIMPLE.json";
    }
    private String constructRequestLevelRerankerFileName(Request r) {
        return Pathnames.eventExtractorFileLocation + mode + "." + r.reqNum + ".REQUESTHITS.FOR_RERANKER.json";
    }

    private String constructExampleFileFromEventExtractorFileName() {
        return Pathnames.eventExtractorFileLocation + mode + ".EXAMPLES.json.results.json";
    }
    private String constructExampleToEventExtractorFileName() {
        return Pathnames.eventExtractorFileLocation + mode + ".EXAMPLES.json";
    }

    /**
     * Creates an input file to give to the event extractor, of the top hits for each request.
     */
    public void createInputForEventExtractorFromRequestHits(QueryManager qf) {
        Map<String, SimpleHit> simpleEntries = new LinkedHashMap<>();
        Map<String, String> entries = new LinkedHashMap<>();
        List<Request> requestList = tasks.getRequests();

        // Load the document text map in one pass through the corpus file:
        Document.buildArabicDocMap(requestList.parallelStream()
                .flatMap(r -> qf.getDocids(r.reqNum, Pathnames.REQUEST_HITS_DETAILED).stream())
                .collect(Collectors.toSet()));

        /* First, build the file to give to the HITL person */
        Map<String,String> queries = qf.getQueries();
        for (Task t : tasks.getTaskList()) {
            for (Request r : t.getRequests().values()) {
                if (r.reqText == null || r.reqText.equals("")) {
                    continue;     // only include the 2 requests with extra HITL info
                }
                List<String> hits = qf.getDocids(r.reqNum, 10);
                simpleEntries.clear();
                for (String td : hits) {
                    String score = qf.getScore(r.reqNum, td);
                    String docid = "RequestLevelHit--" + r.reqNum + "--" + td;
                    String docText = Document.getArabicDocumentWithMap(td);
                    String query = queries.get(r.reqNum);
                    simpleEntries.put(docid, new SimpleHit(docid, docText, score, query,
                            t.taskNum, t.taskTitle, t.taskNarr, t.taskStmt, r.reqNum, r.reqText));
                }
                if (simpleEntries.size() > 0) {
                    String fileForEventExtractor = constructRequestLevelSimpleFileName(r);
                    writeInputFileSimpleFormat(simpleEntries, fileForEventExtractor);
                    logger.info("Simple entries generated this day for " + r.reqNum);
                }
            }
        }

        /* Create the file to give to the ISI event extractor */
        for (Request r : requestList) {
            List<String> hits = qf.getDocids(r.reqNum, Pathnames.REQUEST_HITS_DETAILED);
            simpleEntries.clear();
            createInputFileEntriesFromHits("RequestLevelHit", r.reqNum, hits, simpleEntries);
            if (simpleEntries.size() > 0) {
                String fileForEventExtractor = constructRequestLevelToEventExtractorFileName(r);
                writeInputFileMitreFormat(simpleEntries, fileForEventExtractor);
            }
        }
    }

    /**
     * Creates an input file to give to the event extractor, of the top hits for each request.
     */
    public void createInputForRerankerFromRequestHits(QueryManager qf) {
        Map<String, SimpleHit> simpleEntries = new LinkedHashMap<>();
        List<Request> requestList = tasks.getRequests();

        // Load the document text map in one pass through the corpus file:
        Document.buildArabicDocMap(requestList.parallelStream()
                .flatMap(r -> qf.getDocids(r.reqNum, Pathnames.REQUEST_HITS_DETAILED).stream())
                .collect(Collectors.toSet()));

        Map<String,String> queries = qf.getQueries();
        for (Task t : tasks.getTaskList()) {
            for (Request r : t.getRequests().values()) {
                if (r.reqText.length() == 0) {
                    continue;     // only include the 2 requests with extra HITL info
                }
                List<String> hits = qf.getDocids(r.reqNum, Pathnames.REQUEST_HITS_DETAILED);
                simpleEntries.clear();
                String query = queries.get(r.reqNum);
                for (String td : hits) {
                    String score = qf.getScore(r.reqNum, td);
                    String docText = Document.getArabicDocumentWithMap(td);
                    simpleEntries.put(td, new SimpleHit(td, docText, score, ""));
                }
                if (simpleEntries.size() > 0) {
                    String fileName = constructRequestLevelRerankerFileName(r);
                    writeInputFileRerankerFormat(simpleEntries, fileName, query, r.reqNum, "CLEAR");
                }
            }
        }

    }

    private List<Hit> mergeHits (String reqNum, List<Hit> hits, List<String> docids) {
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
                Hit hit = new Hit(reqNum, docid, "", new ArrayList<Event>());
                finalList.add(hit);
            }
        }
        return finalList;
    }

    public void retrieveEventsFromRequestHits(QueryManager qf) {
        // Load the document text map in one pass through the corpus file:
        Document.buildArabicDocMap(tasks.getTaskList().parallelStream()
                .flatMap(t -> t.getRequests().values().stream())
                .flatMap(r -> readEventFile(constructRequestLevelFileFromEventExtractorFileName(r), -1).stream())
                .map(hit -> hit.docid)
                .collect(Collectors.toSet()));

        String theRunFileName = Pathnames.runFileLocation + qf.getKey() + ".out";
        qf.setRun(theRunFileName);

        for (Task t : tasks.getTaskList()) {
            for (Request r : t.getRequests().values()) {
                String fileFromEventExtractor = constructRequestLevelFileFromEventExtractorFileName(r);
                String requestHitsEventFileName = constructRequestLevelEventFileName(r);
                List<Hit> hits = readEventFile(fileFromEventExtractor, -1);
                List<Hit> mergedHits = mergeHits(r.reqNum, hits, qf.getDocids(r.reqNum, 1000));
                writeEventsAsJson(mergedHits, "REQUESTHITS", requestHitsEventFileName);
            }
        }
    }

    private void createInputFileEntriesFromExampleDocs(Task t, Map<String,SimpleHit> m) {
        for (ExampleDocument d : t.taskExampleDocs) {
            SimpleHit hit = new SimpleHit(d.getDocid(), d.getDocText(), "", d.getSentences());
            m.put("TaskExampleDoc" + "--" + t.taskNum + "--" + d.getDocid(), hit);
        }
        for (Request r : t.getRequests().values()) {
            for (ExampleDocument d : r.reqExampleDocs) {
                SimpleHit hit = new SimpleHit(d.getDocid(), d.getDocText(), "", d.getSentences());
                m.put("TaskExampleDoc" + "--" + t.taskNum + "--" + d.getDocid(), hit);
                m.put("RequestExampleDoc" + "--" + r.reqNum + "--" + d.getDocid(), hit);
            }
        }
    }

    public void annotateExampleDocEvents() {
        try {
            String logFile = Pathnames.logFileLocation + Pathnames.mode + "/annotate_example_docs.log";
            String tempCommand = "cd /home/tasksrunner/scripts && "
                    + " sudo"
                    + " MODELS_BASE_DIR_ENGLISH=" + Pathnames.MODELS_BASE_DIR_ENGLISH
                    + " MODELS_BASE_DIR_FARSI=" + Pathnames.MODELS_BASE_DIR_FARSI
                    + " APP_DIR=" + Pathnames.appFileLocation
                    + " MODE=" + Pathnames.mode
                    + " SCRATCH_DIR=" + Pathnames.scratchFileLocation
                    + " EVENT_EXTRACTOR_FILES_DIRECTORY=" + Pathnames.eventExtractorFileLocation
                    + " CORPUS_DIR=" + Pathnames.corpusFileLocation
                    + " ./annotate_example_docs.sh"
                    + " >& " + logFile;
            logger.info("Executing this command: " + tempCommand);

            try {
                Files.delete(Paths.get(logFile));
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
                logger.log(Level.SEVERE, "Exception doing annotate_example_docs execution", cause);
                throw new TasksRunnerException(cause);
            } finally {
                StringBuilder builder = new StringBuilder();
                try (Stream<String> stream = Files.lines( Paths.get(logFile), StandardCharsets.UTF_8))
                {
                    stream.forEach(s -> builder.append(s).append("\n"));
                    logger.info("annotate_example_docs output:\n" + builder.toString());
                } catch (IOException ignore) {
                    // logger.info("IO error trying to read output file. Ignoring it");
                }
            }
            if (exitVal != 0) {
                logger.log(Level.SEVERE, "Unexpected ERROR from annotate_example_docs, exit value is: " + exitVal);
                throw new TasksRunnerException("Unexpected ERROR from annotate_example_docs, exit value is: " + exitVal);
            }
        } catch (Exception e) {
            throw new TasksRunnerException(e);
        }
    }

    public void annotateProvidedFileEvents() {
        try {
            String script = "./annotate_provided_file.sh.FARSI";
            String trainingDirs = "MODELS_BASE_DIR_FARSI=" + Pathnames.MODELS_BASE_DIR_FARSI;
            if (Pathnames.targetLanguage.equals("ARABIC")) {
                script = "./annotate_provided_file.sh.ARABIC";
                trainingDirs = "MODELS_BASE_DIR_ARABIC=" + Pathnames.MODELS_BASE_DIR_ARABIC;
            }
            String logFile = Pathnames.logFileLocation + Pathnames.mode + "/annotate_provided_file.log";
            String tempCommand = "cd /home/tasksrunner/scripts && "
                    + " sudo"
                    + " MODELS_BASE_DIR_ENGLISH=" + Pathnames.MODELS_BASE_DIR_ENGLISH
                    + " MODELS_BASE_DIR_FARSI=" + Pathnames.MODELS_BASE_DIR_FARSI
                    + " APP_DIR=" + Pathnames.appFileLocation
                    + " MODE=" + Pathnames.mode
                    + " SCRATCH_DIR=" + Pathnames.scratchFileLocation
                    + " EVENT_EXTRACTOR_FILES_DIRECTORY=" + Pathnames.eventExtractorFileLocation
                    + " CORPUS_DIR=" + Pathnames.corpusFileLocation
                    + " " + script
                    + " >& " + logFile;
            logger.info("Executing this command: " + tempCommand);

            try {
                Files.delete(Paths.get(logFile));
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
                logger.log(Level.SEVERE, "Exception doing annotate_provided_file execution", cause);
                throw new TasksRunnerException(cause);
            } finally {
                StringBuilder builder = new StringBuilder();
                try (Stream<String> stream = Files.lines( Paths.get(logFile), StandardCharsets.UTF_8))
                {
                    stream.forEach(s -> builder.append(s).append("\n"));
                    logger.info("annotate_provided_file output:\n" + builder.toString());
                } catch (IOException ignore) {
                    // logger.info("IO error trying to read output file. Ignoring it");
                }
            }
            if (exitVal != 0) {
                logger.log(Level.SEVERE, "Unexpected ERROR from annotate_provided_file, exit value is: " + exitVal);
                throw new TasksRunnerException("Unexpected ERROR from annotate_provided_file, exit value is: " + exitVal);
            }
        } catch (Exception e) {
            throw new TasksRunnerException(e);
        }
    }

    public void annotateRequestDocEvents() {
        String script = "./annotate_request_docs.sh.FARSI";
        String trainingDirs = "MODELS_BASE_DIR_FARSI=" + Pathnames.MODELS_BASE_DIR_FARSI;
        if (Pathnames.targetLanguage.toString().equals("ARABIC")) {
            script = "./annotate_request_docs.sh.ARABIC";
            trainingDirs = "MODELS_BASE_DIR_ARABIC=" + Pathnames.MODELS_BASE_DIR_ARABIC;
        }
        try {
            String logFile = Pathnames.logFileLocation + Pathnames.mode + "/annotate_request_docs.log";
            String tempCommand = "cd /home/tasksrunner/scripts && "
                    + " sudo"
                    + " MODELS_BASE_DIR_ENGLISH=" + Pathnames.MODELS_BASE_DIR_ENGLISH
                    + " " + trainingDirs
                    + " MODE=" + Pathnames.mode
                    + " APP_DIR=" + Pathnames.appFileLocation
                    + " SCRATCH_DIR=" + Pathnames.scratchFileLocation
                    + " EVENT_EXTRACTOR_FILES_DIRECTORY=" + Pathnames.eventExtractorFileLocation
                    + " CORPUS_DIR=" + Pathnames.corpusFileLocation
                    + " " + script
                    + " >& " + logFile;
            logger.info("Executing this command: " + tempCommand);

            try {
                Files.delete(Paths.get(logFile));
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
                logger.log(Level.SEVERE, "Exception doing annotate_request_docs execution", cause);
                throw new TasksRunnerException(cause);
            } finally {
                StringBuilder builder = new StringBuilder();
                try (Stream<String> stream = Files.lines( Paths.get(logFile), StandardCharsets.UTF_8))
                {
                    stream.forEach(s -> builder.append(s).append("\n"));
                    logger.info("annotate_request_docs output:\n" + builder.toString());
                } catch (IOException ignore) {
                    // logger.info("IO error trying to read output file. Ignoring it");
                }
            }
            if (exitVal != 0) {
                logger.log(Level.SEVERE, "Unexpected ERROR from annotate_request_docs, exit value is: " + exitVal);
                throw new TasksRunnerException("Unexpected ERROR from annotate_request_docs, exit value is: " + exitVal);
            }
        } catch (Exception e) {
            throw new TasksRunnerException(e);
        }
    }

    public void preTrainEventAnnotator() {
        try {
            String logFile = Pathnames.logFileLocation + Pathnames.mode + "/pretrain.log";
            String tempCommand = "cd /home/tasksrunner/scripts && "
                    + " sudo"
                    + " MODELS_BASE_DIR_ENGLISH=" + Pathnames.MODELS_BASE_DIR_ENGLISH
                    + " MODELS_BASE_DIR_FARSI=" + Pathnames.MODELS_BASE_DIR_FARSI
                    + " APP_DIR=" + Pathnames.appFileLocation
                    + " SCRATCH_DIR=" + Pathnames.scratchFileLocation
                    + " CORPUS_DIR=" + Pathnames.corpusFileLocation
                    + " ./pretrain.sh"
                    + " " + Pathnames.preTrainSizeParm
                    + " >& " + logFile;
            logger.info("Executing this command: " + tempCommand);

            try {
                Files.delete(Paths.get(logFile));
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
                logger.log(Level.SEVERE, "Exception doing pretrain execution", cause);
                throw new TasksRunnerException(cause);
            } finally {
                StringBuilder builder = new StringBuilder();
                try (Stream<String> stream = Files.lines( Paths.get(logFile), StandardCharsets.UTF_8))
                {
                    stream.forEach(s -> builder.append(s).append("\n"));
                    logger.info("pretrain output log:\n" + builder.toString());
                } catch (IOException ignore) {
                    // logger.info("IO error trying to read output file. Ignoring it");
                }
            }
            if (exitVal != 0) {
                logger.log(Level.SEVERE, "Unexpected ERROR from pretrainer, exit value is: " + exitVal);
                throw new TasksRunnerException("Unexpected ERROR from pretrainer, exit value is: " + exitVal);
            }
        } catch (Exception e) {
            throw new TasksRunnerException(e);
        }
    }

    public void extractExampleEventsPart1() {
        String fileForEventExtractor = constructExampleToEventExtractorFileName();
        createInputForEventExtractorFromExampleDocs(fileForEventExtractor);
    }

    public void extractExampleEventsPart2()  {
        String fileFromEventExtractor = constructExampleFileFromEventExtractorFileName();

        List<Hit> hits = readEventFile(fileFromEventExtractor, -1);

        for (Hit hit : hits) {
            updateSentenceIDs(hit);
            updateTaskOrRequest(hit);
        }
    }

    public void getSentencesFromDocs() {
        JSONParser parser = new JSONParser();
        String corpus = Pathnames.corpusFileLocation + Pathnames.englishCorpusFileName;
        try (BufferedReader br = new BufferedReader(new FileReader(corpus))) {
            String line;
            while ((line = br.readLine()) != null) {
                JSONObject json;
                try {
                    json = (JSONObject) parser.parse(line);
                } catch (ParseException e) {
                    throw new TasksRunnerException(e);
                }
                String uuid;
                String text;
                List<SentenceRange> sentences = new ArrayList<>();
                if (json.containsKey("uuid")) {
                    // OLD STYLE
                    uuid = (String) json.get("uuid");
                    text = (String) json.get("text");
                    getSentenceRangesFromText(text, sentences);
                } else {
                    JSONObject derived_metadata = (JSONObject) json.get("derived-metadata");
                    uuid = (String) derived_metadata.get("id");
                    text = (String) derived_metadata.get("text");
                    if (derived_metadata.containsKey("segment-sections")) {
                        JSONArray segment_sections = (JSONArray) json.get("segment-sections");
                        int id = 0;
                        for (Object oSection : segment_sections) {
                            ++id;
                            JSONObject segment_section = (JSONObject) oSection;
                            int start = (int) segment_section.get("start");
                            int end = (int) segment_section.get("end");
                            String sentenceText = text.substring(start, end);
                            SentenceRange sentence = new SentenceRange(id, start, end, sentenceText);
                            sentences.add(sentence);
                        }
                    } else {
                        getSentenceRangesFromText(text, sentences);
                    }
                }
            }
        } catch (IOException e) {
            throw new TasksRunnerException(e);
        }
    }

    private void getSentenceRangesFromText(String text, List<SentenceRange> sentences) {
        List<String> spacySentences = callSpacy(text);
        int start = 0;
        int end = -1;
        int id = 0;
        for (String sentence : spacySentences) {
            ++id;
            if (id > 1) {
                start = text.indexOf(sentence, end);
            }
            end = start + sentence.length();
            String sentenceText = text.substring(start, end);
            sentences.add(new SentenceRange(id, start, end, sentenceText));
        }

        for (SentenceRange r : sentences) {
            String docText = text.substring(r.start, r.end);
            System.out.println(docText);
        }
    }

    private List<String> callSpacy(String s) {
        return Spacy.getSentences(s);
    }

    public void createInputForEventExtractorFromExampleDocs(String fileForEventExtractor) {
        Map<String, SimpleHit> entries = new HashMap<>();

        for (Task task : tasks.getTaskList()) {
            createInputFileEntriesFromExampleDocs(task, entries);
        }

        writeInputFileMitreFormat(entries, fileForEventExtractor);
    }

    private void writeEventsAsJson(List<Hit> hits, String type,
                                   String eventHumanReadableFile) {
        try {
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(eventHumanReadableFile)));
            JSONArray topLevel = new JSONArray();
            int idx = 0;
            for (Hit h : hits) {
                ++idx;
                JSONObject hit = new JSONObject();
                hit.put("groupId", h.taskID);
                hit.put("groupType", h.taskID.length() > 5 ? "R" : "T");
                hit.put("docid", h.docid);

                if (idx > Pathnames.REQUEST_HITS_DETAILED) {
                    hit.put("docText", "");
                    JSONArray segmentSections = new JSONArray();
                    hit.put("sentences", segmentSections);
                    JSONArray mitreEvents = new JSONArray();
                    hit.put("mitre-events", mitreEvents);
                    JSONArray eventsArray = new JSONArray();
                    hit.put("isi-events", eventsArray);
                } else {
                    hit.put("docText", h.docText);

                    JSONArray segmentSections = new JSONArray();
                    List<SentenceRange> sentences = Document.getArabicDocumentSentences(h.docid);
                    if (sentences == null) {
                        System.out.println("Should not happen");
                    } else {
                        for (SentenceRange sentence : sentences) {
                            JSONObject segmentSection = new JSONObject();
                            segmentSection.put("start", sentence.start);
                            segmentSection.put("end", sentence.end);
                            segmentSection.put("id", sentence.id);
                            segmentSection.put("text", sentence.text);
                            segmentSections.add(segmentSection);
                        }
                    }
                    hit.put("sentences", segmentSections);

                    JSONArray mitreEvents = new JSONArray();
                    hit.put("mitre-events", mitreEvents);

                    List<Event> events = h.events;
                    JSONArray eventsArray = Event.getEventsJSON(events);
                    hit.put("isi-events", eventsArray);
                }
                topLevel.add(hit);
            }
            writer.write(topLevel.toJSONString());
            writer.close();
        } catch (FileNotFoundException cause) {
            throw new TasksRunnerException(cause);
        }
    }

    private void updateSentenceIDs (Hit d) {
        List<Event> events = d.events;
        for (Event event : events) {
            String docid = d.docid;
            long start = (long) event.anchorSpan.start;
            List<SentenceRange> sentences = Document.getDocumentSentences(docid);
            int statementID = findSentence(start, sentences);
            event.sentenceID = statementID;
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

    private void updateTaskOrRequest(JSONObject hit) {
        String groupType = (String) hit.get("groupType");
        if (groupType.equals("T")) {
            String taskID = (String) hit.get("groupId");
            String docid = (String) hit.get("docid");
            JSONArray events = (JSONArray) hit.get("events");
            Task t = tasks.findTask(taskID);
            for (ExampleDocument d : t.taskExampleDocs) {
                if (d.getDocid().equals(docid)) {
                    d.setEvents(events);
                }
            }
        } else {
            String requestID = (String) hit.get("groupId");
            String docid = (String) hit.get("docid");
            JSONArray events = (JSONArray) hit.get("events");
            Request r = tasks.findRequest(requestID);
            for (ExampleDocument d : r.reqExampleDocs) {
                if (d.getDocid().equals(docid)) {
                    d.setEvents(events);
                }
            }
        }
    }

    private void updateTaskOrRequest(Hit hit) {
//        logger.info("In updateTaskOrRequest, hit.taskID is " + hit.taskID + ", length is " + hit.taskID.length());
        String groupType = hit.taskID.length() > 6 ? "R" : "T";
        if (groupType.equals("T")) {
            String taskID = hit.taskID;
            String docid = hit.docid;
            Task t = tasks.findTask(taskID);
            for (ExampleDocument d : t.taskExampleDocs) {
                if (d.getDocid().equals(docid)) {
                    d.setEvents(hit.events);
                }
            }
        } else {
            String requestID = hit.taskID;
            String docid = hit.docid;
//            logger.info("docid is " + docid);  // DEBUG
//            logger.info("Looking for request " + requestID);  // DEBUG
            Request r = tasks.findRequest(requestID);
            for (ExampleDocument d : r.reqExampleDocs) {
                if (d.getDocid().equals(docid)) {
                    d.setEvents(hit.events);
                }
            }
        }
    }

    private List<Hit> readEventFile(String file, int N) {
        List<String> files = new ArrayList<>();
        files.add(file);
        return readEventFile(files, N);
    }

    private List<Hit> readEventFile(List<String> files, int N) {
        try {
            List<Hit> hits = new ArrayList<>();
            for (String file : files) {
                File tempFile = new File(file);
                if (tempFile.exists()) {
                    logger.info("Reading event file " + file);
                    Reader reader = new BufferedReader(new InputStreamReader(
                            new FileInputStream(file)));
                    JSONParser parser = new JSONParser();
                    JSONObject head = (JSONObject) parser.parse(reader);
                    JSONObject entries = (JSONObject) head.get("entries");
                    int idx = 0;

                    for (Iterator iterator = entries.keySet().iterator(); iterator.hasNext(); ) {
                        idx += 1;
                        if (N > 0) {
                            if (idx > N) {
                                break;
                            }
                        }
                        String entryKey = (String) iterator.next();
                        JSONObject entry = (JSONObject) entries.get(entryKey);
                        String[] parts = entryKey.split("--");
                        String docSetType = parts[0];

                        String taskOrRequestID = parts[1];
                        String docid = parts[2];
                        String docText = (String) entry.get("segment-text");

                        JSONObject annotation_sets = (JSONObject) entry.get("annotation-sets");
                        JSONObject basic_events = (JSONObject) annotation_sets.get("basic-events");

                        List<Event> eventList = new ArrayList<>();
                        Map<String, SpanSet> spansMap = new HashMap<>();

                        JSONObject spanSets = (JSONObject) basic_events.get("span-sets");
                        for (Iterator iterator3 = spanSets.keySet().iterator(); iterator3.hasNext(); ) {
                            String key3 = (String) iterator3.next();
                            JSONObject spanSet = (JSONObject) spanSets.get(key3);
                            String ssid = entryKey + "--" + (String) spanSet.get("ssid");
                            SpanSet ss = new SpanSet(entryKey, ssid);
                            JSONArray spans = (JSONArray) spanSet.get("spans");
                            for (Object oSpan : spans) {
                                JSONObject span = (JSONObject) oSpan;
                                String synclass = (String) span.get("synclass");
                                String string = (String) span.get("string");
                                String hstring = (String) span.get("hstring");
                                long start = (long) span.get("start");
                                long hstart = (long) span.get("hstart");
                                long end = (long) span.get("end");
                                long hend = (long) span.get("hend");
                                Span s = new Span(synclass, string, start, end, hstring, hstart, hend);
                                ss.spans.add(s);
                            }
                            spansMap.put(ssid, ss);
                            /* END OF SPANS */
                        }

                        JSONObject events = (JSONObject) basic_events.get("events");
                        for (Iterator iterator2 = events.keySet().iterator(); iterator2.hasNext(); ) {
                            String key2 = (String) iterator2.next();
                            JSONObject event = (JSONObject) events.get(key2);
                            String eventid = (String) event.get("eventid");
                            String eventType = (String) event.get("event-type");
                            String anchor = (String) event.get("anchors");
                            List<String> agentList = new ArrayList<>();
                            JSONArray agents = (JSONArray) event.get("agents");
                            for (Object oAgent : agents) {
                                String agent = (String) oAgent;
                                agentList.add(agent);
                            }
                            List<String> patientList = new ArrayList<>();
                            JSONArray patients = (JSONArray) event.get("patients");
                            for (Object oPatient : patients) {
                                String patient = (String) oPatient;
                                patientList.add(patient);
                            }
                            Event e = new Event(entryKey, docSetType, taskOrRequestID, docid, eventid, eventType, anchor,
                                    agentList, patientList);
                            e.anchorSpan = new Span(spansMap.get(e.entryKey + "--" + e.anchor).spans.get(0));
                            for (String agent : e.agentList) {
                                e.agentSpans.add(new SpanSet(spansMap.get(e.entryKey + "--" + agent)));
                            }
                            for (String patient : e.patientList) {
                                e.patientSpans.add(new SpanSet(spansMap.get(e.entryKey + "--" + patient)));
                            }
                            eventList.add(e);
                            /* END OF EVENTS */
                        }
                        Hit hit = new Hit(taskOrRequestID, docid, docText, eventList);
                        hits.add(hit);
                    }

                } else {
                    logger.severe("Requested event file " + file + " does not exist!");
                }
            }
            return hits;
        } catch (Exception cause) {
            throw new TasksRunnerException(cause);
        }
    }
}
