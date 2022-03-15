set -v
set -o allexport
source /mnt/scratch/BETTER/BETTER_TEST_ENVIRONMENTS/BETTER_MITRE_FARSI/clear_ir.env
source ./run_settings_MITRE_FARSI.env
java -jar target/tasks-runner-3.1.1.jar
