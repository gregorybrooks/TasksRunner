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
    public static String englishIndexName = "BETTER-DryRun-v3";
    public static String englishIndexLocation = indexLocation + "BETTER-DryRun-v3";
    public static String arabicIndexName = "BETTER-DryRun-v3";
    public static String arabicIndexLocation = indexLocation + arabicIndexName;
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

    public enum Required {
        REQUIRED,
        OPTIONAL
    }

    private static String getFromEnv(String key, String default_value) {
        return getFromEnv(key, default_value, Required.OPTIONAL);
    }

    private static String getFromEnv(String key, String default_value, Required required) {
        if (env.containsKey(key)) {
            return env.get(key);
        } else {
            if (required == Required.REQUIRED) {
                throw new TasksRunnerException(default_value);
            } else {
                return default_value;
            }
        }
    }

    private static String ensureTrailingSlash(String s) {
        if (!s.endsWith("/")) {
            return s + "/";
        } else {
            return s;
        }
    }

    static {
        runPreprocess = (getFromEnv("runPreprocess", "false").equals("true"));
        runIndexBuild = (getFromEnv("runIndexBuild", "false").equals("true"));
        runIRPhase1 = (getFromEnv("runIRPhase1", "false").equals("true"));
        runIRPhase2 = (getFromEnv("runIRPhase2", "true").equals("true"));
        runIRPhase3 = (getFromEnv("runIRPhase3", "false").equals("true"));

        scratchFileLocation = ensureTrailingSlash(getFromEnv("scratchFileLocation",
                "MISSING ENV VAR: scratchFileLocation", Required.REQUIRED));
        corpusFileLocation = ensureTrailingSlash(getFromEnv("corpusFileLocation",
                "MISSING ENV VAR: corpusFileLocation", Required.REQUIRED));
        appFileLocation = ensureTrailingSlash(getFromEnv("appFileLocation",
                "MISSING ENV VAR: appFileLocation", Required.REQUIRED));

        requestLevelQueryFormulatorDockerImage = getFromEnv("requestLevelQueryFormulatorDockerImage",
                "MISSING ENV VAR: requestLevelQueryFormulatorDockerImage", Required.REQUIRED);
        // TODO: verify the image is loaded on this system by doing this:
        //     docker image ls <imageName> | wc -l > 1
        //  (if it isn't loaded, the result is only one line, a header line, else you get another
        //   line with the image details)
        taskLevelQueryFormulatorDockerImage = getFromEnv("taskLevelQueryFormulatorDockerImage",
                "MISSING ENV VAR: taskLevelQueryFormulatorDockerImage", Required.REQUIRED);

        queryFileLocation = ensureTrailingSlash(getFromEnv("queryFileLocation",
                scratchFileLocation + "queryfiles/"));
        logFileLocation = ensureTrailingSlash(getFromEnv("logFileLocation",
                scratchFileLocation + "logfiles/"));
        indexLocation = ensureTrailingSlash(getFromEnv("indexLocation",
                scratchFileLocation + "indexes/"));
        arabicIndexName = getFromEnv("arabicIndexName",
                "MISSING ENV VAR: arabicIndexName", Required.REQUIRED);
        arabicIndexLocation = getFromEnv("arabicIndexLocation",
                indexLocation + arabicIndexName);
        // Currently the index on the English training data is not used
        englishIndexName = getFromEnv("englishIndexName", "NOT_USED");
        englishIndexLocation = getFromEnv("englishIndexLocation",
                indexLocation + englishIndexName);

        galagoLocation = ensureTrailingSlash(getFromEnv("galagoLocation",
                scratchFileLocation + "galago/bin/"));
        programFileLocation = ensureTrailingSlash(getFromEnv("programFileLocation",
                scratchFileLocation + "programfiles/"));
        eventExtractorFileLocation = ensureTrailingSlash(getFromEnv("eventExtractorFileLocation",
                scratchFileLocation + "eventextractorfiles/"));
        translationTableLocation = ensureTrailingSlash(getFromEnv("translationTableLocation",
                programFileLocation + "translation_tables/"));
        taskCorpusFileLocation = ensureTrailingSlash(getFromEnv("taskCorpusFileLocation",
                taskCorpusFileLocation));
        galagoJobDirLocation = ensureTrailingSlash(getFromEnv("galagoJobDirLocation",
                scratchFileLocation + "galago_job_dir/"));

        arabicCorpusFileName = getFromEnv("arabicCorpusFileName",
                "MISSING ENV VAR: arabicCorpusFileName", Required.REQUIRED);
        englishCorpusFileName = getFromEnv("englishCorpusFileName",
                "MISSING ENV VAR: englishCorpusFileName", Required.REQUIRED);
        tasksFileName = getFromEnv("tasksFileName",
                "MISSING ENV VAR: tasksFileName", Required.REQUIRED);
        supplementalFileName = getFromEnv("supplementalFileName", "does_not_exist.json");
        qrelFileName = getFromEnv("qrelFileName", qrelFileName);
        readQrelFile = (getFromEnv("readQrelFile", "true").equals("true"));
        expandQrelDocuments = (getFromEnv("expandQrelDocuments", "true").equals("true"));
        mode = getFromEnv("mode", "MISSING ENV VAR: mode (must be AUTO, AUTO-HITL, or HITL)",
                Required.REQUIRED);
        doRequestLevelEvaluation = (getFromEnv("doRequestLevelEvaluation", "true").equals("true"));
        doTaskLevelEvaluation = (getFromEnv("doTaskLevelEvaluation", "true").equals("true"));
        isTargetEnglish = getFromEnv("isTargetEnglish", isTargetEnglish);
        targetLanguageIsEnglish = (isTargetEnglish.equals("true"));
    }
}
