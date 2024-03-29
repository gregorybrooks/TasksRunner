package edu.umass.ciir;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.util.*;
import java.util.logging.*;

public class AnalyticTasks {
    private static final Logger logger = Logger.getLogger("TasksRunner");
    /**
     * The analytic tasks definition file.
     * Pathnames are all defined in the Pathnames object, so they can be configured easily.
     */
    private static final String taskFilesDirectory = Pathnames.appFileLocation;
    private String taskFileName = "";
    private String internalAnalyticTasksInfoFileName = "";
    private String tasksAndRequestsFile = "";
    private String supplementalFile = "";
    private String mode = "";
    private String submissionId;
    private boolean sparse = false;

    /**
     * The tasks in the tasksAndRequestsFile after being converted into a Map of Task objects.
     */
    private final Map<String, Task> tasks = new LinkedHashMap<>();

    private final List<SupplementalExampleDocument> supplementalExampleDocs = new ArrayList<>();

    /**
     * Request iDs include the Task ID so they are unique across tasks. For convenience,
     * we build a Map of request ID to request object.
     */
    private final Map<String, Request> requestMap = new LinkedHashMap<>();

    /**
     * The "qrel" file that contains relevance judgments for the requests in this
     * analytic task definition.
     */
    private static final String qrelFile = Pathnames.qrelFileLocation + Pathnames.qrelFileName;
    /**
     * The relevance judgments after being converted to a Map.
     */
    private final Map<RelevanceJudgmentKey,RelevanceJudgment> relevanceJudgments =
            new HashMap<RelevanceJudgmentKey, RelevanceJudgment>();


    /**
     * Reads in the JSON file containing the analytic tasks and requests to be processed
     * and constructs a Map of Tasks and Requests that represents them.
     * Also reads in the file containing the relevance judgments for these requests.
     * See the log file called better-components.log for activity from these components.
     *
     * A note about exceptions: there are no exceptions that the caller is expected to handle
     * during execution--all exceptions are returned as the unchecked exception BetterComponentException,
     * which should terminate your program and contains the root cause exception and the
     * higher-level exception that gives it context.
     */
    public AnalyticTasks(String mode, String submissionId) {
        this.sparse = false;
        this.mode = mode;
        this.submissionId = submissionId;
        this.internalAnalyticTasksInfoFileName = Pathnames.eventExtractorFileLocation
                + submissionId + ".analytic_tasks.json";
        taskFileName = Pathnames.tasksFileName;
        openFiles();
    }

    private void setDefaults() {
        this.internalAnalyticTasksInfoFileName = Pathnames.eventExtractorFileLocation
                + submissionId + ".analytic_tasks.json";
        taskFileName = Pathnames.tasksFileName;
        this.sparse = false;
    }

    private void openFiles() {
        try {
            tasksAndRequestsFile = Pathnames.appFileLocation + taskFileName;
            supplementalFile = "/home/tasksrunner/scripts/supplemental_info.json";
            logger.info("Opening task definition file " + tasksAndRequestsFile);

            /* Get task and request definitions */
            if (!Pathnames.analyticTasksFileFormat.equals("BETTER")
                    && !Pathnames.analyticTasksFileFormat.equals("FARSI")) {
                throw new TasksRunnerException("Invalid analytic tasks file format: "
                        + Pathnames.analyticTasksFileFormat + ". Must be BETTER or FARSI");
            }
            try {
                if (Pathnames.analyticTasksFileFormat.equals("BETTER")) {
                    readTaskFile();
                } else if (Pathnames.analyticTasksFileFormat.equals("FARSI")) {
                    readTaskFileFarsi();
                }
            } catch (Exception e) {
                throw new TasksRunnerException(e);
            }

            if (!sparse) {
//                if (mode.equals("HITL")) {
//                    logger.info("Opening supplemental task definition file " + supplementalFile);
//                    readSupplementalFile();
//                }

                /* Get relevance judgments for these requests.
                 * Unless there are no relevance judgments available for this analytic task file / corpus
                 * pair.
                 */
                if (Pathnames.readQrelFile) {
                    readQRELFile();
                }

                /* Expand the docids of the relevance judgments into actual doc texts */
                expandRelevantAndExampleDocs();

                /* Add the relevant docs to the Tasks */
                addRelevantDocsToTasks();
            }

            /* Make a map of requestID-to-request object for convenience */
            buildRequestMap();

        } catch (Exception e) {
            throw new TasksRunnerException(e);
        }
    }

