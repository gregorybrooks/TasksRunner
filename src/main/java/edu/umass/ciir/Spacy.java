package edu.umass.ciir;

import java.util.List;
import java.util.logging.Logger;

/**
 * Calls a Python program that uses the spacy library (@see <a href="https://spacy.io/">spaCy</a>)
 * to extract sentences from a document
 */
public class Spacy {
    private static final Logger logger = Logger.getLogger("TasksRunner");

    private static LineOrientedPythonDaemon sentenceDaemon = null;

    static {
        startSpacySentenceDaemon();
    }

    private static void startSpacySentenceDaemon() {
        logger.info("Starting spacy sentence daemon");
        sentenceDaemon = new LineOrientedPythonDaemon("get_sentences_from_spacy_daemon.py");
    }

    /**
     * Calls the spacy sentence daemon with some text, getting back the sentences.
     * It is synchronized because it is called from inside the Document method that uses a
     * ConcurrentHashMap to multi-thread through reading the corpus file, and the Python spacy
     * sentence daemon program is not thread-safe.
     * @param text the text to get sentences from
     * @return the list of sentences
     */
    public static List<String> getSentences(String text) {
        return sentenceDaemon.getAnswers(text);
    }

    // No need to do this, let it run until the whole program ends
    public void stopSpacy() {
        sentenceDaemon.stop();
    }
}
