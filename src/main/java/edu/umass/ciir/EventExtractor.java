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
            if (Pathnames.runGetCandidateDocs || Pathnames.targetLanguageIsEnglish) {
                events = Document.getEnglishDocumentEvents(td);
                sentences = Document.getDocumentSentences(td);
                docText = Document.getDocumentWithMap(td);
                translatedDocText = "";
            } else {
//                logger.info("Looking for events for docid " + td);
                events = Document.getArabicDocumentEvents(td);
//                logger.info("Lookup returned " + events == null ? "NULL" : "non-NULL");
                sentences = Document.getArabicDocumentSentences(td);
                docText = Document.getArabicDocumentWithMap(td);
                translatedDocText = Document.getTranslatedArabicDocumentWithMap(td);
            }
            SimpleHit hit = new SimpleHit(td, docText, translatedDocText, sentences, events);
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
        return Pathnames.eventExtractorFileLocation + submissionId + ".EXAMPLES.json.results.json";
    }
    public String constructExampleToEventExtractorFileName() {
        return Pathnames.eventExtractorFileLocation + submissionId + ".EXAMPLES.json";
    }

    public void createInputFileEntriesFromExampleDocs(Task t, Map<String,SimpleHit> m) {
        for (ExampleDocument d : t.taskExampleDocs) {
            SimpleHit hit = new SimpleHit(d.getDocid(), d.getDocText(), "", d.getSentences(), null);
            m.put("TaskExampleDoc" + "--" + t.taskNum + "--" + d.getDocid(), hit);
        }
        for (Request r : t.getRequests().values()) {
            for (ExampleDocument d : r.reqExampleDocs) {
                SimpleHit hit = new SimpleHit(d.getDocid(), d.getDocText(), "", d.getSentences(), null);
                m.put("TaskExampleDoc" + "--" + t.taskNum + "--" + d.getDocid(), hit);
                m.put("RequestExampleDoc" + "--" + r.reqNum + "--" + d.getDocid(), hit);
            }
        }
    }

    public void annotateExampleDocEvents() {
        try {
//            String logFile = Pathnames.logFileLocation + mode + "/annotate_example_docs.log";
            String logFile = Pathnames.logFileLocation + "/annotate_example_docs." + submissionId + ".log";
            String tempCommand = "cd /home/tasksrunner/scripts && "
                    + " sudo"
                    + " MODELS_BASE_DIR_ENGLISH=" + Pathnames.MODELS_BASE_DIR_ENGLISH
                    + " MODELS_BASE_DIR_FARSI=" + Pathnames.MODELS_BASE_DIR_FARSI
                    + " APP_DIR=" + Pathnames.appFileLocation
                    + " MODE=" + mode
                    + " SUBMISSION_ID=" + submissionId
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
            logger.info("Calling event annotator for test_data.bp.json file");

            if (Pathnames.runGetCandidateDocs || Pathnames.targetLanguageIsEnglish) {
                throw new TasksRunnerException("Cannot call annotateProvidedFileEvents for English docs");
            }
            String script = "./annotate_provided_file.sh.FARSI";
            String trainingDirs = "MODELS_BASE_DIR_FARSI=" + Pathnames.MODELS_BASE_DIR_FARSI;
            if (Pathnames.targetLanguage.equals("ARABIC")) {
                script = "./annotate_provided_file.sh.ARABIC";
                trainingDirs = "MODELS_BASE_DIR_ARABIC=" + Pathnames.MODELS_BASE_DIR_ARABIC;
            }
//            String logFile = Pathnames.logFileLocation + mode + "/annotate_provided_file.log";
            String logFile = Pathnames.logFileLocation + "/annotate_provided_file." + submissionId + ".log";

            String tempCommand = "cd /home/tasksrunner/scripts && "
                    + " sudo"
                    + " MODELS_BASE_DIR_ENGLISH=" + Pathnames.MODELS_BASE_DIR_ENGLISH
                    + " MODELS_BASE_DIR_FARSI=" + Pathnames.MODELS_BASE_DIR_FARSI
                    + " APP_DIR=" + Pathnames.appFileLocation
                    + " MODE=" + mode
                    + " SUBMISSION_ID=" + submissionId
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

    /**
     * A side-effect of this script is that it copies the REQUESTHITS.json files to REQUESTHITS.json.results.json files,
     * so it must be called even if you don't actually call the ISI event extractor
     */
    public void annotateRequestDocEvents() {
        String script = "./annotate_request_docs.sh.FARSI";
        String trainingDirs = "MODELS_BASE_DIR_FARSI=" + Pathnames.MODELS_BASE_DIR_FARSI;
        if (!Pathnames.runGetCandidateDocs && Pathnames.targetLanguage.toString().equals("ARABIC")) {
            script = "./annotate_request_docs.sh.ARABIC";
            trainingDirs = "MODELS_BASE_DIR_ARABIC=" + Pathnames.MODELS_BASE_DIR_ARABIC;
        } else if (Pathnames.runGetCandidateDocs || Pathnames.targetLanguageIsEnglish) {
            script = "./annotate_request_docs.sh.ENGLISH";
            trainingDirs = "";
        }
        try {
//            String logFile = Pathnames.logFileLocation + mode + "/annotate_request_docs.log";
            String logFile = Pathnames.logFileLocation + "/annotate_request_docs." + submissionId + ".log";
            String tempCommand = "cd /home/tasksrunner/scripts && "
                    + " sudo"
                    + " MODELS_BASE_DIR_ENGLISH=" + Pathnames.MODELS_BASE_DIR_ENGLISH
                    + " " + trainingDirs
                    + " MODE=" + mode
                    + " SUBMISSION_ID=" + submissionId
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
//                    logger.info("annotate_request_docs output:\n" + builder.toString());
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

    /**
     *
     * Keep this method in case we ever want to do this:
     */
    public void annotateTaskDocEvents() {
        String script = "./annotate_task_docs.sh.FARSI";
        String trainingDirs = "MODELS_BASE_DIR_FARSI=" + Pathnames.MODELS_BASE_DIR_FARSI;
        if (!Pathnames.runGetCandidateDocs && Pathnames.targetLanguage.toString().equals("ARABIC")) {
            script = "./annotate_task_docs.sh.ARABIC";
            trainingDirs = "MODELS_BASE_DIR_ARABIC=" + Pathnames.MODELS_BASE_DIR_ARABIC;
        } else if (Pathnames.runGetCandidateDocs || Pathnames.targetLanguageIsEnglish) {
            script = "./annotate_task_docs.sh.ENGLISH";
            trainingDirs = "";
        }
        try {
//            String logFile = Pathnames.logFileLocation + mode + "/annotate_task_docs.log";
            String logFile = Pathnames.logFileLocation + "/annotate_task_docs." + submissionId + ".log";
            String tempCommand = "cd /home/tasksrunner/scripts && "
                    + " sudo"
                    + " MODELS_BASE_DIR_ENGLISH=" + Pathnames.MODELS_BASE_DIR_ENGLISH
                    + " " + trainingDirs
                    + " MODE=" + mode
                    + " SUBMISSION_ID=" + submissionId
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
                logger.log(Level.SEVERE, "Exception doing annotate_task_docs execution", cause);
                throw new TasksRunnerException(cause);
            } finally {
                StringBuilder builder = new StringBuilder();
                try (Stream<String> stream = Files.lines( Paths.get(logFile), StandardCharsets.UTF_8))
                {
                    stream.forEach(s -> builder.append(s).append("\n"));
                    logger.info("annotate_task_docs output:\n" + builder.toString());
                } catch (IOException ignore) {
                    // logger.info("IO error trying to read output file. Ignoring it");
                }
            }
            if (exitVal != 0) {
                logger.log(Level.SEVERE, "Unexpected ERROR from annotate_task_docs, exit value is: " + exitVal);
                throw new TasksRunnerException("Unexpected ERROR from annotate_task_docs, exit value is: " + exitVal);
            }
        } catch (Exception e) {
            throw new TasksRunnerException(e);
        }
    }

    public void preTrainEventAnnotator() {
        try {
            logger.info("PRE-TRAINING: Pre-training the event annotator");

//            String logFile = Pathnames.logFileLocation + mode + "/pretrain.log";
            String logFile = Pathnames.logFileLocation + "/pretrain." + submissionId + ".log";
            String tempCommand = "cd /home/tasksrunner/scripts && "
                    + " sudo"
                    + " MODELS_BASE_DIR_ENGLISH=" + Pathnames.MODELS_BASE_DIR_ENGLISH
                    + " MODELS_BASE_DIR_FARSI=" + Pathnames.MODELS_BASE_DIR_FARSI
                    + " APP_DIR=" + Pathnames.appFileLocation
                    + " SUBMISSION_ID=" + submissionId
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
        logger.info("PRE-TRAINING COMPLETE");

    }

    public void writeEventsAsJson(List<Hit> hits, String type,
                                   String eventHumanReadableFile) {
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

                if (idx > Pathnames.REQUEST_HITS_DETAILED) {
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
                        sentences = Document.getArabicDocumentSentences(h.docid);
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
                        String translatedDocText = (String) entry.get("segment-translated-text");

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
                            for (Iterator iterator2 = eventsToParse.keySet().iterator(); iterator2.hasNext(); ) {
                                String key2 = (String) iterator2.next();
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
