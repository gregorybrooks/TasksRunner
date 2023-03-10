package edu.umass.ciir;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.*;
import java.util.logging.Logger;

public class Task {
    private static final Logger logger = Logger.getLogger("TasksRunner");
    public String taskNum;
    public String taskTitle;
    public String taskStmt;
    public String taskNarr;
    public String taskLink;
    public String taskInScope;
    public Map<String, Request> requests;
    public List<ExampleDocument> taskExampleDocs;
    public List<String> taskRelevantDocTextList;
    public String taskLevelQuery;

    public void setTaskLevelQuery(String taskLevelQuery) {
        this.taskLevelQuery = taskLevelQuery;
    }

    public String getTaskLevelQuery() {
        return taskLevelQuery;
    }

    Task (String taskNum, String taskTitle, String taskNarr, String taskLink, Map<String, Request> requests) {
        this.taskNum = taskNum;
        this.taskTitle = filterCertainCharacters(taskTitle);
        this.taskNarr = filterCertainCharacters(taskNarr);
        this.taskLink = taskLink;
        this.requests = requests;
        taskExampleDocs = new ArrayList<ExampleDocument>();
        taskRelevantDocTextList = new ArrayList<String>();
    }

    public List<String> getExampleDocids() {
        List<String> docids = new ArrayList<>();
        for (ExampleDocument d : taskExampleDocs) {
            docids.add(d.getDocid());
        }
        return docids;
    }

