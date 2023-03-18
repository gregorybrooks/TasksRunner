package edu.umass.ciir;

import org.json.simple.JSONArray;
import org.json.simple.JSONAware;
import org.json.simple.JSONObject;

import java.util.*;
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
    //String anchor;
    Span anchorSpan;
    List<Span> agentSpanList;
//    List<SpanSet> agentSpans = new ArrayList<>();
    List<Span> patientSpanList;
//    List<SpanSet> patientSpans = new ArrayList<>()

    Event() {}

    Event(String eventType) {
        this.eventType = eventType;
    }

    Event(String entryKey, String docSetType, String taskOrRequestID, String docid, String eventid,
          String eventType) {
        this.entryKey = entryKey;
        this.docSetType = docSetType;
        this.taskOrRequestID = taskOrRequestID;
        this.docid = docid;
        this.eventid = eventid;
        this.eventType = eventType;
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

    public Span getAnchorSpan() {
        return anchorSpan;
    }

    public void setAnchorSpan(Span anchorSpan) {
        this.anchorSpan = anchorSpan;
    }

    public List<Span> getAgentSpanList() {
        return agentSpanList;
    }

    public void setAgentSpanList(List<Span> agentSpanList) {
        this.agentSpanList = agentSpanList;
    }
    /*public List<SpanSet> getAgentSpans() {
        return agentSpans;
    }

    public void setAgentSpans(List<SpanSet> agentSpans) {
       this.agentSpans = agentSpans;
    }
     */

    public List<Span> getPatientSpanList() {
        return patientSpanList;
    }

    public void setPatientSpanList(List<Span> patientSpanList) {
        this.patientSpanList = patientSpanList;
    }

/*
    public List<SpanSet> getPatientSpans() {
        return patientSpans;
    }

    public void setPatientSpans(List<SpanSet> patientSpans) {
        this.patientSpans = patientSpans;
    }
*/


    public static List<Event> getEventListFromShortFormJSON(JSONArray eventsJSON) {
        List<Event> eventList = new ArrayList<>();
        for (Object oEvent : eventsJSON) {
            Event event = new Event();

            JSONObject eventJSON = (JSONObject) oEvent;

            event.eventType = (String) eventJSON.get("eventType");
            event.sentenceID = ((Number) eventJSON.get("sentenceID")).intValue();

            if (eventJSON.containsKey("anchor")) {
                event.anchorSpan = getSpanFromJSON((JSONObject) eventJSON.get("anchor"));
            } else if (eventJSON.containsKey("anchorSpan")) {
                event.anchorSpan = getSpanFromJSON((JSONObject) eventJSON.get("anchorSpan"));
            }
            if (eventJSON.containsKey("agents")) {
                event.agentSpanList = getSpanListFromJSON((JSONArray) eventJSON.get("agents"));
            } else if (eventJSON.containsKey("agentSpanList")) {
                event.agentSpanList = getSpanListFromJSON((JSONArray) eventJSON.get("agentSpanList"));
            }
            if (eventJSON.containsKey("patients")) {
                event.patientSpanList = getSpanListFromJSON((JSONArray) eventJSON.get("patients"));
            } else if (eventJSON.containsKey("patientSpanList")) {
                event.patientSpanList = getSpanListFromJSON((JSONArray) eventJSON.get("patientSpanList"));
            }
            eventList.add(event);
        }
        return eventList;
    }

    private static List<Span> getSpanListFromJSON(JSONArray agents) {
        int id = 0;
        List<Span> agentSpanList = new ArrayList<>();
        for (Object oSection : agents) {
            ++id;
            JSONObject agentSpanJSON = (JSONObject) oSection;
            Span agentSpan = getSpanFromJSON(agentSpanJSON);
            agentSpanList.add(agentSpan);
        }
        return agentSpanList;
    }

    private static Span getSpanFromJSON(JSONObject anchorJSON) {
        String synclass = (String) anchorJSON.get("synclass");
        long hend = (long) anchorJSON.get("hend");
        long hstart = (long) anchorJSON.get("hstart");
        long end = (long) anchorJSON.get("end");
        long start = (long) anchorJSON.get("start");
        String string = (String) anchorJSON.get("string");
        String hstring = (String) anchorJSON.get("hstring");
        String translatedString = "";
        String translatedHstring = "";
        if (anchorJSON.containsKey("translatedString")) {
            translatedString = (String) anchorJSON.get("translatedString");
        }
        if (anchorJSON.containsKey("translatedHstring")) {
            translatedHstring = (String) anchorJSON.get("translatedHstring");
        }
        return new Span(synclass, string, start, end, hstring, hstart, hend, translatedString, translatedHstring);
    }

    private static Map<String, SpanSet> getSpanMap(JSONObject spanSets, String entryKey) {
        Map<String, SpanSet> spansMap = new HashMap<>();

        for (Iterator iterator3 = spanSets.keySet().iterator(); iterator3.hasNext(); ) {
            String key3 = (String) iterator3.next();
            JSONObject spanSet = (JSONObject) spanSets.get(key3);
            String ssid = entryKey + "--" + (String) spanSet.get("ssid");
            SpanSet ss = new SpanSet(entryKey, ssid);
            JSONArray spans = (JSONArray) spanSet.get("spans");
            for (Object oSpan : spans) {
                ss.spans.add(getSpanFromJSON((JSONObject) oSpan));
            }
            spansMap.put(ssid, ss);
        }
        return spansMap;
    }

    public static List<Event> getEventListFromLongFormJSON(JSONObject entry, String entryKey) {
        List<Event> eventList = new ArrayList<>();
        JSONObject annotation_sets = (JSONObject) entry.get("annotation-sets");
        JSONObject basic_events = (JSONObject) annotation_sets.get("basic-events");

        Map<String, SpanSet> spansMap = getSpanMap((JSONObject) basic_events.get("span-sets"), entryKey);

        JSONObject eventsToParse = (JSONObject) basic_events.get("events");
        for (Iterator iterator2 = eventsToParse.keySet().iterator(); iterator2.hasNext(); ) {
            String key2 = (String) iterator2.next();
            JSONObject event = (JSONObject) eventsToParse.get(key2);
//            Event e = new Event(entryKey, docSetType, taskOrRequestID, docid, eventid, eventType);
            Event e = new Event();
            e.eventType = (String) event.get("eventType");
            String anchor = (String) event.get("anchors");
            e.anchorSpan = new Span(spansMap.get(e.entryKey + "--" + anchor).spans.get(0));
            e.agentSpanList = new ArrayList<>();
            for (Object oAgent : (JSONArray) event.get("agents")) {
                // not needed? e.agentSpans.add(new SpanSet(spansMap.get(e.entryKey + "--" + agent)));
                e.agentSpanList.addAll(spansMap.get(e.entryKey + "--" + (String) oAgent).spans);
            }
            e.patientSpanList = new ArrayList<>();
            for (Object oPatient : (JSONArray) event.get("patients")) {
                // not needed? e.patientSpans.add(new SpanSet(spansMap.get(e.entryKey + "--" + patient)));
                e.patientSpanList.addAll(spansMap.get(e.entryKey + "--" + (String) oPatient).spans);
            }
            eventList.add(e);
        }
        return eventList;
    }

    private static JSONObject getJSONForSpan(Span s) {
        JSONObject span = new JSONObject();
        span.put("synclass", s.synclass);
        span.put("string", s.string.replace("\n", " "));
        span.put("start", s.start);
        span.put("end", s.end);
        span.put("hstring", s.hstring.replace("\n", " "));
        span.put("hstart", s.hstart);
        span.put("hend", s.hend);
        span.put("translatedString", s.translatedString.replace("\n", " "));
        span.put("translatedHstring", s.translatedHstring.replace("\n", " "));
        return span;
    }

    public static JSONArray getEventsJSON(List<Event> events) {
        JSONArray eventsArray = new JSONArray();
        for (Event event : events) {
            JSONObject eventEntry = new JSONObject();
            eventEntry.put("eventType", event.eventType);
            eventEntry.put("sentenceID", event.sentenceID);

            JSONArray agents = new JSONArray();
            for (Span s : event.agentSpanList) {
                agents.add(getJSONForSpan(s));
            }
            /*
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
            */
            eventEntry.put("agentSpanList", agents);

            if (event.anchorSpan != null) {
                eventEntry.put("anchorSpan", getJSONForSpan(event.anchorSpan));
            }

            JSONArray patients = new JSONArray();
            for (Span s : event.patientSpanList) {
                patients.add(getJSONForSpan(s));
            }
            /*
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
             */
            eventEntry.put("patientSpanList", patients);
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

