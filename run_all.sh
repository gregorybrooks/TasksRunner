#!/bin/bash
set -ve
set -o allexport

echo `date`

docker pull gregorybrooks/tasks-runner:6.0.0

docker pull gregorybrooks/better-query-builder-task-noun-phrases:3.0.0

docker pull gregorybrooks/better-query-builder-1:4.0.0

docker pull gregorybrooks/better-query-builder-existing-queryfile

docker pull gregorybrooks/better-reranker-z1:3.2.1
docker pull gregorybrooks/better-reranker-s1:1.0.0
docker pull gregorybrooks/better-reranker-z3:1.0.0


SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

# The following file is also passed to the Docker container, so it has to follow
# the rules for Docker env files--no nested variables
ENV_FILE="${SCRIPT_DIR}/eval.env"
source $ENV_FILE

scratchFileLocation=${scratchFileLocation}clear_ir/
mkdir -p $scratchFileLocation
mkdir -p $scratchFileLocation/indexes/galago
mkdir -p $scratchFileLocation/indexes/neural
mkdir -p $scratchFileLocation/evaluationfiles  
mkdir -p $scratchFileLocation/eventextractorfiles  
mkdir -p $scratchFileLocation/galago_job_dir  
mkdir -p $scratchFileLocation/logfiles  
mkdir -p $scratchFileLocation/qrelfiles  
mkdir -p $scratchFileLocation/queryfiles  
mkdir -p $scratchFileLocation/runfiles  
mkdir -p $scratchFileLocation/taskcorpusfiles  
mkdir -p $scratchFileLocation/tmp
set +e
chmod -R a+rw $scratchFileLocation
set -e

set +e
rm -f ${appFileLocation}/results.json
set -e

MODELS_BASE_DIR_ENGLISH=
MODELS_BASE_DIR=${scratchFileLocation}/isi_models_basic_b

EVENT_EXTRACTOR_FILE_DIRECTORY=${scratchFileLocation}/eventextractorfiles

docker run --rm --env-file=$ENV_FILE -v /var/run/docker.sock:/var/run/docker.sock -v $appFileLocation:$appFileLocation -v $corpusFileLocation:$corpusFileLocation -v $scratchFileLocation:$scratchFileLocation --env MODELS_BASE_DIR_ENGLISH=$MODELS_BASE_DIR_ENGLISH --env MODELS_BASE_DIR=$MODELS_BASE_DIR --env corpusFileLocation=$corpusFileLocation --env scratchFileLocation=$scratchFileLocation --env appFileLocation=$appFileLocation --env EVENT_EXTRACTOR_FILE_DIRECTORY=$EVENT_EXTRACTOR_FILE_DIRECTORY --env targetCorpusFileName=$targetCorpusFileName --env englishCorpusFileName=$englishCorpusFileName --entrypoint /home/tasksrunner/scripts/run_ir.sh gregorybrooks/tasks-runner:6.0.0

echo `date`
