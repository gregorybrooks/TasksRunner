set -v
DOCKERHUB_USER=gregorybrooks
docker rmi $DOCKERHUB_USER/tasks-runner:2.1.0
docker build -t $DOCKERHUB_USER/tasks-runner:2.1.0 .
