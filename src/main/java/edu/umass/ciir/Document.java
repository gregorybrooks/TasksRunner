package edu.umass.ciir;
import com.sun.java.accessibility.util.Translator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class Document {

    private static final Logger logger = Logger.getLogger("TasksRunner");

    private static Map<String,String> docMap = new ConcurrentHashMap<>();
    private static Map<String,String> arabicDocMap = new ConcurrentHashMap<>();
    private static Map<String,List<SentenceRange>> arabicDocSentencesMap = new ConcurrentHashMap<>();
    private static Map<String,List<SentenceRange>> endlishDocSentencesMap = new ConcurrentHashMap<>();

    public static void buildDocMap(Set<String> uniqueDocIDs) {
        if (!Pathnames.englishCorpusFileName.isEmpty()) {
            String corpus = Pathnames.corpusFileLocation + Pathnames.englishCorpusFileName;
            buildDocMap(uniqueDocIDs, corpus, docMap, endlishDocSentencesMap);
        }
    }

    public static void buildArabicDocMap(Set<String> uniqueDocIDs) {
        String corpus = Pathnames.corpusFileLocation + Pathnames.targetCorpusFileName;
        buildDocMap(uniqueDocIDs, corpus, arabicDocMap, arabicDocSentencesMap);
    }

    public static String getArabicDocumentWithGrep (String docid) {
        String command = "grep";
        ProcessBuilder processBuilder = new ProcessBuilder(
                command, docid,
                Pathnames.corpusFileLocation + Pathnames.targetCorpusFileName);
        int exitVal = 0;
        String docText = "";
        try {
            JSONParser parser = new JSONParser();
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() == 0) {
                    continue;
                }
                JSONObject json = (JSONObject) parser.parse(line);
                docText = (String) json.get("text");
            }
            exitVal = process.waitFor();
        } catch (Exception e) {
            throw new TasksRunnerException(e);
        }
        if (exitVal != 0) {
            throw new TasksRunnerException("Unexpected ERROR while executing grep. Exit value is " + exitVal);
        }
        return docText;
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

    private static void buildDocMap(Set<String> uniqueDocIDs, String corpus, Map<String,String> map,
                                    Map<String,List<SentenceRange>> sentenceMap) {
//        AtomicInteger idx = new AtomicInteger(0);
        logger.info("Building document map for " + uniqueDocIDs.size() + " docs from corpus file "
        + corpus);
        try (Stream<String> stream = Files.lines(Paths.get(corpus))) {
            stream.parallel().filter(l -> getGoodOnes(l, uniqueDocIDs))
                    .forEach(line -> {
//                if ((idx.incrementAndGet() % 10000) == 0) {
//                    System.out.println(idx);  // TEMP
//                }
                        JSONParser parser = new JSONParser();
                        JSONObject json = null;
                        try {
                            json = (JSONObject) parser.parse(line);
                        } catch (ParseException e) {
                            throw new TasksRunnerException(e);
                        }
                        String uuid;
                        String text;
                        List<SentenceRange> sentences = new ArrayList<>();
                        if (json.containsKey("uuid")) {
                            // OLD STYLE
                            uuid = (String) json.get("uuid");
                            text = (String) json.get("text");
                            getSentenceRangesFromText(text, sentences);
                        } else {
                            JSONObject derived_metadata = (JSONObject) json.get("derived-metadata");
                            uuid = (String) derived_metadata.get("id");
                            text = (String) derived_metadata.get("text");
                            if (derived_metadata.containsKey("segment-sections")) {
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
                        map.put(uuid, text);
                        sentenceMap.put(uuid, sentences);
                    } );
        } catch (IOException e) {
            throw new TasksRunnerException(e);
        }
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
        return docMap.get(docid);
    }

    public static String getArabicDocumentWithMap (String docid) {
        return arabicDocMap.get(docid);
    }

    public static List<SentenceRange> getDocumentSentences (String docid) {
        return endlishDocSentencesMap.get(docid);
    }

    public static List<SentenceRange> getArabicDocumentSentences (String docid) {
        return arabicDocSentencesMap.get(docid);
    }

}
