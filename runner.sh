#!/bin/bash
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#
#######################################################################
#
# Runs all the available test case scenarios defined in
# scenarioX.properties files
#


PROPERTIES_FILE="src/main/resources/generator.properties"

## 
# Benchmarks a given scenario
#
# params:
#   $1 - file containing the scenario properties
#   $2 - number of iterations
#   $3 - maven profile to use
#   $4 - unit test to run
bench_scenario() {
    # set the scenario rules
    cp $1 $PROPERTIES_FILE

    if [[ "$4" != "" ]]
    then
        test_to_run="BenchTest#$4"
    else
        test_to_run="BenchTest"
    fi

    # run the scenario $2 times
    for it in `seq 1 $2`
    do
        mvn -P$3 -Dtest=${test_to_run} -Dgenerator.rnd.seed=$it test
    done
}



#
# Check that a valid impl has been specified
#

if ! [[ "$1" = *[[:digit:]]* ]]
then
    echo "Error"
    echo "You must specify a number of iterations"
    exit
fi

if [ "$2" != "bval" ] && [ "$2" != "hv-5" ] && [ "$2" != "hv-6-stable" ] && [ "$2" != "hv-6-snapshot" ]
then
    echo "Error"
    echo "You must specify a profile, either bval, hv-5, hv-6-stable or hv-6-snapshot"
    exit
fi

#
# Runs all the scenario?.properties cases present in the current dir
#

tests_run=0
shopt -s nullglob

for test_props in scenario?.properties
do
    bench_scenario $test_props $1 $2 $3
    let "tests_run += 1"
done

if [ $tests_run -eq 0 ]
then
    echo "No scenarios available to run."
    echo
    echo "You can create a scenario by placing a scenarioX.properties file in the "
    echo "current directory."
else
    echo "Benchmarked $tests_run scenarios"
fi
