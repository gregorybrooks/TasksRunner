set -v
DOCKERHUB_USER=gregorybrooks
docker rmi $DOCKERHUB_USER/tasks-runner
docker build -t $DOCKERHUB_USER/tasks-runner .
