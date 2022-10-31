#!/bin/bash
set -euxo pipefail

# The script runs the pipeline for the user supplied `APP_DIR` and
# `BETTER_PATH` environment variables.

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
ROOT_DIR="$SCRIPT_DIR"

DOCKER_IMAGE_PREFIX="${DOCKER_IMAGE_PREFIX:-better}"
DOCKER_IMAGE_TAG="${DOCKER_IMAGE_TAG:-20221010-UMASS}"

# Input data is stored in this directory. Output also will be written here.
# Make sure the user specified below has read/write access to this directory.
APP_DIR="${APP_DIR:-none}"
# See the available paths in doc/development.md.
BETTER_PATH="${BETTER_PATH:-none}"

# TODO: temporary test -- this var should be removed
#       ideally, any component should look at a BetterDocument's lang
#       field and use that, rather than depending on the calling env.
TEST_LANG="${TEST_LANG:-en}"

ENV_FILE="${ENV_FILE:-${ROOT_DIR}/variables.env}"

# Stage at which the pipeline starts.
START_STAGE="${START_STAGE:-ingest}"
# The stage after which the pipeline stops.
END_STAGE="${END_STAGE:-postprocess}"

# Set to true to create an environment similar to the MITRE evaluation
# environment.
DISABLE_NETWORK="${DISABLE_NETWORK:-true}"

# If set, this path will be mounted inside all containers. Newly trained models
# will be saved here and models in this directory will be used for decoding as
# well.
MODELS_BASE_DIR="${MODELS_BASE_DIR:-false}"

# User's home directory inside docker. Code and data will be stored here by
# default.
ISI_HOME=/home/isi

# Flags passed to Docker.
ADDITIONAL_FLAGS="--rm --env-file=${ENV_FILE} --env BETTER_PATH=${BETTER_PATH}"
ADDITIONAL_FLAGS+=" --env TEST_LANG=${TEST_LANG}"
ADDITIONAL_FLAGS+=" --env ISI_HOME=${ISI_HOME}"
ADDITIONAL_FLAGS+=" --env PYTHONIOENCODING=utf8"
ADDITIONAL_FLAGS+=" -v ${APP_DIR}:${ISI_HOME}/app"
if [ "$MODELS_BASE_DIR" != "false" ]; then
    ADDITIONAL_FLAGS+=" -v ${MODELS_BASE_DIR}:${ISI_HOME}/models"
fi
if [ "$DISABLE_NETWORK" = "true" ]; then
    ADDITIONAL_FLAGS+=" --network none"
fi
ADDITIONAL_FLAGS+=" --gpus 1"

#ADDITIONAL_FLAGS+=" --user $(id -u):$(id -g) "
# User and group ID of the Mitre evaluation environment.
#ADDITIONAL_FLAGS+=" --user 1002:1002"

# The list of stages is in doc/development.md.
CURRENT_STAGE=""
# Flag indicating whether $START_STAGE stage has started.
STARTED="false"


echo "Here we go: running the path ${BETTER_PATH}."
date
SECONDS=0

