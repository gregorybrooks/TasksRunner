package edu.umass.ciir;

import org.json.simple.JSONAware;
import org.json.simple.JSONObject;

import java.util.List;


public class Hit /*implements JSONAware*/ {

    HitLevel hitLevel;
    String taskID;
    String docid;
    String docText;
    List<Event> events;

    Hit(HitLevel hitLevel, String taskID, String docid, String docText, List<Event> events) {
        this.hitLevel = hitLevel;
        this.taskID = taskID;
        this.docid = docid;
        this.docText = docText;
        this.events = events;
    }

    /*
    @Override
    public String toJSONString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n{");
        sb.append("taskid");
        sb.append(":");
        sb.append("\"" + JSONObject.escape(taskID) + "\"");
        sb.append(",");

        sb.append("docid");
        sb.append(":");
        sb.append("\"" + JSONObject.escape(docid) + "\"");
        sb.append(",");

        sb.append("doctext");
        sb.append(":");
        sb.append("\"" + JSONObject.escape(docText) + "\"");
        sb.append(",");

        sb.append("events");
        sb.append(": [");
        for (Event e : events) {
            sb.append(e.toJSONString());
            sb.append(",");
        }
        sb.append(" ] } ");
        return sb.toString();
    }

     */
}
