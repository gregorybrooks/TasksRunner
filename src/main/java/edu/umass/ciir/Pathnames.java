package edu.umass.ciir;

import java.util.Map;

/**
 * Pathnames of the files used by the program, and settings such as 'mode' and 'runIRPhase2'.
 * The default values are set here, but when the program starts if there is an environment var
 * with the same name, its value will override the default.
 * So you should source a file that exports all of these that you don't want the default for
 * as environment variables before calling the program,
 * and if running in a Docker container, pass that file as the --env-file parameter to docker run.
 */
public class Pathnames {
    private static Map<String, String> env = System.getenv();

    public static boolean doTaskLevelEvaluation = true;
    public static boolean doRequestLevelEvaluation = true;
    public static boolean runPreprocess = false;
    public static boolean runIndexBuild = false;
    public static boolean runIRPhase1 = false;
    public static boolean runIRPhase2 = true;
    public static boolean runIRPhase3 = false;

    public static String scratchFileLocation = "/mnt/scratch/BETTER_DRY_RUN/scratch/clear_ir/";
    public static String corpusFileLocation = "/mnt/scratch/BETTER_DRY_RUN/corpus/";
    public static String appFileLocation = "/mnt/scratch/BETTER_DRY_RUN/app/";
    public static String requestLevelQueryFormulatorDockerImage = "";
    public static String taskLevelQueryFormulatorDockerImage = "";

    public static String tempFileLocation = scratchFileLocation + "tmp/";
    public static String runFileLocation = scratchFileLocation + "runfiles/";
    public static String qrelFileLocation = scratchFileLocation + "qrelfiles/";
    public static String evaluationFileLocation = scratchFileLocation + "evaluationfiles/";
    public static String queryFileLocation = scratchFileLocation + "queryfiles/";
    public static String logFileLocation = scratchFileLocation + "logfiles/";
    public static String indexLocation = scratchFileLocation + "indexes/";
    public static String englishIndexLocation = indexLocation + "BETTER-DryRun-v3";
    public static String arabicIndexLocation = indexLocation + "BETTER-DryRun-v3";
    public static String galagoLocation = scratchFileLocation + "galago/bin/";
    public static String programFileLocation = scratchFileLocation + "programfiles/";
    public static String eventExtractorFileLocation = scratchFileLocation + "eventextractorfiles/";
    public static String translationTableLocation = programFileLocation + "translation_tables/";
    public static String taskCorpusFileLocation = scratchFileLocation + "taskcorpusfiles/";
    public static String galagoJobDirLocation = scratchFileLocation + "galago_job_dir/";
    public static String arabicCorpusFileName = "english/BETTER-English-IR-data.v1.jl";
    public static String englishCorpusFileName = "english/BETTER-English-IR-data.v1.jl";
    public static String tasksFileName = "dry-run-topics.auto.json";
    public static String qrelFileName = "req-qrels";
    public static String isTargetEnglish = "true";
    public static boolean targetLanguageIsEnglish = true;
    public static String supplementalFileName = "supplemental_info.json";
    public static boolean readQrelFile = true;
    public static boolean expandQrelDocuments = true;
    public static String mode = "";

// english training as english and arabic
/*
    public static String scratchLocation = "/home/glbrooks/BETTER/";
    public static String arabicCorpusFileName = "english-training-corpus.jl";
    public static String englishCorpusFileName = "english-training-corpus.jl";
    public static String tasksFileName = "ir-hitl-performer-tasks.fixed.json";
    public static String englishIndexLocation = "/home/glbrooks/BETTER/indexes/BETTER-IR-English-Training-v1";
    public static String arabicIndexLocation = "/home/glbrooks/BETTER/indexes/BETTER-IR-English-Training-v1";
    public static String isTargetEnglish = "true";
    public static boolean targetLanguageIsEnglish = true;
    public static String supplementalFileName = "supplemental_info.json";
    public static boolean readQrelFile = false;
    public static boolean expandQrelDocuments = false;
    public static String qrelFileName = "";
    public static String mode = "";
    public static boolean doTaskLevelEvaluation = false;
    public static boolean doRequestLevelEvaluation = false;
    public static boolean runPreprocess = false;
    public static boolean runIndexBuild = false;
    public static boolean runIRPhase1 = false;
    public static boolean runIRPhase2 = false;
    public static boolean runIRPhase3 = false;
*/

    private static String getFromEnv(String key, String default_value) {
        if (env.containsKey(key)) {
            return env.get(key);
        } else {
            return default_value;
        }
    }

    static {
        runPreprocess = (getFromEnv("runPreprocess", "false").equals("true"));
        runIndexBuild = (getFromEnv("runIndexBuild", "false").equals("true"));
        runIRPhase1 = (getFromEnv("runIRPhase1", "false").equals("true"));
        runIRPhase2 = (getFromEnv("runIRPhase2", "true").equals("true"));
        runIRPhase3 = (getFromEnv("runIRPhase3", "false").equals("true"));

        scratchFileLocation = getFromEnv("scratchFileLocation", scratchFileLocation);
        corpusFileLocation = getFromEnv("dataFileLocation", corpusFileLocation);
        appFileLocation = getFromEnv("taskFileLocation", appFileLocation);
        requestLevelQueryFormulatorDockerImage = getFromEnv("requestLevelQueryFormulatorDockerImage",
                requestLevelQueryFormulatorDockerImage);
        taskLevelQueryFormulatorDockerImage = getFromEnv("taskLevelQueryFormulatorDockerImage",
                taskLevelQueryFormulatorDockerImage);
        queryFileLocation = getFromEnv("queryFileLocation", queryFileLocation);
        logFileLocation = getFromEnv("logFileLocation", logFileLocation);
        indexLocation = getFromEnv("indexLocation", indexLocation);
        englishIndexLocation = getFromEnv("englishIndexLocation", englishIndexLocation);
        arabicIndexLocation = getFromEnv("arabicIndexLocation", arabicIndexLocation);
        galagoLocation = getFromEnv("galagoLocation", galagoLocation);
        programFileLocation = getFromEnv("programFileLocation", programFileLocation);
        eventExtractorFileLocation = getFromEnv("eventExtractorFileLocation", eventExtractorFileLocation);
        translationTableLocation = getFromEnv("translationTableLocation", translationTableLocation);
        taskCorpusFileLocation = getFromEnv("taskCorpusFileLocation", taskCorpusFileLocation);
        galagoJobDirLocation = getFromEnv("galagoJobDirLocation", galagoJobDirLocation);
        arabicCorpusFileName = getFromEnv("arabicCorpusFileName", arabicCorpusFileName);
        englishCorpusFileName = getFromEnv("englishCorpusFileName", englishCorpusFileName);
        tasksFileName = getFromEnv("tasksFileName", tasksFileName);
        supplementalFileName = getFromEnv("supplementalFileName", supplementalFileName);
        qrelFileName = getFromEnv("qrelFileName", qrelFileName);
        readQrelFile = (getFromEnv("readQrelFile", "true").equals("true"));
        expandQrelDocuments = (getFromEnv("expandQrelDocuments", "true").equals("true"));
        mode = getFromEnv("mode", mode);
        doRequestLevelEvaluation = (getFromEnv("doRequestLevelEvaluation", "true").equals("true"));
        doTaskLevelEvaluation = (getFromEnv("doTaskLevelEvaluation", "true").equals("true"));
        isTargetEnglish = getFromEnv("isTargetEnglish", isTargetEnglish);
        targetLanguageIsEnglish = (isTargetEnglish.equals("true"));
    }
}
