package edu.umass.ciir;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.*;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import static java.nio.file.StandardCopyOption.*;

public class QueryManager {
    private static final int MAX_DOCIDS = 100000;  // max number of hits to look at
    public Map<String, String> queries;
    public Map<String, String> nonTranslatedQueries;

    public Run run;
    private String queryFileName;
    private String queryFileFullPathName;
    private String queryFileNameOnly;
    private String runFileName;
    private String evaluationTaskLevelFileName;
    private String evaluationRequestLevelFileName;
    private String key;
    private String taskFileNameGeneric;
    private AnalyticTasks tasks;
    private long runTime = 0;
    private String rerankedRunFile;
    private String phase;
    private static final Logger logger = Logger.getLogger("TasksRunner");

    public QueryManager(AnalyticTasks tasks, String queryFormulationName, String phase) {
        try {
            this.tasks = tasks;
            this.phase = phase;
            taskFileNameGeneric = tasks.getTaskFileName();
            // When the query formulation name is a Docker image name, it might be qualified
            // with the owner's name and a "/", which confuses things when the formulation name
            // is used in a pathname, so change the "/" to a "-"
            queryFormulationName = queryFormulationName.replace("/", "-");
            this.key =  tasks.getMode() + "." + phase + "." + queryFormulationName;
            queryFileNameOnly = key + ".queries.json";
            queryFileFullPathName = Pathnames.queryFileLocation + queryFileNameOnly;
            queryFileName = queryFileFullPathName; // for historical reasons
            runFileName = Pathnames.runFileLocation + key + ".out";
            evaluationTaskLevelFileName = Pathnames.evaluationFileLocation + "/" + key + ".TASK.csv";
            evaluationRequestLevelFileName = Pathnames.evaluationFileLocation + "/" + key + ".REQUEST.csv";

            queries = getGeneratedQueries(queryFileName);
            nonTranslatedQueries = getGeneratedQueries(queryFileName + ".NON_TRANSLATED");
            rerankedRunFile = Pathnames.runFileLocation + key + ".RERANKED.out";

            run = new Run();
        } catch (Exception e) {
            throw new TasksRunnerException(e.getMessage());
        }
    }

    public void readQueryFile() {
        queries = getGeneratedQueries(queryFileName);
    }

    public String getKey() {
        return key;
    }

    public void setRun(String runFileName) {
        run = new Run(runFileName);  // Get new run file into memory
    }

    // file name only, no path
    public String getQueryFileNameOnly() {
        return queryFileNameOnly;
    }

    public AnalyticTasks getTasks() {
        return tasks;
    }

    /* vars needed by the Docker version of the reranker:
    $TASK_FILE $DATA_DIR $QLANG $DLANG $RUNFILE_MASK $DEVICE $NUM_CPU $TOPK $OUTPUT_DIR
    */
    public void callReranker(String currentRunFile, String outputFileName) {
        try {
            String mode = Pathnames.mode;
            String dockerImageName = Pathnames.rerankerDockerImage;
            String analyticTasksInfoFilename = mode + ".analytic_tasks.json";
            String sudo = (Pathnames.sudoNeeded ? "sudo" : "");
            String gpu_parm = (!Pathnames.gpuDevice.equals("") ? " --gpus device=" + Pathnames.gpuDevice : "");
            // if 4 GPUs, 0 is first one, 1 is second one, etc.
            String deviceParm = "cuda:0";  // or cpu
            String command = sudo + " docker run --rm"
                    + gpu_parm
                    + " --env MODE=" + mode
                    + " --env DEVICE=" + deviceParm
                    + " --env TASK_FILE=" + Pathnames.eventExtractorFileLocation + analyticTasksInfoFilename
                    + " -v " + Pathnames.eventExtractorFileLocation + ":" + Pathnames.eventExtractorFileLocation
                    + " --env DATA_DIR=" + Pathnames.eventExtractorFileLocation
                    + " --env OUTPUT_DIR=" + Pathnames.eventExtractorFileLocation
                    + " --env QLANG=en --env DLANG=" + Pathnames.targetLanguage
                    + " --env RUNFILE_MASK='" + mode + ".[req-num].REQUESTHITS.events.json'"
                    + " --env NUM_CPU=8 --env TOPK=1000"

                    + " " + dockerImageName
                    + " sh -c ./runit.sh";
            String logFile = Pathnames.logFileLocation + mode + "/reranker-docker-program.out";
            String tempCommand = command + " >& " + logFile;

            logger.info("Executing this command: " + tempCommand);

            try {
                Files.delete(Paths.get(logFile));
            } catch (IOException ignore) {
                // do nothing
            }

            int exitVal = 0;
            try {
                ProcessBuilder processBuilder = new ProcessBuilder();
                processBuilder.command("bash", "-c", tempCommand);
                Process process = processBuilder.start();

                exitVal = process.waitFor();
            } catch (Exception cause) {
                logger.log(Level.SEVERE, "Exception doing docker image execution", cause);
                throw new TasksRunnerException(cause);
            } finally {
                StringBuilder builder = new StringBuilder();
                try (Stream<String> stream = Files.lines( Paths.get(logFile), StandardCharsets.UTF_8))
                {
                    stream.forEach(s -> builder.append(s).append("\n"));
                    logger.info("Docker container output log:\n" + builder.toString());
                } catch (IOException ignore) {
                    // logger.info("IO error trying to read output file. Ignoring it");
                }
            }
            if (exitVal != 0) {
                logger.log(Level.SEVERE, "Unexpected ERROR from Docker container, exit value is: " + exitVal);
                throw new TasksRunnerException("Unexpected ERROR from Docker container, exit value is: " + exitVal);
            }

            Files.copy(new File(Pathnames.eventExtractorFileLocation + "fused.run").toPath(),
                    new File(outputFileName).toPath(), REPLACE_EXISTING);

        } catch (Exception e) {
            throw new TasksRunnerException(e);
        }
    }

