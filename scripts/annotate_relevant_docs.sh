#!/bin/bash
set -ve

# Extract events from the relevant documents

cp ${APP_DIR}tasks.json ${APP_DIR}/tasks.json.SAVE
cp tasks-ie.json ${APP_DIR}/tasks.json

cp ${EVENT_EXTRACTOR_FILES_DIRECTORY}/${SUBMISSION_ID}.RELEVANT.json ${APP_DIR}/test_data.bp.json

echo `date`
MODELS_BASE_DIR=${MODELS_BASE_DIR} APP_DIR=${APP_DIR} BETTER_PATH=BASIC-B GPUS=${GPUS} ./run_ie.sh

## move the output of the event extractor to where the clear-ir program expects it
mv ${APP_DIR}/results.json ${EVENT_EXTRACTOR_FILES_DIRECTORY}/${SUBMISSION_ID}.RELEVANT.results.json

cp ${APP_DIR}/tasks.json.SAVE ${APP_DIR}tasks.json

echo `date`

set -e