    public boolean isInExampleDocs(String docid) {
        for (ExampleDocument d : taskExampleDocs) {
            if (docid.equals(d.getDocText())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Filters certain characters that cause problems for the Galago query.
     * @param q
     * @return
     */
    public static String filterCertainCharacters(String q) {
        if (q == null || q.length() == 0) {
            return q;
        }
        else {
            q = q.replaceAll("\\.", " ");  // with the periods the queries hang
            q = q.replaceAll("\\(", " ");  // parentheses are included in the token
            q = q.replaceAll("\\)", " ");  // which causes that term to not be matched
            q = q.replaceAll("@", " ");  // Galago has @ multi-word token thing
            q = q.replaceAll("#", " ");  // Galago thinks #926 is an illegal node type
            q = q.replaceAll("\"", " "); // remove potential of mis-matched quotes
            q = q.replaceAll("“", " ");  // causes Galago to silently run an empty query
            q = q.replaceAll("”", " ");
            return q;
        }
    }

    /**
     * Filters certain characters that cause problems for the Galago query, but
     * this version is for strings that have Galago operators already in them,
     * so we don't filter out the # and ()
     * @param q
     * @return
     */
    public static String filterCertainCharactersPostTranslation(String q) {
        if (q == null || q.length() == 0) {
            return q;
        }
        else {
            q = q.replaceAll("\\.", " ");  // with the periods the queries hang
            q = q.replaceAll("@", " ");  // Galago has @ multi-word token thing
            q = q.replaceAll("\"", " "); // remove potential of mis-matched quotes
            q = q.replaceAll("“", " ");  // causes Galago to silently run an empty query
            q = q.replaceAll("”", " ");
            return q;
        }
    }

    public static String filterQuotes(String q) {
        if (q == null || q.length() == 0) {
            return q;
        }
        else {
            int x = q.indexOf("\"");
            q = q.replaceAll("\"", " ");
            x = q.indexOf("“");
            q = q.replaceAll("“", " ");
            x = q.indexOf("”");
            q = q.replaceAll("”", " ");
            return q;
        }
    }

    public static String filterCertainCharactersForFrench(String q) {
        if (q == null || q.length() == 0) {
            return q;
        }
        else {
            q = q.replaceAll("&#39;", "'");
            return q;
        }
    }

    /**
     * Copy constructor (deep copy)
     * @param otherTask The Task to make a copy of.
     */
    Task(Task otherTask) {
        this.taskNum = new String(otherTask.taskNum);
        this.taskTitle = (otherTask.taskTitle == null ? null : new String(otherTask.taskTitle));
        this.taskStmt = (otherTask.taskStmt == null ? null : new String(otherTask.taskStmt));
        this.taskNarr = (otherTask.taskNarr == null ? null : new String(otherTask.taskNarr));
        this.taskLink = (otherTask.taskLink == null ? null : new String(otherTask.taskLink));
        this.taskInScope = (otherTask.taskInScope == null ? null : new String(otherTask.taskInScope));
        this.requests = new TreeMap<String,Request>();
        for(Map.Entry<String, Request> entry : otherTask.requests.entrySet()) {
            this.requests.put(entry.getKey(), new Request(entry.getValue()));
        }
        this.taskExampleDocs = new ArrayList<ExampleDocument>(otherTask.taskExampleDocs);
        this.taskRelevantDocTextList = new ArrayList<String>();
        if (otherTask.taskRelevantDocTextList != null) {
            for (String x : otherTask.taskRelevantDocTextList) {
                this.taskRelevantDocTextList.add(x);
            }
        }
    }

    private void checkTaskField(JSONObject task, String fieldName) {
        if (!task.containsKey(fieldName)) {
            throw new TasksRunnerException("A task in the task file is missing its " + fieldName
                    + " field");
        }
    }

    private String getOptionalValue(JSONObject t, String field) {
        if (t.containsKey(field)) {
            return (String) t.get(field);
        } else {
            return "";
        }
    }
    /**
     * Contructs a Task from a JSON representation of an analytic task.
     * @param task The JSONObject version of the task.
     */
    Task(JSONObject task) {   // convert from JSON
        requests = new TreeMap<String,Request>();
        taskExampleDocs = new ArrayList<ExampleDocument>();
        taskRelevantDocTextList = new ArrayList<String>();

        checkTaskField(task, "task-num");
        checkTaskField(task, "task-docs");
        checkTaskField(task, "requests");

        this.taskNum = (String) task.get("task-num");
        this.taskTitle = Task.filterCertainCharacters((String) task.get("task-title"));
        this.taskStmt = Task.filterCertainCharacters((String) task.get("task-stmt"));
        this.taskNarr = Task.filterCertainCharacters((String) task.get("task-narr"));
        this.taskLink = (String) task.get("task-link");
        this.taskInScope = Task.filterCertainCharacters((String) task.get("task-in-scope"));

        Object od = task.get("task-docs");
        /* In old-style files, the task-docs field is an array of docids.
           In new-style files, the task-docs field is a dictionary, one entry per doc,
           with fields for doc-id, segment-text, and annotation-sets. We ignore all but the docid.
         */
        Boolean oldStyle = false;
        if (od instanceof JSONArray) {
            oldStyle = true;
            JSONArray taskDocs = (JSONArray) od;
            for (Object d : taskDocs) {
                taskExampleDocs.add(new ExampleDocument((String) d));
            }
        } else {
            JSONObject taskDocMap = (JSONObject) od;
            for (Object value : taskDocMap.keySet()) {
                String entryKey = (String) value;
                JSONObject taskDoc = (JSONObject) taskDocMap.get(entryKey);
                String docText = getOptionalValue(taskDoc, "doc-text");
                String docID = (String) taskDoc.get("doc-id");
                List<SentenceRange> sentences = new ArrayList<>();
                if (taskDoc.containsKey("sentences")) {
                    JSONArray jsonSentences = (JSONArray) taskDoc.get("sentences");
                    for (Object jsonObjectSentenceDescriptor : jsonSentences) {
                        JSONObject jsonSentenceDescriptor = (JSONObject) jsonObjectSentenceDescriptor;
                        long start = (long) jsonSentenceDescriptor.get("start");
                        long end = (long) jsonSentenceDescriptor.get("end");
                        long id = (long) jsonSentenceDescriptor.get("id");
                        String sentence = docText.substring((int) start, (int) end);
                        sentences.add(new SentenceRange((int) id, (int) start, (int) end, sentence));
                    }
                }
                String eventsAsSentences = "";
/*
                if (taskDoc.containsKey("annotation-sets")) {
                    JSONObject jsonAnnotationSets = (JSONObject) taskDoc.get("annotation-sets");
                    if (jsonAnnotationSets.containsKey("basic-events")) {
                        JSONObject jsonBasicEvents = (JSONObject) jsonAnnotationSets.get("basic-events");
                        if (Pathnames.IEAllowed) {
                            // Construct a set of sentences from the Mitre-provided events for this document
                            // and save them to be added to the doc text later, when example docs are expanded
                            eventsAsSentences = Event.getEventsAsSentencesFromJSON(jsonBasicEvents, docID);
//                            logger.info("Saving this events-as-sentences for docid " + docID + ":");
//                            logger.info(eventsAsSentences);
                        }
                    }
                }
*/
                taskExampleDocs.add(new ExampleDocument(docID, docText, sentences, eventsAsSentences));
            }
        }

/*       if (taskExampleDocs.size() == 0) {
            throw new BetterComponentException("A task (" + taskNum + ") in the task file has no example docs");
        }
*/
        JSONArray taskRequests = (JSONArray) task.get("requests");

        for (Object o : taskRequests) {
            Request r = new Request((JSONObject) o);
            requests.put(r.reqNum,r);
        }
    }

    public Map<String,Request> getRequests() { return requests; }

    public static JSONObject convertToJSON(Task task) {
        JSONObject top = new JSONObject();
        try {
            top.put("task-num", task.taskNum);
            top.put("task-title", task.taskTitle);
            top.put("task-stmt", task.taskStmt);
            top.put("task-narr", task.taskNarr);
            top.put("task-link", task.taskLink);

            List<ExampleDocument> taskDocs = task.taskExampleDocs;
            JSONObject targetDocsArray = new JSONObject();
            for (ExampleDocument d : taskDocs) {
                JSONObject targetExampleDoc = new JSONObject();
                targetExampleDoc.put("doc-id", d.getDocid());
                targetExampleDoc.put("doc-text", filterQuotes(d.getDocText()));

                JSONArray segmentSections = new JSONArray();
                List<SentenceRange> sentences = d.getSentences();
                if (sentences != null) {
                    for (SentenceRange sentence : sentences) {
                        JSONObject segmentSection = new JSONObject();
                        segmentSection.put("start", sentence.start);
                        segmentSection.put("end", sentence.end);
                        segmentSection.put("id", sentence.id);
                        segmentSection.put("text", sentence.text);
                        segmentSections.add(segmentSection);
                    }
                }
                targetExampleDoc.put("sentences", segmentSections);

                List<String> highlights = d.getHighlights();
                String highlight = "";
                if (highlights.size() > 0) {
                    highlight = highlights.get(0);
                }
                /* I am removing the filterQuotes because it was preventing the HITL program from matching the
                   hilight to the doc text. But will this break the query formulators or rerankers or neural end-to-end?
                 */
                //targetExampleDoc.put("highlight", filterQuotes(highlight));
                targetExampleDoc.put("highlight", highlight);

                JSONArray mitreEvents = new JSONArray();
                targetExampleDoc.put("mitre-events", mitreEvents);

                JSONArray eventsArray = new JSONArray();
                List<Event> events = d.getEvents();
                if (events != null) {
                    eventsArray = Event.getEventsJSON(events);
                }
                targetExampleDoc.put("isi-events", eventsArray);

                targetDocsArray.put(d.getDocid(), targetExampleDoc);
            }
            top.put("task-docs", targetDocsArray);

            JSONArray targetRequestsArray = new JSONArray();
            for (Request r : task.getRequests().values()) {
                JSONObject targetRequest = new JSONObject();
                targetRequest.put("req-num", r.reqNum);
                targetRequest.put("req-text", r.reqText);

                JSONObject targetRequestDocArray = new JSONObject();
                for (ExampleDocument d : r.reqExampleDocs) {
                    JSONObject targetExampleDoc = new JSONObject();
                    targetExampleDoc.put("doc-id", d.getDocid());
                    targetExampleDoc.put("doc-text", d.getDocText());

                    JSONArray segmentSections = new JSONArray();
                    List<SentenceRange> sentences = d.getSentences();
                    for (SentenceRange sentence : sentences) {
                        JSONObject segmentSection = new JSONObject();
                        segmentSection.put("start", sentence.start);
                        segmentSection.put("end", sentence.end);
                        segmentSection.put("id", sentence.id);
                        segmentSection.put("text", sentence.text);
                        segmentSections.add(segmentSection);
                    }
                    targetExampleDoc.put("sentences", segmentSections);

                    List<String> highlights = d.getHighlights();
                    String highlight = "";
                    if (highlights.size() > 0) {
                        highlight = highlights.get(0);
                    }
                    targetExampleDoc.put("highlight", highlight);
                    JSONArray mitreEvents = new JSONArray();
                    targetExampleDoc.put("mitre-events", mitreEvents);

                    JSONArray eventsArray = new JSONArray();
                    List<Event> events = d.getEvents();
                    if (events != null) {
                        eventsArray = Event.getEventsJSON(events);
                    }
                    targetExampleDoc.put("isi-events", eventsArray);

                    targetRequestDocArray.put(d.getDocid(), targetExampleDoc);
                }
                targetRequest.put("req-docs", targetRequestDocArray);

                targetRequestsArray.add(targetRequest);
            }
            top.put("requests", targetRequestsArray);
        } catch (Exception e) {
            throw new TasksRunnerException(e.getMessage());
        }
        return top;
    }

}

