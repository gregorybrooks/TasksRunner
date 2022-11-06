# TasksRunner
Given a test environment (analytic tasks file, English training corpus, Arabic (e.g.) target corpus, qrel file), 
this program converts the tasks into queries and executes them. 

To use this Docker image, first pull it from Docker Hub by running get_docker_image_from_dockerhub.sh.
This gets the Docker image into your machine's Docker repository.
You should never have to rebuild the Docker image, but if you want to, use rebuild_docker.sh.

