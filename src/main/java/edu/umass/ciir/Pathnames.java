package edu.umass.ciir;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Pathnames of the files used by the program, and settings such as 'mode' and 'runIRPhase2'.
 * The default values are set here, but when the program starts if there is an environment var
 * with the same name, its value will override the default.
 * So you should source a file that exports all of these that you don't want the default for
 * as environment variables before calling the program,
 * and if running in a Docker container, pass that file as the --env-file parameter to docker run.
 */
public class Pathnames {
    private static final Logger logger = Logger.getLogger("TasksRunner");

    private static Map<String, String> env = System.getenv();

    public static boolean useTaskSetFile = false;
    public static int RESULTS_CAP = 1000;
    public static boolean includeEventsInFinalResults = true;
    public static boolean checkForSudo = true;
    public static boolean doTaskLevelEvaluation = true;
    public static boolean doRequestLevelEvaluation = true;
    public static boolean runEnglishPreprocess = false;
    public static boolean runEnglishIndexBuild = false;
    public static boolean runPreTrain = false;
    public static boolean runPreprocess = false;
    public static boolean runIndexBuild = false;
    public static boolean runIRPhase1 = false;
    public static boolean runIRPhase2 = true;
    public static boolean runIRPhase3 = false;
    public static boolean runIEPhase = false;
    public static ProcessingModel processingModel = ProcessingModel.TWO_STEP;

    public static String scratchFileLocation = "/mnt/scratch/BETTER_DRY_RUN/scratch/clear_ir/";
    public static String corpusFileLocation = "/mnt/scratch/BETTER_DRY_RUN/corpus/";
    public static String appFileLocation = "/mnt/scratch/BETTER_DRY_RUN/app/";
    public static String requestLevelQueryFormulatorDockerImage = "";
    public static String taskLevelQueryFormulatorDockerImage = "";
    public static String neuralQueryProcessorDockerImage = "";
    public static String rerankerDockerImage = "";

