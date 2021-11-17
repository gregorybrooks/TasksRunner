set -v
DOCKERHUB_USER=gregorybrooks
docker rmi $DOCKERHUB_USER/tasks-runner:3.0.1
docker build -t $DOCKERHUB_USER/tasks-runner:3.0.1 .
