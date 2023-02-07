package edu.umass.ciir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class QueryFormulatorPython extends QueryFormulator {

    private static final Logger logger = Logger.getLogger("TasksRunner");

    QueryFormulatorPython(String submissionId, String mode, AnalyticTasks tasks, String phase, Pathnames.ProcessingModel processingModel,
                          String queryFileNameKey) {
        super(submissionId, mode, tasks, phase, queryFileNameKey);
    }

    String pythonProgramName = "run_multipartiterank_qformulator.py";

    private void callPythonProgram(String queryFileName, String outLang, String phase) {
        String programName = "python3";
        String pythonProgramNameFullPath = Pathnames.programFileLocation + pythonProgramName;
        String logFile = Pathnames.logFileLocation + submissionId + ".python-program.log";
        String inputFile = Pathnames.eventExtractorFileLocation + submissionId + ".analytic_tasks.json";

        String tempCommand = programName + " " + pythonProgramNameFullPath
                + " --input_file=" + inputFile
                + " --output_file=" + Pathnames.queryFileLocation + queryFileName
                + " --out_lang=" + outLang
                + " --program_directory=" + Pathnames.programFileLocation
                + " --mode=" + mode
                + " --phase=" + phase;
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

        callPythonProgram(queryFileNameKey + ".queries.json", language, phase);
    }
}
