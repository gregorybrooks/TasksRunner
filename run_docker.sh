#!/bin/bash
set +v

die () {
    echo >&2 "$@"
    exit 1
}

[ "$#" -eq 1 ] || die "1 argument required, $# provided"

set -o allexport
set -v
ENV_FILE=/mnt/scratch/BETTER/BETTER_TEST_ENVIRONMENTS/BETTER_DRY_RUN/clear_ir.dry_run.$1.env
source $ENV_FILE

docker run --rm --env-file=$ENV_FILE -v /var/run/docker.sock:/var/run/docker.sock -v $appFileLocation:$appFileLocation -v $corpusFileLocation:$corpusFileLocation -v $scratchFileLocation:$scratchFileLocation tasks-runner:1.0.0 bash -c "./runit_dry_run_DOCKER.sh ${1}"
