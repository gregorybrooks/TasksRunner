package edu.umass.ciir;

import java.util.Objects;

public class Submission {
    private String id;
    private String taskNum;
    private String reqNum;
    private String status;
    private String when;
    private SearchResults searchResults;

    Submission(String taskNum, String reqNum) {
        this.taskNum = taskNum;
        this.reqNum = reqNum;
    }

    Submission(Submission otherSubmission) {
        this.id = otherSubmission.id;
        this.when = otherSubmission.when;
        this.taskNum = otherSubmission.taskNum;
        this.reqNum = otherSubmission.reqNum;
        this.status = otherSubmission.status;
        this.searchResults = otherSubmission.searchResults;
    }

    public Submission() { }

    public SearchResults getSearchResults() {
        return searchResults;
    }

    public void setSearchResults(SearchResults searchResults) {
        this.searchResults = searchResults;
    }

    public String getTaskNum() {
        return taskNum;
    }

    public String getReqNum() {
        return reqNum;
    }

    public void setReqNum(String reqNum) {
        this.reqNum = reqNum;
    }

    public void setTaskNum(String taskNum) {
        this.taskNum = taskNum;
    }

    public String getId() {
        return this.id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public String getStatus() {
        return this.status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
    public String getWhen() {
        return this.when;
    }
    public void setWhen(String when) {
        this.when = when;
    }
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Submission))
            return false;
        Submission submission = (Submission) o;
        return Objects.equals(this.id, submission.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    @Override
    public String toString() {
        return "Submission{" + "id=" + this.id + '}';
    }
}

