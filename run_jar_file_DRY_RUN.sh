set -v
set -o allexport
source /mnt/scratch/BETTER/BETTER_TEST_ENVIRONMENTS/BETTER_DRY_RUN/clear_ir.env
source ./run_settings_DRY_RUN.env
java -jar target/tasks-runner-2.1.0.jar
