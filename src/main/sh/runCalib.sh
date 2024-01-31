#!/bin/bash --login
#SBATCH --time=200:00:00
#SBATCH --partition=smp
#SBATCH --output=logfile_%x-%j.log
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
