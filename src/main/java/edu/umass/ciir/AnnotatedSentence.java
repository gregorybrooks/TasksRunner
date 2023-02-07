package edu.umass.ciir;

public class AnnotatedSentence {
    private String sentenceId;
    private String sentence;
    private String judgment;

    AnnotatedSentence(String sentenceId, String sentence, String judgment) {
        this.sentenceId = sentenceId;
        this.sentence = sentence;
        this.judgment = judgment;
    }

    AnnotatedSentence() {}

    public String getSentence() {
        return sentence;
    }

    public String getSentenceId() {
        return sentenceId;
    }

    public String getJudgment() {
        return judgment;
    }

    public void setSentence(String sentence) {
        this.sentence = sentence;
    }

    public void setSentenceId(String sentenceId) {
        this.sentenceId = sentenceId;
    }

    public void setJudgment(String judgment) {
        this.judgment = judgment;
    }

}

