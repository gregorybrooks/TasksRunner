set -v
cp /home/glbrooks/BETTER/tools/TasksRunner/target/TasksRunner-1.0.4.jar target
docker rmi tasks-runner:1.0.0
docker build -t tasks-runner:1.0.0 .
