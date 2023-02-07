package edu.umass.ciir;

import org.bson.types.ObjectId;
import org.lemurproject.galago.core.tools.Search;
import org.springframework.boot.logging.java.SimpleFormatter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.*;

@RestController
@CrossOrigin
public class TasksRunnerController {
    private static final Logger logger = Logger.getLogger("TasksRunner");

    private TasksRunner betterIR;

    TasksRunnerController()  {
        betterIR = new TasksRunner();
        betterIR.setupLogging();
    }

    @GetMapping("/search")
    public List<SearchHit> getSearchResults( ) {
        logger.info("GET /search");
        betterIR.readTaskSetFile(Pathnames.appFileLocation + "tasks.json");
        betterIR.process();
        return betterIR.getSearchHits();
    }

}
