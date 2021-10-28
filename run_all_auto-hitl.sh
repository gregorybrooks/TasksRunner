#!/bin/bash
set -ve
set -o allexport

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
ENV_FILE="${SCRIPT_DIR}/eval_clear_ir.env.auto-hitl"
source $ENV_FILE

echo ${SCRATCH_DIR}
set +e
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
set -e
echo `date`

corpusFileLocation=${CORPUS_DIR}
scratchFileLocation=$SCRATCH_DIR/clear_ir
appFileLocation=${APP_DIR}
EVENT_EXTRACTOR_FILE_DIRECTORY=${SCRATCH_DIR}/clear_ir/eventextractorfiles

docker run --rm --env-file=$ENV_FILE -v /var/run/docker.sock:/var/run/docker.sock -v $APP_DIR:$APP_DIR -v $CORPUS_DIR:$CORPUS_DIR -v $SCRATCH_DIR:$SCRATCH_DIR --env corpusFileLocation=$corpusFileLocation --env scratchFileLocation=$scratchFileLocation --env appFileLocation=$appFileLocation --env EVENT_EXTRACTOR_FILE_DIRECTORY=$EVENT_EXTRACTOR_FILE_DIRECTORY --entrypoint /home/tasksrunner/scripts/run_ir.sh gregorybrooks/tasks-runner:2.1.0

# Save a copy of the results to distinguish it from the other modes

cp ${APP_DIR}/results.json ${APP_DIR}/results.auto-hitl.json
chmod a+rw  ${APP_DIR}/results.auto-hitl.json

echo `date`
