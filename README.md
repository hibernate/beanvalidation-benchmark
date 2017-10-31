# Bean Validation benchmark

## Introduction

This work is derived from the [Apache BVal benchmark](http://svn.apache.org/repos/asf/bval/sandbox/jsr303-impl-bench/) described in [this article from 2010](http://carinae.net/2010/06/benchmarking-hibernate-validator-and-apache-beanvalidation-the-two-jsr-303-implementations/).

Note that the new benchmarks has a slightly different behavior than what is described in the article.

It is licensed under the Apache 2 license.

## Implementations available

| Implementation      | Version          | Profile name      |
|---------------------|------------------|-------------------|
| Apache BVal         | `1.1.2`          | `bval`            |
| Hibernate Validator | `5.4.2.Final`    | `hv-5.4`          |
| Hibernate Validator | `6.0.4.Final`    | `hv-6.0-stable`   |
| Hibernate Validator | `6.0.5-SNAPSHOT` | `hv-6.0-snapshot` |

## Running a benchmark

### Creating the scenario

To execute a benchmark, a scenario is required.

A scenario is a property file located at the root directory and called `scenario.properties`.

A default scenario file can be found at `src/main/resources/generator.default.properties`.

### Generating the beans

To generate the beans, run:

```
./generate-beans.sh
```

Note: the beans are not generated automatically anymore to allow running a benchmark several
times with the same beans.

### Running the benchmark

Now that you have created your scenario file, the `runner.sh` script at the root directory
is your entry point to run the benchmark.

Let's say you want to run a full benchmark for Hibernate Validator `5.4.2.Final`:

```
./runner.sh 1 hv-5.4
```

The above command will run the benchmark once for the `hv-5.4` profile (see the available
profile names in the table above).


Now, let's run it five times:

```
./runner.sh 5 hv-5.4
```

You can also run a particular test with the following command:

```
./runner.sh 1 hv-5.4 checkRawValidationSpeed
```

The following tests are available:

| Test name                         | Main goal                                |
|-----------------------------------|------------------------------------------|
| checkRawValidationSpeed           | Evaluate validation only                 |
| checkRawParsingSpeed              | Evaluate the metadata building phase     |
| checkFirstParseAndValidateSpeed   | Real life scenario                       |
| checkMultithreadedValidationSpeed | Real life scenario with multiple threads |

## Future

For now, we kept the overall behavior of the benchmark, it would be nice to migrate it to JMH.
