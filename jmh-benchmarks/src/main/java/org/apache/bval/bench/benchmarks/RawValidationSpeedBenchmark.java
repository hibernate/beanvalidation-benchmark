/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.bval.bench.benchmarks;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;

import org.apache.bval.bench.generated.Holder;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Tests the speed of validating beans that are already parsed.
 */
public class RawValidationSpeedBenchmark {

	@State(Scope.Benchmark)
	public static class RawValidationSpeedState {

		public volatile Validator validator;

		public volatile Holder holder;

		public RawValidationSpeedState() {
			ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
			validator = factory.getValidator();
			holder = new Holder();
		}
	}

	@Benchmark
	@BenchmarkMode(Mode.Throughput)
	@OutputTimeUnit(TimeUnit.SECONDS)
	@Fork(value = 1)
	@Threads(50)
	@Warmup(iterations = 20) // it seems that as there are a lot of beans it takes some time to warmup
	@Measurement(iterations = 30)
	public void testCascadedValidation(RawValidationSpeedState state, Blackhole bh) {
		for ( Object o : state.holder.beans ) {
			Set<ConstraintViolation<Object>> constraintViolations = state.validator.validate( o );
			bh.consume( constraintViolations );
		}
	}
}
