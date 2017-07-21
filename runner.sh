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
# The JSR303 impl to test is passed as first param.
# Possible values:
#   - Apache
#   - Hibernate
#


PROPERTIES_FILE="src/main/resources/generator.properties"

# Will iterate 20 times for each scenario, using seeds from 1 to 20
SEED_LOW=1
SEED_HIGH=20

## 
# Benchmarks a given scenario
#
# params:
#   $1 - file containing the scenario properties
#   $2 - unit test to run
bench_scenario() {
    # set the scenario rules
    cp $1 $PROPERTIES_FILE
    mvn clean

    # run the scenario ($SEED_HIGH - $SEED_LOW) times
    for it in `seq $SEED_LOW $SEED_HIGH`
    do
        mvn -Dtest=$2 -Dgenerator.rnd.seed=$it -DargLine="-Dtester.repetitions=50" test
    done

    # copy the results back to the current dir
    cp target/$2"-results.txt" $1"-"$2".txt"
}



#
# Check that a valid impl has been specified
#

if [ "$1" != "Apache" ] && [ "$1" != "Hibernate" ];
then
    echo "Error"
    echo "You must specify either Apache or Hibernate to test"
    exit
fi

#
# Runs all the scenario?.properties cases present in the current dir
#

tests_run=0
shopt -s nullglob

for test_props in scenario?.properties
do
    bench_scenario $test_props $1"Test"
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
