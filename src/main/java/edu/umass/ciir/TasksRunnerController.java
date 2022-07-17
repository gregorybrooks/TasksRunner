package edu.umass.ciir;

import org.bson.types.ObjectId;
import org.lemurproject.galago.core.tools.Search;
import org.springframework.boot.logging.java.SimpleFormatter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.*;
import java.util.logging.*;

@RestController
@CrossOrigin
public class TasksRunnerController {
    private static final Logger logger = Logger.getLogger("TasksRunner");

    private TasksRunner betterIR;

    TasksRunnerController()  {
        betterIR = new TasksRunner();
//        betterIR.setupLogging();
        //List<SearchHit> hits = getSearchResults();   // TEMP FOR TESTING
        testMerge();  // TEMP for testing merge
    }

    @GetMapping("/search")
    public List<SearchHit> getSearchResults( ) {
        logger.info("GET /search");
        betterIR.readTaskSetFile(Pathnames.appFileLocation + "tasks.json");
        betterIR.process();
        return betterIR.getSearchHits();
    }

    private void testMerge() {
        List<String> filesToMerge = Arrays.asList("/mnt/scratch/BETTER/BETTER_TEST_ENVIRONMENTS/BETTER_PHASE3_COMBO_ARABIC_FARSI/scratch/clear_ir/runfiles/better.arabic.Request.gregorybrooks-better-query-builder-task-noun-phrases:2.1.0.RERANKED.out",
                "/mnt/scratch/BETTER/BETTER_TEST_ENVIRONMENTS/BETTER_PHASE3_COMBO_ARABIC_FARSI/scratch/clear_ir/runfiles/better.farsi.Request.gregorybrooks-better-query-builder-task-noun-phrases:2.1.0.RERANKED.out");
        betterIR.readTaskSetFile(Pathnames.appFileLocation + "tasks.json");
        betterIR.mergeRerankedRunFiles(filesToMerge);
    }

}
