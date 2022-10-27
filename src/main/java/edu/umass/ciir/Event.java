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
    String anchor;
    Span anchorSpan;
    List<String> agentList;
    List<SpanSet> agentSpans = new ArrayList<>();
    List<String> patientList;
    List<SpanSet> patientSpans = new ArrayList<>();

    Event() {}

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

    private void setSentenceID(int id) {
        this.sentenceID = id;
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

    public static String getEventsAsSentencesFromJSON(JSONObject jsonBasicEvents, String docid) {
        String eventsAsString = "";
        Map<String, SpanSet> spansMap = new HashMap<>();

        if (!jsonBasicEvents.containsKey("span-sets") || !jsonBasicEvents.containsKey("events")) {
            return eventsAsString;
        }

        JSONObject spanSets = (JSONObject) jsonBasicEvents.get("span-sets");
        for (Iterator iterator3 = spanSets.keySet().iterator(); iterator3.hasNext(); ) {
            String key3 = (String) iterator3.next();
            JSONObject spanSet = (JSONObject) spanSets.get(key3);
            String ssid = docid + "--" + (String) spanSet.get("ssid");
            SpanSet ss = new SpanSet(docid, ssid);
            JSONArray spans = (JSONArray) spanSet.get("spans");
            for (Object oSpan : spans) {
                JSONObject span = (JSONObject) oSpan;
                String string = (String) span.get("string");
                long start = (long) span.get("start");
                long end = (long) span.get("end");
                Span s = new Span("", string, start, end, "", 0, 0);
                ss.spans.add(s);
            }
            spansMap.put(ssid, ss);
        }

        JSONObject eventsToParse = (JSONObject) jsonBasicEvents.get("events");
        for (Iterator iterator2 = eventsToParse.keySet().iterator(); iterator2.hasNext(); ) {
            String key2 = (String) iterator2.next();
            JSONObject event = (JSONObject) eventsToParse.get(key2);
            String eventid = (String) event.get("eventid");
            String eventType = (String) event.get("event-type");
            String anchor = spansMap.get(docid + "--" + ((String) event.get("anchors"))).spans.get(0).string;
            List<String> agentList = new ArrayList<>();
            JSONArray agents = (JSONArray) event.get("agents");
            for (Object oAgent : agents) {
                String agent = (String) oAgent;
                agentList.add(spansMap.get(docid + "--" + agent).spans.get(0).string);
            }
            List<String> patientList = new ArrayList<>();
            JSONArray patients = (JSONArray) event.get("patients");
            for (Object oPatient : patients) {
                String patient = (String) oPatient;
                patientList.add(spansMap.get(docid + "--" + patient).spans.get(0).string);
            }
            for (String agent : agentList) {
                eventsAsString += (agent + " ");
            }
            eventsAsString += (anchor + " ");
            for (String patient : patientList) {
                eventsAsString += (patient + " ");
            }
            eventsAsString += ". ";
        }
        return eventsAsString;
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

}

