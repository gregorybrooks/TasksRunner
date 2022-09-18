#!/bin/bash
set -ve
set -o allexport

echo `date`

docker pull gregorybrooks/tasks-runner:5.1.0

docker pull gregorybrooks/better-query-builder-task-noun-phrases:3.0.0

docker pull gregorybrooks/better-query-builder-1:4.0.0

docker pull gregorybrooks/better-reranker-z1:3.2.0


SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

# The following file is also passed to the Docker container, so it has to follow
# the rules for Docker env files--no nested variables
ENV_FILE="${SCRIPT_DIR}/eval.env"
source $ENV_FILE

#sudo chmod -R a+rw $scratchFileLocation

set +e
rm -f ${appFileLocation}/results.json
set -e

MODELS_BASE_DIR_ENGLISH=
MODELS_BASE_DIR=${scratchFileLocation}/clear_ir/isi_models_basic_b

EVENT_EXTRACTOR_FILE_DIRECTORY=${scratchFileLocation}/eventextractorfiles

docker run --rm --env-file=$ENV_FILE -v /var/run/docker.sock:/var/run/docker.sock -v $appFileLocation:$appFileLocation -v $corpusFileLocation:$corpusFileLocation -v $scratchFileLocation:$scratchFileLocation --env MODELS_BASE_DIR_ENGLISH=$MODELS_BASE_DIR_ENGLISH --env MODELS_BASE_DIR=$MODELS_BASE_DIR --env corpusFileLocation=$corpusFileLocation --env scratchFileLocation=$scratchFileLocation --env appFileLocation=$appFileLocation --env EVENT_EXTRACTOR_FILE_DIRECTORY=$EVENT_EXTRACTOR_FILE_DIRECTORY --env targetCorpusFileName=$targetCorpusFileName --env englishCorpusFileName=$englishCorpusFileName --entrypoint /home/tasksrunner/scripts/run_ir.sh gregorybrooks/tasks-runner:5.1.0

echo `date`