if [ "$BETTER_PATH" = "BASIC-A" ] || [ "$BETTER_PATH" = "BASIC-B" ] || [ "$BETTER_PATH" = "BASIC-C" ]; then
      if [ "$START_STAGE" = "ingest" ]; then
        echo "---------------- 1. INGEST ----------------"
        CURRENT_STAGE="ingest"
        STARTED="true"
        time sudo docker run --env CURRENT_STAGE=$CURRENT_STAGE $ADDITIONAL_FLAGS \
             --entrypoint $ISI_HOME/scripts/ingest.sh \
             ${DOCKER_IMAGE_PREFIX}-json-converter:$DOCKER_IMAGE_TAG
        if [ "$END_STAGE" = "$CURRENT_STAGE" ]; then exit; fi
    fi

    if [ "$START_STAGE" = "augment-train-data-with-pre-processing" ] ||
           [ $STARTED = "true" ]; then
        echo "---------------- 2. AUGMENT TRAIN DATA WITH PRE-PROCESSING ----------------"
        CURRENT_STAGE="augment-train-data-with-pre-processing"
        STARTED="true"
        time sudo docker run --env CURRENT_STAGE=$CURRENT_STAGE $ADDITIONAL_FLAGS \
             --entrypoint $ISI_HOME/scripts/augment-train.sh \
             ${DOCKER_IMAGE_PREFIX}-json-converter:$DOCKER_IMAGE_TAG
        if [ "$END_STAGE" = "$CURRENT_STAGE" ]; then exit; fi
    fi

    if [ "$START_STAGE" = "prepare-test-text" ] || [ $STARTED = "true" ]; then
        echo "---------------- 3. PREPARE TEST TEXT ----------------"
        CURRENT_STAGE="prepare-test-text"
        STARTED="true"
        time sudo docker run --env CURRENT_STAGE=$CURRENT_STAGE $ADDITIONAL_FLAGS \
             --entrypoint $ISI_HOME/scripts/prepare-text.sh \
             ${DOCKER_IMAGE_PREFIX}-multilingual-text-prep:$DOCKER_IMAGE_TAG
        if [ "$END_STAGE" = "$CURRENT_STAGE" ]; then exit; fi
    fi

    if [ "$START_STAGE" = "detect-triggers" ] || [ $STARTED = "true" ]; then
        echo "---------------- 4. DETECT TRIGGERS  ----------------"
        CURRENT_STAGE="detect-triggers"
        STARTED="true"
        time sudo docker run --env CURRENT_STAGE=$CURRENT_STAGE $ADDITIONAL_FLAGS \
             --entrypoint $ISI_HOME/scripts/detect-basic-triggers.sh \
             ${DOCKER_IMAGE_PREFIX}-bert-bio:$DOCKER_IMAGE_TAG
        if [ "$END_STAGE" = "$CURRENT_STAGE" ]; then exit; fi
    fi

    if [ "$START_STAGE" = "extract-event-event-relations" ] || [ $STARTED = "true" ]; then
        echo "---------------- 5. EXTRACT EVENT-EVENT RELATIONS  ----------------"
        CURRENT_STAGE="extract-event-event-relations"
        STARTED="true"
        time sudo docker run --env CURRENT_STAGE=$CURRENT_STAGE $ADDITIONAL_FLAGS \
             --entrypoint $ISI_HOME/scripts/extract-event-event-relations.sh \
             ${DOCKER_IMAGE_PREFIX}-argument-attachment:$DOCKER_IMAGE_TAG
        if [ "$END_STAGE" = "$CURRENT_STAGE" ]; then exit; fi
    fi

    if [ "$START_STAGE" = "attach-arguments" ] || [ $STARTED = "true" ]; then
        echo "---------------- 6. ATTACH ARGUMENTS  ----------------"
        CURRENT_STAGE="attach-arguments"
        STARTED="true"
        time sudo docker run --env CURRENT_STAGE=$CURRENT_STAGE $ADDITIONAL_FLAGS \
             --entrypoint $ISI_HOME/scripts/basic-attach-arguments.sh \
             ${DOCKER_IMAGE_PREFIX}-argument-attachment:$DOCKER_IMAGE_TAG
        if [ "$END_STAGE" = "$CURRENT_STAGE" ]; then exit; fi
    fi

    if [ "$START_STAGE" = "postprocess" ] || [ $STARTED = "true" ]; then
        echo "---------------- 7. POSTPROCESS ----------------"
        CURRENT_STAGE="postprocess"
        STARTED="true"
        time sudo docker run --env CURRENT_STAGE=$CURRENT_STAGE $ADDITIONAL_FLAGS \
             --entrypoint $ISI_HOME/scripts/postprocess.sh \
             ${DOCKER_IMAGE_PREFIX}-event-extractor-boston:$DOCKER_IMAGE_TAG
        if [ "$END_STAGE" = "$CURRENT_STAGE" ]; then exit; fi
    fi

fi

date
DURATION=$SECONDS
echo "Total running time: $(($DURATION / 60)) mins and $(($DURATION % 60)) secs."
