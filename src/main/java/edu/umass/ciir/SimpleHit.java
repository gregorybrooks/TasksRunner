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
    String translatedDocText;
    String score;
    List<SentenceRange> sentences;
    List<Event> events;
    String query;
    String language;

    SimpleHit(String docid, String docText, String translatedDocText, String score, List<SentenceRange> sentences,
              List<Event> events, String language) {
        this.docid = docid;
        this.events = events;
        this.docText = docText;
        this.translatedDocText = translatedDocText;
        this.score = score;
        this.sentences = sentences;
        this.language = language;
    }

    SimpleHit(String docid, String docText, String translatedDocText, List<SentenceRange> sentences, List<Event> events,
              String language) {
        this.docid = docid;
        this.events = events;
        this.docText = docText;
        this.translatedDocText = translatedDocText;
        this.score = "";
        this.sentences = sentences;
        this.language = language;
    }

    public void setSentences(List<SentenceRange> sentences) {
        this.sentences = new ArrayList<>(sentences);
    }
}
