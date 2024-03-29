package edu.umass.ciir;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import org.lemurproject.galago.core.retrieval.Retrieval;
import org.lemurproject.galago.core.retrieval.RetrievalFactory;
import org.lemurproject.galago.utility.Parameters;


import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class Document {

    private static final Logger logger = Logger.getLogger("TasksRunner");



    private static Map<String, DocumentDetails> docMap = new ConcurrentHashMap<>();
//    private static Map<String,String> docMap = new ConcurrentHashMap<>();
//    private static Map<String,String> arabicDocMap = new ConcurrentHashMap<>();
//    private static Map<String,String> translatedArabicDocMap = new ConcurrentHashMap<>();
//    private static Map<String,List<SentenceRange>> arabicDocSentencesMap = new ConcurrentHashMap<>();
//    private static Map<String,List<SentenceRange>> englishDocSentencesMap = new ConcurrentHashMap<>();
//    private static Map<String,List<Event>> arabicDocEventsMap = new ConcurrentHashMap<>();
//    private static Map<String,List<Event>> englishDocEventsMap = new ConcurrentHashMap<>();

    public static void buildDocMap(Set<String> uniqueDocIDs) {
        if (!Pathnames.englishCorpusFileName.isEmpty()) {
            String corpus = Pathnames.corpusFileLocation + Pathnames.englishCorpusFileName;
            buildDocMap(uniqueDocIDs, corpus, docMap, /*englishDocSentencesMap, null, englishDocEventsMap,*/
                    true);
        }
    }

    public static void buildTargetDocMap(Set<String> uniqueDocIDs) {
        String corpus = Pathnames.corpusFileLocation + Pathnames.targetCorpusFileName;
        buildDocMap(uniqueDocIDs, corpus, docMap,/*arabicDocMap, arabicDocSentencesMap, translatedArabicDocMap, arabicDocEventsMap,*/
                false);
    }

    public static void getDocumentWithGrep (String docid, String corpus, Map<String, DocumentDetails> map
                                            /*Map<String,String> map,
                                                    Map<String,List<SentenceRange>> sentenceMap,
                                            Map<String,String> translatedMap, Map<String, List<Event>> eventMap*/) {
        String command = "grep";
//        String grepText = "\"id\": \"" + docid + "\"";
        String grepText = docid;
        ProcessBuilder processBuilder = new ProcessBuilder(
                command, grepText,
                corpus);
        int exitVal = 0;
        logger.info("Calling " + command + " " + grepText + " " + corpus);
        try {
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() == 0) {
                    continue;
                }
                doALine(line, map/*, sentenceMap, translatedMap, eventMap*/);
            }
            exitVal = process.waitFor();
        } catch (Exception e) {
            throw new TasksRunnerException(e);
        }
        if (exitVal != 0) {
            throw new TasksRunnerException("Unexpected ERROR while executing grep. Exit value is " + exitVal);
        }
    }

    public static void getDocumentWithGalago(String docid, String indexPath, Map<String,String> map,
                                             Map<String,List<SentenceRange>> sentenceMap, boolean includeSentences) {
        try {
            Parameters queryParams = Parameters.create();
            queryParams.set ("index", indexPath);
            queryParams.set ("requested",1000);
            Retrieval ret = RetrievalFactory.create(queryParams);
            org.lemurproject.galago.core.parse.Document.DocumentComponents dcs =
                    new org.lemurproject.galago.core.parse.Document.DocumentComponents(true, false, true);
            org.lemurproject.galago.core.parse.Document document = ret.getDocument(docid, dcs);
            String docText = document.text;

            // Strip out the TEXT and EXID fields
            int x = docText.indexOf("<TEXT>");
            int y = docText.indexOf("</EXID>");
            String s = docText.substring(x, y + 7);
            docText = docText.replace(s, "");
            docText = docText.replace("</TEXT>", "");

            List<SentenceRange> sentences = new ArrayList<>();
            if (includeSentences) {
                getSentenceRangesFromText(docText, sentences);
            }

            map.put(docid, docText);
            sentenceMap.put(docid, sentences);
        }
        catch (Exception ex) {
            throw new TasksRunnerException(ex);
        }
    }

    private static boolean getGoodOnes(String line, Set<String> uniqueDocIDs) {
        JSONParser parser = new JSONParser();
        JSONObject json = null;
        try {
            json = (JSONObject) parser.parse(line);
        } catch (ParseException e) {
            throw new TasksRunnerException(e);
        }
        String uuid;
        String text;
        if (json.containsKey("uuid")) {
            // OLD STYLE
            uuid = (String) json.get("uuid");
        } else {
            JSONObject derived_metadata = (JSONObject) json.get("derived-metadata");
            uuid = (String) derived_metadata.get("id");
        }
        return (uniqueDocIDs.contains(uuid));
    }

    private static void buildDocMap(Set<String> uniqueDocIDs, String corpus, Map<String, DocumentDetails> map,
                                    /*Map<String,String> map,
                                    Map<String,List<SentenceRange>> sentenceMap, Map<String,String> translatedMap,
                                    Map<String, List<Event>> eventMap,*/ boolean isEnglishCorpus) {
//        AtomicInteger idx = new AtomicInteger(0);
        logger.info("Building document map for " + uniqueDocIDs.size() + " docs from corpus file "
        + corpus);
/*
        if (isEnglishCorpus) {

            logger.info("Using Galago method");
            for (String docid : uniqueDocIDs) {
                getDocumentWithGalago(docid, Pathnames.indexLocation + "better-clear-ir-english",
                        map, sentenceMap, true);
            }
        } else {
/*
            logger.info("Using Galago method");
            for (String docid : uniqueDocIDs) {
                getDocumentWithGalago(docid, isEnglishCorpus ? Pathnames.englishIndexLocation : Pathnames.targetIndexLocation,
                        map, sentenceMap, false);
            }
*/
            logger.info("Using corpus file scan method");
            try (Stream<String> stream = Files.lines(Paths.get(corpus))) {
                stream.parallel().filter(l -> getGoodOnes(l, uniqueDocIDs))
                        .forEach(line -> {
                            doALine(line, map/*, sentenceMap, translatedMap, eventMap*/);
                        });
            } catch (IOException e) {
                throw new TasksRunnerException(e);
            }
 //       }
        logger.info("Document map complete");
        List<String> missingDocids = new ArrayList<>();
        for (String d : uniqueDocIDs) {
            if (!map.containsKey(d)) {
                missingDocids.add(d);
            }
        }
        if (missingDocids.size() > 0) {
            throw new TasksRunnerException("Requested docids not found in corpus file: "
                    + String.join(", ", missingDocids));
        }
    }

    private static void doALine(String line, Map<String, DocumentDetails> map
                                /*Map<String,String> map,
                         Map<String,List<SentenceRange>> sentenceMap, Map<String, String> translatedMap,
                                Map<String, List<Event>> eventMap */) {
        JSONParser parser = new JSONParser();
        JSONObject json = null;
        try {
            json = (JSONObject) parser.parse(line);
        } catch (ParseException e) {
            throw new TasksRunnerException(e);
        }
        String uuid;
        String text;
        String language = "";
        String translatedText;
        List<SentenceRange> sentences = new ArrayList<>();
        List<Event> events = new ArrayList<>();
        if (json.containsKey("uuid")) {
            // OLD STYLE
            uuid = (String) json.get("uuid");
            text = (String) json.get("text");
            translatedText = "";
            if (json.containsKey("translated-text")) {
                translatedText = (String) json.get("translated-text");
            }
            getSentenceRangesFromText(text, sentences);
        } else {
            JSONObject derived_metadata = (JSONObject) json.get("derived-metadata");
            uuid = (String) derived_metadata.get("id");
            text = (String) derived_metadata.get("text");
            language = SearchEngineInterface.toCanonicalForm((String) derived_metadata.get("language"));
            translatedText = "";
            if (derived_metadata.containsKey("translated-text")) {
                translatedText = (String) derived_metadata.get("translated-text");
            }
            if (derived_metadata.containsKey("isi-events")) {
                logger.info("Found isi-events object in corpus line");
                events = Event.getEventsFromJSON((JSONArray) derived_metadata.get("isi-events"));
            }
            /* Russian docs have sentence metadata left over from Arabic, so must calc them here */
            if (!language.equals("russian") && derived_metadata.containsKey("segment-sections")) {
                JSONArray segment_sections = (JSONArray) derived_metadata.get("segment-sections");
                int id = 0;
                for (Object oSection : segment_sections) {
                    ++id;
                    JSONObject segment_section = (JSONObject) oSection;
                    long start = (long) segment_section.get("start");
                    long end = (long) segment_section.get("end");
                    String sentenceText = "";
                    try {
                        sentenceText = text.substring((int) start, (int) end);
                    } catch (IndexOutOfBoundsException e) {
                        System.out.println("ERROR: sentence boundaries not right");
                        System.out.println("Start: " + start + ", End: " + end);
                        System.out.println("Length of text: " + text.length());
                        System.out.println(text);
                    }
                    SentenceRange sentence = new SentenceRange(id, (int) start, (int) end, sentenceText);
                    sentences.add(sentence);
                }
            } else {
                getSentenceRangesFromText(text, sentences);
            }
        }
        // If there are duplicate docs in the corpus, we will keep the last one we encounter
        /*
        map.put(uuid, text);
        if (translatedMap != null) {
            translatedMap.put(uuid, translatedText);
        }
        sentenceMap.put(uuid, sentences);
        eventMap.put(uuid, events);
         */
        map.put(uuid, new DocumentDetails(text, translatedText, sentences, events, language));
    }

    private static List<String> callSpacy(String s) {
        return Spacy.getSentences(s);
    }

    private static void getSentenceRangesFromText(String text, List<SentenceRange> sentences) {
        List<String> spacySentences = callSpacy(text);
        int start = 0;
        int end = -1;
        int id = 0;
        for (String sentence : spacySentences) {
            ++id;
            if (id > 1) {
                start = text.indexOf(sentence, end);
                if (start == -1) {
                    System.out.println("ERROR: Cannot find spacy sentence in doc");
                }
            }
            end = start + sentence.length();
            String sentenceText = text.substring(start, end);
            if (sentence.length() > 0) {
                sentences.add(new SentenceRange(id, start, end, sentenceText));
            }
        }
    }

    public static String getDocumentWithMap (String docid) {
        return docMap.get(docid).text;
    }

    public static String getTargetDocumentWithMap(String docid) {
        return docMap.get(docid).text;
    }

    public static String getTranslatedTargetDocumentWithMap(String docid) {
        return docMap.get(docid).translatedText;
    }

    public static List<SentenceRange> getDocumentSentences (String docid) {
        return docMap.get(docid).sentences;
    }

    public static List<SentenceRange> getTargetDocumentSentences(String docid) {
        return docMap.get(docid).sentences;
    }

    public static List<Event> getTargetDocumentEvents(String docid) {
        return docMap.get(docid).events;
    }

    public static List<Event> getEnglishDocumentEvents (String docid) {
        return docMap.get(docid).events;
    }

    public static String getLanguage(String docid) {
        return docMap.get(docid).language;
    }

    public static String getArabicLanguage(String docid) {
        return docMap.get(docid).language;
    }


    private static void doALineForAllCorpus(String line, Map<String, SimpleHit> hitMap) {
        JSONParser parser = new JSONParser();
        JSONObject json = null;
        try {
            json = (JSONObject) parser.parse(line);
        } catch (ParseException e) {
            throw new TasksRunnerException(e);
        }
        String uuid;
        String text;
        String language = "";
        String translatedText;
        List<SentenceRange> sentences = new ArrayList<>();
        List<Event> events = new ArrayList<>();
        JSONObject derived_metadata = (JSONObject) json.get("derived-metadata");
        uuid = (String) derived_metadata.get("id");
        text = (String) derived_metadata.get("text");
        language = SearchEngineInterface.toCanonicalForm((String) derived_metadata.get("language"));
        translatedText = "";
        if (derived_metadata.containsKey("translated-text")) {
            translatedText = (String) derived_metadata.get("translated-text");
        }
        if (derived_metadata.containsKey("isi-events")) {
            logger.info("Found isi-events object in corpus line");
            events = Event.getEventsFromJSON((JSONArray) derived_metadata.get("isi-events"));
        }
        /* Russian docs have sentence metadata left over from Arabic, so must calc them here */
        if (!language.equals("russian") && derived_metadata.containsKey("segment-sections")) {
            JSONArray segment_sections = (JSONArray) derived_metadata.get("segment-sections");
            int id = 0;
            for (Object oSection : segment_sections) {
                ++id;
                JSONObject segment_section = (JSONObject) oSection;
                long start = (long) segment_section.get("start");
                long end = (long) segment_section.get("end");
                String sentenceText = "";
                try {
                    sentenceText = text.substring((int) start, (int) end);
                } catch (IndexOutOfBoundsException e) {
                    System.out.println("ERROR: sentence boundaries not right");
                    System.out.println("Start: " + start + ", End: " + end);
                    System.out.println("Length of text: " + text.length());
                    System.out.println(text);
                }
                SentenceRange sentence = new SentenceRange(id, (int) start, (int) end, sentenceText);
                sentences.add(sentence);
            }
        } else {
            getSentenceRangesFromText(text, sentences);
        }
        hitMap.put("CorpusDoc" + "--" + "dummy" + "--" + uuid,
                new SimpleHit(uuid, text, translatedText, sentences, events, language));
    }

    public static Map<String,SimpleHit> getSimpleHitsFromCorpus(int startIndex, int numDocsPerFile) {
        int limit = Pathnames.DOCS_TO_PROCESS;
        logger.info("Getting " + limit + " documents from corpus file for event extraction");
        Map<String, SimpleHit> hitMap = new ConcurrentHashMap<>();
        try (Stream<String> stream = Files.lines(Paths.get(Pathnames.corpusFileLocation + Pathnames.targetCorpusFileName))) {
            stream.skip(startIndex).limit(numDocsPerFile)
                    .forEach(line -> {
                        doALineForAllCorpus(line, hitMap);
                    });
        } catch (IOException e) {
            throw new TasksRunnerException(e);
        }
        return hitMap;
    }

}
