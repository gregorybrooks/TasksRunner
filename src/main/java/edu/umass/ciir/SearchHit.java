package edu.umass.ciir;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SearchHit {
    String docid;
    String docText;
    String translatedDocText;
    List<Event> events;
    List<SentenceRange> sentenceRanges;
    boolean isRelevant;

    SearchHit() {}

    SearchHit(JSONObject jsonObject) {
        this.sentenceRanges = new ArrayList<>();
        this.events = new ArrayList<>();
        this.isRelevant = false;

        this.docid = (String)jsonObject.get("docid");
        this.docText = (String)jsonObject.get("docText");
        this.translatedDocText = (String)jsonObject.get("translatedDocText");
        JSONArray sentences = (JSONArray) jsonObject.get("sentences");
        for (Object oSentence : sentences) {
            JSONObject sentence = (JSONObject) oSentence;
            int start = (int) sentence.get("start");
            int end = (int) sentence.get("end");
            int id = (int) sentence.get("id");
            String text = (String) sentence.get("text");
            SentenceRange sr = new SentenceRange(id, start, end, text);
            this.sentenceRanges.add(sr);
        }
        JSONArray JSONevents = (JSONArray) jsonObject.get("events");
        for (Object oEvent : JSONevents) {
            JSONObject event = (JSONObject) oEvent;
            String eventType = (String) event.get("eventType");
            Event sr = new Event(eventType);
            this.events.add(sr);
        }

    }

    SearchHit(String docid, String docText, String translatedDocText, List<Event> events, List<SentenceRange> sentenceRanges,
        boolean isRelevant) {
        this.docid = docid;
        this.docText = docText;
        this.translatedDocText = translatedDocText;
        this.events = events;
        this.sentenceRanges = sentenceRanges;
        this.isRelevant = isRelevant;
    }
    @JsonProperty("isRelevant")
    public boolean getIsRelevant() {
        return this.isRelevant;
    }

    public boolean isRelevant() {
        return isRelevant;
    }

    public void setEvents(List<Event> events) {
        this.events = events;
    }

    public void setRelevant(boolean relevant) {
        isRelevant = relevant;
    }

    public void setSentenceRanges(List<SentenceRange> sentenceRanges) {
        this.sentenceRanges = sentenceRanges;
    }

    public void setIsRelevant(boolean relevant) {
        isRelevant = relevant;
    }

    public String getTranslatedDocText() {
        return translatedDocText;
    }

    public void setTranslatedDocText(String translatedDocText) {
        this.translatedDocText = translatedDocText;
    }

    public List<Event> getEvents() {
        return events;
    }

    public List<SentenceRange> getSentenceRanges() {
        return sentenceRanges;
    }

    public String getDocText() {
        return docText;
    }

    public void setDocText(String docText) {
        this.docText = docText;
    }

    public String getDocid() {
        return docid;
    }

    public void setDocid(String docid) {
        this.docid = docid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Hit))
            return false;
        Hit submission = (Hit) o;
        return Objects.equals(this.docid, submission.docid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.docid);
    }

    @Override
    public String toString() {
        return "SearchHit{" + this.docid + '}';
    }
}
