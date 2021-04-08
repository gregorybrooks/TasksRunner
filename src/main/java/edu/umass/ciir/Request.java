package edu.umass.ciir;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.*;
import java.util.logging.Logger;

/**
 * Represents a specific analytic request within the larger analytic task (@see ConvertDryRunTasks.Task).
 */
public class Request {
    private static final Logger logger = Logger.getLogger("TasksRunner");
    public String reqNum;
    public String reqText;
    public List<ExampleDocument> reqExampleDocs;
    public Set<RelevanceJudgment> relevanceJudgments;

    private void checkRequestField(JSONObject request, String fieldName) {
        if (!request.containsKey(fieldName)) {
            throw new TasksRunnerException("A request in the task file is missing its " + fieldName
                    + " field");
        }
    }

    /**
     * Constructs a Request from a JSON representation of the analytic request.
     * @param request The JSONObject version of the request.
     */
    Request(JSONObject request) {
        checkRequestField(request, "req-num");
        checkRequestField(request, "req-docs");

        reqNum = (String) request.get("req-num");
        reqText = Task.filterCertainCharacters((String) request.get("req-text"));
        JSONArray reqDocTexts = (JSONArray) request.get("req-doc-texts");

        reqExampleDocs = new ArrayList<ExampleDocument>();

        /* In old-style files, the req-docs field is an array of docids.
           In new-style files, the req-docs field is a dictionary, one entry per doc,
           with fields for doc-id, segment-text, and annotation-sets. We ignore the
           event annotations, but the segment-text is the passage from this
           example doc that is relevant to this request.
           We extract that sentence and call it the "req-extr" to be compatible with the old style.
         */
        Boolean oldStyle = false;
        Object reqDocsObject = request.get("req-docs");
        if (reqDocsObject instanceof JSONArray) {
            oldStyle = true;
            JSONArray taskDocs = (JSONArray) reqDocsObject;
            for (Object d : taskDocs) {
                reqExampleDocs.add(new ExampleDocument((String) d));
            }
        } else {
            JSONObject tds = (JSONObject) reqDocsObject;
            for (Iterator iterator = tds.keySet().iterator(); iterator.hasNext(); ) {
                String entryKey = (String) iterator.next();
                JSONObject reqDoc = (JSONObject) tds.get(entryKey);
                List<String> highlights = new ArrayList<>();
                if (reqDoc.containsKey("segment-text")) {
                    highlights.add( Task.filterCertainCharacters((String) reqDoc.get("segment-text")));
                }
                reqExampleDocs.add(new ExampleDocument(entryKey, highlights));
            }
        }

 /*       if (reqExampleDocs.size() == 0) {
            throw new BetterComponentException("A request (" + reqNum + ") in the task file has no example docs");
        }
*/
/* We don't use this anymore
        if (reqDocTexts != null) {
            for (Object d : reqDocTexts) {
                reqDocTextList.add((String) d);
            }
        }
*/
        if (oldStyle) {
            String extractions = "";
            JSONArray reqExtr = (JSONArray) request.get("req-extr");
            for (Object d : reqExtr) {
                extractions += ((Task.filterCertainCharacters((String) d)) + " ");
            }
            ExampleDocument d = reqExampleDocs.get(0);
            List<String> highlights = d.getHighlights();
            highlights.add(extractions);
        }
    }

    public boolean isInExampleDocs(String docid) {
        for (ExampleDocument d : reqExampleDocs) {
            if (docid.equals(d.getDocText())) {
                return true;
            }
        }
        return false;
    }
    /**
     * Copy constructor (deep copy)
     * @param otherRequest The Request to make a copy of.
     */
    Request(Request otherRequest) {
        this.reqNum = otherRequest.reqNum;
        this.reqText = otherRequest.reqText;
        this.reqExampleDocs = new ArrayList<ExampleDocument>(otherRequest.reqExampleDocs);
        this.relevanceJudgments = new HashSet<RelevanceJudgment>();
        if (otherRequest.relevanceJudgments != null) {
            for (RelevanceJudgment j : otherRequest.relevanceJudgments) {
                this.relevanceJudgments.add(new RelevanceJudgment(j));
            }
        }
    }

    public List<String> getReqExtrList() {
        List<String> extractions = new ArrayList<>();
        for (ExampleDocument d : reqExampleDocs) {
            extractions.addAll(d.getHighlights());
        }
        return extractions;
    }

    public List<String> getExampleDocids() {
        List<String> docids = new ArrayList<>();
        for (ExampleDocument d : reqExampleDocs) {
            docids.add(d.getDocid());
        }
        return docids;
    }


}
