package edu.umass.ciir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class QueryFormulatorDocker extends QueryFormulator {

    private static final Logger logger = Logger.getLogger("TasksRunner");

    QueryFormulatorDocker(AnalyticTasks tasks) {
        super(tasks);
    }

    private void callDockerImage(String queryFileName, String outLang, String phase, String dockerImageName) {
        try {
            String analyticTasksInfoFilename = mode + ".analytic_tasks.json";
            String command = "sudo docker run --rm"
                    + " --env MODE=" + mode
                    + " --env OUT_LANG=" + outLang
                    + " --env PHASE=" + phase
                    + " --env INPUTFILE=" + analyticTasksInfoFilename
                    + " --env QUERYFILE=" + queryFileName
                    /* For each directory that we want to share between this parent docker container (TasksRunner)
                     and the child docker container (TaskQueryBuilder1 e.g.), we pass the pathname
                     in an environment variable, and we make that path a bind-volume so the child container
                     can actually access it.
                     */
/*
                    + " --env eventExtractorFileLocation=$eventExtractorFileLocation"
                    + " --env queryFileLocation=$queryFileLocation"
                    + " -v $eventExtractorFileLocation:$eventExtractorFileLocation"
                    + " -v $queryFileLocation:$queryFileLocation"
*/
                    + " --env eventExtractorFileLocation=" + Pathnames.eventExtractorFileLocation
                    + " --env queryFileLocation=" + Pathnames.queryFileLocation
                    + " -v " + Pathnames.eventExtractorFileLocation + ":" + Pathnames.eventExtractorFileLocation
                    + " -v " + Pathnames.queryFileLocation + ":" + Pathnames.queryFileLocation

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

        String dockerImageName;
        if (phase.equals("Request")) {
            dockerImageName = Pathnames.requestLevelQueryFormulatorDockerImage;
        } else {
            dockerImageName = Pathnames.taskLevelQueryFormulatorDockerImage;
        }

        callDockerImage(queryFileName, language, phase, dockerImageName);
    }
}
