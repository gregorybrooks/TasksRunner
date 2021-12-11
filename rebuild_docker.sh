set -v
DOCKERHUB_USER=gregorybrooks
docker rmi $DOCKERHUB_USER/tasks-runner:3.1.0-TEST
docker build -t $DOCKERHUB_USER/tasks-runner:3.1.0-TEST .
docker push gregorybrooks/tasks-runner:3.1.0-TEST
