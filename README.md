# TasksRunner
Given a test environment (tasks file, English training corpus, Arabic (e.g.) target corpus, qrel file), 
this program converts the tasks into Galago queries and executes them. 

See the script named run_docker.sh for how to run the Docker image. It takes one parameter, the MODE
(AUTO, AUTO-HITL, or HITL):

    #############################################################################################
    #!/bin/bash
    set -o allexport
    set -v

    ENV_FILE=/mnt/scratch/BETTER/BETTER_TEST_ENVIRONMENTS/BETTER_DRY_RUN/clear_ir.dry_run.env
    source $ENV_FILE

    CONTROL_FILE=./run_settings.env
    source $CONTROL_FILE

    docker run --rm --env-file=$ENV_FILE --env-file=$CONTROL_FILE -v /var/run/docker.sock:/var/run/docker.sock -v $appFileLocation:$appFileLocation -v $corpusFileLocation:$corpusFileLocation -v $scratchFileLocation:$scratchFileLocation gregorybrooks/tasks-runner:latest bash -c "./runit_DOCKER.sh"

    #############################################################################################

The "control file" called run_settings.env is where you tell TasksRunner which Docker images to use
as the query formulators--one for task-level and one for request-level:

    #############################################################################################

    #!/bin/bash
    #
    # ----- CLEAR IR settings -----
    #
    # These settings control the run:
    #
    mode=AUTO     # can be AUTO, AUTO-HITL, or HITL
    runIndexBuild=false    # This pre-phase indexes the pre-processed corpus data with Galago.
                           # No need to do this--it is included in the Environment.
    runIRPhase1=false      # Phase 1 creates the file for the event annotator to find events in the sample docs.
                           # No need to do this--the annotator was run once and the file was saved in the Docker image.
    runIRPhase2=true       # Phase 2 builds the queries, executes them, and evaluates them.
    runIRPhase3=false      # Phase 3 calls the re-ranker and builds the final output run file--we skip this for now.
    doTaskLevelEvaluation=true
    doRequestLevelEvaluation=true
    
    # In the following 2 settings, put the names of the Docker images to be used to formulate the queries. 
    # These Docker images should be in Docker Hub.

    requestLevelQueryFormulatorDockerImage=gregorybrooks/doct5query_combine_all_passage_queries
    taskLevelQueryFormulatorDockerImage=gregorybrooks/better-query-builder-2

    # (gregorybrooks/better-query-builder-2 corresponds to TASK_LEVEL_4 / TASK_LEVEL_HITL)

    #############################################################################################
