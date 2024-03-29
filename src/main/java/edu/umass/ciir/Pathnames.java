package edu.umass.ciir;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Pathnames of the files used by the program, and settings such as 'targetCorpusFileName'.
 * The default values are set here, but when the program starts if there is an environment var
 * with the same name, its value will override the default.
 * So you should source a file that exports all of these that you don't want the default for
 * as environment variables before calling the program,
 * and if running in a Docker container, pass that file as the --env-file parameter to docker run.
 */
public class Pathnames {
    private static final Logger logger = Logger.getLogger("TasksRunner");

    private static Map<String, String> env = System.getenv();

    public static String preTrainSizeParm = "FULL";    // or "SMALL"
    public static int REQUEST_HITS_DETAILED = 100;  // number of scoredHits to get full text and event details
    public static int RESULTS_CAP = 1000;
    public static int RESULTS_CAP_IN_FINAL_RESULTS_FILE = 1000;
    public static int DOCS_TO_PROCESS = 2000;

    public static String searchEngine = "galago";
    public static boolean developmentTestingNoDocker = false;
    public static boolean IEAllowed = true;

    public static boolean skipReranker = false;
    public static boolean skipPretrain = false;
    public static boolean skipIndexBuild = false;
    public static boolean skipExampleDocAnnotation = false;
    public static boolean skipRelevantDocAnnotation = false;
    public static boolean skipRequestDocAnnotation = false;
    public static boolean skipNeuralIndexBuild = false;

    public static String gpusForEventExtractor = "--gpus device=1";

    public static boolean includeEventsInFinalResults = false;
    public static boolean checkForSudo = true;
    public static boolean doTaskLevelEvaluation = false;
    public static boolean doRequestLevelEvaluation = false;

    public static ProcessingModel processingModel = ProcessingModel.TWO_STEP;
    public static boolean runGetCandidateDocs = false;  // disable this operation for now
    public static boolean targetLanguageIsEnglish = false; // disable this mode of operation for now
    public static String scratchFileLocation = "/mnt/scratch/BETTER_DRY_RUN/scratch/";
    public static String corpusFileLocation = "/mnt/scratch/BETTER_DRY_RUN/corpus/";
    public static String appFileLocation = "/mnt/scratch/BETTER_DRY_RUN/app/";
    public static String requestLevelQueryFormulatorDockerImage = "";
    public static String taskLevelQueryFormulatorDockerImage = "";
    public static String neuralQueryProcessorDockerImage = "";
    public static String getCandidateDocsQueryFormulatorDockerImage = "";
    public static String getPhrasesQueryFormulatorDockerImage = "";
    public static String rerankerDockerImage = "";
    public static String reranker2DockerImage = "";

    public static String tempFileLocation = scratchFileLocation + "tmp/";
    public static String runFileLocation = scratchFileLocation + "runfiles/";
    public static String qrelFileLocation = scratchFileLocation + "qrelfiles/";
    public static String evaluationFileLocation = scratchFileLocation + "evaluationfiles/";
    public static String queryFileLocation = scratchFileLocation + "queryfiles/";
    public static String logFileLocation = scratchFileLocation + "logfiles/";
    public static String indexLocation = scratchFileLocation + "indexes/";
    public static String neuralFilesLocation = scratchFileLocation + "indexes/neural/";
    public static String galagoLocation = scratchFileLocation + "galago/bin/";
    public static String anseriniLocation = scratchFileLocation + "galago/bin/";
    public static String galagoBaseLocation = scratchFileLocation + "galago";
    public static String programFileLocation = "/home/tasksrunner/programfiles/";
    public static String scriptFileLocation = "/home/tasksrunner/scripts/";
    public static String eventExtractorFileLocation = scratchFileLocation + "eventextractorfiles/";
    public static String taskCorpusFileLocation = scratchFileLocation + "taskcorpusfiles/";
    public static String galagoJobDirLocation = scratchFileLocation + "galago_job_dir/";
    public static String targetCorpusFileName = "";
    public static String englishCorpusFileName = "";
    public static String tasksFileName = "ir-tasks.json";
    public static String qrelFileName = "IR-relevance-assessments.qrels";
    public static String supplementalFileName = "supplemental_info.json";
    public static boolean readQrelFile = false;
    public static boolean expandQrelDocuments = false;
    public static String corpusFileFormat = "BETTER";
    public static String analyticTasksFileFormat = "BETTER";
    public static boolean sudoNeeded = true;
    public static String gpuDevice = "";
    public static String rerankerDevice = "";
    public static String MODELS_BASE_DIR = "";

