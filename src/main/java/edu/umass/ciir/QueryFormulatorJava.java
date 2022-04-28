package edu.umass.ciir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class QueryFormulatorJava extends QueryFormulator {

    private static final Logger logger = Logger.getLogger("TasksRunner");

    QueryFormulatorJava(AnalyticTasks tasks) {
        super(tasks);
    }

    String javaJarFile = "TaskQueryBuilder1-1.0.0.jar";

    private void callJavaProgram(String queryFileName, String outLang, String phase) {
        try {
            String programName = "java -jar ";
            String javaProgramName = Pathnames.programFileLocation + javaJarFile;
            String logFile = Pathnames.logFileLocation + "java-program.log";
            String inputFile = Pathnames.eventExtractorFileLocation + mode + ".analytic_tasks.json";
            String tempCommand = programName + " " + javaProgramName + " " + inputFile + " " + mode + " "
                    + Pathnames.queryFileLocation + queryFileName + " " + outLang + " " + Pathnames.programFileLocation + " "
                    + phase + " >& " + logFile;

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
                logger.log(Level.SEVERE, "Exception doing java program execution", cause);
                throw new TasksRunnerException(cause);
            } finally {
                StringBuilder builder = new StringBuilder();
                try (Stream<String> stream = Files.lines( Paths.get(logFile), StandardCharsets.UTF_8))
                {
                    stream.forEach(s -> builder.append(s).append("\n"));
                    logger.info("java program output log:\n" + builder.toString());
                } catch (IOException ignore) {
                    // logger.info("IO error trying to read output file. Ignoring it");
                }
            }
            if (exitVal != 0) {
                logger.log(Level.SEVERE, "Unexpected ERROR from java program, exit value is: " + exitVal);
                throw new TasksRunnerException("Unexpected ERROR from java program, exit value is: " + exitVal);
            }

        } catch (Exception e) {
            throw new TasksRunnerException(e);
        }
    }

    /**
     * Constructs the queries from the Tasks, writes the Galago-ready query file
     *
     **/
    public void buildQueries(String phase, Pathnames.ProcessingModel processingModel, String queryFileName) {
        String language;

        if (Pathnames.targetLanguageIsEnglish) {
            language = "en";
        } else {
            language = "ar";
        }

        callJavaProgram(queryFileName, language, phase);
    }
}
