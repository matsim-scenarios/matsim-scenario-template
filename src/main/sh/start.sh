#!/bin/bash

name=$( echo "$*" | sed -e 's/ //g' -e 's/--//g')

export RUN_ARGS="$*"
export RUN_NAME="$name"

echo "Starting run kh-$name"
echo "$*"

qsub -V -N matsim-"$name" job.sh
