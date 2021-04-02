package edu.umass.ciir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.stream.Stream;

public class QueryFormulatorDockerTaskLevel extends NewQueryFormulator {

    QueryFormulatorDockerTaskLevel(AnalyticTasks tasks) {
        super(tasks);
    }

    String dockerImageName = "task-query-builder1:1.0.0";

    public void callDockerImage(String queryFileName, String outLang, String phase) {
        try {
            String analyticTasksInfoFilename = mode + ".analytic_tasks.json";
            String command = "sudo docker run --rm"
                    + " -e MODE=" + mode
                    + " -e OUT_LANG=" + outLang
                    + " -e PHASE=" + phase
                    + " -e INPUTFILE=" + analyticTasksInfoFilename
                    + " -e QUERYFILE=" + queryFileName
                    + " -v /var/run/docker.sock:/var/run/docker.sock"
                    + " -v " + Pathnames.scratchLocation + ":/scratch"
                    + " -v " + Pathnames.queryFileLocation + ":/queryfiles"
                    // the analytic_tasks.json file is considered a task file to the outside world,
                    // but actually lives in the event extractor directory and is the "input file"
                    + " -v " + Pathnames.eventExtractorFileLocation + ":/taskfiles"
                    + " " + dockerImageName
                    + " sh -c ./runit_DOCKER.sh";
            String logFile = Pathnames.logFileLocation + "docker-program.log";
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

        } catch (Exception e) {
            throw new TasksRunnerException(e);
        }
    }

    /**
     * Constructs the queries from the Tasks, writes the Galago-ready query file
     *
     **/
    public void buildQueries(String phase, String queryFileName) {
        String language;

        if (Pathnames.targetLanguageIsEnglish) {
            language = "en";
        } else {
            language = "ar";
        }

        callDockerImage(queryFileName, language, phase);
    }
}
