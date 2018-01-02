# Bean Validation benchmark

## Introduction

This work is derived from the [Apache BVal benchmark](http://svn.apache.org/repos/asf/bval/sandbox/jsr303-impl-bench/) described in [this article from 2010](http://carinae.net/2010/06/benchmarking-hibernate-validator-and-apache-beanvalidation-the-two-jsr-303-implementations/).

Note that the new benchmarks have a slightly different behavior than what is described in the article.

It is licensed under the Apache 2 license.

## Implementations available

| Implementation      | Version          | Profile name      |
|---------------------|------------------|-------------------|
| Apache BVal         | `1.1.2`          | `bval`            |
| Hibernate Validator | `5.4.2.Final`    | `hv-5.4`          |
| Hibernate Validator | `6.0.7.Final`    | `hv-6.0-stable`   |
| Hibernate Validator | `6.0.8-SNAPSHOT` | `hv-6.0-snapshot` |

## Generating the beans

### Creating the scenario

To execute a benchmark, a scenario is required.

A scenario is a property file located at the root directory of the `bean-generator-bv-1.1` module and called `scenario.properties`.

A default scenario file can be found at `bean-generator-bv-1.1/src/main/resources/generator.default.properties`.

If you don't define a specific scenario, the default one is used.

### Generating the beans

Once your scenario is in place in the `bean-generator-bv-1.1` module, you need to generate the beans.

From the root directory, run:

```bash
pushd bean-generator-bv-1.1
mvn clean install
popd
```

The `install` goal must be used here so that the jars get installed to the local
.m2 repository and can be reused in further builds. This is required to be able
to run multiple benchmarks with the same set of generated beans.
 
## Running the benchmark

Now that you have created your generated beans, corresponding JMH benchmarks
jars can be created for the available Bean Validation implementations.

Let's say you want to compare the results of the full benchmark for the current stable
and latest snapshot versions of Hibernate Validator.

First, jars including the JMH benchmarks and the corresponding implementation should be prepared.

Assuming root is the current location:

```bash
pushd jmh-benchmarks
mvn clean package -Phv-6.0-snapshot
mvn package -Phv-6.0-stable
```

Finally, you can run the benchmarks as follows:

```bash
java -jar target/bv-benchmarks-${snapshot-version}.jar
java -jar target/bv-benchmarks-${stable-version}.jar
popd
```

The following benchmarks are available:

| Name                              | Main goal                                |
|-----------------------------------|------------------------------------------|
| RawValidationSpeedBenchmark       | Evaluate validation only                 |
| ParsingBeansSpeedBenchmark        | Evaluate the metadata building phase     |

It is also possible to run a single benchmark if needed. To do so
pass a benchmark test name from the above table as a parameter:

```bash
java -jar target/bv-benchmarks-${impl-version}.jar RawValidationSpeedBenchmark
```

## Future

For now, our benchmarks only tests the Bean Validation 1.1 features.

It would be also good to have another bean-generator with beans using Bean Validation 2.0
features and some benchmarks for it.
