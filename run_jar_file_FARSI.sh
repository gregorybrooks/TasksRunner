set -v
set -o allexport
source /mnt/scratch/BETTER/BETTER_TEST_ENVIRONMENTS/BETTER_FARSI/clear_ir.farsi.env
source ./run_settings_FARSI.env
java -jar target/tasks-runner-1.0.4.jar