    public String getInternalAnalyticTasksInfoFileName() {
        return internalAnalyticTasksInfoFileName;
    }

    private void addSupplementalDocToRequest(SupplementalExampleDocument doc) {
        Request r = findRequest(doc.getRequestID());
        if (r == null) {
            throw new TasksRunnerException("Request in supplemental doc file not valid: " + doc.getRequestID());
        }
        ExampleDocument exDoc = new ExampleDocument(doc.getDocid(), doc.getHighlights());
        if (!r.isInExampleDocs(doc.getDocid())) {
            r.reqExampleDocs.add(exDoc);
        }
    }

    private void addSupplementalDocToTask(SupplementalExampleDocument doc) {
        Task t = findTask(doc.getTaskID());
        if (t == null) {
            throw new TasksRunnerException("Task in supplemental doc file not valid: " + doc.getTaskID());
        }
        ExampleDocument exDoc = new ExampleDocument(doc.getDocid(), doc.getHighlights());
        if (!t.isInExampleDocs(doc.getDocid())) {
            t.taskExampleDocs.add(exDoc);
        }
    }

    private void addSupplementalDocsToRequests() {
        for (SupplementalExampleDocument supDoc : supplementalExampleDocs) {
            long score = supDoc.getScore();
            if (score == 3) {
                addSupplementalDocToTask(supDoc);
            } else if (score == 4 || score == 5) {
                addSupplementalDocToRequest(supDoc);
            }
        }
    }

    /**
     * Builds a map of request ID to Request object, for all of
     * the requests in the analytic tasks definition.
     */
    private void buildRequestMap() {
        for (Task t : tasks.values()) {
            for (Map.Entry<String, Request> requestEntry : t.requests.entrySet()) {
                requestMap.put(requestEntry.getKey(), requestEntry.getValue());
            }
        }
    }

    /**
     * Returns the fileName (not the full pathname) of this analytic task definition.
     * @return the fileName (not the full pathname) of this analytic task definition
     */
    public String getTaskFileName() {
        return taskFileName;
    }

    /**
     * Returns the number of requests in the analytic tasks definition.
     * @return the number of requests in the analytic tasks definition
     */
    public int numRequests() {
        return requestMap.keySet().size();
    }

    private void addRelevantDocsToTasks() {
        for (Task t : tasks.values()) {
            for (Request r : t.requests.values()) {
                r.relevanceJudgments = getPositiveRelevanceJudgmentObjects(r.reqNum);
            }
        }
    }

    public List<String> getAllExampleDocIDs() {
        List<String> outList = new ArrayList<>();
        for (Task t : tasks.values()) {
            for (ExampleDocument d : t.taskExampleDocs) {
                outList.add(d.getDocid());
            }
            for (Request r : t.requests.values()) {
                for (ExampleDocument d : r.reqExampleDocs) {
                    outList.add(d.getDocid());
                }
            }
        }
        return outList;
    }

