#!/bin/bash --login
#SBATCH --time=216:00:00
#SBATCH --output=./logfile/logfile_$SLURM_JOB_NAME.log
#SBATCH --nodes=1
#SBATCH --ntasks=1
#SBATCH --cpus-per-task=12
#SBATCH --mem=48G
#SBATCH --job-name=calib-scenario

date
hostname

command="python -u calibrate.py"

echo ""
echo "command is $command"
echo ""

source env/bin/activate

module add java/17
java -version

$command
