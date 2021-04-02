package edu.umass.ciir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.stream.Stream;

public class QueryFormulatorPythonRequestLevel extends NewQueryFormulator {

    QueryFormulatorPythonRequestLevel(AnalyticTasks tasks) {
        super(tasks);
    }
    String pythonProgramName = "run_multipartiterank_qformulator.py";

    public void callPythonProgram(String queryFileName, String outLang, String phase) {
        try {
            String programName = "python3";
            String pythonProgramNameFullPath = Pathnames.programFileLocation + pythonProgramName;
            String logFile = Pathnames.logFileLocation + "python-program.log";
            String inputFile = Pathnames.eventExtractorFileLocation + mode + ".analytic_tasks.json";

            String tempCommand = programName + " " + pythonProgramNameFullPath
                    + " --input_file=" + inputFile
                    + " --output_file=" + Pathnames.queryFileLocation + queryFileName
                    + " --out_lang=" + outLang
                    + " --program_directory=" + Pathnames.programFileLocation
                    + " --mode=" + mode
                    + " --phase=" + phase
                    + " >& " + logFile;

            try {
                Files.delete(Paths.get(logFile));
            } catch (IOException ignore) {
                // do nothing
            }

            logger.info("Executing this command: " + tempCommand);

            int exitVal = 0;
            try {
                ProcessBuilder processBuilder = new ProcessBuilder();
                processBuilder.command("bash", "-c", tempCommand);
                Process process = processBuilder.start();

                exitVal = process.waitFor();
            } catch (Exception cause) {
                logger.log(Level.SEVERE, "Exception doing multipartiterank_qformulator execution", cause);
                throw new TasksRunnerException(cause);
            } finally {
                StringBuilder builder = new StringBuilder();
                try (Stream<String> stream = Files.lines( Paths.get(logFile), StandardCharsets.UTF_8))
                {
                    stream.forEach(s -> builder.append(s).append("\n"));
                    logger.info("python program output log:\n" + builder.toString());
                } catch (IOException ignore) {
                    // logger.info("IO error trying to read output file. Ignoring it");
                }
            }
            if (exitVal != 0) {
                logger.log(Level.SEVERE, "Unexpected ERROR from Python query formulator " + pythonProgramName
                        + ", exit value is: " + exitVal);
                throw new TasksRunnerException("Unexpected ERROR from query formulator, exit value is: " + exitVal);
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

        callPythonProgram(queryFileName, language, phase);
    }
}
