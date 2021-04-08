package edu.umass.ciir;

import org.json.simple.JSONAware;

import java.util.ArrayList;
import java.util.List;

public class SpanSet implements JSONAware {
    String docid;
    String ssid;
    List<Span> spans;
    SpanSet(String docid, String ssid) {
        this.docid = docid;
        this.ssid = ssid;
        this.spans = new ArrayList<Span>();
    }
    SpanSet(SpanSet other) {
        this.docid = other.docid;
        this.ssid = other.ssid;
        this.spans = new ArrayList<Span>(other.spans);
    }
    @Override
    public String toJSONString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[ ");
        for (Span span : spans) {
            sb.append(span.toJSONString());
        }
        sb.append("] ");
        return sb.toString();
    }
}
