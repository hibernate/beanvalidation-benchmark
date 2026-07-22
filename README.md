# Bean Validation benchmark

## Introduction

This work is derived from the [Apache BVal benchmark](http://svn.apache.org/repos/asf/bval/sandbox/jsr303-impl-bench/) described in [this article from 2010](http://carinae.net/2010/06/benchmarking-hibernate-validator-and-apache-beanvalidation-the-two-jsr-303-implementations/).

Note that the new benchmarks have a slightly different behavior than what is described in the article.

It is licensed under the Apache 2 license.

## Implementations available

| Implementation      | Version          | Profile name | Bean Generator |
|---------------------|------------------|--------------|----------------|
| Apache BVal         | `1.1.2`          | `bval-1.1`   | `bv-1.1`       |
| Apache BVal         | `3.0.2`          | `bval-3.0`   | `bv-3.1`       |
| Hibernate Validator | `5.4.2.Final`    | `hv-5.4`     | `bv-1.1`       |
| Hibernate Validator | `6.0.10.Final`   | `hv-6.0`     | `bv-1.1`       |
| Hibernate Validator | `9.0.1.Final`    | `hv-9.0`     | `bv-3.1`       |
| Hibernate Validator | `9.1.0.Alpha1`   | `hv-9.1`     | `bv-3.1`       |
| Hibernate Validator | `9.1.0-SNAPSHOT` | `hv-current` | `bv-3.1`       |

## Bean generators

### `bean-generator-bv-1.1`

Generates beans using Bean Validation 1.1 features only (`javax.validation`):

- Constraints: `@NotNull`, `@Size`, `@Min`, `@Max`, `@Pattern`, `@Null`
- Composed constraints: `@GoodCode`, `@RangedInt`, `@AlwaysValid`
- `@Valid` on reference fields

### `bean-generator-bv-3.1`

Generates beans using Bean Validation 2.0+ features (`jakarta.validation`):

- All BV 1.1 constraints plus BV 2.0 additions: `@NotBlank`, `@NotEmpty`, `@Email`, `@Positive`, `@PositiveOrZero`, `@Negative`, `@NegativeOrZero`
- Container element validation: basic fields can be `List<@NotNull String>`, `Set<@Size(...) Integer>`, `Optional<@Email String>`
- Container element `@Valid`: reference fields can be `List<@Valid Bean>`, `Set<@Valid Bean>`, `Optional<@Valid Bean>`
- `@Valid` is placed on container elements (type-use), not on the container getter
- Type-use constraint ratio is configurable via `generator.typeuse.ratio` (default: 30%)

## Generating the beans

### Creating the scenario

To execute a benchmark, a scenario is required.

A scenario is a property file located at the root directory of the `bean-generator-bv-*.*` module and called `scenario.properties`.

A default scenario file can be found at `bean-generator-bv-*.*/src/main/resources/generator.default.properties`.

If you don't define a specific scenario, the default one is used.

### Generating the beans

Once your scenario is in place in the `bean-generator-bv` module (e.g. in `bean-generator-bv-3.1`), you need to generate the beans.

From the root directory, run:

```bash
pushd bean-generator-bv-3.1
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
mvn clean package -Phv-current
mvn package -Phv-9.0
```

Finally, you can run the benchmarks as follows:

```bash
java -jar target/bv-benchmarks-hv-current.jar
java -jar target/bv-benchmarks-hv-9.0.jar
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
java -jar target/bv-benchmarks-${impl}.jar RawValidationSpeedBenchmark
```

## Running benchmarks with the script

A convenience script (`scripts/run-benchmarks.sh`) automates the full workflow:
building the bean generator, packaging the JMH benchmark JARs, and running the benchmarks.
Results are saved to timestamped directories under `scripts/.results/`.

### Prerequisites

- Java 17+ on `PATH`
- Maven on `PATH`

### Quick start

```bash
# Run all benchmarks with the default profile (hv-current).
# This will build the bean generator, package the benchmark JAR, and run it.
./scripts/run-benchmarks.sh

# Quick dev run (1 fork, 3 warmup, 5 measurement, 1 thread)
./scripts/run-benchmarks.sh --quick --profile hv-9.0
```

### Options

| Option                   | Description                                                      |
|--------------------------|------------------------------------------------------------------|
| `--profile <name>`       | Implementation profile to benchmark (repeatable). Default: `hv-current`. Valid: `hv-current`, `hv-9.1`, `hv-9.0`, `hv-6.0`, `hv-5.4`, `bval-3.0`, `bval-1.1` |
| `--skip-build`           | Skip Maven builds and reuse existing JARs                        |
| `--quick`                | Quick mode: 1 fork, 3 warmup, 5 measurement, 1 thread           |
| `--forks <N>`            | Number of JMH forks                                              |
| `--warmup <N>`           | Number of warmup iterations                                      |
| `--measurement <N>`      | Number of measurement iterations                                 |
| `--threads <N>`          | Number of threads                                                |
| `--jvm-args "<args>"`    | Extra JVM arguments passed to benchmark forks                    |
| `-h`, `--help`           | Show help                                                        |

Any positional arguments are treated as JMH benchmark name patterns (e.g. `RawValidationSpeedBenchmark`).

### Examples

```bash
# Run a single benchmark
./scripts/run-benchmarks.sh --quick --profile hv-9.0 RawValidationSpeedBenchmark

# Compare two implementations side by side
./scripts/run-benchmarks.sh --profile hv-9.0 --profile hv-9.1

# Skip rebuilding if the beans and JARs are already in place
./scripts/run-benchmarks.sh --skip-build --profile hv-9.0

# Custom JMH parameters
./scripts/run-benchmarks.sh --forks 2 --warmup 5 --measurement 10 --threads 4

# Custom JVM arguments
./scripts/run-benchmarks.sh --quick --jvm-args "-XX:+UseG1GC -Xmx2g"
```

### Output

Each run creates a timestamped directory under `scripts/.results/` (gitignored) containing:

- `environment.txt` — profile, date, Java version, OS, JMH arguments
- `jmh-results.json` — structured JMH results
- `output.log` — full console output

When comparing multiple profiles, each gets its own subdirectory within the same timestamped run.
