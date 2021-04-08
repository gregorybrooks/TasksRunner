package edu.umass.ciir;

import java.util.ArrayList;
import java.util.List;

public class SupplementalExampleDocument {
    private String docid;
    private String taskID;
    private String requestID;
    private long score;
    private List<String> highlights;
    String docText;

    SupplementalExampleDocument(String taskID, String requestID, String docid,
                                long score, List<String> highlights) {
        this.highlights = new ArrayList<>(highlights);
        this.docid = docid;
        this.taskID = taskID;
        this.requestID = requestID;
        this.score = score;
    }

    public String getDocid() {
        return docid;
    }

    public String getTaskID() {
        return taskID;
    }

    public String getRequestID() {
        return requestID;
    }

    public long getScore() {
        return score;
    }

    public List<String> getHighlights() {
        return highlights;
    }

    public void setDocText(String docText) {
        this.docText = docText;
    }

    public String getDocText() {
        return docText;
    }
}