    public void callRerankerOLD(String currentRunFile, String outputFileName) {
        try {
            String logFile = Pathnames.logFileLocation + Pathnames.mode + "/reranker.log";
            String tempCommand = "cd " + Pathnames.programFileLocation + "BETTER-prod && PYTHONPATH=" + Pathnames.programFileLocation + "BETTER-prod "
                    + "CUDA_VISIBLE_DEVICES=3 python3 "
                    + Pathnames.programFileLocation + "BETTER-prod/src/document_reader.py --data_dir="
                    + Pathnames.eventExtractorFileLocation
                    + " --task_file=" + tasks.getInternalAnalyticTasksInfoFileName()
                    + " --prefix=" + tasks.getMode() + "."
                    + " >& " + logFile;
            logger.info("Executing this command: " + tempCommand);

            try {
                Files.delete(Paths.get(logFile));
            } catch (IOException ignore) {
                ;
            }

            int exitVal = 0;
            try {
                ProcessBuilder processBuilder = new ProcessBuilder();
                processBuilder.command("bash", "-c", tempCommand);
                Process process = processBuilder.start();

                exitVal = process.waitFor();
            } catch (Exception cause) {
                logger.log(Level.SEVERE, "Exception doing reranker execution", cause);
                throw new TasksRunnerException(cause);
            } finally {
                StringBuilder builder = new StringBuilder();
                try (Stream<String> stream = Files.lines( Paths.get(logFile), StandardCharsets.UTF_8))
                {
                    stream.forEach(s -> builder.append(s).append("\n"));
                    logger.info("reranker output log:\n" + builder.toString());
                } catch (IOException ignore) {
                    // logger.info("IO error trying to read output file. Ignoring it");
                }
            }
            if (exitVal != 0) {
                logger.log(Level.SEVERE, "Unexpected ERROR from reranker, exit value is: " + exitVal);
                throw new TasksRunnerException("Unexpected ERROR from reranker, exit value is: " + exitVal);
            }
            Files.copy(new File(Pathnames.eventExtractorFileLocation + "path_to_runs/fused.run").toPath(),
                    new File(outputFileName).toPath(), REPLACE_EXISTING);

        } catch (Exception e) {
            throw new TasksRunnerException(e);
        }

    }

    public void rerank() {
        callReranker(runFileName, rerankedRunFile);
        // Refresh the in-memory runfile data
        run = new Run(rerankedRunFile);
    }

    /**
     * Reads the generated query file for a given query formulation, created by ConvertDryRunTasks,
     * and makes a Map of the generated queries, with request number as key.
     *
     * @return Returns the Map of request number to generated query text.
     * @throws IOException
     * @throws ParseException
     */
    private Map<String, String> getGeneratedQueries(String queryFileName) {
        logger.info("Reading query file: " + queryFileName);
        Map<String, String> queriesMap = new HashMap<>();
        try {
            File tempFile = new File(queryFileName);
            if (tempFile.exists()) {
                Reader reader = new BufferedReader(new InputStreamReader(
                        new FileInputStream(queryFileName)));
                JSONParser parser = new JSONParser();
                JSONObject head = (JSONObject) parser.parse(reader);
                JSONArray queries = (JSONArray) head.get("queries");
                for (Object oRequest : queries) {
                    JSONObject r = (JSONObject) oRequest;
                    String reqNum = (String) r.get("number");
                    String reqText = (String) r.get("text");
                    queriesMap.put(reqNum, reqText);
                }
            }
        } catch (Exception ex) {
            throw new TasksRunnerException(ex);
        }
        return queriesMap;
    }

    public void resetQueries() {
        if (queries.size() > 0)
            queries.clear();
        if (nonTranslatedQueries.size() > 0)
            nonTranslatedQueries.clear();
    }

    public Map<String,String> getQueries() { return queries; }

    /**
     * Writes a separate query file for each task, containing the queries for the requests
     * belonging to that task. So they can be executed against the task-level index.
     */
    public void writeQueryFiles() {
        for (Task t : tasks.getTaskList()) {
            String queryFileName = Pathnames.queryFileLocation + key + ".TASK." + t.taskNum + ".queries.json";
            logger.info("Writing query file " + queryFileName);
            writeQueryFile(t.taskNum, queryFileName);
        }
    }

    private String getTaskIDFromRequestID(String requestID) {
        String thisTaskID;
        if (Pathnames.analyticTasksFileFormat.equals("FARSI")) {
            thisTaskID = requestID;
        } else {
            thisTaskID = requestID.substring(0, 5);
        }
        return thisTaskID;
    }

