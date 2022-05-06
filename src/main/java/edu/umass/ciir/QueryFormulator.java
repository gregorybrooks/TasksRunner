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
    protected String mode;
    String phase;
    String queryFileNameKey;

    QueryFormulator(AnalyticTasks tasks,  String phase, String queryFileNameKey) {
        this.tasks = tasks;
        this.mode = tasks.getMode();
        this.phase = phase;
        this.queryFileNameKey = queryFileNameKey;
    }

    protected void buildQueries() {}
}
