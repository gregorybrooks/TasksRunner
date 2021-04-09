set -v
docker rmi tasks-runner:latest
docker build -t tasks-runner:latest .
docker tag tasks-runner:latest gregorybrooks/tasks-runner:latest
