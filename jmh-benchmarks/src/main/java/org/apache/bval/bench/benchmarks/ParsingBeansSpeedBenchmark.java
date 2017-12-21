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

import java.util.concurrent.TimeUnit;

import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;

import org.apache.bval.bench.generated.Holder;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Tests the speed of parsing beans without performing any validation.
 */
public class ParsingBeansSpeedBenchmark {

	@State(Scope.Benchmark)
	public static class ParsingBeansSpeedState {

		public Holder holder;
		public Validator validator;
		private ValidatorFactory validatorFactory;

		public ParsingBeansSpeedState() {
			holder = new Holder();
		}

		@Setup(Level.Iteration)
		public void setup() {
			validatorFactory = Validation.buildDefaultValidatorFactory();
			validator = validatorFactory.getValidator();
		}

		@TearDown(Level.Iteration)
		public void teardown() {
			validatorFactory.close();
		}

	}

	@Benchmark
	@BenchmarkMode(Mode.Throughput)
	@OutputTimeUnit(TimeUnit.MILLISECONDS)
	@Fork(value = 1)
	@Threads(1)
	@Warmup(iterations = 5)
	@Measurement(iterations = 20)
	public void testCascadedValidation(ParsingBeansSpeedState state, Blackhole bh) {
		// Validator in new factory

		for ( Object o : state.holder.beans ) {
			bh.consume( state.validator.getConstraintsForClass( o.getClass() ).isBeanConstrained() );
		}
	}
}
