package edu.umass.ciir;

public class SentenceRange {
    public int id;
    public int start;
    public int end;
    public String text;
    SentenceRange(int id, int start, int end) {
        this.id = id;
        this.start = start;
        this.end = end;
    }
    SentenceRange(int id, int start, int end, String text) {
        this.id = id;
        this.start = start;
        this.end = end;
        this.text = text;
    }
}
