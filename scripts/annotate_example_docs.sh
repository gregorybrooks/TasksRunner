#!/bin/bash
set -ve

# Extract events from the example documents

cp ${EVENT_EXTRACTOR_FILES_DIRECTORY}/${MODE}.EXAMPLES.json ${APP_DIR}/test_data.bp.json

echo `date`
MODELS_BASE_DIR=${MODELS_BASE_DIR_ENGLISH} APP_DIR=${APP_DIR} BETTER_PATH=BASIC-E-1 ./run.sh

# move the output of the event extractor to where the clear-ir program expects it
mv ${APP_DIR}/results.json ${EVENT_EXTRACTOR_FILES_DIRECTORY}/${MODE}.EXAMPLES.results.json

echo `date`

