package edu.umass.ciir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class QueryFormulatorDocker extends QueryFormulator {

    private static final Logger logger = Logger.getLogger("TasksRunner");
    String dockerImageName;
    String language;
    String phase;
    String queryFileNameKey;

    QueryFormulatorDocker(String submissionId, String mode, String language, AnalyticTasks tasks, String phase, String queryFileNameKey, String dockerImageName) {
        super(submissionId, mode, tasks, phase, queryFileNameKey);
        this.phase = phase;
        this.queryFileNameKey = queryFileNameKey;
        this.dockerImageName = dockerImageName;
    }

    private void callDockerImage() {
        String analyticTasksInfoFilename = submissionId + ".analytic_tasks.json";
        // IF THE DOCKER EVER REALLY USES THE GPUs, MIGHT HAVE TO MAKE THIS --gpus 1 like for reranker
        String gpu_parm = (!Pathnames.gpuDevice.equals("") ? " --gpus device=" + Pathnames.gpuDevice : "");
        // if 4 GPUs, 0 is first one, 1 is second one, etc.
        String command = "docker run --rm"
                + gpu_parm
                + " --env MODE=" + mode
                + " --env OUT_LANG=" + language
                + " --env PHASE=" + phase
                + " --env SEARCH_ENGINE=" + Pathnames.searchEngine
                + " --env INPUTFILE=" + analyticTasksInfoFilename
                + " --env QUERYFILE=" + queryFileNameKey
                /* For each directory that we want to share between this parent docker container (TasksRunner)
                 and the child docker container (TaskQueryBuilder1 e.g.), we pass the pathname
                 in an environment variable, and we make that path a bind-volume so the child container
                 can actually access it.
                 */
/*
                + " --env eventExtractorFileLocation=$eventExtractorFileLocation"
                + " --env queryFileLocation=$queryFileLocation"
                + " -v $eventExtractorFileLocation:$eventExtractorFileLocation"
                + " -v $queryFileLocation:$queryFileLocation"
*/
                + " --env eventExtractorFileLocation=" + Pathnames.eventExtractorFileLocation
                + " --env queryFileLocation=" + Pathnames.queryFileLocation
                + " --env logFileLocation=" + Pathnames.logFileLocation
                + " -v " + Pathnames.eventExtractorFileLocation + ":" + Pathnames.eventExtractorFileLocation
                + " -v " + Pathnames.queryFileLocation + ":" + Pathnames.queryFileLocation
                + " -v " + Pathnames.logFileLocation  + ":" + Pathnames.logFileLocation
                + " --env galagoLocation=" + Pathnames.galagoLocation
                // must define volume for galago, not galago/bin, so it can see the galago/lib files, too:
                + " -v " + Pathnames.galagoBaseLocation + ":" + Pathnames.galagoBaseLocation
                + " --env englishIndexLocation=" + Pathnames.indexLocation + "better-clear-ir-english/"
                + " -v " + Pathnames.indexLocation + "better-clear-ir-english" + ":" + Pathnames.indexLocation + "better-clear-ir-english"
                + " --env targetIndexLocation=" + Pathnames.indexLocation + "better-clear-ir-" + language + "/"
                + " -v " + Pathnames.indexLocation + "better-clear-ir-" + language + ":" + Pathnames.indexLocation + "better-clear-ir-" + language
                + " --env qrelFile=" + Pathnames.qrelFileLocation + Pathnames.qrelFileName
                + " -v " + Pathnames.qrelFileLocation + ":" + Pathnames.qrelFileLocation

                + " " + dockerImageName
                + " sh -c ./runit.sh";
        String logFile = Pathnames.logFileLocation + submissionId + "." + phase + ".query-formulator.out";

        Command.execute(command, logFile);
    }

    /**
     * Constructs the queries from the Tasks, writes the Galago-ready query file
     *
     **/
    public void buildQueries() {
        callDockerImage();
    }
}
