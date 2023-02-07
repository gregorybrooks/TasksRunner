#!/bin/bash
set -ve

# Extract events from the example documents

cp ${EVENT_EXTRACTOR_FILES_DIRECTORY}/${SUBMISSION_ID}.EXAMPLES.json ${APP_DIR}/test_data.bp.json
cp ${APP_DIR}tasks.json ${APP_DIR}/tasks.json.SAVE
cp tasks-ie.json ${APP_DIR}/tasks.json

echo `date`
MODELS_BASE_DIR=${MODELS_BASE_DIR} APP_DIR=${APP_DIR} BETTER_PATH=BASIC-B GPUS=${GPUS} ./run_ie.sh

## move the output of the event extractor to where the clear-ir program expects it
mv ${APP_DIR}/results.json ${EVENT_EXTRACTOR_FILES_DIRECTORY}/${SUBMISSION_ID}.EXAMPLES.results.json


cp ${APP_DIR}/tasks.json.SAVE ${APP_DIR}tasks.json

echo `date`

