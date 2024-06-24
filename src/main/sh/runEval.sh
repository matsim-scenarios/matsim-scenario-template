#!/bin/bash --login
#SBATCH --time=100:00:00
#SBATCH --output=logfile_%x-%j.log
#SBATCH --partition=smp
#SBATCH --nodes=1
#SBATCH --ntasks=1
#SBATCH --cpus-per-task=16
#SBATCH --mem=32G
#SBATCH --job-name=eval-scenario
#SBATCH --mail-type=END,FAIL

date
hostname

echo "Task Id: $SLURM_ARRAY_TASK_ID"
echo "Task Count: $SLURM_ARRAY_TASK_COUNT"

JAR=...
CONFIG="../input/vx.y/config.xml"
ARGS="--3pct --iterations 200 --yaml params/run10.yaml --config:plans.inputPlansFile=<path>"
JVM_ARGS="-Xmx30G -Xms30G -XX:+AlwaysPreTouch -XX:+UseParallelGC"
RUNS=12

# Start job as array with --array=0-5
idx=$SLURM_ARRAY_TASK_ID
total=$SLURM_ARRAY_TASK_COUNT

source env/bin/activate

module add java/21
java -version

# Runs simulation multiple times (simultaneously) with different random seeds

python -u -m matsim.calibration run-simulations\
 --jar $JAR --config $CONFIG --args "$ARGS" --jvm-args "$JVM_ARGS" --runs $RUNS\
 --worker-id $idx --workers $total