    public static void checkDockerImage (String imageName) {
        if (!developmentTestingNoDocker) {  /* This doesn't work in the JetBrains debugger */
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
//        if (env.containsKey(key)) {
//            return env.get(key);
        /* To run successfully in the JetBrains debugger, you can't use containsKey() */
        for (String candidate : env.keySet()) {
            if (candidate.trim().equals(key)) {
                return env.get(candidate);
            }
        }
        if (required == Required.REQUIRED) {
            throw new TasksRunnerException(default_value);
        } else {
            return default_value;
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

        // Set this to true in the environment vars if you are testing on cessnock, outside of a Docker container
        developmentTestingNoDocker = (getFromEnv("developmentTestingNoDocker", "false").equals("true"));
        searchEngine = getFromEnv("searchEngine", "galago");

        preTrainSizeParm = getFromEnv("preTrainSizeParm", "FULL");
        REQUEST_HITS_DETAILED = Integer.parseInt(getFromEnv("REQUEST_HITS_DETAILED", "10"));
        DOCS_TO_PROCESS = Integer.parseInt(getFromEnv("DOCS_TO_PROCESS", "1000"));
        RESULTS_CAP = Integer.parseInt(getFromEnv("RESULTS_CAP", "1000"));
        RESULTS_CAP_IN_FINAL_RESULTS_FILE = Integer.parseInt(getFromEnv("RESULTS_CAP_IN_FINAL_RESULTS_FILE", "1000"));
        includeEventsInFinalResults = (getFromEnv("includeEventsInFinalResults", "false").equals("true"));
        skipIndexBuild = (getFromEnv("skipIndexBuild", "false").equals("true"));
        skipNeuralIndexBuild = (getFromEnv("skipNeuralIndexBuild", "false").equals("true"));
        skipExampleDocAnnotation = (getFromEnv("skipAnnotateExampleDocs", "false").equals("true"));
        skipRelevantDocAnnotation = (getFromEnv("skipAnnotateRelevantDocs", "false").equals("true"));
        skipRequestDocAnnotation = (getFromEnv("skipAnnotateRequestDocs", "false").equals("true"));
        skipPretrain = (getFromEnv("skipPretrain", "true").equals("true"));
        skipReranker = (getFromEnv("skipReranker", "false").equals("true"));
        checkForSudo = (getFromEnv("checkForSudo", "true").equals("true"));

        processingModel = ProcessingModel.valueOf((getFromEnv("processingModel", "TWO_STEP")));

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
                "");
        if (requestLevelQueryFormulatorDockerImage.length() > 0) {
            checkDockerImage(requestLevelQueryFormulatorDockerImage);
        }

        taskLevelQueryFormulatorDockerImage = getFromEnv("taskLevelQueryFormulatorDockerImage",
                "");
        if (taskLevelQueryFormulatorDockerImage.length() > 0) {
            checkDockerImage(taskLevelQueryFormulatorDockerImage);
        }

        getCandidateDocsQueryFormulatorDockerImage = getFromEnv("getCandidateDocsQueryFormulatorDockerImage",
                "");
        if (getCandidateDocsQueryFormulatorDockerImage.length() > 0) {
            checkDockerImage(getCandidateDocsQueryFormulatorDockerImage);
        }

        getPhrasesQueryFormulatorDockerImage = getFromEnv("getPhrasesQueryFormulatorDockerImage",
                "");
        if (getPhrasesQueryFormulatorDockerImage.length() > 0) {
            checkDockerImage(getPhrasesQueryFormulatorDockerImage);
        }

        neuralQueryProcessorDockerImage = getFromEnv("neuralQueryProcessorDockerImage",
                "");
        if (neuralQueryProcessorDockerImage.length() > 0) {
            checkDockerImage(neuralQueryProcessorDockerImage);
        }

        rerankerDockerImage = getFromEnv("rerankerDockerImage",
                "");
        if (rerankerDockerImage.length() > 0) {
            checkDockerImage(rerankerDockerImage);
        }

        reranker2DockerImage = getFromEnv("reranker2DockerImage",
                "");
        if (reranker2DockerImage.length() > 0) {
            checkDockerImage(reranker2DockerImage);
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
        neuralFilesLocation = ensureTrailingSlash(getFromEnv("neuralFilesLocation",
                scratchFileLocation + "indexes/neural/"));

        galagoLocation = ensureTrailingSlash(getFromEnv("galagoLocation",
                "/home/tasksrunner/galago/bin/"));
        if (developmentTestingNoDocker) {
            galagoLocation = "/mnt/scratch/BETTER/dev/tools/galago-dev/core/target/appassembler/bin/";
        }

        galagoBaseLocation = galagoLocation.replace("/bin/","");

        anseriniLocation = ensureTrailingSlash(getFromEnv("anseriniLocation",
                "/home/tasksrunner/anserini/target/appassembler/bin/"));
        if (developmentTestingNoDocker) {
            anseriniLocation = "/mnt/scratch/LEMUR_PROJECT/dev/anserini/target/appassembler/bin/";
        }

        programFileLocation = ensureTrailingSlash(getFromEnv("programFileLocation",
                "/home/tasksrunner/programfiles/"));
        if (developmentTestingNoDocker) {
            programFileLocation = "/mnt/scratch/BETTER/dev/tools/TasksRunner/programfiles/";
        }

        scriptFileLocation = ensureTrailingSlash(getFromEnv("programFileLocation",
                "/home/tasksrunner/scripts/"));
        if (developmentTestingNoDocker) {
            scriptFileLocation = "/mnt/scratch/BETTER/dev/tools/TasksRunner/scripts";
        }
        eventExtractorFileLocation = ensureTrailingSlash(getFromEnv("eventExtractorFileLocation",
                scratchFileLocation + "eventextractorfiles/"));
        taskCorpusFileLocation = ensureTrailingSlash(getFromEnv("taskCorpusFileLocation",
                scratchFileLocation + "taskcorpusfiles/"));
        galagoJobDirLocation = ensureTrailingSlash(getFromEnv("galagoJobDirLocation",
                scratchFileLocation + "galago_job_dir/"));

        targetCorpusFileName = getFromEnv("targetCorpusFileName",
                "MISSING ENV VAR: targetCorpusFileName", Required.REQUIRED);
        englishCorpusFileName = getFromEnv("englishCorpusFileName",
                "MISSING ENV VAR: englishCorpusFileName", Required.REQUIRED);
        tasksFileName = getFromEnv("tasksFileName","ir-tasks.json");
        supplementalFileName = getFromEnv("supplementalFileName", "supplemental_info.json");
        qrelFileName = getFromEnv("qrelFileName","IR-relevance-assessments.qrels");
        readQrelFile = (getFromEnv("readQrelFile", "false").equals("true"));
        expandQrelDocuments = (getFromEnv("expandQrelDocuments", "false").equals("true"));
        corpusFileFormat = getFromEnv("corpusFileFormat", "BETTER");
        analyticTasksFileFormat = getFromEnv("analyticTasksFileFormat","BETTER");
        doRequestLevelEvaluation = (getFromEnv("doRequestLevelEvaluation", "false").equals("true"));
        doTaskLevelEvaluation = (getFromEnv("doTaskLevelEvaluation", "false").equals("true"));
        sudoNeeded = (getFromEnv("sudoNeeded", "true").equals("true"));
        gpuDevice = getFromEnv("gpuDevice", "");
        rerankerDevice = getFromEnv("rerankerDevice", "cpu");
        MODELS_BASE_DIR = getFromEnv("MODELS_BASE_DIR", MODELS_BASE_DIR);
        gpusForEventExtractor = getFromEnv("gpusForEventExtractor", gpusForEventExtractor);
    }
}
