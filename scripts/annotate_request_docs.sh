#!/bin/bash
set -ve

# Extract events from the top hits for each request and create the 
#   merged results.json file.

for f in ${EVENT_EXTRACTOR_FILES_DIRECTORY}/${MODE}.*.REQUESTHITS.json; do
    cp $f ${APP_DIR}/test_data.bp.json
    MODELS_BASE_DIR=${MODELS_BASE_DIR_FARSI} APP_DIR=${APP_DIR} BETTER_PATH=BASIC-B-1 ./run.sh
    cp ${APP_DIR}/results.json ${f}.results.json
done

echo `date`

