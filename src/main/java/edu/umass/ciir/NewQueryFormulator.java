package edu.umass.ciir;

import com.cedarsoftware.util.io.JsonWriter;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class NewQueryFormulator {
    private Map<String, String> queries = new ConcurrentHashMap<>();
    private Map<String, String> nonTranslatedQueries = new ConcurrentHashMap<>();
    Logger logger;
    AnalyticTasks tasks;
    String mode;

    NewQueryFormulator(AnalyticTasks tasks) {
        this.tasks = tasks;
        this.mode = tasks.getMode();
        logger = Logger.getLogger("TasksRunner");
    }

    public void setQuery(String key, String query) {
        queries.put(key, query);
    }
    public void setNonTranslatedQuery(String key, String query) {
        nonTranslatedQueries.put(key, query);
    }

    public void setNonTranslatedQueries(Map<String, String> nonTranslatedQueries) {
        this.nonTranslatedQueries = nonTranslatedQueries;
    }

    public void setQueries(Map<String, String> queries) {
        this.queries = queries;
    }

    protected void buildQueries(String queryFileName) {}

    protected void writeQueryFile(String queryFileName) {
        // Output the query list as a JSON file,
        // in the format Galago's batch-search expects as input
        try {
            JSONArray qlist = new JSONArray();
            for (Map.Entry<String, String> entry : queries.entrySet()) {
                JSONObject qentry = new JSONObject();
                qentry.put("number", entry.getKey());
                String query = entry.getValue();
                /* WRONG: it's a REGEX so this just removes all "#sdm"
                query = query.replaceAll("#sdm()", " ");
                */
                query = query.replaceAll("#sdm\\(\\)", " ");   // empty #sdm causes Galago errors
                qentry.put("text", query);
                qlist.add(qentry);
            }
            JSONObject outputQueries = new JSONObject();
            outputQueries.put("queries", qlist);
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(queryFileName)));
            writer.write(outputQueries.toJSONString());
            writer.close();

            String niceFormattedJson = JsonWriter.formatJson(outputQueries.toJSONString());
            writer = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(queryFileName + ".PRETTY.json")));
            writer.write(niceFormattedJson);
            writer.close();

            qlist = new JSONArray();
            for (Map.Entry<String, String> entry : nonTranslatedQueries.entrySet()) {
                JSONObject qentry = new JSONObject();
                qentry.put("number", entry.getKey());
                qentry.put("text", entry.getValue());
                qlist.add(qentry);
            }
            outputQueries = new JSONObject();
            outputQueries.put("queries", qlist);
            writer = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(queryFileName + ".NON_TRANSLATED.json")));
            writer.write(outputQueries.toJSONString());
            writer.close();

            niceFormattedJson = JsonWriter.formatJson(outputQueries.toJSONString());
            writer = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(queryFileName + ".NON_TRANSLATED.PRETTY.json")));
            writer.write(niceFormattedJson);
            writer.close();

        } catch (Exception e) {
            throw new BetterComponentException(e.getMessage());
        }
    }

    public static String filterCertainCharactersPostTranslation(String q) {
        if (q == null || q.length() == 0) {
            return q;
        }
        else {
            q = q.replaceAll("\\.", " ");  // with the periods the queries hang
            q = q.replaceAll("@", " ");  // Galago has @ multi-word token thing
            q = q.replaceAll("\"", " "); // remove potential of mis-matched quotes
            q = q.replaceAll("“", " ");  // causes Galago to silently run an empty query
            q = q.replaceAll("”", " ");
            return q;
        }
    }
    public static String filterCertainCharacters(String q) {
        if (q == null || q.length() == 0) {
            return q;
        }
        else {
            q = q.replaceAll("\\.", " ");  // with the periods the queries hang
            q = q.replaceAll("\\(", " ");  // parentheses are included in the token
            q = q.replaceAll("\\)", " ");  // which causes that term to not be matched
            q = q.replaceAll("@", " ");  // Galago has @ multi-word token thing
            q = q.replaceAll("#", " ");  // Galago thinks #926 is an illegal node type
            q = q.replaceAll("\"", " "); // remove potential of mis-matched quotes
            q = q.replaceAll("“", " ");  // causes Galago to silently run an empty query
            q = q.replaceAll("”", " ");
            return q;
        }
    }
}
