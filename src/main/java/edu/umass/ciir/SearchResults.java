package edu.umass.ciir;

import java.util.ArrayList;
import java.util.List;

public class SearchResults {
    String taskNum;
    String taskTitle;
    String taskStmt;
    String taskNarr;
    String reqNum;
    String reqText;
    int totalNumHits;
    List<SearchHit> hits;

    public SearchResults(String taskNum, String taskTitle, String taskStmt,
                         String taskNarr, String reqNum, String reqText, int totalNumHits, List<SearchHit> hits) {
        this.taskNum = taskNum;
        this.taskTitle = taskTitle;
        this.taskStmt = taskStmt;
        this.taskNarr = taskNarr;
        this.reqNum = reqNum;
        this.reqText = reqText;
        this.totalNumHits = totalNumHits;
        this.hits = new ArrayList<>(hits);
    }

    public SearchResults() {}

    public String getTaskNum() {
        return taskNum;
    }

    public void setTaskNum(String taskNum) {
        this.taskNum = taskNum;
    }

    public String getTaskTitle() {
        return taskTitle;
    }

    public void setTaskTitle(String taskTitle) {
        this.taskTitle = taskTitle;
    }

    public String getTaskStmt() {
        return taskStmt;
    }

    public void setTaskStmt(String taskStmt) {
        this.taskStmt = taskStmt;
    }

    public String getTaskNarr() {
        return taskNarr;
    }

    public void setTaskNarr(String taskNarr) {
        this.taskNarr = taskNarr;
    }

    public String getReqNum() {
        return reqNum;
    }

    public void setReqNum(String reqNum) {
        this.reqNum = reqNum;
    }

    public String getReqText() {
        return reqText;
    }

    public void setReqText(String reqText) {
        this.reqText = reqText;
    }

    public int getTotalNumHits() {
        return totalNumHits;
    }

    public void setTotalNumHits(int totalNumHits) {
        this.totalNumHits = totalNumHits;
    }

    public List<SearchHit> getHits() {
        return hits;
    }

    public void setHits(List<SearchHit> hits) {
        this.hits = new ArrayList<>(hits);
    }
}

