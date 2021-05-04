set -v
set -o allexport
source /mnt/scratch/BETTER/BETTER_TEST_ENVIRONMENTS/BETTER_DRY_RUN/clear_ir.dry_run.env
source ./run_settings_DRY_RUN.env
java -jar target/tasks-runner-1.0.4.jar
