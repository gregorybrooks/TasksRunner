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

    QueryFormulatorJava(String submissionId, String mode, AnalyticTasks tasks, String phase,
                        String queryFileNameKey) {
        super(submissionId, mode, tasks, phase, queryFileNameKey);
    }

    String javaJarFile = "BetterQueryBuilderTaskNounPhrases-2.0.0.jar";

    private void callJavaProgram(String queryFileName, String outLang, String phase) {
        String programName = "java -jar ";
        String javaProgramName = Pathnames.programFileLocation + javaJarFile;
        String logFile = Pathnames.logFileLocation + submissionId + ".java-program.log";
        String inputFile = Pathnames.eventExtractorFileLocation + submissionId + ".analytic_tasks.json";
        String tempCommand = programName + " " + javaProgramName + " " + inputFile + " " + mode + " "
                + Pathnames.queryFileLocation + queryFileName + " " + outLang + " " + Pathnames.programFileLocation + " "
                + phase;

        Command.execute(tempCommand, logFile);
    }

    /**
     * Constructs the queries from the Tasks, writes the Galago-ready query file
     *
     **/
    public void buildQueries() {
        String language;

        if (Pathnames.runGetCandidateDocs || Pathnames.targetLanguageIsEnglish) {
            language = "en";
        } else {
            language = "ar";
        }

        callJavaProgram(queryFileNameKey + ".queries.json", language, phase);
    }
}
