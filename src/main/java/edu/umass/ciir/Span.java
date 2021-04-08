package edu.umass.ciir;

import org.json.simple.JSONAware;
import org.json.simple.JSONObject;

public class Span implements JSONAware {
    String synclass;
    String string;
    long start;
    long end;
    String hstring;
    long hstart;
    long hend;
    Span(String synclass, String string, long start, long end, String hstring, long hstart, long hend) {
        this.synclass = synclass;
        this.string = string;
        this.start = start;
        this.end = end;
        this.hstring = hstring;
        this.hstart = hstart;
        this.hend = hend;
    }
    Span(Span other) {
        this.synclass = other.synclass;
        this.string = other.string;
        this.start = other.start;
        this.end = other.end;
        this.hstring = other.hstring;
        this.hstart = other.hstart;
        this.hend = other.hend;

    }
    @Override
    public String toJSONString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n{ ");
        sb.append("synclass");
        sb.append(": ");
        sb.append("\"" + JSONObject.escape(synclass) + "\"");
        sb.append(", ");

        sb.append("string");
        sb.append(": ");
        sb.append("\"" + JSONObject.escape(string) + "\"");
        sb.append(", ");

        sb.append("start");
        sb.append(": ");
        sb.append(start);
        sb.append(", ");

        sb.append("end");
        sb.append(": ");
        sb.append(end);
        sb.append(", ");

        sb.append("hstring");
        sb.append(": ");
        sb.append("\"" + JSONObject.escape(hstring) + "\"");
        sb.append(", ");

        sb.append("hstart");
        sb.append(": ");
        sb.append(hstart);
        sb.append(", ");

        sb.append("hend");
        sb.append(": ");
        sb.append(hend);

        sb.append("} ");
        return sb.toString();
    }
}