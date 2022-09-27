set -v
DOCKERHUB_USER=gregorybrooks
docker rmi $DOCKERHUB_USER/tasks-runner:6.0.0
docker build -t $DOCKERHUB_USER/tasks-runner:6.0.0 .
docker push gregorybrooks/tasks-runner:6.0.0
