#!/bin/bash
set -ve

SIZE=$1

mkdir -p ${MODELS_BASE_DIR_ENGLISH}
mkdir -p ${MODELS_BASE_DIR_FARSI}
chmod a+rw ${MODELS_BASE_DIR_ENGLISH}
chmod a+rw ${MODELS_BASE_DIR_FARSI}

cp ${APP_DIR}/tasks.json ${SCRATCH_DIR}/tasks.json.SAVE
cp isi_hidden_training_tasks.json ${APP_DIR}/tasks.json
cp ${APP_DIR}/ir-tasks.json ${APP_DIR}/train_ir_data.json

echo `date`

if [ $SIZE = "SMALL" ]; then
    cp variables.env.FARSI.SMALL variables.env.FARSI
fi

# pre-training for English is not needed
#cp english_dummy_test_data.bp.json ${APP_DIR}/test_data.bp.json
#MODELS_BASE_DIR=${MODELS_BASE_DIR_ENGLISH} APP_DIR=${APP_DIR} BETTER_PATH=BASIC-E ./run.sh

echo `date`

cp farsi_dummy_test_data.bp.json ${APP_DIR}/test_data.bp.json
MODELS_BASE_DIR=${MODELS_BASE_DIR_FARSI} APP_DIR=${APP_DIR} BETTER_PATH=BASIC-A ./run.sh

echo `date`

# Clean up
cp ${SCRATCH_DIR}/tasks.json.SAVE ${APP_DIR}/tasks.json
rm ${APP_DIR}/train_ir_data.json
