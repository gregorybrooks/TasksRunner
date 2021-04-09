set -v
docker rmi tasks-runner:latest
docker build -t tasks-runner:latest .
