#!/bin/bash

#SBATCH --time=1:00:00
#SBATCH --cpus-per-task 1 

logPath=$1
if [ -z "$SLURM_ARRAY_TASK_ID" ]; then
  individual=$2
else
  individual=$SLURM_ARRAY_TASK_ID
fi

echo "Individual " $individual

if [ `whoami` == "martin" ]; then
  export PATH=/work/R-3.1.1/bin:$PATH
  export R_LIBS=/work/R-3.1.1/library/
else
  module add apps/R/3.1.2
  export R_LIBS=/home/m/mllofriualon/R-library/
fi

/usr/bin/java -cp "./platform/src/:./multiscalemodel/src/:./bin/:./deps/*:./deps/j3dport/*" edu.usf.experiment.RunIndividualByNumber $logPath $individual

