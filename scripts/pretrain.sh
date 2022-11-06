#!/bin/bash
set -ve

echo `date`

#SIZE=$1

#mkdir -p ${MODELS_BASE_DIR_ENGLISH}
mkdir -p ${MODELS_BASE_DIR}
#chmod a+rw ${MODELS_BASE_DIR_ENGLISH}
chmod a+rw ${MODELS_BASE_DIR}

cp ${APP_DIR}/tasks.json ${SCRATCH_DIR}/tasks.json.SAVE
cp isi_hidden_training_tasks.json ${APP_DIR}/tasks.json
cp ${APP_DIR}/ir-tasks.json ${APP_DIR}/train_ir_data.json

#if [ $SIZE = "SMALL" ]; then
#    cp variables.env.FARSI.SMALL variables.env.FARSI
#fi

# pre-training for English is not needed
#cp english_dummy_test_data.bp.json ${APP_DIR}/test_data.bp.json
#MODELS_BASE_DIR=${MODELS_BASE_DIR_ENGLISH} APP_DIR=${APP_DIR} BETTER_PATH=BASIC-E ./run.sh

set +e
rm ${SCRATCH_DIR}/test_data.bp.json.SAVE
cp ${APP_DIR}/test_data.bp.json ${SCRATCH_DIR}/test_data.bp.json.SAVE
set -e
cp test_data.bp.json ${APP_DIR}/test_data.bp.json

MODELS_BASE_DIR=${MODELS_BASE_DIR} APP_DIR=${APP_DIR} BETTER_PATH=BASIC-A ./run_ie.sh
echo `date`

# Clean up
cp ${SCRATCH_DIR}/tasks.json.SAVE ${APP_DIR}/tasks.json
set +e
cp ${SCRATCH_DIR}/test_data.bp.json.SAVE ${APP_DIR}/test_data.bp.json
set -e
rm ${APP_DIR}/train_ir_data.json
chmod a+rw ${APP_DIR}/results.json
