package edu.umass.ciir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;


public class Command {
    private static final Logger logger = Logger.getLogger("TasksRunner");

    public Command() {}

    private static void deleteLogFile (String logFileName) {
        try {
            Files.delete(Paths.get(logFileName));
        } catch (IOException ignore) {
            // do nothing
        }
    }

    public static void execute(String command, String logFileName) {
        try {
            String sudo = (Pathnames.sudoNeeded ? "sudo " : "");

            deleteLogFile(logFileName);

            String tempCommand = sudo + command + " >& " + logFileName;
            logger.info("Executing this command: " + tempCommand);

            int exitVal;
            try {
                ProcessBuilder processBuilder = new ProcessBuilder();
                processBuilder.command("bash", "-c", tempCommand);
                Process process = processBuilder.start();

                exitVal = process.waitFor();
            } catch (Exception cause) {
                logger.log(Level.SEVERE, "Exception while executing command", cause);
                throw new TasksRunnerException(cause);
            } finally {
                StringBuilder builder = new StringBuilder();
                try (Stream<String> stream = Files.lines( Paths.get(logFileName), StandardCharsets.UTF_8))
                {
                    stream.forEach(s -> builder.append(s).append("\n"));
                    logger.info("Docker container output log:\n" + builder.toString());
                } catch (IOException ignore) {
                    // logger.info("IO error trying to read output file. Ignoring it");
                }
            }
            if (exitVal != 0) {
                logger.log(Level.SEVERE, "Unexpected ERROR from command, exit value is: " + exitVal);
                throw new TasksRunnerException("Unexpected ERROR from command, exit value is: " + exitVal);
            }
        } catch (Exception e) {
            throw new TasksRunnerException(e);
        }
    }

}
