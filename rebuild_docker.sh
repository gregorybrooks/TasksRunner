set -v
docker rmi tasks-runner:1.0.0
docker build -t tasks-runner:1.0.0 .