    public static String tempFileLocation = scratchFileLocation + "tmp/";
    public static String runFileLocation = scratchFileLocation + "runfiles/";
    public static String qrelFileLocation = scratchFileLocation + "qrelfiles/";
    public static String evaluationFileLocation = scratchFileLocation + "evaluationfiles/";
    public static String queryFileLocation = scratchFileLocation + "queryfiles/";
    public static String logFileLocation = scratchFileLocation + "logfiles/";
    public static String indexLocation = scratchFileLocation + "indexes/";
    public static String englishIndexName = "BETTER-DryRun-v3";
    public static String englishIndexLocation = indexLocation + englishIndexName;
    public static String targetIndexName = "BETTER-DryRun-v3";
    public static String targetIndexLocation = indexLocation + targetIndexName;
    public static String galagoLocation = scratchFileLocation + "galago/bin/";
    public static String galagoBaseLocation = scratchFileLocation + "galago";
    public static String programFileLocation = "/home/tasksrunner/programfiles/";
    public static String eventExtractorFileLocation = scratchFileLocation + "eventextractorfiles/";
    public static String translationTableLocation = programFileLocation + "translation_tables/";
    public static String taskCorpusFileLocation = scratchFileLocation + "taskcorpusfiles/";
    public static String galagoJobDirLocation = scratchFileLocation + "galago_job_dir/";
    public static String targetCorpusFileName = "english/BETTER-English-IR-data.v1.jl";
    public static String englishCorpusFileName = "english/BETTER-English-IR-data.v1.jl";
    public static String tasksFileNameAUTO = "dry-run-topics.auto.json";
    public static String tasksFileNameAUTOHITL = "dry-run-topics.auto-hitl.json";
    public static String tasksFileNameHITL = "dry-run-topics.hitl.json";
    public static String qrelFileName = "req-qrels";
    public static String isTargetEnglish = "true";
    public static boolean targetLanguageIsEnglish = true;
    public static Language targetLanguage = Language.ENGLISH;
    public static String supplementalFileName = "supplemental_info.json";
    public static boolean readQrelFile = true;
    public static boolean expandQrelDocuments = true;
    public static String mode = "";
    public static String corpusFileFormat = "BETTER";
    public static String analyticTasksFileFormat = "BETTER";
    public static boolean sudoNeeded = true;
    public static String gpuDevice = "";
    public static String MODELS_BASE_DIR_ENGLISH = "";
    public static String MODELS_BASE_DIR_FARSI = "";
    public static String MODELS_BASE_DIR = "";

// english training as english and arabic
/*
    public static String scratchLocation = "/home/glbrooks/BETTER/";
    public static String targetCorpusFileName = "english-training-corpus.jl";
    public static String englishCorpusFileName = "english-training-corpus.jl";
    public static String tasksFileName = "ir-hitl-performer-tasks.fixed.json";
    public static String englishIndexLocation = "/home/glbrooks/BETTER/indexes/BETTER-IR-English-Training-v1";
    public static String targetIndexLocation = "/home/glbrooks/BETTER/indexes/BETTER-IR-English-Training-v1";
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
    public static void checkDockerImage (String imageName) {
        int exitVal = 0;
        int numLines = 0;
        try {
            // String command = "sudo docker image ls " + imageName + " | wc -l ";
            List builders;
            if (Pathnames.sudoNeeded) {
                builders = Arrays.asList(
                        new ProcessBuilder("sudo", "docker", "image", "ls", imageName),
                        new ProcessBuilder("wc", "-l"));
            } else {
                builders = Arrays.asList(
                        new ProcessBuilder("docker", "image", "ls", imageName),
                        new ProcessBuilder("wc", "-l"));
            }
            List<Process> processes = ProcessBuilder.startPipeline(builders);
            Process process = processes.get(processes.size() - 1);

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() == 0) {
                    continue;
                }
                numLines = Integer.parseInt(line.strip());
            }
            exitVal = process.waitFor();
        } catch (Exception e) {
            throw new TasksRunnerException(e);
        }
        if (exitVal != 0) {
            throw new TasksRunnerException("Unexpected ERROR while executing docker command. Exit value is " + exitVal);
        }
        if (numLines < 2) {
            System.out.println();
            throw new TasksRunnerException("Docker image " + imageName + " not found on your system");
        }
    }

    public enum Required {
        REQUIRED,
        OPTIONAL
    }

    public enum ProcessingModel {
        TWO_STEP,
        ONE_STEP,
        NEURAL
    }

    public enum Language {
        ENGLISH,
        ARABIC,
        FARSI
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

        RESULTS_CAP = Integer.parseInt(getFromEnv("RESULTS_CAP", "1000"));
        includeEventsInFinalResults = (getFromEnv("includeEventsInFinalResults", "true").equals("true"));
        useTaskSetFile = (getFromEnv("useTaskSetFile", "false").equals("true"));
        checkForSudo = (getFromEnv("checkForSudo", "true").equals("true"));
        runEnglishPreprocess = (getFromEnv("runEnglishPreprocess", "false").equals("true"));
        runEnglishIndexBuild = (getFromEnv("runEnglishIndexBuild", "false").equals("true"));
        runPreTrain = (getFromEnv("runPreTrain", "false").equals("true"));
        runPreprocess = (getFromEnv("runPreprocess", "false").equals("true"));
        runIndexBuild = (getFromEnv("runIndexBuild", "false").equals("true"));
        runIRPhase1 = (getFromEnv("runIRPhase1", "false").equals("true"));
        runIRPhase2 = (getFromEnv("runIRPhase2", "true").equals("true"));
        runIRPhase3 = (getFromEnv("runIRPhase3", "false").equals("true"));
        runIEPhase = (getFromEnv("runIEPhase", "false").equals("true"));

        processingModel = ProcessingModel.valueOf((getFromEnv("processingModel",
                "TWO_STEP")));

        scratchFileLocation = ensureTrailingSlash(getFromEnv("scratchFileLocation",
                "MISSING ENV VAR: scratchFileLocation", Required.REQUIRED));
        corpusFileLocation = ensureTrailingSlash(getFromEnv("corpusFileLocation",
                "MISSING ENV VAR: corpusFileLocation", Required.REQUIRED));
        appFileLocation = ensureTrailingSlash(getFromEnv("appFileLocation",
                "MISSING ENV VAR: appFileLocation", Required.REQUIRED));

        tempFileLocation = ensureTrailingSlash(getFromEnv("tempFileLocation",
                scratchFileLocation + "tmp/"));
        logFileLocation = ensureTrailingSlash(getFromEnv("logFileLocation",
                scratchFileLocation + "logfiles/"));
        requestLevelQueryFormulatorDockerImage = getFromEnv("requestLevelQueryFormulatorDockerImage",
                "MISSING ENV VAR: requestLevelQueryFormulatorDockerImage");
        if (processingModel == ProcessingModel.ONE_STEP || processingModel == ProcessingModel.TWO_STEP) {
            checkDockerImage(requestLevelQueryFormulatorDockerImage);
        }

        taskLevelQueryFormulatorDockerImage = getFromEnv("taskLevelQueryFormulatorDockerImage",
                "MISSING ENV VAR: taskLevelQueryFormulatorDockerImage");
        if (processingModel == ProcessingModel.TWO_STEP) {
            checkDockerImage(taskLevelQueryFormulatorDockerImage);
        }

        neuralQueryProcessorDockerImage = getFromEnv("neuralQueryProcessorDockerImage",
                "MISSING ENV VAR: neuralQueryProcessorDockerImage");
        if (processingModel == ProcessingModel.NEURAL) {
            checkDockerImage(neuralQueryProcessorDockerImage);
        }

        rerankerDockerImage = getFromEnv("rerankerDockerImage",
                "MISSING ENV VAR: rerankerDockerImage");
        if (!(processingModel == ProcessingModel.NEURAL)) {
            checkDockerImage(rerankerDockerImage);
        }

        queryFileLocation = ensureTrailingSlash(getFromEnv("queryFileLocation",
                scratchFileLocation + "queryfiles/"));
        runFileLocation = ensureTrailingSlash(getFromEnv("runFileLocation",
                scratchFileLocation + "runfiles/"));
        evaluationFileLocation = ensureTrailingSlash(getFromEnv("evaluationFileLocation",
                scratchFileLocation + "evaluationfiles/"));
        qrelFileLocation = ensureTrailingSlash(getFromEnv("qrelFileLocation",
                scratchFileLocation + "qrelfiles/"));
        indexLocation = ensureTrailingSlash(getFromEnv("indexLocation",
                scratchFileLocation + "indexes/"));
        targetIndexName = getFromEnv("targetIndexName",
                "MISSING ENV VAR: targetIndexName", Required.REQUIRED);
        targetIndexLocation = getFromEnv("targetIndexLocation",
                indexLocation + targetIndexName);
        // Currently the index on the English training data is not used
        englishIndexName = getFromEnv("englishIndexName", "NOT_USED");
        englishIndexLocation = getFromEnv("englishIndexLocation",
                indexLocation + englishIndexName);

        galagoLocation = ensureTrailingSlash(getFromEnv("galagoLocation",
                "/home/tasksrunner/galago/bin/"));
        galagoBaseLocation = galagoLocation.replace("/bin/","");
        programFileLocation = ensureTrailingSlash(getFromEnv("programFileLocation",
                "/home/tasksrunner/programfiles/"));
        eventExtractorFileLocation = ensureTrailingSlash(getFromEnv("eventExtractorFileLocation",
                scratchFileLocation + "eventextractorfiles/"));
        translationTableLocation = ensureTrailingSlash(getFromEnv("translationTableLocation",
                programFileLocation + "translation_tables/"));
        taskCorpusFileLocation = ensureTrailingSlash(getFromEnv("taskCorpusFileLocation",
                scratchFileLocation + "taskcorpusfiles/"));
        galagoJobDirLocation = ensureTrailingSlash(getFromEnv("galagoJobDirLocation",
                scratchFileLocation + "galago_job_dir/"));

        targetCorpusFileName = getFromEnv("targetCorpusFileName",
                "MISSING ENV VAR: targetCorpusFileName", Required.REQUIRED);
        englishCorpusFileName = getFromEnv("englishCorpusFileName",
                "MISSING ENV VAR: englishCorpusFileName", Required.REQUIRED);
        tasksFileNameAUTO = getFromEnv("tasksFileNameAUTO",
                "MISSING ENV VAR: tasksFileNameAUTO", Required.REQUIRED);
        tasksFileNameAUTOHITL = getFromEnv("tasksFileNameAUTOHITL",
                "MISSING ENV VAR: tasksFileNameAUTOHITL", Required.REQUIRED);
        tasksFileNameHITL = getFromEnv("tasksFileNameHITL",
                "MISSING ENV VAR: tasksFileNameHITL", Required.REQUIRED);
        supplementalFileName = getFromEnv("supplementalFileName", "supplemental_info.json");
        qrelFileName = getFromEnv("qrelFileName","");
        readQrelFile = (getFromEnv("readQrelFile", "true").equals("true"));
        expandQrelDocuments = (getFromEnv("expandQrelDocuments", "true").equals("true"));
        mode = getFromEnv("mode", "AUTO");
        corpusFileFormat = getFromEnv("corpusFileFormat", "MISSING ENV VAR: corpusFileFormat",
                Required.REQUIRED);
        analyticTasksFileFormat = getFromEnv("analyticTasksFileFormat",
                "MISSING ENV VAR: analyticTasksFileFormat", Required.REQUIRED);
        doRequestLevelEvaluation = (getFromEnv("doRequestLevelEvaluation", "true").equals("true"));
        doTaskLevelEvaluation = (getFromEnv("doTaskLevelEvaluation", "true").equals("true"));
        isTargetEnglish = getFromEnv("isTargetEnglish", isTargetEnglish);
        targetLanguageIsEnglish = (isTargetEnglish.equals("true"));
        targetLanguage = Language.valueOf(getFromEnv("targetLanguage", "MISSING ENV VAR: targetLanguage",
                Required.REQUIRED));
        sudoNeeded = (getFromEnv("sudoNeeded", "true").equals("true"));
        gpuDevice = getFromEnv("gpuDevice", "");
        MODELS_BASE_DIR = getFromEnv("MODELS_BASE_DIR", MODELS_BASE_DIR);
        MODELS_BASE_DIR_ENGLISH = getFromEnv("MODELS_BASE_DIR_ENGLISH", MODELS_BASE_DIR_ENGLISH);
        MODELS_BASE_DIR_FARSI = getFromEnv("MODELS_BASE_DIR_ENGLISH", MODELS_BASE_DIR_FARSI);
        targetLanguage = Language.valueOf(getFromEnv("targetLanguage", "MISSING ENV VAR: targetLanguage",
                Required.REQUIRED));
    }
}
