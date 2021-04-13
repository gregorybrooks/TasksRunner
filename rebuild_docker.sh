set -v
docker rmi tasks-runner
docker build -t tasks-runner .
docker tag tasks-runner gregorybrooks/tasks-runner