    /**
     * Writes a query file for all of the requests for this task.
     * @param taskID the task
     * @param outputFileName the name for the query file
     */
    private void writeQueryFile(String taskID, String outputFileName) {
        // Output the query list as a JSON file,
        // in the format Galago's batch-search expects as input
        try {
            JSONArray qlist = new JSONArray();
            for (Map.Entry<String, String> entry : queries.entrySet()) {
                String requestID = entry.getKey();
                String thisTaskID = getTaskIDFromRequestID(requestID);
                if (thisTaskID.equals(taskID)) {
                    JSONObject qentry = new JSONObject();
                    qentry.put("number", entry.getKey());
                    qentry.put("text", entry.getValue());
                    qlist.add(qentry);
                }
            }
            JSONObject outputQueries = new JSONObject();
            outputQueries.put("queries", qlist);
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(outputFileName)));
            writer.write(outputQueries.toJSONString());
            writer.close();
        } catch (Exception cause) {
            throw new TasksRunnerException(cause);
        }
    }

    public long getRunTime() {
        return runTime;
    }

    public List<String> getAllDocids(String requestID) {
        if (run.requestRuns.containsKey(requestID)) {
            List<String> docids = run.requestRuns.get(requestID).docids;
            return docids;
        } else {
            return new ArrayList<String>();
        }
    }

    public List<String> getDocids(String requestID, int size) {
        if (run.requestRuns.containsKey(requestID)) {
            List<String> docids = run.requestRuns.get(requestID).docids;
            if (size < docids.size()) {
                return docids.subList(0, size);
            } else {
                return docids;
            }
        } else {
            return new ArrayList<String>();
        }
    }

    public String getScore(String requestID, String docid) {
        List<Hit> hits = run.requestRuns.get(requestID).hits;
        for (Hit h : hits) {
            if (h.docid.equals(docid)) {
                return h.score;
            }
        }
        return "";
    }

    public class Hit {
        public String docid;
        public String score;
        Hit(String docid, String score) {
            this.docid = docid;
            this.score = score;
        }
        Hit(Hit other) {
            this.docid = other.docid;
            this.score = other.score;
        }
    }
    /**
     * Within the solution are multiple Tasks, and each Task has multiple Requests.
     * This class represents the results of running the query for a Request.
     */
    public class RequestRun {
        public String requestID;
        public List<String> docids;
        public List<Hit> hits;
        public List<MissingDoc> missingDocs;

        RequestRun(String requestID, List<String> docids, List<Hit> hits) {
            this.requestID = requestID;
            this.docids = docids;
            this.missingDocs = null;
            this.hits = hits;
        }
    }

    /**
     * Represents the results of a particular query formulation's Galago execution.
     */
    public class Run {
        public Map<String, RequestRun> requestRuns = new HashMap<String, RequestRun>();
        Run(String theRunFileName) {
            readFile(theRunFileName);
        }
        /**
         * Reads in the file that was output from Galago's batch-search function,
         * which is the top x hits for each of the Requests in the input file.
         */
        Run() {
            readFile(runFileName);
        }

        /**
         * Reads in the file that was output from Galago's batch-search function,
         * which is the top x hits for each of the Requests in the input file.
         */
        private void readFile(String theRunFileName) {
            File f = new File(theRunFileName);
            if (f.exists()) {
                logger.info("Opening run file " + theRunFileName);
                List<String> docids = new ArrayList<>();
                List<Hit> hits = new ArrayList<>();
                try (BufferedReader br = new BufferedReader(new FileReader(theRunFileName))) {
                    String line;
                    String prevQueryID = "NONE";
                    int docidsAdded = 0;
                    while ((line = br.readLine()) != null) {
                        String queryID = line.split("[ \t]+")[0];
                        if (!prevQueryID.equals(queryID)) {
                            if (!prevQueryID.equals("NONE")) {
                                // Clone the lists
                                List<String> cloned_docid_list
                                        = new ArrayList<String>(docids);
                                List<Hit> cloned_hit_list
                                        = new ArrayList<Hit>(hits);
                                RequestRun r = new RequestRun(prevQueryID, cloned_docid_list, cloned_hit_list);
                                requestRuns.put(prevQueryID, r);
                                docids.clear();
                                hits.clear();
                                docidsAdded = 0;
                            }
                        }
                        prevQueryID = queryID;
                        if (docidsAdded < MAX_DOCIDS) {
                            String[] tokens = line.split("[ \t]+");
                            String docid = tokens[2];
                            String score = tokens[4];
                            docids.add(docid);
                            Hit h = new Hit(docid, score);
                            hits.add(h);
                            ++docidsAdded;
                        }
                    }
                    if (!prevQueryID.equalsIgnoreCase("")) {
                        // Clone the lists
                        List<String> cloned_docid_list
                                = new ArrayList<String>(docids);
                        List<Hit> cloned_hit_list
                                = new ArrayList<Hit>(hits);
                        RequestRun r = new RequestRun(prevQueryID, cloned_docid_list, cloned_hit_list);
                        requestRuns.put(prevQueryID, r);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                logger.info("Run file requested does not exist: " + theRunFileName);
            }
        }
    }

    public class MissingDoc {
        public String taskID;
        public String relevanceStrength;
        public String docid;
        public String doctext;
        MissingDoc(String taskID, String relevanceStrength, String docid, String doctext) {
            this.taskID = taskID;
            this.relevanceStrength = relevanceStrength;
            this.docid = docid;
            this.doctext = doctext;
        }
        public String getDocid() {
            return docid;
        }

        public void setDocid(String docid) {
            this.docid = docid;
        }

    }

    /**
     * Creates a single CLEAR.out file that contains all of the runfiles from the
     * executions of the requests, which were done one-file-per-task.
     * @param runFiles the names of the runfiles to be merged
     */
    private void mergeRunFiles(List<String> runFiles) {
        String combinedRunFileName = Pathnames.runFileLocation + key + ".out";
        Path outFile= Paths.get(combinedRunFileName);
        try {
            Files.delete(outFile);
        } catch (IOException ignore) {
            ;
        }
        Charset charset = StandardCharsets.UTF_8;
        try {
            for (String runFile : runFiles) {
                Path inFile=Paths.get(runFile);
                List<String> lines = Files.readAllLines(inFile, charset);
                Files.write(outFile, lines, charset, StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            }
        } catch (IOException e) {
            throw new TasksRunnerException(e);
        }
        // Refresh the in-memory runfile data
        run = new Run(combinedRunFileName);
    }

    /**
     * For each task, executes the task's request queryfile with Galago's batch-search,
     * using a single Galago thread, getting 1000 hits, producing a task-level
     * runfile, using the task's task-level index built previously from the task's top hits.
     * Also, it creates an event extractor input file for each task, while the hits
     * are in memory.
     */
    public void executeRequestQueries(String taskLevelFormulationName) {
        // When the query formulation name is a Docker image name, it might be qualified
        // with the owner's name and a "/", which confuses things when the formulation name
        // is used in a pathname, so change the "/" to a "-"
        String finalTaskLevelFormulationName = taskLevelFormulationName.replace("/", "-");

        AtomicLong totalRunTime = new AtomicLong(0);
        List<String> runFiles = new CopyOnWriteArrayList<>();
        logger.info("Executing request queries for " + tasks.getTaskList().size());
        tasks.getTaskList().parallelStream().forEach(t ->  {
            String theRunFileName = Pathnames.runFileLocation + key + ".TASK." + t.taskNum + ".out";
            String queryFileName = Pathnames.queryFileLocation + key + ".TASK." + t.taskNum + ".queries.json";

            String taskLevelKey = tasks.getMode() + ".Task."
                    + finalTaskLevelFormulationName;
            String indexName = Pathnames.indexLocation + taskLevelKey + "." + t.taskNum + ".PARTIAL";
            runFiles.add(theRunFileName);

            logger.info("Executing request queries for task " + t.taskNum);

            executeAgainstPartialIndex(1, 1000, queryFileName, theRunFileName, indexName, t.taskNum);

            totalRunTime.getAndAdd(runTime);
        });
        logger.info("Merging run files");

        mergeRunFiles(runFiles);

        runTime = totalRunTime.get();
    }

    /**
     * Executes the default queryfile with Galago's batch-search,
     * using the default number of Galago batch threads,
     * requesting N hits, producing the default runfile, using the Arabic index.
     * @param N the number of hits to get
     */
    public void execute(int N) {
        execute(3, N, queryFileName, runFileName, Pathnames.targetIndexLocation);
    }

    /**
     * Executes the specified queryfile with Galago's batch-search,
     * using the specified number of Galago batch threads,
     * requesting the specified number of hits, producing the specified runfile, using
     * the specified index.
     *
     * @param threadCount
     * @param N
     * @param theQueryFileName
     * @param theRunFileName
     * @param indexName
     */
    private void execute(int threadCount, int N, String theQueryFileName, String theRunFileName, String indexName) {
        Instant start = Instant.now();

        String command = "galago threaded-batch-search";
        if (threadCount == 1) {
            command = "galago batch-search";
        }
        String galagoLogFile = Pathnames.logFileLocation + Pathnames.mode + "/galago.log";
        String arabicParm = "";
        if (Pathnames.targetLanguage == Pathnames.Language.ARABIC) {
            arabicParm = "--defaultTextPart=postings.snowball ";
        }
        String tempCommand = Pathnames.galagoLocation + command
                + " --outputFile=" + theRunFileName + " --threadCount=" + threadCount
                + " --systemName=CLEAR " + arabicParm + "--trec=true --index=" + indexName
                + " --requested=" + N + " " + theQueryFileName + " >& " + galagoLogFile;

        logger.info("Executing this command: " + tempCommand);
        logger.info("Run file will be  " + theRunFileName);

        try {
            Files.delete(Paths.get(galagoLogFile));
        } catch (IOException ignore) {
            ;
        }

        int exitVal = 0;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command("bash", "-c", tempCommand);
            Process process = processBuilder.start();

            exitVal = process.waitFor();
        } catch (Exception cause) {
            logger.log(Level.SEVERE, "Exception doing Galago execution", cause);
            throw new TasksRunnerException(cause);
        } finally {
            StringBuilder builder = new StringBuilder();
            try (Stream<String> stream = Files.lines( Paths.get(galagoLogFile), StandardCharsets.UTF_8))
            {
                stream.forEach(s -> builder.append(s).append("\n"));
                logger.info("Galago output log:\n" + builder.toString());
            } catch (IOException ignore) {
                // logger.info("IO error trying to read Galago output file. Ignoring it");
            }
        }
        if (exitVal != 0) {
            logger.log(Level.SEVERE, "Unexpected ERROR from Galago, exit value is: " + exitVal);
            throw new TasksRunnerException("Unexpected ERROR from Galago, exit value is: " + exitVal);
        }

        run = new Run(theRunFileName);  // Get new run file into memory
        Instant end = Instant.now();
        Duration interval = Duration.between(start, end);
        runTime  = interval.toMinutes();
    }

    /**
     * Executes the specified queryfile with Galago's batch-search,
     * using the specified number of Galago batch threads,
     * requesting the specified number of hits, producing the specified runfile, using
     * the specified PARTIAL index.
     *
     * @param threadCount
     * @param N
     * @param theQueryFileName
     * @param theRunFileName
     * @param indexName
     */
    private void executeAgainstPartialIndex(int threadCount, int N, String theQueryFileName,
                                            String theRunFileName, String indexName, String taskID) {
        Instant start = Instant.now();

        String command = "galago threaded-batch-search";
        if (threadCount == 1) {
            command = "galago batch-search";
        }
        String arabicPart = "";
        if (Pathnames.targetLanguage == Pathnames.Language.ARABIC) {
            arabicPart = " --defaultTextPart=postings.snowball";
        }
        String galagoLogFile = Pathnames.logFileLocation + Pathnames.mode + "/galago_" + taskID + "_executeAgainstPartial.log";
        String tempCommand = Pathnames.galagoLocation + command
                + " --outputFile=" + theRunFileName + " --threadCount=" + threadCount
                + " --systemName=CLEAR --trec=true "
                + " --index/partial=" + indexName
                + " --index/full=" + Pathnames.targetIndexLocation
                + " --defaultIndexPart=partial --backgroundIndex=full"
                + arabicPart
                + " --requested=" + N + " " + theQueryFileName + " >& " + galagoLogFile;

        logger.info("Executing this command: " + tempCommand);
        logger.info("Run file will be  " + runFileName);

        try {
            Files.delete(Paths.get(galagoLogFile));
        } catch (IOException ignore) {
            ;
        }

        int exitVal = 0;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command("bash", "-c", tempCommand);
            Process process = processBuilder.start();

            exitVal = process.waitFor();
        } catch (Exception cause) {
            logger.log(Level.SEVERE, "Exception doing Galago execution", cause);
            throw new TasksRunnerException(cause);
        } finally {
            StringBuilder builder = new StringBuilder();
            try (Stream<String> stream = Files.lines( Paths.get(galagoLogFile), StandardCharsets.UTF_8))
            {
                stream.forEach(s -> builder.append(s).append("\n"));
                logger.info("Galago output log:\n" + builder.toString());
            } catch (IOException ignore) {
                // logger.info("IO error trying to read Galago output file. Ignoring it");
            }
        }
        if (exitVal != 0) {
            logger.log(Level.SEVERE, "Unexpected ERROR from Galago, exit value is: " + exitVal);
            throw new TasksRunnerException("Unexpected ERROR from Galago, exit value is: " + exitVal);
        }

        run = new Run(theRunFileName);  // Get new run file into memory
        Instant end = Instant.now();
        Duration interval = Duration.between(start, end);
        runTime  = interval.toMinutes();
    }

    /**
     * Builds a Galago index on the top hits for each task.
     */
    public void buildTaskLevelIndexes() {
        tasks.getTaskList().parallelStream().forEach(t ->  buildTaskPartialIndex(t.taskNum));
    }

    /**
     * Creates a file containing the docids from the top hits
     * for this task, ready to be indexed by Galago.
     * @param taskID the task ID
     */
    private void createTaskDocIDListFromHits(String taskID) {
        List<String> docids = this.getAllDocids(taskID);
        String outputFile = Pathnames.taskCorpusFileLocation + key + "." + taskID + ".DOC_LIST.txt";
        try {
            PrintWriter writer = new PrintWriter(outputFile);
            for (String docid : docids) {
                writer.println(docid);
            }
            writer.close();
        } catch (Exception cause) {
            logger.log(Level.SEVERE, "Exception creating task-level doc list file", cause);
            throw new TasksRunnerException(cause);
        }
    }

    /**
     * Builds a Galago partial index on the top hits for this task.
     * @param taskID the task ID
     */
    public void buildTaskPartialIndex(String taskID) {
        createTaskDocIDListFromHits(taskID);

        String confFile = createGalagoPartialIndexConfFile(taskID);
        Instant start = Instant.now();

        String galagoLogFile = Pathnames.logFileLocation + Pathnames.mode + "/galago_" + taskID + "_indexbuild.log";
        String tempCommand = Pathnames.galagoLocation + "galago build-partial-index --documentNameList=" +
                Pathnames.taskCorpusFileLocation + key + "." + taskID + ".DOC_LIST.txt" +
                " --index=" + Pathnames.targetIndexLocation +
                " --partialIndex=" + Pathnames.indexLocation + key + "." + taskID + ".PARTIAL "
                + confFile + " >& " + galagoLogFile;  // this is the way to specify fields for a partial index build

        logger.info("Executing this command: " + tempCommand);

        try {
            Files.delete(Paths.get(galagoLogFile));
        } catch (IOException ignore) {
            ;
        }

        int exitVal = 0;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command("bash", "-c", tempCommand);
            Process process = processBuilder.start();

            exitVal = process.waitFor();
        } catch (Exception cause) {
            logger.log(Level.SEVERE, "Exception doing Galago execution", cause);
            throw new TasksRunnerException(cause);
        } finally {
            StringBuilder builder = new StringBuilder();
            try (Stream<String> stream = Files.lines( Paths.get(galagoLogFile), StandardCharsets.UTF_8))
            {
                stream.forEach(s -> builder.append(s).append("\n"));
                logger.info("Galago output log:\n" + builder.toString());
            } catch (IOException ignore) {
                // logger.info("IO error trying to read Galago output file. Ignoring it");
            }
        }
        if (exitVal != 0) {
            logger.log(Level.SEVERE, "Unexpected ERROR from Galago, exit value is: " + exitVal);
            throw new TasksRunnerException("Unexpected ERROR from Galago, exit value is: " + exitVal);
        }

        Instant end = Instant.now();
        Duration interval = Duration.between(start, end);
        long runTime  = interval.toMinutes();
        logger.info("Galago run time (minutes):\n" + runTime);
    }

    /**
     * Creates a Galago config file specifying the parameters for building the index
     * for this task's top hits. This is the version to be used when building a PARTIAL index.
     * @param taskID the task ID
     * @return the name of the Galago config file, specific to this task
     */
    private String createGalagoPartialIndexConfFile(String taskID) {
        String taskLevelConfFile;
        try {
            JSONObject outputQueries = new JSONObject();
            outputQueries.put("mode", "local" );
            outputQueries.put("fieldIndex", true);
            outputQueries.put("tmpdir", Pathnames.tempFileLocation );

            JSONArray stemmerList = new JSONArray();
            JSONObject stemmerClass = new JSONObject();
            if (Pathnames.targetLanguage == Pathnames.Language.ARABIC) {
                stemmerList.add("krovetz");
                stemmerList.add("snowball");
                stemmerClass.put("krovetz", "org.lemurproject.galago.core.parse.stem.KrovetzStemmer");
                stemmerClass.put("snowball", "org.lemurproject.galago.core.parse.stem.SnowballArabicStemmer");
            } else if (Pathnames.targetLanguage == Pathnames.Language.ENGLISH) {
                stemmerList.add("krovetz");
                stemmerClass.put("krovetz", "org.lemurproject.galago.core.parse.stem.KrovetzStemmer");
            } else if (Pathnames.targetLanguage == Pathnames.Language.FARSI) {
            }
            outputQueries.put("stemmer", stemmerList);
            outputQueries.put("stemmerClass", stemmerClass );

            JSONObject tokenizer = new JSONObject();
            JSONArray fields = new JSONArray();
            fields.add("exid");
            tokenizer.put("fields", fields);
            JSONObject formats = new JSONObject();
            formats.put("exid", "string");
            tokenizer.put("formats", formats);
            outputQueries.put("tokenizer", tokenizer);
            outputQueries.put("galagoJobDir", Pathnames.galagoJobDirLocation + taskID);
            outputQueries.put("deleteJobDir", true);
            outputQueries.put("mem", "40g");

            taskLevelConfFile = Pathnames.taskCorpusFileLocation + key + "." + taskID + ".conf";
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(taskLevelConfFile)));
            writer.write(outputQueries.toJSONString());
            writer.close();
        } catch (Exception cause) {
            throw new TasksRunnerException(cause);
        }
        return taskLevelConfFile;
    }

    public Map<String, EvaluationStats> evaluateTaskLevel() {
        return new EvaluatorTaskLevel().evaluate();
    }

    /**
     * Evaluates a run file, request level.
     * @param phase RAW or RERANKED (added to the file name)
     * @return
     */
    public Map<String, Double> evaluate(String phase) {
        evaluationRequestLevelFileName = Pathnames.evaluationFileLocation + key + "." + phase + ".REQUEST.csv";
        return new Evaluator().evaluate();
    }

    public class EvaluationStats {
        private String key;
        private int n;
        private Double recallAtN;
        private Double recallAt100;
        private Double precisionAt100;
        int maxRelevantDocRank;

        public Double getPrecisionAt100() {
            return precisionAt100;
        }

        public Double getRecallAt100() {
            return recallAt100;
        }

        public void setPrecisionAt100(Double precisionAt100) {
            this.precisionAt100 = precisionAt100;
        }

        public void setRecallAt100(Double recallAt100) {
            this.recallAt100 = recallAt100;
        }

        public int getMaxRelevantDocRank() {
            return maxRelevantDocRank;
        }

        public void setMaxRelevantDocRank(int maxRelevantDocRank) {
            this.maxRelevantDocRank = maxRelevantDocRank;
        }

        public String getKey() {
            return key;
        }

        public int getN() {
            return n;
        }

        public void setN(int n) {
            this.n = n;
        }

        public Double getRecallAtN() {
            return recallAtN;
        }

        public void setRecallAtN(Double recallAtN) {
            this.recallAtN = recallAtN;
        }

        EvaluationStats(String key, int n, Double recallAtN, int maxRelevantDocRank, Double recallAt100,
                        Double precisionAt100) {
            this.key = key;
            this.n = n;
            this.recallAtN = recallAtN;
            this.maxRelevantDocRank = maxRelevantDocRank;
            this.recallAt100 = recallAt100;
            this.precisionAt100 = precisionAt100;
        }
    }

    public class Evaluator {
        /**
         * Calculates the normalized discounted cumulative gain
         * for this request and this ranked set of docs.
         *
         * @param requestID The request, with its relevance judgments available.
         * @param runDocids The ranked hits.
         * @return The calculated nDCG.
         */

        private Map<String, Double> stats = new TreeMap<>();

        /** This version computes nDCG to the depth equal to the number
         * of known relevant documents ("R"), instead of a hard cutoff of 10
         * as in his original version.
         *
         * @param requestID
         * @param runDocids
         * @return
         */
        private double calculatenDCG(String requestID, List<String> runDocids) {
            List<RelevanceJudgment> judgments = tasks.getPositiveRelevanceJudgments(requestID);
            int cutoff = judgments.size();
            /* Calculate the ideal discounted cumulative gain for this query */
            judgments.sort(Comparator.comparingInt(RelevanceJudgment::getRelevanceJudgmentValue)
                    .reversed());
            double iDCG = 0.0;
            int index = 1;
            for (RelevanceJudgment j : judgments) {
                if (index == 1) {
                    iDCG += j.getRelevanceJudgmentValueWithMapping();
                } else {
                    iDCG += (j.getRelevanceJudgmentValueWithMapping() / ((Math.log(index + 1) / Math.log(2))));
                }
                ++index;
            }
            /* Calculate discounted cumulative gain of the ranked hits */
            double DCG = 0.0;
            index = 1;
            for (String docid : runDocids) {
                if (index == 1) {
                    DCG += tasks.getRelevanceJudgmentValueWithMapping(requestID, docid);
                } else {
                    DCG += (tasks.getRelevanceJudgmentValueWithMapping(requestID, docid)
                            / ((Math.log(index + 1) / Math.log(2))));
                }
                ++index;
                if (index > cutoff) {
                    break;
                }
            }
            /* Calculate the normalized discounted cumulative gain */
            double nCDG = DCG / iDCG;
            return nCDG;
        }

        /**
         * Evaluates the query formulations and outputs a CSV file of evaluation results.
         * This version only calculates nDCG.
         * <p>
         * For averaging the evaluation results, we use the MICRO approach.
         *
         * @throws IOException
         * @throws InterruptedException
         */
        public Map<String,Double> evaluate() {
            try {

                /* Create and open the output CSV file */
                FileWriter csvWriter = new FileWriter(evaluationRequestLevelFileName);
                /* Write the header line */
                csvWriter.append("Solution");
                csvWriter.append(",");
                csvWriter.append("Request");
                csvWriter.append(",");
                csvWriter.append("nDCG@R");
                csvWriter.append("\n");

                List<String> requestIDs = tasks.getRequestIDs();
                ListIterator<String> requestIDIterator = requestIDs.listIterator();

                /* macro averaging approach accumulators */
                double totalTasknCDG = 0.0;

                int totalRequests = 0;
                int totalTasks = 0;  // this will be calculated as we go through the requests
                String prevTaskID = "EMPTY";  // used to detect Task changes
                String taskID = "";  // used to detect Task changes
                int totalRequestsInTask = 0;  // this will be calculated as we go through the requests

                /* macro averaging approach task-level accumulators */
                double tasknCDG = 0.0;

                double totalnDCG = 0.0;

                /* Assumption: all requests for a task are contiguous as we iterate them */
                while (requestIDIterator.hasNext()) {
                    String requestID = requestIDIterator.next();
                    List<String> runDocids = getDocids(requestID, 1000);
                    /* If this solution did not provide a query for this request, skip it */
                    if (runDocids.size() == 0) {
                        continue;
                    }
                    /* If we have no relevance judgments for this request, skip it */
                    if (!tasks.hasRelevanceJudgments(requestID)) {
                        continue;
                    }

                    taskID = getTaskIDFromRequestID(requestID);
                    if (!taskID.equals(prevTaskID) && !prevTaskID.equals("EMPTY")) {
                        ++totalTasks;
                        totalTasknCDG += (tasknCDG / totalRequestsInTask);
                        tasknCDG = 0.0;
                        totalRequestsInTask = 0;
                    }
                    prevTaskID = taskID;

                    ++totalRequests;
                    ++totalRequestsInTask;

                    double nDCG = calculatenDCG(requestID, runDocids);

                    totalnDCG += nDCG;
                    /* Accumulators for macro averaging approach */
                    tasknCDG += nDCG;

                    csvWriter.append(key);
                    csvWriter.append(",");
                    csvWriter.append(requestID);
                    csvWriter.append(",");
                    csvWriter.append(String.format("%.4f", nDCG));
                    csvWriter.append("\n");

                    stats.put(requestID,nDCG);

                }
                /* Flush out that last task */
                if (!prevTaskID.equals("EMPTY")) {
                    ++totalTasks;
                    totalTasknCDG += (tasknCDG / totalRequestsInTask);
                }

                double microAvgnDCG = totalnDCG / totalRequests;

                csvWriter.append(key);
                csvWriter.append(",");
                csvWriter.append("TOTAL");
                csvWriter.append(",");
                csvWriter.append(String.format("%.4f", microAvgnDCG));
                csvWriter.append("\n");
                csvWriter.close();

                stats.put("TOTAL", microAvgnDCG);
            } catch (Exception e) {
                throw new TasksRunnerException(e.getMessage());
            }
            recordRequestLevelMissingDocs();

            return stats;
        }

        public void recordRequestLevelMissingDocs() {
            for (Map.Entry<String, RequestRun> entry : run.requestRuns.entrySet()) {
                RequestRun rr = entry.getValue();
                String requestID = entry.getKey();
                if (rr.missingDocs == null) {
                    rr.missingDocs = new ArrayList<>();
                }
                List<String> hits = getDocids(requestID, 20000);
                for (RelevanceJudgment rj : tasks.getPositiveRelevanceJudgmentObjects(requestID)) {
                    if (!hits.contains(rj.getDocid())) {
                        rr.missingDocs.add(new MissingDoc(requestID, rj.getRelevanceJudgmentAsString(), rj.getDocid(), Document.getDocumentWithMap(rj.getDocid())));
                    }
                }
            }
        }
    }

    public class EvaluatorTaskLevel {
        private Map<String, EvaluationStats> stats = new TreeMap<>();

        private double calculatenRecallAtN(String taskID, List<String> runDocids) {
            Set<String> judgments = tasks.getPositiveTaskLevelRelevanceJudgments(taskID);
            int count = 0;
            for (String j : judgments) {
                if (runDocids.contains(j)) {
                    ++count;
                }
            }
            return (double) count / judgments.size();
        }

        private double calculatenRecallAtN(String taskID, List<String> runDocids, int N) {
            Set<String> judgments = tasks.getPositiveTaskLevelRelevanceJudgments(taskID);
            List<String> subList = runDocids.subList(0,N);
            int count = 0;
            for (String j : judgments) {
                if (subList.contains(j)) {
                    ++count;
                }
            }
            return (double) count / judgments.size();
        }

        private double calculatenPrecisionAtN(String taskID, List<String> runDocids, int N) {
            Set<String> judgments = tasks.getPositiveTaskLevelRelevanceJudgments(taskID);
            List<String> subList = runDocids.subList(0,N);
            int count = 0;
            for (String j : subList) {
                if (judgments.contains(j)) {
                    ++count;
                }
            }
            return (double) count / subList.size();
        }

        private int getMaxRelevantDocRank(String taskID, List<String> runDocids) {
            Set<String> judgments = tasks.getPositiveTaskLevelRelevanceJudgments(taskID);
            int maxRank = 0;
            for (String j : judgments) {
                if (runDocids.contains(j)) {
                    int index = runDocids.indexOf(j);
                    if (index > maxRank) {
                        maxRank = index;
                    }
                }
            }
            if (maxRank == 0) {
                maxRank = runDocids.size();
            }
            return maxRank;
        }

        /**
         * Evaluates the query formulations and outputs a CSV file of evaluation results.
         * This version only calculates recallAtN.
         * <p>
         * For averaging the evaluation results, we use the MICRO approach.
         *
         * @return the Map of task ID to the stats for that task
         */
        public Map<String,EvaluationStats> evaluate() {
            try {
                /* Create and open the output CSV file */
                FileWriter csvWriter = new FileWriter(evaluationTaskLevelFileName);
                /* Write the header line */
                csvWriter.append("Solution");
                csvWriter.append(",");
                csvWriter.append("Request");
                csvWriter.append(",");
                csvWriter.append("recall@N");
                csvWriter.append(",");
                csvWriter.append("N");
                csvWriter.append(",");
                csvWriter.append("Max Relevant Doc Rank");
                csvWriter.append(",");
                csvWriter.append("recall@100");
                csvWriter.append(",");
                csvWriter.append("precision@100");
                csvWriter.append("\n");

                List<String> taskIDs = tasks.getTaskIDs();
                ListIterator<String> taskIDIterator = taskIDs.listIterator();

                int totalTasks = 0;
                double totalScore = 0.0;
                double totalRecallAt100 = 0.0;
                double totalPrecisionAt100 = 0.0;
                int totalN = 0;
                int maxMaxRelevantDocRank = 0;
                while (taskIDIterator.hasNext()) {
                    String taskID = taskIDIterator.next();
                    List<String> runDocids = getDocids(taskID, MAX_DOCIDS);
                    /* If this solution did not provide a query for this task, skip it */
                    if (runDocids.size() == 0) {
                        continue;
                    }
                    /* If we have no relevance judgments for this task, skip it */
                    if (tasks.getPositiveTaskLevelRelevanceJudgments(taskID).size() == 0) {
                        continue;
                    }

                    ++totalTasks;

                    double score = calculatenRecallAtN(taskID, runDocids);
                    int maxRelevantDocRank  = getMaxRelevantDocRank(taskID, runDocids);
                    double recallAt100 = calculatenRecallAtN(taskID, runDocids, 100);
                    double precisionAt100 = calculatenPrecisionAtN(taskID, runDocids, 100);

                    totalScore += score;
                    totalN += runDocids.size();
                    maxMaxRelevantDocRank = Integer.max(maxMaxRelevantDocRank, maxRelevantDocRank);
                    totalRecallAt100 += recallAt100;
                    totalPrecisionAt100 += precisionAt100;

                    csvWriter.append(key);
                    csvWriter.append(",");
                    csvWriter.append(taskID);
                    csvWriter.append(",");
                    csvWriter.append(String.format("%.4f", score));
                    csvWriter.append(",");
                    csvWriter.append(String.format("%d", runDocids.size()));
                    csvWriter.append(",");
                    csvWriter.append(String.format("%d", maxRelevantDocRank ));
                    csvWriter.append(",");
                    csvWriter.append(String.format("%.4f", recallAt100 ));
                    csvWriter.append(",");
                    csvWriter.append(String.format("%.4f", precisionAt100 ));
                    csvWriter.append("\n");

                    stats.put(taskID,new EvaluationStats(taskID, runDocids.size(), score, maxRelevantDocRank,
                            recallAt100, precisionAt100));

                }

                if (totalTasks > 0) {
                    double microAvg = totalScore / totalTasks;
                    int averageN = totalN / totalTasks;
                    double microAvgRecallAt100 = totalRecallAt100 / totalTasks;
                    double microAvgPrecisionAt100 = totalPrecisionAt100 / totalTasks;

                    csvWriter.append(key);
                    csvWriter.append(",");
                    csvWriter.append("TOTAL");
                    csvWriter.append(",");
                    csvWriter.append(String.format("%.4f", microAvg));
                    csvWriter.append(",");
                    csvWriter.append(String.format("%d", averageN));
                    csvWriter.append(",");
                    csvWriter.append(String.format("%d", maxMaxRelevantDocRank ));
                    csvWriter.append(",");
                    csvWriter.append(String.format("%.4f", microAvgRecallAt100));
                    csvWriter.append(",");
                    csvWriter.append(String.format("%.4f", microAvgPrecisionAt100));
                    csvWriter.append("\n");
                    csvWriter.close();

                    stats.put("TOTAL", new EvaluationStats("TOTAL", averageN, microAvg, maxMaxRelevantDocRank,
                            microAvgRecallAt100, microAvgPrecisionAt100));

                }
                recordMissingDocs();

            } catch (Exception e) {
                throw new TasksRunnerException(e.getMessage());
            }
            return stats;
        }

        public void recordMissingDocs() {
            for (Map.Entry<String, RequestRun> entry : run.requestRuns.entrySet()) {
                RequestRun rr = entry.getValue();
                String taskid = entry.getKey();
                if (rr.missingDocs == null) {
                    rr.missingDocs = new ArrayList<>();
                }
                List<String> hits = getDocids(taskid, 20000);
                for (RelevanceJudgment rj : tasks.getPositiveTaskLevelRelevanceJudgmentObjects(taskid)) {
                    if (!hits.contains(rj.docid)) {
                        rr.missingDocs.add(new MissingDoc(taskid, rj.getRelevanceJudgmentAsString(), rj.docid, Document.getDocumentWithMap(rj.docid)));
                    }
                }
            }
        }
    }

    /**
     * Writes the "entries" part of the event file for this request to the PrintWriter.
     * @param requestID the request ID
     * @param writer the PrintWriter
     */
    private void addEvents(String requestID, PrintWriter writer) {
        String fileName = Pathnames.eventExtractorFileLocation + Pathnames.mode + "."
            + requestID + ".REQUESTHITS.json.results.json";
        File f = new File(fileName);
        if (f.exists()) {
            logger.info("Reading top hits event file " + fileName);
            int lineCounter = 0;
            try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
                String line;
                while ((line = br.readLine()) != null) {
                    ++lineCounter;
                    if (lineCounter <= 2) {  /* don't output the 1st two lines */
                        continue;
                    }
                    if (line.equals("}")) {
                        break; /* don't output the last line */
                    }
                    writer.println(line);
                }
            } catch (IOException e) {
                throw new TasksRunnerException(e);
            }
        }
    }

    public void writeFinalResultsFile() {
        Map<String, Map<String, List<Hit>>> tasks = new HashMap<>();
        String fileName = rerankedRunFile;
        File f = new File(fileName);
        if (f.exists()) {
            logger.info("Reading reranked run file " + fileName);
            try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] tokens = line.split("[ \t]+");
                    if (tokens.length != 6) {
                        throw new TasksRunnerException("Bad runfile line: " + line);
                    }
                    String requestID = tokens[0];
                    String docid = tokens[2];
                    String score = tokens[4];
                    int x = requestID.lastIndexOf('-');
                    String taskID = requestID.substring(0, x);
                    Hit hit = new Hit(docid, score);
                    if (tasks.containsKey(taskID)) {
                        Map<String, List<Hit>> requests = tasks.get(taskID);
                        if (requests.containsKey(requestID)) {
                            List<Hit> hitlist = requests.get(requestID);
                            if (hitlist.size() < Pathnames.RESULTS_CAP) {
                                hitlist.add(hit);
                            }
                        } else {
                            List<Hit> requestHits = new ArrayList<>();
                            requestHits.add(hit);
                            requests.put(requestID, requestHits);
                        }
                    } else {
                        Map<String, List<Hit>> requests = new HashMap<>();
                        List<Hit> requestHits = new ArrayList<>();
                        requestHits.add(hit);
                        requests.put(requestID, requestHits);
                        tasks.put(taskID, requests);
                    }
                }
            } catch (IOException e) {
                throw new TasksRunnerException(e);
            }
            /* Now write the info to the output file */
            try {
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                        new FileOutputStream(Pathnames.appFileLocation + "results.json")));

                JSONObject topLevel = new JSONObject();
                topLevel.put("format-type", "ir-results");
                topLevel.put("format-version", "v1");
                topLevel.put("corpus-id", "release-foo");

                writer.println("{");
                writer.println("\"format-type\": \"ir-results\",");
                writer.println("\"format-version\": \"v1\",");
                writer.println("\"corpus-id\": \"release-1\",");
                writer.println("\"search-results\": {" );
                /* search-results dict: */
                JSONObject searchResults = new JSONObject();
                int numTasks = tasks.size();
                int currentTaskIdx = 0;
                for (String taskID : tasks.keySet()) {
                    ++currentTaskIdx;
                    writer.println("  \"" + taskID + "\": {");
                    writer.println("    \"task\": \"" + taskID + "\",");
                    writer.println("    \"requests\": {");

                    JSONObject searchResult = new JSONObject();
                    Map<String, List<Hit>> requests = tasks.get(taskID);
                    JSONObject jsonRequests = new JSONObject();
                    int numRequests = requests.size();
                    int currentRequestIdx = 0;
                    for (String requestID : requests.keySet()) {
                        ++currentRequestIdx;
                        writer.println("      \"" + requestID + "\": {");
                        writer.println("        \"request\": \"" + requestID + "\",");
                        writer.println("        \"ranking\": [");

                        List<Hit> hits = requests.get(requestID);
                        JSONObject jsonRequest = new JSONObject();
                        JSONArray ranking = new JSONArray();
                        int numHits = hits.size();
                        int currentHitIdx = 0;
                        for (Hit hit : hits) {
                            ++currentHitIdx;
                            JSONObject jsonHit = new JSONObject();
                            String hitLine = "          { \"docid\": \"" + hit.docid + "\", \"score\": " + hit.score + " }";
                            if (currentHitIdx < numHits) {
                                hitLine += ",";
                            }
                            writer.println(hitLine);
                            jsonHit.put("docid", hit.docid);
                            jsonHit.put("score", hit.score);
                            ranking.add(jsonHit);
                        }
                        // end of ranking - time for the events

                        if (Pathnames.includeEventsInFinalResults) {
                            writer.println("        ],");
                            addEvents(requestID, writer);
                        } else {
                            writer.println("        ]");  // no comma
                        }

                        if (currentRequestIdx < numRequests) {
                            writer.println("      },");  // end of this request
                        } else {
                            writer.println("      }");  // end of this request
                        }
                        jsonRequest.put("ranking", ranking);
                        jsonRequest.put("request", requestID);
                        jsonRequests.put(requestID, jsonRequest);
                    }
                    writer.println("    }");  // end of requests
                    if (currentTaskIdx < numTasks) {
                        writer.println("  },");  // end of this task
                    } else {
                        writer.println("  }");  // end of this task
                    }
                    searchResult.put("requests", jsonRequests);
                    searchResult.put("task", taskID);
                    searchResults.put(taskID, searchResult);
                }
                writer.println("}");  // end of search-results
                writer.println("}");  // end of file
                topLevel.put("search-results", searchResults);

                // writer.write(topLevel.toJSONString());
                writer.close();
            } catch (Exception cause) {
                throw new TasksRunnerException(cause);
            }
        }
        else {
            throw new TasksRunnerException("Run file " + runFileName + " does not exist");
        }
    }
}
