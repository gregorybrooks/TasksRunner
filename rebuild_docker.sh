set -v
DOCKERHUB_USER=gregorybrooks
sudo docker rmi $DOCKERHUB_USER/tasks-runner:5.2.0
sudo docker build -t $DOCKERHUB_USER/tasks-runner:5.2.0 .
sudo docker push gregorybrooks/tasks-runner:5.2.0
