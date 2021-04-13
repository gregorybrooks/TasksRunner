set -v
set -o allexport
source /mnt/scratch/BETTER_DRY_RUN/clear_ir.dry_run.AUTO.env
source ./run_settings.env
java -jar target/TasksRunner-1.0.4.jar
