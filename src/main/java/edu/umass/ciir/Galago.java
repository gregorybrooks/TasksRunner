package edu.umass.ciir;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.lemurproject.galago.core.parse.Document;
import org.lemurproject.galago.core.retrieval.Results;
import org.lemurproject.galago.core.retrieval.Retrieval;
import org.lemurproject.galago.core.retrieval.RetrievalFactory;
import org.lemurproject.galago.core.retrieval.ScoredDocument;
import org.lemurproject.galago.core.retrieval.query.Node;
import org.lemurproject.galago.core.retrieval.query.StructuredQuery;
import org.lemurproject.galago.utility.Parameters;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class Galago {
    private static final Logger logger = Logger.getLogger("TasksRunner");
    private String indexLocation;
    private String mode;
    private String logFileLocation;
    private String galagoLocation;

    Galago(String indexLocation, String mode, String logFileLocation, String galagoLocation) {
        this.indexLocation = indexLocation;
        this.mode = mode;
        this.galagoLocation = galagoLocation;
        this.logFileLocation = logFileLocation;
    }

    public void search(Map<String, String> queries, String runFileName, int N) {
        try {
            logger.info("Start Galago search");
            PrintWriter writer = new PrintWriter(runFileName);

            Parameters queryParams = Parameters.create();
            queryParams.set ("index", indexLocation);
            queryParams.set ("requested", N);
            if (Pathnames.targetLanguage.equals("ARABIC")) {
                queryParams.set("defaultTextPart", "postings.snowball");
            }
            Retrieval ret = RetrievalFactory.create(queryParams);

            for (Map.Entry entry : queries.entrySet()) {
                String qid = (String) entry.getKey();
                String qText = (String) entry.getValue();
                Node q = StructuredQuery.parse(qText);
                Node transq = ret.transformQuery(q, Parameters.create());
                Results results = ret.executeQuery(transq, queryParams);
                if (results.scoredDocuments.isEmpty()) {
                    logger.info("Search failed. Nothing retrieved.");
                } else {
                    for (ScoredDocument sd : results.scoredDocuments) {
                        int rank = sd.rank;
                        double score = sd.score;
                        String eid = ret.getDocumentName(sd.document); // ID in the text
                        writer.println(qid + " Q0 " + eid + " " + rank + " " + score + " CLEAR");
                    }
                }
            }
            writer.close();
        }
        catch (Exception ex) {
            throw new TasksRunnerException(ex);
        }
        logger.info("End Galago search");
    }

    public long getCollectionDocumentCount() {
        long termDocCounts = 0;
        String command = "galago dump-index-manifest " + indexLocation + "/corpus ";
        String galagoOutFile = logFileLocation + "/galago_dump-index-manifest.out";
        String tempCommand = galagoLocation + command
                + " > " + galagoOutFile;

        logger.info("Executing this command: " + tempCommand);

        try {
            Files.delete(Paths.get(galagoOutFile));
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
            try {
                Reader reader = new BufferedReader(new InputStreamReader(
                        new FileInputStream(galagoOutFile)));
                JSONParser parser = new JSONParser();
                JSONObject top = (JSONObject) parser.parse(reader);
                termDocCounts = (long) top.get("keyCount");
            } catch (Exception cause){
                logger.log(Level.SEVERE, "Exception reading JSON output of dump-index-manifest", cause);
                throw new TasksRunnerException(cause);
            }
        }
/*
        if (exitVal != 0) {
            logger.log(Level.SEVERE, "Unexpected ERROR from Galago, exit value is: " + exitVal);
            throw new BetterQueryBuilderException("Unexpected ERROR from Galago, exit value is: " + exitVal);
        }
*/
        return termDocCounts;
    }

    // Number of term occurences in the whole collection
    public long getCollectionTermFrequency() {
        long termFrequency = 0;
        String command = "galago dump-lengths --index=" + indexLocation + " | cut  -f2 | paste -sd+ | bc";
        String galagoOutFile = logFileLocation + "/galago_dump-lengths.out";
        String tempCommand = galagoLocation + command
                + " > " + galagoOutFile;

        logger.info("Executing this command: " + tempCommand);

        try {
            Files.delete(Paths.get(galagoOutFile));
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
            try {
                File f = new File(galagoOutFile);
                if (f.exists()) {
                    BufferedReader qrelReader = new BufferedReader(new InputStreamReader(
                            new FileInputStream(galagoOutFile)));
                    String line = qrelReader.readLine();
                    termFrequency = Long.parseLong(line);
                    qrelReader.close();
                }
            } catch (Exception cause) {
                logger.log(Level.SEVERE, "Exception reading Galago ouput file", cause);
                throw new TasksRunnerException(cause);
            }
        }
        return termFrequency;
    }

    public Map<String, Long> getTermDocCounts() {
        Map<String, Long> termDocCounts = new HashMap<>();
        String command = "galago dump-term-stats " + indexLocation + "/postings ";
        String galagoOutFile = logFileLocation + "/galago_dump-term-stats.out";
        String tempCommand = galagoLocation + command
                + " > " + galagoOutFile;

        logger.info("Executing this command: " + tempCommand);

        try {
            Files.delete(Paths.get(galagoOutFile));
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
            try (Stream<String> stream = Files.lines( Paths.get(galagoOutFile), StandardCharsets.UTF_8))
            {
                stream.forEach(s -> {
                    if (s.trim().length() > 0) {
                        String[] tokens = s.split("\t");
                        if (tokens.length < 3) {
                            throw new TasksRunnerException("Bad line in dump-term-stats output");
                        }
                        String term = tokens[0];
                        int termFrequency = Integer.parseInt(tokens[1]);
                        Long termDocCount = Long.parseLong(tokens[2]);
                        termDocCounts.put(term,termDocCount);
                    }});
            } catch (IOException ignore) {
                // logger.info("IO error trying to read Galago output file. Ignoring it");
            }
        }
/*
        if (exitVal != 0) {
            logger.log(Level.SEVERE, "Unexpected ERROR from Galago, exit value is: " + exitVal);
            throw new BetterQueryBuilderException("Unexpected ERROR from Galago, exit value is: " + exitVal);
        }
*/

        return termDocCounts;
    }

    public Map<String, Long> getTermFrequencies() {
        Map<String, Long> termFrequencies = new HashMap<>();
        String command = "galago dump-term-stats " + indexLocation + "/postings ";
        String galagoOutFile = logFileLocation + "/galago_dump-term-stats.out";
        String tempCommand = galagoLocation + command
                + " > " + galagoOutFile;

        logger.info("Executing this command: " + tempCommand);

        try {
            Files.delete(Paths.get(galagoOutFile));
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
            try (Stream<String> stream = Files.lines( Paths.get(galagoOutFile), StandardCharsets.UTF_8))
            {
                stream.forEach(s -> {
                    if (s.trim().length() > 0) {
                        String[] tokens = s.split("\t");
                        if (tokens.length < 3) {
                            throw new TasksRunnerException("Bad line in dump-term-stats output");
                        }
                        String term = tokens[0];
                        Long termFrequency = Long.parseLong(tokens[1]);
                        termFrequencies.put(term,termFrequency);
                    }});
            } catch (IOException ignore) {
                // logger.info("IO error trying to read Galago output file. Ignoring it");
            }
        }
        return termFrequencies;
    }

    private Map<String, Map<String, Integer>> cache = null;

    public Map<String, Integer> getDocTerms(String docid) {
        if (cache == null) {
            cache = new HashMap<>();
        }
        if (cache.containsKey(docid)) {
            return cache.get(docid);
        }
        Map<String, Integer> terms = new HashMap<>();

        String command = "galago dump-doc-terms --index=" + indexLocation + "/postings --eidList="
                + docid ;
        String galagoOutFile = logFileLocation + "/galago_dump-doc-terms.out";
        String tempCommand = galagoLocation + command
                + " > " + galagoOutFile;

        logger.info("Executing this command: " + tempCommand);

        try {
            Files.delete(Paths.get(galagoOutFile));
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
            try (Stream<String> stream = Files.lines( Paths.get(galagoOutFile), StandardCharsets.UTF_8))
            {
                stream.forEach(s -> {
                    if (s.trim().length() > 0) {   // skip empty last line
                        if (!s.startsWith("Doc: ")) {
                            String[] tokens = s.split(",");
                            if (tokens.length < 3) {
                                throw new TasksRunnerException("Bad line in dump-doc-terms output: " + s);
                            }
                            String term = tokens[0];
                            int termCount = tokens.length - 2;
                            terms.put(term,termCount);
                        }
                    }});
            } catch (IOException ignore) {
                // logger.info("IO error trying to read Galago output file. Ignoring it");
            }
        }
        /*
        if (exitVal != 0) {
            logger.log(Level.SEVERE, "Unexpected ERROR from Galago, exit value is: " + exitVal);
            throw new BetterQueryBuilderException("Unexpected ERROR from Galago, exit value is: " + exitVal);
        }
         */
        cache.put(docid, terms);
        return terms;
    }

    public Map<String, Long> evaluate(String runFileName) {
        /*
        galago eval --judgments=/mnt/scratch/BETTER/BETTER_TEST_ENVIRONMENTS/BETTER_MITRE_EVAL_JAN_2021/scratch/clear_ir/qrelfiles/IR-relevance-assessments.qrels.GALAGO
        --runs+AUTO.Request.gregorybrooks-better-query-builder-ultimate:2.0.0_2500_1_1.out --metrics+ndcg140 --metrics+r100 --metrics+r1000 --metrics+p10 --metrics+p25 --metrics+p50 --metrics+p100 --metrics+p1000 --metrics+map --metrics+num_ret --metrics+num_rel --metrics+num_rel_ret --details=true > eval_2500_1_1.txt
        */
        Map<String, Long> termFrequencies = new HashMap<>();
        String command = "galago eval --judgments=" + Pathnames.qrelFileLocation + Pathnames.qrelFileName + ".GALAGO"
                + " --runs+" + Pathnames.runFileLocation + runFileName + " --metrics+ndcg140 --metrics+r100 --metrics+r1000 --metrics+p10 --metrics+p25 --metrics+p50 --metrics+p100 --metrics+p1000 --metrics+map --metrics+num_ret --metrics+num_rel --metrics+num_rel_ret --details=true";
        String galagoOutFile = logFileLocation + "/galago_eval.out";
        String tempCommand = galagoLocation + command
                + " > " + galagoOutFile;

        logger.info("Executing this command: " + tempCommand);

        try {
            Files.delete(Paths.get(galagoOutFile));
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
            try (Stream<String> stream = Files.lines( Paths.get(galagoOutFile), StandardCharsets.UTF_8))
            {
                stream.forEach(s -> {
                    if (s.trim().length() > 0) {
                        String[] tokens = s.split("\t");
                        if (tokens.length < 3) {
                            throw new TasksRunnerException("Bad line in dump-term-stats output");
                        }
                        String term = tokens[0];
                        Long termFrequency = Long.parseLong(tokens[1]);
                        termFrequencies.put(term,termFrequency);
                    }});
            } catch (IOException ignore) {
                // logger.info("IO error trying to read Galago output file. Ignoring it");
            }
        }
        return termFrequencies;
    }



}
