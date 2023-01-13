package edu.umass.ciir;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;

import static java.nio.file.StandardCopyOption.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
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
    private String submissionId;
    private List<SearchHit> searchHits;

    public EventExtractor(AnalyticTasks tasks, String mode, String submissionId) {
        this.tasks = tasks;
        this.mode = mode;
        this.submissionId = submissionId;
    }

    public List<SearchHit> getSearchHits() {
        return searchHits;
    }

    /**
     * Creates a JSON file containing the specified entries, in the format that the
     * ISI event extractor expects (which is the same as the official BETTER format for an IE input file)
     * @param entries a map of key to text-to-be-processed-for-events
     * @return the total number of characters in the text fields
     */
    public int writeInputFileForEventExtractor(Map<String,SimpleHit> entries, String fileForExtractor)  {
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
                String translatedText = hit.translatedDocText;
                tot_chars += text.length();
                JSONObject docEntry = new JSONObject();

                JSONObject events = new JSONObject();
                JSONObject spansets = new JSONObject();
                JSONObject basicEvents = new JSONObject();
                basicEvents.put("events", events);  // to be filled in by ISI event extractor
                basicEvents.put("span-sets", spansets);
                JSONObject annotationSets = new JSONObject();
                annotationSets.put("basic-events", basicEvents);
                docEntry.put("annotation-sets", annotationSets);
                docEntry.put("doc-id", key);
                docEntry.put("lang", SearchEngineInterface.toISIThreeCharForm(hit.language)); // ISO 639-3
                docEntry.put("entry-id", key);
                JSONArray segmentSections = new JSONArray();
                if (hit.sentences != null) {
                    List<SentenceRange> sentences = hit.sentences;
                    for (SentenceRange sentence : sentences) {
                        JSONObject segmentSection = new JSONObject();
                        segmentSection.put("start", sentence.start);
                        segmentSection.put("end", sentence.end);
                        segmentSection.put("structural-element", "Sentence");
                        segmentSections.add(segmentSection);
                    }
                }
                docEntry.put("segment-sections", segmentSections);

                // If we've read in events from the corpus file, send them along
                JSONArray eventsJSON = new JSONArray();
                List<Event> isiEvents = hit.events;
//                logger.info("Writing Hit to file, hit.events is " +
//                        hit.events == null ? "NULL" : Integer.toString(hit.events.size()));
                if (isiEvents != null) {
                    for (Event event : isiEvents) {
                        JSONObject eventJSON = new JSONObject();
                        eventJSON.put("eventType", event.eventType);
                        eventsJSON.add(eventJSON);
                    }
                }
                docEntry.put("isi-events", eventsJSON);

                docEntry.put("segment-text", text);
                docEntry.put("segment-translated-text", translatedText);
                docEntry.put("segment-type", "document");
                docEntries.put(key, docEntry);

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
            outermostEntry.put("scoredHits", docEntries);

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
     * @param entries the scoredHits
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
            outermostEntry.put("scoredHits", docEntries);

            PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(fileForExtractor)));
            writer.write(outermostEntry.toJSONString());
            writer.close();
        } catch (Exception cause) {
            throw new TasksRunnerException(cause);
        }
        return tot_chars;
    }


    public void createInputFileEntriesFromHits(String docSetType, String taskOrRequestID,
                                                List<String> hits, Map<String,SimpleHit> m) {
        for (String td : hits) {
            List<SentenceRange> sentences = null;
            List<Event> events = null;
            String docText;
            String translatedDocText;
            String language;
            if (Pathnames.runGetCandidateDocs || Pathnames.targetLanguageIsEnglish) {
                events = Document.getEnglishDocumentEvents(td);
                sentences = Document.getDocumentSentences(td);
                docText = Document.getDocumentWithMap(td);
                language = "english";
                translatedDocText = "";
            } else {
//                logger.info("Looking for events for docid " + td);
                events = Document.getTargetDocumentEvents(td);
//                logger.info("Lookup returned " + events == null ? "NULL" : "non-NULL");
                sentences = Document.getTargetDocumentSentences(td);
                docText = Document.getTargetDocumentWithMap(td);
                translatedDocText = Document.getTranslatedTargetDocumentWithMap(td);
                language = Document.getLanguage(td);
            }
            SimpleHit hit = new SimpleHit(td, docText, translatedDocText, sentences, events, language);
            m.put(docSetType + "--" + taskOrRequestID + "--" + td, hit);
        }
    }

    /**
     * Number of scoredHits to have full event details.
     */
    private final int TASK_HITS_DETAILED = 1000;


    public String constructTaskLevelFileFromEventExtractorFileName(Task t) {
        return Pathnames.eventExtractorFileLocation + submissionId + "." + t.taskNum + ".TASKHITS.json.results.json";
    }
    public String constructTaskLevelToFromEventExtractorFileName(Task t) {
        return Pathnames.eventExtractorFileLocation + submissionId + "." + t.taskNum + ".TASKHITS.json";
    }
    public String constructTaskLevelEventFileName(Task t) {
        return Pathnames.eventExtractorFileLocation + submissionId + "." + t.taskNum + ".TASKHITS.events.json";
    }
    public String constructTaskLevelSimpleFileName(Task t) {
        return Pathnames.eventExtractorFileLocation + submissionId + "." + t.taskNum + ".TASKHITS.SIMPLE.json";
    }

    public String constructRequestLevelFileFromEventExtractorFileName(Request r) {
        return Pathnames.eventExtractorFileLocation + submissionId + "." + r.reqNum + ".REQUESTHITS.json.results.json";
    }
    public String constructRequestLevelToEventExtractorFileName(Request r) {
        return Pathnames.eventExtractorFileLocation + submissionId + "." + r.reqNum + ".REQUESTHITS.json";
    }
    public String constructRequestLevelEventFileName(Request r) {
        return Pathnames.eventExtractorFileLocation + submissionId + "." + r.reqNum + ".REQUESTHITS.events.json";
    }


    public String constructRequestLevelSimpleFileName(Request r) {
        return Pathnames.eventExtractorFileLocation + submissionId + "." + r.reqNum + ".REQUESTHITS.SIMPLE.json";
    }
    public String constructRequestLevelRerankerFileName(Request r) {
        return Pathnames.eventExtractorFileLocation + submissionId + "." + r.reqNum + ".REQUESTHITS.FOR_RERANKER.json";
    }

    public String constructExampleFileFromEventExtractorFileName() {
        return Pathnames.eventExtractorFileLocation + submissionId + ".EXAMPLES.results.json";
    }
    public String constructExampleFileToEventExtractorFileName() {
        return Pathnames.eventExtractorFileLocation + submissionId + ".EXAMPLES.json";
    }
    public String constructExampleEventFileName() {
        return Pathnames.eventExtractorFileLocation + submissionId + ".EXAMPLES.events.json";
    }
    public String constructRelevantFileFromEventExtractorFileName() {
        return Pathnames.eventExtractorFileLocation + submissionId + ".RELEVANT.results.json";
    }
    public String constructRelevantFileToEventExtractorFileName() {
        return Pathnames.eventExtractorFileLocation + submissionId + ".RELEVANT.json";
    }
    public String constructRelevantEventFileName() {
        return Pathnames.eventExtractorFileLocation + submissionId + ".RELEVANT.events.json";
    }

    /**
     * Creates a map of SimpleHits for the relevant documents for each Request in the specified Task. Each SimpleHit has the
     * document text, translated text, sentences, and language, which are needed by the ISI event annotator.
     * The key for each SimpleHit has enough information to allow us to determine which example document is which
     * in the output file from the ISI event annotator.
     * @param t The Task
     * @param m The map to fill in (OUTPUT)
     */
    public void createInputFileEntriesFromRelevantDocs(Task t, Map<String,SimpleHit> m) {
        for (Request r : t.getRequests().values()) {
            for (RelevanceJudgment relevanceJudgment : r.relevanceJudgments) {
                SimpleHit hit = new SimpleHit(relevanceJudgment.getDocid(), relevanceJudgment.getDocText(),
                        relevanceJudgment.getTranslatedText(), relevanceJudgment.getSentences(), null,
                        relevanceJudgment.getLanguage());
                m.put("RequestRelevantDoc" + "--" + r.reqNum + "--" + relevanceJudgment.getDocid(), hit);
            }
        }
    }

    /**
     * Calls the ISI event annotator for all of the relevant documents that are in the RELEVANT.json file created by
     * createInputFileEntriesFromRelevantDocs().
     */
    public void annotateRelevantDocEvents() {
        runAScript("./annotate_relevant_docs.sh",
                " MODELS_BASE_DIR=" + Pathnames.MODELS_BASE_DIR
                + " APP_DIR=" + Pathnames.appFileLocation
                + " GPUS=" + Pathnames.gpusForEventExtractor
                + " MODE=" + mode
                + " SUBMISSION_ID=" + submissionId
                + " SCRATCH_DIR=" + Pathnames.scratchFileLocation
                + " EVENT_EXTRACTOR_FILES_DIRECTORY=" + Pathnames.eventExtractorFileLocation
                + " CORPUS_DIR=" + Pathnames.corpusFileLocation);
    }

    private int findSentence(long start, List<SentenceRange> sentences) {
        for (SentenceRange sentence : sentences) {
            if (start >= sentence.start && start <= sentence.end) {
                return sentence.id;
            }
        }
        return -1;
    }

    private int splitFile(String baseFilename, int linesPerFile)  {
        /* each filename should be baseFileName +  ".N.json" */
        /* N should start at 1 not 0 */
        String inputFileName = baseFilename + ".json";
        long lines = 0;
        try {
            lines = Files.lines(Paths.get(inputFileName)).parallel().count();
        } catch (IOException e) {
            throw new TasksRunnerException(e);
        }
        logger.info("splitFile, baseFilename is " + baseFilename + ", linesPerFile is " + linesPerFile
                + ", line count is " + lines);
        int startIndex = 0;
        int part = 1;
        while ((part - 1) * linesPerFile < lines) {
            logger.info("part " + part);
            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                        new FileOutputStream(baseFilename + "." + part + ".json")))) {
                startIndex = (part - 1) * linesPerFile;
                logger.info("startIndex is " + startIndex + ", limit is " + (startIndex + linesPerFile));
                try (Stream<String> stream = Files.lines(Paths.get(inputFileName))) {
                    stream.skip(startIndex).limit(startIndex + linesPerFile).forEach(writer::println);
                } catch (IOException e) {
                    throw new TasksRunnerException(e);
                }
            } catch (Exception e) {
                throw new TasksRunnerException(e);
            }
            ++part;
        }
        logger.info("Returning " + (part - 1) + " parts");
        return part - 1;
    }

    private int splitJSONFile(String baseFilename, int linesPerFile)  {
        /* each filename should be baseFileName +  ".N.json" */
        /* N should start at 1 not 0 */
        String inputFileName = baseFilename + ".json";
        List<Hit> hits = readEventFile(inputFileName, -1);

        int startIndex = 0;
        int part = 1;
        while ((part - 1) * linesPerFile < hits.size()) {
            logger.info("part " + part);
            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(baseFilename + "." + part + ".json")))) {
                startIndex = (part - 1) * linesPerFile;
                logger.info("startIndex is " + startIndex + ", limit is " + (startIndex + linesPerFile));
                hits.stream().skip(startIndex).limit(startIndex + linesPerFile).forEach(writer::println);
            } catch (Exception e) {
                throw new TasksRunnerException(e);
            }
            ++part;
        }
        logger.info("Returning " + (part - 1) + " parts");
        return part - 1;
    }
    /**
     * Calls the ISI event extractor to get the events in the specified documents.
     * Creates files in the eventExtractorFileLocation directory. Calls a script.
     * @param name a name for this set of documents, like "EXAMPLES" or "RELEVANT" or "CORPUS"
     * @param getSimpleHits a function that returns the list of documents, expressed as a list of SimpleHits
     * @return the list of documents with their events added, expressed as a list of Hits
     * It also writes the list of documents with their events and other information to a file as a side effect
     * (the ...events.json file).
     */
    public List<Hit> annotateSomeDocs(String name, int startIndex, int numDocsPerFile,
                                      BiFunction<Integer, Integer, Map<String, SimpleHit>> getSimpleHits) {
        /* Write out the documents to a file to send to the ISI event extractor */
        writeInputFileForEventExtractor(getSimpleHits.apply(startIndex, numDocsPerFile),
                Pathnames.eventExtractorFileLocation + submissionId + "." + name + ".json");

//        int numberOfParts = splitFile(Pathnames.eventExtractorFileLocation + submissionId + "." + name, 6000);

        /* Run the ISI event extractor */
        List<Hit> totalHits = new ArrayList<>();

//        for (int currentPart = 1; currentPart <= numberOfParts; ++currentPart) {
//            String currentName = name + "." + currentPart;

            runAScript("./annotate_docs.sh",
                    " MODELS_BASE_DIR=" + Pathnames.MODELS_BASE_DIR
                            + " APP_DIR=" + Pathnames.appFileLocation
                            + " GPUS=" + Pathnames.gpusForEventExtractor
                            + " MODE=" + mode
                            + " SUBMISSION_ID=" + submissionId
                            + " SCRATCH_DIR=" + Pathnames.scratchFileLocation
                            + " EVENT_EXTRACTOR_FILES_DIRECTORY=" + Pathnames.eventExtractorFileLocation
                            + " CORPUS_DIR=" + Pathnames.corpusFileLocation
                            + " NAME=" + name);

            /* Read in the results of the event extractor, which is the same set of documents but with events added to them */
            List<Hit> hits = readEventFile(Pathnames.eventExtractorFileLocation + submissionId + "." + name + ".results.json", -1);

            /* Load all documents referenced in the list into memory (including their translated texts and sentence details) */
            Document.buildTargetDocMap(hits.stream().map(hit -> hit.docid).collect(Collectors.toSet()));
            for (Hit d : hits) {
            /* Now that we have access to the document's sentences, add the IDs of the sentences the events occur in.
               This is something the ISI event extractor did not do. */
                for (Event event : d.events) {
                    event.sentenceID = findSentence(event.anchorSpan.start, Document.getTargetDocumentSentences(d.docid));
                }
                /* The ISI event annotator did not preserve the translated document text even if we sent it. Add it here. */
                d.translatedDocText = Document.getTranslatedTargetDocumentWithMap(d.docid);
            }

            /* Write out the documents, with their events and other information, to a file for use downstream */
            writeEventsAsJson(hits, name,
                    Pathnames.eventExtractorFileLocation + submissionId + "." + name + ".events.json",
                    999999);
            totalHits.addAll(hits);
//        }
        return totalHits;
    }

    /**
     * Creates a map of SimpleHits for the example documents for the Task and for each Request. Each SimpleHit has the
     * document text and sentences, which are needed by the ISI event annotator. The key for each SimpleHit has enough
     * information to allow us to determine which example document is which in the output file from the ISI event annotator.
     * @param t The Task
     * @param m The map to fill in (OUTPUT)
     */
    public void createInputFileEntriesFromExampleDocs(Task t, Map<String,SimpleHit> m) {
        for (ExampleDocument d : t.taskExampleDocs) {
            SimpleHit hit = new SimpleHit(d.getDocid(), d.getDocText(), "", d.getSentences(), null,
                    "english");
            m.put("TaskExampleDoc" + "--" + t.taskNum + "--" + d.getDocid(), hit);
        }
        for (Request r : t.getRequests().values()) {
            for (ExampleDocument d : r.reqExampleDocs) {
                SimpleHit hit = new SimpleHit(d.getDocid(), d.getDocText(), "", d.getSentences(),
                        null, "english");
                m.put("TaskExampleDoc" + "--" + t.taskNum + "--" + d.getDocid(), hit);
                m.put("RequestExampleDoc" + "--" + r.reqNum + "--" + d.getDocid(), hit);
            }
        }
    }

    /**
     * Calls the ISI event annotator for all of the example documents that are in the EXAMPLES.json file created by
     * createInputFileEntriesFromExampleDocs().
     */
    public void annotateExampleDocEvents() {
        runAScript("./annotate_example_docs.sh",
                " MODELS_BASE_DIR=" + Pathnames.MODELS_BASE_DIR
                + " APP_DIR=" + Pathnames.appFileLocation
                + " GPUS=" + Pathnames.gpusForEventExtractor
                + " MODE=" + mode
                + " SUBMISSION_ID=" + submissionId
                + " SCRATCH_DIR=" + Pathnames.scratchFileLocation
                + " EVENT_EXTRACTOR_FILES_DIRECTORY=" + Pathnames.eventExtractorFileLocation
                + " CORPUS_DIR=" + Pathnames.corpusFileLocation);
    }

    /**
     * Runs the provided script after changing to the scripts directory. Output from the script goes to a logfile.
     * The provided environment variable definitions are passed to the script.
     * @param script The script to run, e.g. "./annotate_events.sh"
     * @param environmentVars The set of environment variable definitions to pass to the script. For instance,
     *                        MODE=AUTO SUBMISSION_ID=xyz
     */
    public void runAScript(String script, String environmentVars ) {
        // e.g. script = "./annotate_request_docs.sh.FARSI";
        Command.execute("cd " + Pathnames.scriptFileLocation + " && " + environmentVars + " " + script,
                Pathnames.logFileLocation + script.replace("./", "") + "." + submissionId + ".log");
    }

    public void annotateProvidedFileEvents() {
        logger.info("Calling event annotator for user-supplied test_data.bp.json file");

        runAScript("./annotate_provided_file.sh",
                "MODELS_BASE_DIR=" + Pathnames.MODELS_BASE_DIR
                        + " APP_DIR=" + Pathnames.appFileLocation
                        + " MODE=" + mode
                        + " GPUS=" + Pathnames.gpusForEventExtractor
                        + " SUBMISSION_ID=" + submissionId
                        + " SCRATCH_DIR=" + Pathnames.scratchFileLocation
                        + " EVENT_EXTRACTOR_FILES_DIRECTORY=" + Pathnames.eventExtractorFileLocation
                        + " CORPUS_DIR=" + Pathnames.corpusFileLocation);
    }

    private void copyFile(String source, String dest) {
        try {
            Files.copy(new File(source).toPath(), new File(dest).toPath(), REPLACE_EXISTING);
        } catch (IOException e) {
            throw new TasksRunnerException(e);
        }
    }

    public void copyRequestEventFilesToResultsFiles() {
        List<Request> requestList = tasks.getRequests();
        for (Request request : requestList) {
            String fileForEventExtractor = constructRequestLevelToEventExtractorFileName(request);
            copyFile(fileForEventExtractor, fileForEventExtractor + ".results.json");
        }
    }

    public void copyRelevantDocEventFileToResultsFile() {
        String source = constructRelevantFileToEventExtractorFileName();
        copyFile(constructRelevantFileToEventExtractorFileName(), constructRelevantFileFromEventExtractorFileName());
    }

    public void copyExampleDocEventFileToResultsFile() {
        String source = constructExampleFileToEventExtractorFileName();
        copyFile(constructExampleFileToEventExtractorFileName(), constructExampleFileFromEventExtractorFileName());
    }
    /**
     * A side-effect of this script is that it copies the REQUESTHITS.json files to REQUESTHITS.json.results.json files,
     * so it must be called even if you don't actually call the ISI event extractor.
     * For now, I changed the script to do the copy and commented-out the call to the event annotator.
     */
    public void annotateRequestDocEvents() {
        String script = "./annotate_request_docs.sh";
        String trainingDirs = "MODELS_BASE_DIR=" + Pathnames.MODELS_BASE_DIR;
        String environmentVars = trainingDirs
                    + " MODE=" + mode
                    + " SUBMISSION_ID=" + submissionId
                    + " APP_DIR=" + Pathnames.appFileLocation
                    + " SCRATCH_DIR=" + Pathnames.scratchFileLocation
                    + " EVENT_EXTRACTOR_FILES_DIRECTORY=" + Pathnames.eventExtractorFileLocation
                    + " CORPUS_DIR=" + Pathnames.corpusFileLocation;
        runAScript(script, environmentVars);
    }

    /**
     *
     * Keep this method in case we ever want to do this:
     */
    public void annotateTaskDocEvents() {
        String script = "./annotate_task_docs.sh";
        String trainingDirs = "MODELS_BASE_DIR=" + Pathnames.MODELS_BASE_DIR;
        String environmentVars = trainingDirs
                    + " MODE=" + mode
                    + " SUBMISSION_ID=" + submissionId
                    + " APP_DIR=" + Pathnames.appFileLocation
                    + " SCRATCH_DIR=" + Pathnames.scratchFileLocation
                    + " EVENT_EXTRACTOR_FILES_DIRECTORY=" + Pathnames.eventExtractorFileLocation
                    + " CORPUS_DIR=" + Pathnames.corpusFileLocation;
        runAScript(script, environmentVars);
    }

    public void preTrainEventAnnotator() {
        logger.info("PRE-TRAINING: Pre-training the event annotator");
        String script = "./pretrain.sh";
        String environmentVars = " MODELS_BASE_DIR=" + Pathnames.MODELS_BASE_DIR
                + " APP_DIR=" + Pathnames.appFileLocation
                + " SUBMISSION_ID=" + submissionId
                + " SCRATCH_DIR=" + Pathnames.scratchFileLocation
                + " CORPUS_DIR=" + Pathnames.corpusFileLocation;
        runAScript(script, environmentVars);
        logger.info("PRE-TRAINING COMPLETE");
    }

    public void writeEventsAsJson(List<Hit> hits, String type, String eventHumanReadableFile, int limit) {
        try {
            searchHits = new ArrayList<>();

            PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(eventHumanReadableFile)));
            JSONArray topLevel = new JSONArray();
            int idx = 0;
            for (Hit h : hits) {
                ++idx;
                JSONObject hit = new JSONObject();
                hit.put("groupId", h.taskID);
                hit.put("groupType", h.hitLevel == HitLevel.REQUEST_LEVEL ? "R" : "T");
                hit.put("docid", h.docid);

//                if (idx > Pathnames.REQUEST_HITS_DETAILED) {
                if (idx > limit) {
                    hit.put("docText", "");
                    hit.put("translatedDocText", "");
                    JSONArray segmentSections = new JSONArray();
                    hit.put("sentences", segmentSections);
                    JSONArray mitreEvents = new JSONArray();
                    hit.put("mitre-events", mitreEvents);
                    JSONArray eventsArray = new JSONArray();
                    hit.put("isi-events", eventsArray);
                } else {
                    hit.put("docText", h.docText);
                    hit.put("translatedDocText", h.translatedDocText);

                    JSONArray segmentSections = new JSONArray();
                    List<SentenceRange> sentences = null;
                    if (Pathnames.runGetCandidateDocs || Pathnames.targetLanguageIsEnglish) {
                        sentences = Document.getDocumentSentences(h.docid);
                    } else {
                        sentences = Document.getTargetDocumentSentences(h.docid);
                    }
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
                searchHits.add(new SearchHit(hit));
            }
            writer.write(topLevel.toJSONString());
            writer.close();
        } catch (FileNotFoundException cause) {
            throw new TasksRunnerException(cause);
        }
    }

    public List<Hit> readEventFile(String file, int N) {
        List<String> files = new ArrayList<>();
        files.add(file);
        return readEventFile(files, N);
    }

    public List<Hit> readEventFile(List<String> files, int N) {
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

                    for (Object o : entries.keySet()) {
                        idx += 1;
                        if (N > 0) {
                            if (idx > N) {
                                break;
                            }
                        }
                        String entryKey = (String) o;
                        JSONObject entry = (JSONObject) entries.get(entryKey);
                        String[] parts = entryKey.split("--");
                        String docSetType = parts[0];

                        String taskOrRequestID = parts[1];
                        String docid = parts[2];
                        String docText = (String) entry.get("segment-text");
                        String translatedDocText = (String) entry.get("segment-translated-text");

                        JSONObject annotation_sets = (JSONObject) entry.get("annotation-sets");
                        JSONObject basic_events = (JSONObject) annotation_sets.get("basic-events");

                        List<Event> eventList = new ArrayList<>();
                        Map<String, SpanSet> spansMap = new HashMap<>();

                        JSONObject spanSets = (JSONObject) basic_events.get("span-sets");
                        for (Iterator iterator3 = spanSets.keySet().iterator(); iterator3.hasNext(); ) {
                            String key3 = (String) iterator3.next();
                            JSONObject spanSet = (JSONObject) spanSets.get(key3);
                            String ssid = entryKey + "--" + spanSet.get("ssid");
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

                        if (entry.containsKey("isi-events")) {
                            JSONArray eventsToParse = (JSONArray) entry.get("isi-events");
//                            logger.info("Reading from isi-events");
                            for (Object oEvent : eventsToParse) {
                                JSONObject event = (JSONObject) oEvent;
                                String eventType = (String) event.get("eventType");
                                Event e = new Event();
                                e.eventType = eventType;
                                eventList.add(e);
                            }
                        } else {
                            JSONObject eventsToParse;
                            eventsToParse = (JSONObject) basic_events.get("events");
//                            logger.info("Reading from basic events");
                            for (Object value : eventsToParse.keySet()) {
                                String key2 = (String) value;
                                JSONObject event = (JSONObject) eventsToParse.get(key2);
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
                            }
                            /* END OF EVENTS */
                        }
                        HitLevel hitLevel = docSetType.equals("RequestLevelHit") ? HitLevel.REQUEST_LEVEL : HitLevel.TASK_LEVEL;
                        Hit hit = new Hit(hitLevel, taskOrRequestID, docid, docText, translatedDocText, eventList);
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
