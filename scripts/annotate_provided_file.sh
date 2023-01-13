#!/bin/bash
set -ve

# Extract events from the provided test_data.bp.json file (target language documents)
echo `date`

MODELS_BASE_DIR=${MODELS_BASE_DIR} APP_DIR=${APP_DIR} BETTER_PATH=BASIC-B GPUS=${GPUS} ./run_ie.sh

echo `date`
