set -v
set -o allexport
source /mnt/scratch/BETTER/BETTER_TEST_ENVIRONMENTS/BETTER_FARSI/clear_ir.farsi.env
source ./run_settings_FARSI.env
java -jar target/tasks-runner-2.0.1.jar