    /**
     * Reads in the document text for all documents we are going to need
     * (example docs and optionally relevance judgment docs)
     */
    private void expandRelevantAndExampleDocs() {
        /* New way: doctext and sentences are already in the ir-tasks.json and have been loaded into memory */
        /*
        for (Task t : tasks.values()) {
            for (ExampleDocument d : t.taskExampleDocs) {
                Document.addDocToMap(d.getDocid(), d.getDocText());
                Document.addDocSentencesToMap(d.getDocid(), d.getSentences());
            }
            for (Request r : t.requests.values()) {
                for (ExampleDocument d : r.reqExampleDocs) {
                    Document.addDocToMap(d.getDocid(), d.getDocText());
                    Document.addDocSentencesToMap(d.getDocid(), d.getSentences());
                }
            }
        }
        */
        /* OLD WAY: */
        // Build the list of docids to pass to Document.buildDocMap
        Set<String> uniqueDocids = new HashSet<>();
        for (String docid : getAllExampleDocIDs()) {
            uniqueDocids.add(docid);
        }
        Document.buildDocMap(uniqueDocids);

        /* Expand all task and request example docs */
        /* OLD WAY */
        for (Task t : tasks.values()) {
            for (ExampleDocument d : t.taskExampleDocs) {
//                logger.info("Adding this to " + d.getDocid() + "'s text:");
//                logger.info(d.getEventsAsSentences());
                d.setDocText(Document.getDocumentWithMap(d.getDocid()) + " " + d.getEventsAsSentences());
                d.setSentences(Document.getDocumentSentences(d.getDocid()));
            }
            for (Request r : t.requests.values()) {
                for (ExampleDocument d2 : r.reqExampleDocs) {
//                   logger.info("Adding this to " + d2.getDocid() + "'s text:");
//                    logger.info(d2.getEventsAsSentences());
                    d2.setDocText(Document.getDocumentWithMap(d2.getDocid()) + " " + d2.getEventsAsSentences());
                    d2.setSentences(Document.getDocumentSentences(d2.getDocid()));
                }
            }
        }

        /*
        Expand all positive relevance judgment docs
        (but not if the relevance judgments are for a corpus we don't have)
        */
        if (Pathnames.expandQrelDocuments) {
            Set<String> uniqueRelevantDocids = new HashSet<>();
            for (RelevanceJudgmentKey k : relevanceJudgments.keySet())
                uniqueRelevantDocids.add(k.docid);
            Document.buildTargetDocMap(uniqueRelevantDocids);
            for (Map.Entry<RelevanceJudgmentKey, RelevanceJudgment> entry : relevanceJudgments.entrySet()) {
                RelevanceJudgmentKey k = entry.getKey();
                RelevanceJudgment j = entry.getValue();
                if (j.judgment.isRelevant()) {
                    j.setDocText(Document.getTargetDocumentWithMap(k.docid));
                    j.setSentences(Document.getTargetDocumentSentences(k.docid));
                    j.setLanguage(Document.getLanguage(k.docid));
                }
            }
        }
    }

    private class RelevanceJudgmentKey {
        String requestID;
        String docid;
        RelevanceJudgmentKey(String requestID, String docid) {
            this.requestID = requestID;
            this.docid = docid;
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RelevanceJudgmentKey oKey = (RelevanceJudgmentKey) o;
            if (!requestID.equals(oKey.requestID)) return false;
            return (docid.equals(oKey.docid));
        }
        @Override
        public int hashCode() {
            int result = requestID.hashCode();
            result = 31 * result + (docid.hashCode());
            return result;
        }
    }

