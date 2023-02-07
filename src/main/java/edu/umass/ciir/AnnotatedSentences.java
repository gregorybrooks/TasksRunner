package edu.umass.ciir;

import org.apache.xmlbeans.impl.xb.xsdschema.Annotated;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class AnnotatedSentences {
    private static final Logger logger = Logger.getLogger("TasksRunner");

    AnnotatedSentences() { }

    public List<AnnotatedSentence> fetchSentences(String annotatedSentencesFile) {
        List<AnnotatedSentence> sentences = new ArrayList<>();
        String fileName = annotatedSentencesFile;
        logger.info("Opening annotated sentence file " + fileName);
        File myObj = new File(fileName);
        if (myObj.exists()) {
            try {
                File f = new File(fileName);
                logger.info("Opening annotated sentence file " + fileName);
                Reader reader = new BufferedReader(new InputStreamReader(
                        new FileInputStream(fileName)));
                JSONParser parser = new JSONParser();
                JSONObject topLevelJSON = (JSONObject) parser.parse(reader);
                JSONObject requestJSON = (JSONObject) topLevelJSON.get("request");

                JSONArray exampleDocsJSON = (JSONArray) requestJSON.get("exampleDocs");
                for (Object exampleDocObject : exampleDocsJSON) {
                    JSONObject exampleDocJSON = (JSONObject) exampleDocObject;
                    int docNumber = Math.toIntExact((long) exampleDocJSON.get("docNumber"));
                    String docId = (String) exampleDocJSON.get("docId");

                    JSONArray sentencesJSON = (JSONArray) exampleDocJSON.get("sentences");
                    for (Object sentenceObject : sentencesJSON) {
                        JSONObject sentenceJSON = (JSONObject) sentenceObject;
                        String sentence = (String) sentenceJSON.get("sentence");
                        String sentenceId = (String) sentenceJSON.get("sentenceId");
                        String judgment = (String) sentenceJSON.get("judgment");
                        AnnotatedSentence annotatedSentence = new AnnotatedSentence(sentenceId, sentence, judgment);
                        if (sentence != null) {
                            sentences.add(annotatedSentence);
                        }
                    }
                }
            } catch (Exception e) {
                throw new BetterQueryBuilderException(e);
            }
        } else {
            logger.info("File does not exist");
        }
        return sentences;
    }
}
