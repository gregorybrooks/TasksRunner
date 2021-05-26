set -v
set -o allexport
source /mnt/scratch/BETTER/BETTER_TEST_ENVIRONMENTS/BETTER_MITRE_EVAL_JAN_2021/clear_ir.env
source ./run_settings_MITRE_EVAL_JAN_2021.env
java -jar target/tasks-runner-2.0.1.jar
