set -v
docker rmi gregorybrooks/tasks-runner
docker build -t gregorybrooks/tasks-runner .
