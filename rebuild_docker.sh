set -v
DOCKERHUB_USER=gregorybrooks
docker rmi $DOCKERHUB_USER/tasks-runner:5.2.0
docker build -t $DOCKERHUB_USER/tasks-runner:5.2.0 .
docker push gregorybrooks/tasks-runner:5.2.0
