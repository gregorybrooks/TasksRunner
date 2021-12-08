set -v
DOCKERHUB_USER=gregorybrooks
docker rmi $DOCKERHUB_USER/tasks-runner:3.0.3
docker build -t $DOCKERHUB_USER/tasks-runner:3.0.3 .
