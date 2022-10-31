package edu.umass.ciir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class NeuralQueryProcessorDocker  {

    private static final Logger logger = Logger.getLogger("TasksRunner");
    protected AnalyticTasks tasks;
    protected String mode;
    String submissionId;

    NeuralQueryProcessorDocker(String submissionId, String mode, AnalyticTasks tasks) {
        this.tasks = tasks;
        this.mode = mode;
        this.submissionId = submissionId;
    }

    public void buildIndex() {
        callDockerImage("runit_indexbuild.sh");
    }

    public void search() {
        callDockerImage("runit_retrieval.sh");
    }

    public void callDockerImage(String script) {
        try {
            String dockerImageName = Pathnames.neuralQueryProcessorDockerImage;
            String analyticTasksInfoFilename = submissionId + ".analytic_tasks.json";
            String sudo = (Pathnames.sudoNeeded ? "sudo" : "");
            // For the device parm: if 4 GPUs, 0 is first one, 1 is second one, etc.
// doesn't seem to work at Mitre:            String gpu_parm = (!Pathnames.gpuDevice.equals("") ? " --gpus device=" + Pathnames.gpuDevice : "");
            // For --gpus N, it is how many GPUs to make available, 1, 2, 3 or 4
            String gpu_parm = " --gpus 1";
//            String gpu_parm = (!Pathnames.gpuDevice.equals("") ? " --gpus device=" + Pathnames.gpuDevice : "");

            String deviceParm = Pathnames.rerankerDevice;   // cuda:0 or cpu
            String command = sudo + " docker run --rm"
                    + gpu_parm
                    + " --env MODE=" + mode
                    + " --env DEVICE=" + deviceParm
                    + " --env NUM_CPU=8 --env TOPK=100"

                    + " --env TASK_FILE=" + /*Pathnames.eventExtractorFileLocation + */ analyticTasksInfoFilename
                    + " -v " + Pathnames.eventExtractorFileLocation + ":" + Pathnames.eventExtractorFileLocation
                    + " --env DATA_DIR=" + Pathnames.eventExtractorFileLocation

                    + " --env RUNFILE_DIR=" + Pathnames.runFileLocation
                    + " -v " + Pathnames.runFileLocation + ":" + Pathnames.runFileLocation

                    + " --env NEURAL_FILES_DIR=" + Pathnames.neuralFilesLocation
                    + " -v " + Pathnames.neuralFilesLocation + ":" + Pathnames.neuralFilesLocation

                    + " --env logFileLocation=" + Pathnames.logFileLocation
                    + " -v " + Pathnames.logFileLocation + ":" + Pathnames.logFileLocation

                    + " --env CORPUS_FILE=" + Pathnames.corpusFileLocation + Pathnames.targetCorpusFileName
                    + " -v " + Pathnames.corpusFileLocation + ":" + Pathnames.corpusFileLocation

                    + " " + dockerImageName
                    + " sh -c ./" + script;
            String logFile = Pathnames.logFileLocation + submissionId + ".neural-docker-program." + script + ".out";
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
}
