# TasksRunner
Given a test environment (tasks file, English training corpus, Arabic (e.g.) target corpus, qrel file), 
this program converts the tasks into Galago queries and executes them. 

To use this Docker image, first pull it from Docker Hub by running get_docker_image_from_dockerhub.sh.
This gets the Docker image into your machine's Docker repository.
You should never have to rebuild the Docker image, but if you want to use rebuild_docker.sh.

If you haven't set up a DRY RUN test environment yet on your machine, get the zip file from Greg 
and install it. This creates a directory tree on your machine that has the right structure for 
TasksRunner, and includes the corpus and some other required files.

Edit run_docker_DRY_RUN.sh to point to that environment. Leave DOCKERHUB_USER set to gregorybrooks
since the Docker image that you pulled from Docker Hub and that you will be running is 
called gregorybrooks/tasks-runner.

Edit run_settings_DRY_RUN.env to tell TasksRunner what mode to run in (e.g. AUTO), what processing
steps to run, and which Docker images to use to formulate the task-level and request-level queries.

Then run run_docker_DRY_RUN.sh.
