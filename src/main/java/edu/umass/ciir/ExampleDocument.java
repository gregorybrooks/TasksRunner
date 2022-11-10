package edu.umass.ciir;

import java.util.ArrayList;
import java.util.List;

public class ExampleDocument {
    private String docid;
    private String docText;
    private String eventsAsSentences;
    private List<String> highlights;
    private List<Event> events;
    private List<SentenceRange> sentences;

    ExampleDocument(String docid, String docText, List<SentenceRange> sentences, String eventsAsSentences) {
        this.highlights = new ArrayList<>();
        this.sentences = new ArrayList<>(sentences);
        this.docid = docid;
        this.docText = docText;
        this.eventsAsSentences = eventsAsSentences;
    }

    ExampleDocument(String docid, String docText, List<String> highlights, List<SentenceRange> sentences, String eventsAsSentences) {
        this.highlights = new ArrayList<>(highlights);
        this.sentences = new ArrayList<>(sentences);
        this.docid = docid;
        this.docText = docText;
        this.eventsAsSentences = eventsAsSentences;
    }

    ExampleDocument(String docid) {
        this.docid = docid;
        this.highlights = new ArrayList<>();
    }

    ExampleDocument(String docid, List<String> highlights) {
        this.highlights = new ArrayList<>(highlights);
        this.docid = docid;
    }
    ExampleDocument(ExampleDocument other) {
        this.docid = other.docid;
        this.docText = other.docText;
        this.eventsAsSentences = other.eventsAsSentences;
        this.highlights = new ArrayList<>(other.highlights);
        this.sentences = new ArrayList<>(other.sentences);
    }

    public String getDocid() {
        return docid;
    }

    public String getDocText() {
        return docText;
    }

    public String getEventsAsSentences() {
        return eventsAsSentences;
    }

    public List<String> getHighlights() {
        return highlights;
    }

    public void setHighlights(List<String> highlights) {
        this.highlights = highlights;
    }

    public void setDocText(String docText) {
        this.docText = docText;
    }

    public void setEvents(List<Event> events) {
        this.events = events;
    }

    public List<Event> getEvents() {
        return events;
    }

    public void setSentences(List<SentenceRange> sentences) {
        this.sentences = new ArrayList<SentenceRange>(sentences);
    }

    public List<SentenceRange> getSentences() {
        return sentences;
    }

}
