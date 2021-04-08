package edu.umass.ciir;

import java.util.Comparator;

public class RelevanceJudgment {

    // OLD WAY: public static enum RelevanceJudgmentType {REQUEST_RELEVANT, TASK_RELEVANT, NOT_RELEVANT}
    public enum RelevanceJudgmentType {
        NON (0),
        TOPICALLY_RELEVANT (1),
        SPECIFIC_INFORMATION (2),
        DIRECT_ANSWER (3),
        DECISIONAL (4);

        private final int relevanceValue;

        RelevanceJudgmentType(int r) {
            this.relevanceValue = r;
        }

        public int getRelevanceValue() {
            return this.relevanceValue;
        }

        public Boolean isRelevant() { return this != NON; }
    }

    String requestID;
    String docid;
    String who;
    String when;
    RelevanceJudgmentType judgment;
    String docText;

    public String getRelevanceJudgmentAsString() {
        return judgment.name();
    }

    public String getRequestID() {
        return this.requestID;
    }

    public String getDocText() {
        return docText;
    }

    public String getDocid() {
        return this.docid;
    }
    /*   @Override
       public boolean equals(Object o) {
           if (this == o) return true;
           if (o == null || getClass() != o.getClass()) return false;
           RelevanceJudgment other = (RelevanceJudgment) o;
           return requestID.equals(other.requestID) && docid.equals(other.docid)
                   && judgment.relevanceValue == other.judgment.relevanceValue;
       }

       @Override
       public int compareTo(RelevanceJudgment other) {
           if (!this.getRequestID().equals(other.getRequestID())) {
               return this.getRequestID().compareTo(other.getRequestID());
           }
           if (other.getRelevanceJudgmentValue() == this.getRelevanceJudgmentValue()) {
               return other.getRelevanceJudgmentValue() - this.getRelevanceJudgmentValue();
           }
           return this.getDocid().compareTo(other.getDocid());
       }
   */
    public int getRelevanceJudgmentValue() {
        return judgment.getRelevanceValue();
    }

    public int getRelevanceJudgmentValueWithMapping() {
        int x = judgment.getRelevanceValue();
        return (x == 3 ? 5 : (x == 4 ? 10 : x));
    }

    /**
     * Copy constructor (deep copy)
     * @param other The RelevanceJudgment to make a copy of.
     */
    RelevanceJudgment(RelevanceJudgment other) {
        this.requestID = new String(other.requestID);
        this.docid = new String(other.docid);
        this.who = new String(other.who);
        this.when = new String(other.when);
        this.judgment = other.judgment;
        this.docText = other.docText;
    }

    RelevanceJudgment(String requestID, String docid, String who, String when,
                      RelevanceJudgmentType judgment) {
        this.requestID = requestID;
        this.docid = docid;
        this.who = who;
        this.when = when;
        this.judgment = judgment;
        this.docText = "";
    }
    RelevanceJudgment(String requestID, String docid, String who, String when,
                      String judgment) {
        this.requestID = requestID;
        this.docid = docid;
        this.who = who;
        this.when = when;
        this.docText = "";
        String newJudgment;
        switch (judgment) {
            /* For the official qrel file: */
            case "0":  // NON
                newJudgment = "NON";
                break;
            case "1":  // TOPICALLY_RELEVANT
                newJudgment = "TOPICALLY_RELEVANT";
                break;
            case "2":  //SPECIFIC_INFORMATION
                newJudgment = "SPECIFIC_INFORMATION";
                break;
            case "3":  // DIRECT_ANSWER
                newJudgment = "DIRECT_ANSWER";
                break;
            case "4":  // DECISIONAL
                newJudgment = "DECISIONAL";
                break;
            default:
                throw new IllegalArgumentException("Invalid relevance judgment string:" + judgment
                );
        }
        this.judgment = RelevanceJudgmentType.valueOf(newJudgment);
    }

    public static class SortByScoreDesc implements Comparator<RelevanceJudgment> {
        public int compare(RelevanceJudgment a, RelevanceJudgment b) {
            if (!a.getRequestID().equals(b.getRequestID())) {
                return a.getRequestID().compareTo(b.getRequestID());
            }
            if (a.getRelevanceJudgmentValue() != b.getRelevanceJudgmentValue()) {
                return b.getRelevanceJudgmentValue() - a.getRelevanceJudgmentValue();
            }
            return a.getDocid().compareTo(b.getDocid());
        }
    }
}
