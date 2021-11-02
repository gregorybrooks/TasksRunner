#!/bin/bash
set -ve
set -o allexport

if [ ! $# -eq 1 ]
    then echo 'Supply ARABIC or FARSI as the only argument'
    exit 1
fi

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

TOP_LEVEL_SETTINGS_FILE="${SCRIPT_DIR}/top_level_settings.env.$1"
source $TOP_LEVEL_SETTINGS_FILE

# The following file is also passed to the Docker container, so it has to follow
# the rules for Docker env files--no nested variables

ENV_FILE="${SCRIPT_DIR}/eval.env"
source $ENV_FILE

if [ ! -d "${SCRATCH_DIR}/clear_ir" ]; then
  mkdir -p ${SCRATCH_DIR}/clear_ir
  mkdir -p ${SCRATCH_DIR}/clear_ir/indexes
  mkdir -p ${SCRATCH_DIR}/clear_ir/runfiles
  mkdir -p ${SCRATCH_DIR}/clear_ir/queryfiles
  mkdir -p ${SCRATCH_DIR}/clear_ir/logfiles
  mkdir -p ${SCRATCH_DIR}/clear_ir/logfiles/AUTO
  mkdir -p ${SCRATCH_DIR}/clear_ir/logfiles/AUTO-HITL
  mkdir -p ${SCRATCH_DIR}/clear_ir/logfiles/HITL
  mkdir -p ${SCRATCH_DIR}/clear_ir/tmp
  mkdir -p ${SCRATCH_DIR}/clear_ir/taskcorpusfiles
  mkdir -p ${SCRATCH_DIR}/clear_ir/eventextractorfiles
  mkdir -p ${SCRATCH_DIR}/clear_ir/evaluationfiles
  mkdir -p ${SCRATCH_DIR}/clear_ir/galago_job_dir
  chmod -R a+rw ${SCRATCH_DIR}/clear_ir
fi

echo `date`

corpusFileLocation=${CORPUS_DIR}
scratchFileLocation=$SCRATCH_DIR/clear_ir
appFileLocation=${APP_DIR}
EVENT_EXTRACTOR_FILE_DIRECTORY=${SCRATCH_DIR}/clear_ir/eventextractorfiles

docker run --rm --env-file=$ENV_FILE -v /var/run/docker.sock:/var/run/docker.sock -v $APP_DIR:$APP_DIR -v $CORPUS_DIR:$CORPUS_DIR -v $SCRATCH_DIR:$SCRATCH_DIR --env APP_DIR=$APP_DIR --env CORPUS_DIR=$CORPUS_DIR --env SCRATCH_DIR=$SCRATCH_DIR --env targetIndexName=$targetIndexName --env englishIndexName=$englishIndexName --env targetLanguage=$targetLanguage --env targetCorpusFileName=$targetCorpusFileName --env englishCorpusFileName=$englishCorpusFileName --env corpusFileLocation=$corpusFileLocation --env scratchFileLocation=$scratchFileLocation --env appFileLocation=$appFileLocation --env EVENT_EXTRACTOR_FILE_DIRECTORY=$EVENT_EXTRACTOR_FILE_DIRECTORY --entrypoint /home/tasksrunner/scripts/run_ir.sh gregorybrooks/tasks-runner:3.0.0

echo `date`
