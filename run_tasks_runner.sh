#!/bin/bash
set -ve
set -o allexport

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

# The following file is also passed to the Docker container, so it has to follow
# the rules for Docker env files--no nested variables
ENV_FILE="${SCRIPT_DIR}/eval.env.HITL"
source $ENV_FILE

#docker pull gregorybrooks/tasks-runner:5.1.0
#docker pull gregorybrooks/better-query-builder-task-noun-phrases:2.0.0
#docker pull gregorybrooks/better-query-builder-1:3.1.0
#docker pull gregorybrooks/better-reranker-z1:3.2.1
#docker pull gregorybrooks/better-query-builder-get-candidate-docs:1.0.0

MODELS_BASE_DIR_ENGLISH=
MODELS_BASE_DIR_ARABIC=
#MODELS_BASE_DIR_ARABIC=${SCRATCH_DIR}/clear_ir/isi_models_basic_b_bos

corpusFileLocation=${CORPUS_DIR}
scratchFileLocation=$SCRATCH_DIR/clear_ir
appFileLocation=${APP_DIR}
EVENT_EXTRACTOR_FILE_DIRECTORY=${SCRATCH_DIR}/clear_ir/eventextractorfiles

docker run --rm --env-file=$ENV_FILE -p 9400:9400 -v /var/run/docker.sock:/var/run/docker.sock -v $APP_DIR:$APP_DIR -v $CORPUS_DIR:$CORPUS_DIR -v $SCRATCH_DIR:$SCRATCH_DIR --env MODELS_BASE_DIR_ENGLISH=$MODELS_BASE_DIR_ENGLISH --env MODELS_BASE_DIR_ARABIC=$MODELS_BASE_DIR_ARABIC --env corpusFileLocation=$corpusFileLocation --env scratchFileLocation=$scratchFileLocation --env appFileLocation=$appFileLocation --env EVENT_EXTRACTOR_FILE_DIRECTORY=$EVENT_EXTRACTOR_FILE_DIRECTORY --env targetCorpusFileName=$targetCorpusFileName --env englishCorpusFileName=$englishCorpusFileName --entrypoint /home/tasksrunner/scripts/run_ir.sh gregorybrooks/tasks-runner:5.1.0
