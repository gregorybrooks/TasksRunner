package edu.umass.ciir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.stream.Stream;

public class QueryFormulatorArabic1 extends NewQueryFormulator {

    QueryFormulatorArabic1(AnalyticTasks tasks) {
        super(tasks);
    }

    public void buildQueries(String queryFileName) {
        buildAndWriteQueryFile_MultiPartite(queryFileName + ".NON_TRANSLATED", "en");
        if (Pathnames.targetLanguageIsEnglish) {
            buildAndWriteQueryFile_MultiPartite(queryFileName, "en");
        } else {
            buildAndWriteQueryFile_MultiPartite(queryFileName, "ar");
        }
    }

    public void buildAndWriteQueryFile_MultiPartite(String queryFileName, String outLang) {
        try {
            String programName = "python3";
            String pythonProgramName = Pathnames.programFileLocation + "run_multipartiterank_qformulator.py";
            String logFile = Pathnames.logFileLocation + "multipartiterank_qformulator.log";
            String inputFile = Pathnames.eventExtractorFileLocation + tasks.getMode() + ".analytic_tasks.json";
            String tt_path = Pathnames.translationTableLocation + "unidirectional-with-null-en-ar.simple-tok.txt";
            String muse_en_path = Pathnames.programFileLocation + "translation_package/muse/wiki.multi.en.vec";
            String muse_ar_path = Pathnames.programFileLocation + "translation_package/muse/wiki.multi.ar.vec";
            String tempCommand = programName + " " + pythonProgramName + " --input_file=" + inputFile + " --output_file="
                    + queryFileName + " --out_lang=" + outLang + " --tt_path=" + tt_path + " --muse_en_path="
                    + muse_en_path + " --muse_ar_path=" + muse_ar_path + " >& " + logFile;

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
                logger.log(Level.SEVERE, "Exception doing multipartiterank_qformulator execution", cause);
                throw new TasksRunnerException(cause);
            } finally {
                StringBuilder builder = new StringBuilder();
                try (Stream<String> stream = Files.lines( Paths.get(logFile), StandardCharsets.UTF_8))
                {
                    stream.forEach(s -> builder.append(s).append("\n"));
                    logger.info("multipartiterank_qformulator output log:\n" + builder.toString());
                } catch (IOException ignore) {
                    // logger.info("IO error trying to read Galago output file. Ignoring it");
                }
            }
            if (exitVal != 0) {
                logger.log(Level.SEVERE, "Unexpected ERROR from multipartiterank_qformulator, exit value is: " + exitVal);
                throw new TasksRunnerException("Unexpected ERROR from multipartiterank_qformulator, exit value is: " + exitVal);
            }

        } catch (Exception e) {
            throw new TasksRunnerException(e);
        }
    }
}