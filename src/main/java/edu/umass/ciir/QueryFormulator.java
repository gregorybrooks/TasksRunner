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

    QueryFormulator(AnalyticTasks tasks) {
        this.tasks = tasks;
        this.mode = tasks.getMode();
    }

    protected void buildQueries(String phase, Pathnames.ProcessingModel processingModel, String queryFileName) {}
}
