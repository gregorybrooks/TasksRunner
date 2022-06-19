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

    public void callDockerImage() {
        try {
            String dockerImageName = Pathnames.neuralQueryProcessorDockerImage;
            String analyticTasksInfoFilename = submissionId + ".analytic_tasks.json";
            String sudo = (Pathnames.sudoNeeded ? "sudo" : "");
            String gpu_parm = (!Pathnames.gpuDevice.equals("") ? " --gpus device=" + Pathnames.gpuDevice : "");
            // if 4 GPUs, 0 is first one, 1 is second one, etc.
            String command = sudo + " docker run --rm"
                    + gpu_parm
                    + " --env MODE=" + mode
                    + " --env INPUTFILE=" + analyticTasksInfoFilename
                    /* For each directory that we want to share between this parent docker container (TasksRunner)
                     and the child docker container (TaskQueryBuilder1 e.g.), we pass the pathname
                     in an environment variable, and we make that path a bind-volume so the child container
                     can actually access it.
                     */
                    + " --env eventExtractorFileLocation=" + Pathnames.eventExtractorFileLocation
                    + " --env logFileLocation=" + Pathnames.logFileLocation
                    + " -v " + Pathnames.eventExtractorFileLocation + ":" + Pathnames.eventExtractorFileLocation
                    + " -v " + Pathnames.logFileLocation + ":" + Pathnames.logFileLocation
                    + " --env corpusFileLocation=" + Pathnames.corpusFileLocation + "/"
                    + " -v " + Pathnames.corpusFileLocation + ":" + Pathnames.corpusFileLocation
                    + " --env targetCorpusFileName=" + Pathnames.targetCorpusFileName
                    + " --env runFileLocation=" + Pathnames.runFileLocation + "/"
                    + " -v " + Pathnames.runFileLocation + ":" + Pathnames.runFileLocation

                    + " " + dockerImageName
                    + " sh -c ./runit.sh";
            String logFile = Pathnames.logFileLocation + submissionId + ".neural-docker-program.out";
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
