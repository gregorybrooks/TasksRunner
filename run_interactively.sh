#!/bin/bash
set -ve
set -o allexport

APP_DIR=/mnt/scratch/BETTER/BETTER_TEST_ENVIRONMENTS/BETTER_MITRE_EVAL_JAN_2021/app
CORPUS_DIR=/mnt/scratch/BETTER/BETTER_TEST_ENVIRONMENTS/BETTER_MITRE_EVAL_JAN_2021/corpus
SCRATCH_DIR=/mnt/scratch/BETTER/BETTER_TEST_ENVIRONMENTS/BETTER_MITRE_EVAL_JAN_2021/scratch

targetCorpusFileName=arabic/arabic-corpus.jl
englishCorpusFileName=english/english-training-corpus.jl

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
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

docker run -it --rm --env-file=$ENV_FILE -v /var/run/docker.sock:/var/run/docker.sock -v $APP_DIR:$APP_DIR -v $CORPUS_DIR:$CORPUS_DIR -v $SCRATCH_DIR:$SCRATCH_DIR --env corpusFileLocation=$corpusFileLocation --env scratchFileLocation=$scratchFileLocation --env appFileLocation=$appFileLocation --env EVENT_EXTRACTOR_FILE_DIRECTORY=$EVENT_EXTRACTOR_FILE_DIRECTORY --env targetCorpusFileName=$targetCorpusFileName --env englishCorpusFileName=$englishCorpusFileName gregorybrooks/tasks-runner:2.1.0 bash

echo `date`
