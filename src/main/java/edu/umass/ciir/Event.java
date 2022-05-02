package edu.umass.ciir;

import org.json.simple.JSONArray;
import org.json.simple.JSONAware;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.List;
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

    /*
    @Override
    public String toJSONString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n{");
        sb.append("\"eventtype\"");
        sb.append(": ");
        sb.append("\"" + JSONObject.escape(eventType) + "\"");
        sb.append(", ");

        sb.append("\"anchor\"");
        sb.append(": ");
        sb.append(anchorSpan.toJSONString());
        sb.append(", ");

        sb.append("\"agents\"");
        sb.append(": ");
        if (agentSpans.size() == 0) {
            sb.append("[] ");
        } else {
            for (SpanSet ss : agentSpans) {
                sb.append(ss.toJSONString());
            }
        }
        sb.append(", ");

        sb.append("\"patients\"");
        sb.append(": ");
        if (patientSpans.size() == 0) {
            sb.append("[] ");
        } else {
            for (SpanSet ss : patientSpans) {
                sb.append(ss.toJSONString());
            }
        }

        sb.append("} ");
        return sb.toString();
    }

     */

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
            anchorSpanJSON.put("synclass", anchorSpan.synclass);
            anchorSpanJSON.put("string", anchorSpan.string.replace("\n", " "));
            anchorSpanJSON.put("start", anchorSpan.start);
            anchorSpanJSON.put("end", anchorSpan.end);
            anchorSpanJSON.put("hstring", anchorSpan.hstring.replace("\n", " "));
            anchorSpanJSON.put("hstart", anchorSpan.hstart);
            anchorSpanJSON.put("hend", anchorSpan.hend);
            eventEntry.put("anchor", anchorSpanJSON);

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

