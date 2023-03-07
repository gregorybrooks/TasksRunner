#!/bin/bash
set -ve

# Extract events from the relevant documents

cp ${APP_DIR}tasks.json ${APP_DIR}/tasks.json.SAVE
cp tasks-ie.json ${APP_DIR}/tasks.json

echo `date`

set +e
for f in ${EVENT_EXTRACTOR_FILES_DIRECTORY}/${SUBMISSION_ID}.*.REQUESTHITS.json; do
    cp $f ${APP_DIR}/test_data.bp.json
    MODELS_BASE_DIR=${MODELS_BASE_DIR} APP_DIR=${APP_DIR} BETTER_PATH=BASIC-B GPUS=${GPUS} ./run_ie.sh
## copy the output of the event extractor to where the clear-ir program expects it
    cp ${APP_DIR}/results.json ${f}.results.json
done
set -e

cp ${APP_DIR}/tasks.json.SAVE ${APP_DIR}tasks.json

echo `date`