    /**
     * Reads the relevance judgments file and creates relevanceJudgments.
     * @throws IOException if there is a problem reading the file
     */
    private void readQRELFile() throws IOException {
        File f = new File(qrelFile);
        if (f.exists()) {
            logger.info("Opening qrel file " + qrelFile);
            BufferedReader qrelReader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(qrelFile)));
            String line = qrelReader.readLine();
            while (line != null) {
                String[] tokens = line.split(" ");
                String requestID = tokens[0];
                String docid = tokens[1];
                String judgment = tokens[2];
                RelevanceJudgment j = new RelevanceJudgment(requestID, docid, "", "", judgment, "");
                RelevanceJudgmentKey jk = new RelevanceJudgmentKey(requestID,docid);
                relevanceJudgments.put(jk, j);
                line = qrelReader.readLine();
            }
            qrelReader.close();
        } else {
            logger.info("Qrel file " + qrelFile + " not found");
        }
    }

    /**
     * Gets the relevance judgment for a given request ID and document ID.
     * @param requestID the request ID
     * @param docid the document ID, with dashes
     * @return the relevance judgment object
     */
    public RelevanceJudgment getRelevanceJudgment(String requestID, String docid) {
        RelevanceJudgmentKey jk = new RelevanceJudgmentKey(requestID,docid);
        RelevanceJudgment j = relevanceJudgments.get(jk);
        if (j == null) { // for unjudged, assume not relevant
            return new RelevanceJudgment(requestID, docid, "", "", "0", "");
        } else {
            return j;
        }
    }

    /**
     * Returns the relevance judgment integer value, mapped as MITRE maps them for the IR
     * nDCG calculation, for the given request and doc ID.
     * @param requestID the request ID
     * @param docid the doc ID
     * @return the mapped relevance judgment integer value
     */
    public int getRelevanceJudgmentValueWithMapping(String requestID, String docid) {
        RelevanceJudgmentKey jk = new RelevanceJudgmentKey(requestID,docid);
        RelevanceJudgment j = relevanceJudgments.get(jk);
        if (j == null) {
            return 0;  // for unjudged, assume not relevant
        } else {
            return j.getRelevanceJudgmentValueWithMapping();
        }
    }

    /**
     * Returns the relevance judgment integer value for the given request and doc ID.
     * @param requestID the request ID
     * @param docid the doc ID
     * @return the relevance judgment integer value
     */
    public int getRelevanceJudgmentValue(String requestID, String docid) {
        RelevanceJudgmentKey jk = new RelevanceJudgmentKey(requestID,docid);
        RelevanceJudgment j = relevanceJudgments.get(jk);
        if (j == null) {
            return 0;  // for unjudged, assume not relevant
        } else {
            return j.getRelevanceJudgmentValue();
        }
    }

    /**
     * Returns true if this request has any relevance judgments, else return false.
     * @param requestID the request ID
     * @return true if this request has any relevance judgments else false
     */
    public Boolean hasRelevanceJudgments(String requestID) {
        for (Map.Entry<RelevanceJudgmentKey, RelevanceJudgment> entry : relevanceJudgments.entrySet()) {
            RelevanceJudgmentKey k = entry.getKey();
            RelevanceJudgment j = entry.getValue();
            if (k.requestID.equals(requestID)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a list of relevance judgments for this request, but only those for documents
     * that are not also example documents (req-docs and task-docs), and only for those where
     * the judgment is relevant in some way (not "not relevant").
     * @param requestID the request ID
     * @return a list of relevance judgments
     */
    public List<RelevanceJudgment> getPositiveNonExampleRelevanceJudgments(String requestID) {
        List<RelevanceJudgment> judgments = new ArrayList<>();
        for (Map.Entry<RelevanceJudgmentKey, RelevanceJudgment> entry : relevanceJudgments.entrySet()) {
            RelevanceJudgmentKey k = entry.getKey();
            RelevanceJudgment j = entry.getValue();
            if (k.requestID.equals(requestID)) {
                if (j.judgment.isRelevant() ) { /* Don't include judgments of "not relevant" */
                    /* Omit the example docs from the known relevant docs, like we omit them from run results */
                    if (!isDocInReqDocList(requestID, k.docid) && !isDocInTaskDocList(requestID, k.docid)) {
                        judgments.add(new RelevanceJudgment(j));
                    }
                }
            }
        }
        return judgments;
    }

    /**
     * Returns a list of relevance judgments (the objects) for this task, but only for those where
     * the judgment is relevant in some way (not "not relevant").
     * Task-level relevance judgments are judgments for all of the requests contained by this task.
     * @param taskID the task ID
     * @return a list of relevance judgments
     */
    public List<RelevanceJudgment> getPositiveTaskLevelRelevanceJudgmentObjects(String taskID) {
        Set<String> judgments = new HashSet<>();
        List<RelevanceJudgment> judgmentObjects = new ArrayList<>();
        for (Map.Entry<RelevanceJudgmentKey, RelevanceJudgment> entry : relevanceJudgments.entrySet()) {
            RelevanceJudgmentKey k = entry.getKey();
            RelevanceJudgment j = entry.getValue();
            String tid = parseTaskIDFromRequestID(k.requestID);
            if (tid.equals(taskID)) {
                if (j.judgment.isRelevant() ) { /* Don't include judgments of "not relevant" */
                    if (!judgments.contains(k.docid )) {
                        judgments.add(k.docid);
                        judgmentObjects.add(j);
                    }
                }
            }
        }
        return judgmentObjects;
    }

    /**
     * Returns a set of the docids of the relevance judgments for this task, but only for those where
     * the judgment is relevant in some way (not "not relevant").
     * Task-level relevance judgments are judgments for all of the requests contained by this task.
     * @param taskID the task ID
     * @return a set of docids
     */
    public Set<String> getPositiveTaskLevelRelevanceJudgments(String taskID) {
        Set<String> judgments = new HashSet<>();
        for (Map.Entry<RelevanceJudgmentKey, RelevanceJudgment> entry : relevanceJudgments.entrySet()) {
            RelevanceJudgmentKey k = entry.getKey();
            RelevanceJudgment j = entry.getValue();
            String tid = parseTaskIDFromRequestID(k.requestID);
            if (tid.equals(taskID)) {
                if (j.judgment.isRelevant() ) { /* Don't include judgments of "not relevant" */
                    judgments.add(k.docid);
                }
            }
        }
        return judgments;
    }

    /**
     * Returns a set of the docids of the relevance judgments for this request, but only for those where
     * the judgment is relevant in some way (not "not relevant").
     * @param requestID the request ID
     * @return a set of docids
     */
    public Set<String> getPositiveRequestLevelRelevanceJudgments(String requestID) {
        Set<String> judgments = new HashSet<>();
        for (Map.Entry<RelevanceJudgmentKey, RelevanceJudgment> entry : relevanceJudgments.entrySet()) {
            RelevanceJudgmentKey k = entry.getKey();
            RelevanceJudgment j = entry.getValue();
            if (k.requestID.equals(requestID)) {
                if (j.judgment.isRelevant() ) { /* Don't include judgments of "not relevant" */
                    judgments.add(k.docid);
                }
            }
        }
        return judgments;
    }

    /**
     * Returns a set of relevance judgment objects for this request, but only for those where
     * the judgment is relevant in some way (not "not relevant").
     * There will be one relevance judgment object per request/docid.
     * @param requestID the request ID
     * @return a set of relevance judgment objects
     */
    public Set<RelevanceJudgment> getPositiveRelevanceJudgmentObjects(String requestID) {
        Set<RelevanceJudgment> judgments = new HashSet<>();
        for (Map.Entry<RelevanceJudgmentKey, RelevanceJudgment> entry : relevanceJudgments.entrySet()) {
            RelevanceJudgmentKey k = entry.getKey();
            RelevanceJudgment j = entry.getValue();
            if (k.requestID.equals(requestID) && (j.judgment.isRelevant())) {
                judgments.add(j);
            }
        }
        return judgments;
    }

    /**
     * Returns a list of relevance judgment objects for this request, but only for those where
     * the judgment is relevant in some way (not "not relevant").
     * There might be multiple relevance judgment objects per request/docid.
     * @param requestID the request ID
     * @return a list of relevance judgment objects
     */
    public List<RelevanceJudgment> getPositiveRelevanceJudgments(String requestID) {
        List<RelevanceJudgment> judgments = new ArrayList<>();
        for (Map.Entry<RelevanceJudgmentKey, RelevanceJudgment> entry : relevanceJudgments.entrySet()) {
            RelevanceJudgmentKey k = entry.getKey();
            RelevanceJudgment j = entry.getValue();
            if (k.requestID.equals(requestID)) {
                if (j.judgment.isRelevant() ) { /* Don't include judgments of "not relevant" */
                    judgments.add(new RelevanceJudgment(j));
                }
            }
        }
        return judgments;
    }

    /**
     * Returns a list of relevance judgment objects for this request.
     * There might be multiple relevance judgment objects per request/docid.
     * @param requestID the request ID
     * @return a list of relevance judgment objects
     */
    public List<RelevanceJudgment> getRelevanceJudgments(String requestID) {
        List<RelevanceJudgment> judgments = new ArrayList<>();
        for (Map.Entry<RelevanceJudgmentKey, RelevanceJudgment> entry : relevanceJudgments.entrySet()) {
            RelevanceJudgmentKey k = entry.getKey();
            RelevanceJudgment j = entry.getValue();
            if (k.requestID.equals(requestID)) {
                judgments.add(new RelevanceJudgment(j));
            }
        }
        return judgments;
    }

    /**
     * Returns a list of docids from all relevance judgments for this request,
     * but only those for documents that are not also example documents (req-docs and task-docs),
     * and only for those where the judgment is relevant in some way (not "not relevant").
     * @param requestID the request ID
     * @return a list of docids
     */
    public List<String> getTaskAndRequestRelevantDocids(String requestID) {
        List<String> docids = new ArrayList<>();
        for (Map.Entry<RelevanceJudgmentKey,RelevanceJudgment> entry : relevanceJudgments.entrySet()) {
            RelevanceJudgmentKey k = entry.getKey();
            RelevanceJudgment j = entry.getValue();
            if (k.requestID.equals(requestID)
                    && (j.judgment.isRelevant())) {
                /* Omit the example docs from the relevant docs, like we omit them from run results */
                String taskID = parseTaskIDFromRequestID(requestID);
                Task t = findTask(taskID);
                if (t != null &&  !t.isInExampleDocs(k.docid)) {
                    docids.add(k.docid);
                }
//                else {
//                    System.out.println("Skipping example doc " + k.docid);
//                }
            }
        }
        return docids;
    }

    /**
     * Returns a list of docids from all relevance judgments for request,
     * but only those for documents that are not also example documents (req-docs and task-docs),
     * and only for those where the judgment is request-relevant (not just TOPICALLY_RELEVANT).
     * @param requestID the request ID
     * @return a list of docids
     */
    public List<String> getRequestRelevantDocids(String requestID) {
        List<String> docids = new ArrayList<>();
        for (Map.Entry<RelevanceJudgmentKey,RelevanceJudgment> entry : relevanceJudgments.entrySet()) {
            RelevanceJudgmentKey k = entry.getKey();
            RelevanceJudgment j = entry.getValue();
            if (k.requestID.equals(requestID)
                    && ((j.judgment == RelevanceJudgment.RelevanceJudgmentType.SPECIFIC_INFORMATION)
                    || (j.judgment == RelevanceJudgment.RelevanceJudgmentType.DIRECT_ANSWER)
                    || (j.judgment == RelevanceJudgment.RelevanceJudgmentType.DECISIONAL))) {
                /* Omit the example docs from the relevant docs, like we omit them from run results */
                String taskID = parseTaskIDFromRequestID(requestID);
                Task t = findTask(taskID);
                if (t != null &&  !t.isInExampleDocs(k.docid)) {
                    docids.add(k.docid);
                }
//                else {
//                    System.out.println("Skipping example doc " + k.docid);
//                }
            }
        }
        return docids;
    }

    /** Returns a list of the Task objects for this analytic task definition.
     *
     * @return a list of the Task objects for this analytic task definition
     */
    public List<Task> getTaskList() {
        List<Task> taskList = new ArrayList<>();
        for (Map.Entry<String,Task> entry : tasks.entrySet()) {
            Task t = new Task(entry.getValue());  // deep copy via copy constructor
            taskList.add(t);
        }
        return taskList;
    }

    public void writeJSONVersion()  {
        writeJSONVersionToFile(internalAnalyticTasksInfoFileName);
    }

    public void writeJSONVersionToFile(String fileName)  {
        try {
            JSONArray targetTopArray = new JSONArray();
            for (Map.Entry<String,Task> entry : tasks.entrySet()) {
                Task t = new Task(entry.getValue());  // deep copy via copy constructor
                JSONObject j = Task.convertToJSON(t);
                targetTopArray.add(j);
            }
            logger.info("Writing analytic_tasks.json to " + fileName);
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(fileName)));
            writer.write(targetTopArray.toJSONString());
            writer.close();
        } catch (Exception e) {
            throw new TasksRunnerException(e);
        }
    }

    /**
     * Reads in the analytic task definition file and creates tasks.
     * @throws IOException if it has a problem reading the file
     */
    private void readTaskFileFarsi() throws IOException, ParseException {
        BufferedReader rdr = new BufferedReader(new InputStreamReader(new FileInputStream(tasksAndRequestsFile)));
        String line;
        int id = 0;
        String taskNum = "";
        String taskTitle = "";
        String taskNarr = "";
        String taskLink = "";
        String reqNum = "";
        String reqText = "";
        while ((line = rdr.readLine()) != null) {
            if (line.startsWith("<?xml ")) {
            } else if (line.startsWith("<topics>")) {
            } else if (line.startsWith("</topics>")) {
            } else if (line.startsWith("<topic>")) {
                ++id;
            } else if (line.startsWith("</topic>")) {
                reqNum = taskNum; // one-to-one, qrels use this identifier
                Request r = new Request(reqNum, reqText);
                Map<String,Request> requests = new TreeMap<>();
                requests.put(reqNum, r);
                Task t = new Task(taskNum, taskTitle, taskNarr, taskLink, requests);
                tasks.put(t.taskNum, t);
            } else if (line.startsWith("<identifier>")) {
                line = line.replace("<identifier>", "");
                line = line.replace("</identifier>", "");
                taskNum = line;
            } else if (line.startsWith("<title>")) {
                line = line.replace("<title>", "");
                line = line.replace("</title>", "");
                taskTitle = line;
            } else if (line.startsWith("<description>")) {
                line = line.replace("<description>", "");
                line = line.replace("</description>", "");
                reqText = line;
            } else if (line.startsWith("<narrative>")) {
                line = line.replace("<narrative>", "");
                line = line.replace("</narrative>", "");
                taskNarr = line;
            }
        }
    }

    /**
     * Reads in the analytic task definition file and creates tasks.
     * @throws IOException if it has a problem reading the file
     * @throws ParseException if it has a problem parsing the file
     */
    private void readTaskFile() throws IOException, ParseException {
        Reader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(tasksAndRequestsFile)));
        JSONParser parser = new JSONParser();
        JSONArray tasksJSON = (JSONArray) parser.parse(reader);
        for (Object oTask : tasksJSON) {
            Task t = new Task((JSONObject) oTask);  // this gets Requests, too
            tasks.put(t.taskNum, t);
        }
    }


    private void readSupplementalFile() {
        try {
            File f = new File(supplementalFile);
            if (f.exists()) {
                Reader reader = new BufferedReader(new InputStreamReader(
                        new FileInputStream(supplementalFile)));

                JSONParser parser = new JSONParser();
                JSONObject topLevel = (JSONObject) parser.parse(reader);
                JSONArray entriesJSON = (JSONArray) topLevel.get("entries");
                for (Object oTask : entriesJSON) {
                    JSONObject entry = (JSONObject) oTask;
                    String tempDocid = (String) entry.get("doc-id");
                    String[] parts = tempDocid.split("--");
                    String docid = parts[2];
                    String requestID = (String) entry.get("req-num");
                    String taskID = (String) entry.get("task-num");
                    long score = (long) entry.get("score");
                    JSONArray highlightsJSON = (JSONArray) entry.get("highlights");
                    List<String> highlights = new ArrayList<>();
                    for (Object highlightJSON : highlightsJSON) {
                        String highlight = (String) highlightJSON;
                        highlights.add(highlight);
                    }
                    SupplementalExampleDocument doc = new SupplementalExampleDocument(
                            taskID, requestID, docid, score, highlights);
                    supplementalExampleDocs.add(doc);
                }
                addSupplementalDocsToRequests();
            } else {
                logger.info("Supplemental file " + supplementalFile + " not present");
            }
        } catch (Exception cause) {
            throw new TasksRunnerException(cause);
        }
    }
    /**
     * Returns a list of all of the request IDs in this analytic task definition.
     * @return a list of all of the request IDs in this analytic task definition
     */
    public List<String> getRequestIDs() {
        List<String> list = new ArrayList<String>();
        for (Map.Entry<String,Task> entry : tasks.entrySet()) {
            Task t = entry.getValue();
            for (Map.Entry<String, Request> requestEntry : t.requests.entrySet()) {
                String requestNbr = requestEntry.getKey();
                list.add(requestNbr);
            }
        }
        return list;
    }

    public List<Request> getRequests() {
        List<Request> list = new ArrayList<>(requestMap.values());
        return list;
    }

    /**
     * Returns a list of all of the task IDs in this analytic task definition.
     * @return
     */
    public List<String> getTaskIDs() {
        List<String> list = new ArrayList<String>();
        for (String taskID : tasks.keySet()) {
            list.add(taskID);
        }
        return list;
    }

    /**
     * Returns the map of task definitions.
     * @return the map of task definitions
     */
    public Map<String,Task> getTasks() { return tasks; }

    /**
     * Returns the task object identified by the given task ID.
     * @param taskNum the task ID
     * @return the task object identified by the given task ID, or null
     */
    public Task findTask(String taskNum) {
        return tasks.get(taskNum);
    }

    /**
     * Returns the request object identified by the request ID, for the given task.
     * @param t the task (object) that this request belongs to
     * @param requestNum the ID of the request
     * @return the request object identified by the request ID, for the given task
     */
    public Request findRequest(Task t, String requestNum) {
        return t.requests.get(requestNum);
    }

    /**
     * Returns true if the given docid is included in this request's example docs,
     * else false.
     * @param requestID the request ID
     * @param docid the docid
     * @return true if the given docid is included in this request's example docs,
     *      * else false
     */
    public Boolean isDocInReqDocList(String requestID, String docid) {
        Request r = findRequest(requestID);
        return (r != null &&  r.isInExampleDocs(docid));
    }

    /**
     * Returns true if the given docid is included in this task's example docs,
     * else false.
     * @param taskID the task ID
     * @param docid the docid
     * @return true if the given docid is included in this task's example docs, else false
     */
    public Boolean isDocInTaskDocList(String taskID, String docid) {
        Task t = findTask(taskID);
        return (t != null &&  t.isInExampleDocs(docid));
    }

    private String parseTaskIDFromRequestID(String requestID) {
        String[] parts = requestID.split("-");
        if (parts.length != 3) {
            throw new TasksRunnerException("Bad request ID: " + requestID);
        }
        String taskID = parts[0] + "-" + parts[1];
        return taskID;
    }

    /**
     * Returns the request object identified by the given request ID.
     * This version does not require a task ID.
     * @param requestID the request ID
     * @return the request object identified by the given request ID, or null
     */
    public Request findRequest(String requestID) {
        String taskID = parseTaskIDFromRequestID(requestID);
        Task t = findTask(taskID);
        if (t == null) {
            return null;
        }
        return t.requests.get(requestID);
    }

    /**
     * For each task, forces the taskDocList field to contain the docids from all of
     * that task's requests.
     * We do this so we have a handy list of all the example docs at the task or
     * request level for the "E2" evaluation approach.
     */
    public void fixTaskDocs() {
        tasks.forEach((k,v)->{
            String taskName = k;
            Task t = v;
            t.requests.forEach((rk,rv)->{
                String requestName = rk;
                Request r = rv;
                r.reqExampleDocs.forEach((d)->{
                    if (!t.isInExampleDocs(d.getDocid())) {
                        t.taskExampleDocs.add(d);
                    }
                });
            });
        });
    }

}
