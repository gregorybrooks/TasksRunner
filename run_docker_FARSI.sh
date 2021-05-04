#!/bin/bash
set -v
set -o allexport

DOCKERHUB_USER=gregorybrooks

ENV_FILE=/mnt/scratch/BETTER/BETTER_TEST_ENVIRONMENTS/BETTER_FARSI/clear_ir.farsi.env
source $ENV_FILE

CONTROL_FILE=./run_settings_FARSI.env
source $CONTROL_FILE

docker run --rm --env-file=$ENV_FILE --env-file=$CONTROL_FILE -v /var/run/docker.sock:/var/run/docker.sock -v $appFileLocation:$appFileLocation -v $corpusFileLocation:$corpusFileLocation -v $scratchFileLocation:$scratchFileLocation $DOCKERHUB_USER/tasks-runner bash -c "./runit.sh"