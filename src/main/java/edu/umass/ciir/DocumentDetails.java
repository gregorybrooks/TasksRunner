package edu.umass.ciir;

import java.util.List;

public class DocumentDetails {
    String text;
    String translatedText;
    List<SentenceRange> sentences;
    List<Event> events;
    String language;
    DocumentDetails(String text, String translatedText, List<SentenceRange> sentences, List<Event> events,
                    String language) {
        this.text = text;
        this.translatedText = translatedText;
        this.sentences = sentences;
        this.events = events;
        this.language = language;
    }

    public String getText() {
        return text;
    }

    public String getTranslatedText() {
        return translatedText;
    }

    public List<SentenceRange> getSentences() {
        return sentences;
    }

    public List<Event> getEvents() {
        return events;
    }

    public String getLanguage() {
        return language;
    }
}
