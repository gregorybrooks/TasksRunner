#!/bin/bash
set -ve

# Extract events from the example documents

#SINCE NO ONE IS USING THE EVENTS, JUST COPY THE FILE TO THE EXPECTED NAME:
cp ${EVENT_EXTRACTOR_FILES_DIRECTORY}/${SUBMISSION_ID}.EXAMPLES.json ${EVENT_EXTRACTOR_FILES_DIRECTORY}/${SUBMISSION_ID}.EXAMPLES.json.results.json

#THIS IS WHAT IT WAS WHEN WE WERE CALLING THE EVENT ANNOTATOR:

#cp ${EVENT_EXTRACTOR_FILES_DIRECTORY}/${SUBMISSION_ID}.EXAMPLES.json ${APP_DIR}/test_data.bp.json
#cp ${APP_DIR}tasks.json ${APP_DIR}/tasks.json.SAVE
#cp tasks-ie.json ${APP_DIR}/tasks.json

#echo `date`
#MODELS_BASE_DIR=${MODELS_BASE_DIR_ENGLISH} APP_DIR=${APP_DIR} BETTER_PATH=BASIC-E ./run.sh.FARSI

## move the output of the event extractor to where the clear-ir program expects it
#mv ${APP_DIR}/results.json ${EVENT_EXTRACTOR_FILES_DIRECTORY}/${SUBMISSION_ID}.EXAMPLES.results.json


#cp ${APP_DIR}/tasks.json.SAVE ${APP_DIR}tasks.json

#echo `date`

