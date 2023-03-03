set -v
DOCKERHUB_USER=gregorybrooks
sudo docker rmi $DOCKERHUB_USER/tasks-runner:DD2023Dev
sudo docker build -t $DOCKERHUB_USER/tasks-runner:DD2023Dev .
sudo docker push gregorybrooks/tasks-runner:DD2023Dev
