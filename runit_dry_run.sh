set -v
set -o allexport
source /mnt/scratch/BETTER_DRY_RUN/clear_ir.dry_run.AUTO.env
java -jar target/TasksRunner-1.0.1.jar
source /mnt/scratch/BETTER_DRY_RUN/clear_ir.dry_run.AUTO-HITL.env
java -jar target/TasksRunner-1.0.1.jar
source /mnt/scratch/BETTER_DRY_RUN/clear_ir.dry_run.HITL.env
java -jar target/TasksRunner-1.0.1.jar
