package edu.umass.ciir;

import com.cedarsoftware.util.io.JsonWriter;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import java.io.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class QueryFormulator {
    private static final Logger logger = Logger.getLogger("TasksRunner");
    private Map<String, String> queries = new ConcurrentHashMap<>();
    private Map<String, String> nonTranslatedQueries = new ConcurrentHashMap<>();
    protected AnalyticTasks tasks;
    String phase;
    String mode;
    String queryFileNameKey;
    String submissionId;

    QueryFormulator(String submissionId, String mode, AnalyticTasks tasks,  String phase, String queryFileNameKey) {
        this.tasks = tasks;
        this.phase = phase;
        this.mode = mode;
        this.submissionId = submissionId;
        this.queryFileNameKey = queryFileNameKey;
    }

    protected void buildQueries() {}
}
