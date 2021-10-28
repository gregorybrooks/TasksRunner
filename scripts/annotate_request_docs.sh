#!/bin/bash
set -ve

# Extract events from the top hits for each request and create the 
#   merged results.json file.

cp ${APP_DIR}tasks.json ${APP_DIR}/tasks.json.SAVE
cp ./tasks-ie.json ${APP_DIR}/tasks.json

for f in ${EVENT_EXTRACTOR_FILES_DIRECTORY}/${MODE}.*.REQUESTHITS.json; do
    cp $f ${APP_DIR}/test_data.bp.json
    MODELS_BASE_DIR=${MODELS_BASE_DIR_FARSI} APP_DIR=${APP_DIR} BETTER_PATH=BASIC-B-1 ./run.sh
    cp ${APP_DIR}/results.json ${f}.results.json
done

# remove results.json left over from event extractor, so later code
# can write to that name:
set +e
rm -f ${APP_DIR}/results.json
set -e

cp ${APP_DIR}/tasks.json.SAVE ${APP_DIR}tasks.json

echo `date`

