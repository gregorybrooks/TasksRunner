package edu.umass.ciir;

import org.json.simple.JSONArray;
import org.json.simple.JSONAware;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

public class Event /*implements JSONAware*/ {
    private static final Logger logger = Logger.getLogger("TasksRunner");
    String entryKey;
    String docSetType;
    String taskOrRequestID;
    String docid;
    String eventid;
    String eventType;
    int sentenceID;
    String anchor;
    Span anchorSpan;
    List<String> agentList;
    List<SpanSet> agentSpans = new ArrayList<>();
    List<String> patientList;
    List<SpanSet> patientSpans = new ArrayList<>();

    Event() {}

    Event(String eventType) {
        this.eventType = eventType;
    }

    Event(String entryKey, String docSetType, String taskOrRequestID, String docid, String eventid,
          String eventType, String anchor, List<String> agentList, List<String> patientList) {
        this.entryKey = entryKey;
        this.docSetType = docSetType;
        this.taskOrRequestID = taskOrRequestID;
        this.docid = docid;
        this.eventid = eventid;
        this.eventType = eventType;
        this.anchor = anchor;
        this.agentList = agentList;
        this.patientList = patientList;
    }

    public String getEntryKey() {
        return entryKey;
    }

    public void setEntryKey(String entryKey) {
        this.entryKey = entryKey;
    }

    public String getDocSetType() {
        return docSetType;
    }

    public void setDocSetType(String docSetType) {
        this.docSetType = docSetType;
    }

    public String getTaskOrRequestID() {
        return taskOrRequestID;
    }

    public void setTaskOrRequestID(String taskOrRequestID) {
        this.taskOrRequestID = taskOrRequestID;
    }

    public String getDocid() {
        return docid;
    }

    public void setDocid(String docid) {
        this.docid = docid;
    }

    public String getEventid() {
        return eventid;
    }

    public void setEventid(String eventid) {
        this.eventid = eventid;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public int getSentenceID() {
        return sentenceID;
    }

    public void setSentenceID(int sentenceID) {
        this.sentenceID = sentenceID;
    }

    public String getAnchor() {
        return anchor;
    }

    public void setAnchor(String anchor) {
        this.anchor = anchor;
    }

    public Span getAnchorSpan() {
        return anchorSpan;
    }

    public void setAnchorSpan(Span anchorSpan) {
        this.anchorSpan = anchorSpan;
    }

    public List<String> getAgentList() {
        return agentList;
    }

    public void setAgentList(List<String> agentList) {
        this.agentList = agentList;
    }

    public List<SpanSet> getAgentSpans() {
        return agentSpans;
    }

    public void setAgentSpans(List<SpanSet> agentSpans) {
        this.agentSpans = agentSpans;
    }

    public List<String> getPatientList() {
        return patientList;
    }

    public void setPatientList(List<String> patientList) {
        this.patientList = patientList;
    }

    public List<SpanSet> getPatientSpans() {
        return patientSpans;
    }

    public void setPatientSpans(List<SpanSet> patientSpans) {
        this.patientSpans = patientSpans;
    }

    // WARNING: This is currently limited to getting only eventType
    public static List<Event> getEventsFromJSON(JSONArray eventsJSON) {
        List<Event> eventList = new ArrayList<>();
        for (Object oEvent : eventsJSON) {
            JSONObject eventJSON = (JSONObject) oEvent;
            Event event = new Event();
            event.eventType = (String) eventJSON.get("eventType");
            eventList.add(event);
        }
        return eventList;
    }

    public static JSONArray getEventsJSON(List<Event> events) {
        JSONArray eventsArray = new JSONArray();
        for (Event event : events) {
            JSONObject eventEntry = new JSONObject();
            eventEntry.put("eventType", event.eventType);
            eventEntry.put("sentenceID", event.sentenceID);

            JSONArray agents = new JSONArray();
            for (int index = 0, limit = event.agentSpans.size() - 1; index < event.agentSpans.size(); ++index) {
                SpanSet ss = event.agentSpans.get(index);
                for (int index2 = 0, limit2 = ss.spans.size() - 1; index2 < ss.spans.size(); ++index2) {
                    Span s = ss.spans.get(index2);
                    JSONObject span = new JSONObject();
                    span.put("synclass", s.synclass);
                    span.put("string", s.string.replace("\n", " "));
                    span.put("start", s.start);
                    span.put("end", s.end);
                    span.put("hstring", s.hstring.replace("\n", " "));
                    span.put("hstart", s.hstart);
                    span.put("hend", s.hend);

                    agents.add(span);
                }
            }
            eventEntry.put("agents", agents);

            JSONObject anchorSpanJSON = new JSONObject();
            Span anchorSpan = event.anchorSpan;
            if (anchorSpan != null) {
                anchorSpanJSON.put("synclass", anchorSpan.synclass);
                anchorSpanJSON.put("string", anchorSpan.string.replace("\n", " "));
                anchorSpanJSON.put("start", anchorSpan.start);
                anchorSpanJSON.put("end", anchorSpan.end);
                anchorSpanJSON.put("hstring", anchorSpan.hstring.replace("\n", " "));
                anchorSpanJSON.put("hstart", anchorSpan.hstart);
                anchorSpanJSON.put("hend", anchorSpan.hend);
                eventEntry.put("anchor", anchorSpanJSON);
            }
            JSONArray patients = new JSONArray();
            for (int index = 0, limit = event.patientSpans.size() - 1; index < event.patientSpans.size(); ++index) {
                SpanSet p = event.patientSpans.get(index);
                for (int index2 = 0, limit2 = p.spans.size() - 1; index2 < p.spans.size(); ++index2) {
                    Span s = p.spans.get(index2);
                    JSONObject spanJSON = new JSONObject();
                    spanJSON.put("synclass", s.synclass);
                    spanJSON.put("string", s.string.replace("\n", " "));
                    spanJSON.put("start", s.start);
                    spanJSON.put("end", s.end);
                    spanJSON.put("hstring", s.hstring.replace("\n", " "));
                    spanJSON.put("hstart", s.hstart);
                    spanJSON.put("hend", s.hend);
                    patients.add(spanJSON);
                }
            }
            eventEntry.put("patients", patients);
            eventsArray.add(eventEntry);
        }
        return eventsArray;
    }
    @Override
    public int hashCode() {
        return Objects.hash(this.docid);
    }

    @Override
    public String toString() {
        return "Event{" + this.docid + '}';
    }
}

