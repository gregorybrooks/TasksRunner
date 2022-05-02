package edu.umass.ciir;

import java.util.ArrayList;
import java.util.List;

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
