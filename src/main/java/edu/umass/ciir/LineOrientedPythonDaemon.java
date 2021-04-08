package edu.umass.ciir;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class LineOrientedPythonDaemon {
    private  ProcessBuilder sentenceProcessBuilder = null;
    private  Process sentenceProcess = null;
    private  BufferedWriter sentenceCalledProcessStdin = null;
    private  BufferedReader sentenceCalledProcessStdout = null;

    LineOrientedPythonDaemon(String programName) {
        try {
            sentenceProcessBuilder = new ProcessBuilder("/usr/bin/python3", Pathnames.programFileLocation +
                    programName);
            sentenceProcessBuilder.directory(new File(Pathnames.programFileLocation));
            sentenceProcess = sentenceProcessBuilder.start();

            sentenceCalledProcessStdin = new BufferedWriter(
                    new OutputStreamWriter(sentenceProcess.getOutputStream()));
            sentenceCalledProcessStdout = new BufferedReader(
                    new InputStreamReader(sentenceProcess.getInputStream()));
        } catch (Exception e) {
            throw new TasksRunnerException(e);
        }
    }

    public  synchronized List<String> getAnswers(String text) {
        List<String> phrases = new ArrayList<String>();
        try {
            sentenceCalledProcessStdin.write(text + "\n");
            sentenceCalledProcessStdin.write("EOD\n");
            sentenceCalledProcessStdin.flush();

            String line;
            while ((line = sentenceCalledProcessStdout.readLine()) != null) {
                if (line.equals("EOL")) {
                    break;
                }
                phrases.add(line);
            }
        } catch (Exception e) {
            throw new TasksRunnerException(e);
        }
        return phrases;
    }

    // No need to do this, let it run until the whole program ends
    public  void stop() {
        try {
            sentenceProcess.destroy();
        } catch (Exception e) {
            throw new TasksRunnerException(e);
        }
    }
}
