package edu.umass.ciir;

public class ScoredHit {
    public String docid;
    public String score;
    ScoredHit(String docid, String score) {
        this.docid = docid;
        this.score = score;
    }
    ScoredHit(ScoredHit other) {
        this.docid = other.docid;
        this.score = other.score;
    }
}