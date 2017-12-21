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
| Hibernate Validator | `6.0.7.Final`    | `hv-6.0-stable`   |
| Hibernate Validator | `6.0.8-SNAPSHOT` | `hv-6.0-snapshot` |

## Running a benchmark

### Creating the scenario

To execute a benchmark, a scenario is required.

A scenario is a property file located at the root directory and called `scenario.properties`.

A default scenario file can be found at `src/main/resources/generator.default.properties`.

### Generating the beans

To generate the beans, starting in the root run:

```bash
pushd bean-generator-bv-1.1
mvn clean install
popd
```
`install` goal must be used here so that jar with generated beans get to local
m2 repository and can be re-used in further builds. This is needed to be able
to run multiple benchmarks with the same set of generated beans.
 
### Running the benchmark

Now that you have created your generated beans, corresponding JMH benchmarks
jars can be created for available Bean Validation implementations.

Let's say you want to compare full benchmark for current stable and snapshot versions
of Hibernate Validator. First jars with JMH benchmarks packaged with corresponding
implementation versions should be prepared. Assuming root is a current location:

```bash
pushd jmh-benchmarks
mvn clean package -Phv-6.0-snapshot
mvn package -Phv-6.0-stable
```

Now having jars packaged benchmarks can be run as follows:

```bash
java -jar target/bv-benchmarks-${snapshot-version}.jar
java -jar target/bv-benchmarks-${stable-version}.jar
popd
```

The following tests are available:

| Test name                         | Main goal                                |
|-----------------------------------|------------------------------------------|
| RawValidationSpeedBenchmark       | Evaluate validation only                 |
| ParsingBeansSpeedBenchmark        | Evaluate the metadata building phase     |

It is also possible to run single benchmark if needed. To do so
pass a benchmark test name from above table as parameter: 

```bash
java -jar target/bv-benchmarks-${impl-version}.jar RawValidationSpeedBenchmark
```


## Future

For now, we only have benchmarks for Bean Validation 1.1, it would be also good to
have another bean-generator with beans using Bean Validation 2.0 features and 
some benchmarks for it.
