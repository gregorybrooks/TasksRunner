set +v

die () {
    echo >&2 "$@"
    exit 1
}

[ "$#" -eq 1 ] || die "1 argument required, $# provided"
set -v
set -o allexport
ENV_FILE=/mnt/scratch/BETTER_DRY_RUN/clear_ir.dry_run.$1.env
source $ENV_FILE

docker run -it --rm --env-file=$ENV_FILE -v /var/run/docker.sock:/var/run/docker.sock -v $appFileLocation:$appFileLocation -v $corpusFileLocation:$corpusFileLocation -v $scratchFileLocation:$scratchFileLocation tasks-runner:1.0.0 bash
