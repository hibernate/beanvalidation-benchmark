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
package org.apache.bval.bench;

import java.util.concurrent.CyclicBarrier;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import org.apache.bval.bench.generated.Holder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;


/**
 * A simple unit test that benchmarks a validator impl.
 * 
 * @author Carlos Vara
 */
public abstract class AbstractBenchTest {


    private static Holder holder;

    // Variables to hold the results
    protected static long fpavTime = 0l;
    protected static long fpavErrors = 0l;
    protected static long rvTime = 0l;
    protected static long rvErrors = 0l;
    protected static long rpTime = 0l;
    protected static long rpDescs = 0l;

    protected static Long mtStart = null;
    protected static long mtElapsed = 0l;


    // Barrier to control the synchronous execution of threads
    public static CyclicBarrier barrier = new CyclicBarrier(Config.CFG.numThreads(), new Runnable() {
        @Override
        public void run() {
            // On start
            if (mtStart == null) {
                mtStart = System.nanoTime();
            }
            // On finish
            else {
                mtElapsed = System.nanoTime() - mtStart;
                mtStart = null; // Restore, although not needed atm
            }
        }
    });


    /**
     * @return A concrete implementation of the validator factory.
     */
    public abstract ValidatorFactory getValidatorFactory();


    /**
     * Tests the speed of validating beans that are already parsed.
     */
    @Test
    public void checkRawValidationSpeed() {

        Validator validator = getValidatorFactory().getValidator();

        // Initial parse
        for (Object o : holder.beans) {
            rvErrors += validator.validate(o).size();
        }

        // Check validation speed
        for (int i = 0; i < Config.CFG.numTestRepetitions(); ++i) {
            long initTime = System.nanoTime();
            for (Object o : holder.beans) {
                rvErrors += validator.validate(o).size();
            }
            rvTime += System.nanoTime() - initTime;
        }

    }


    /**
     * Tests the speed of parsing beans without performing any validation.
     */
    @Test
    public void checkRawParsingSpeed() {

        for (int i = 0; i < Config.CFG.numTestRepetitions(); ++i) {

            // Validator in new factory
            Validator validator = getValidatorFactory().getValidator();

            // Validate all the classes in the holder
            long initTime = System.nanoTime();

            for (Object o : holder.beans) {
                rpDescs += (validator.getConstraintsForClass(o.getClass()).isBeanConstrained()) ? 1 : 0;
            }

            rpTime += System.nanoTime() - initTime;
        }

    }


    /**
     * Tests the speed of parsing and doing one validation of all the beans.
     */
    @Test
    public void checkFirstParseAndValidateSpeed() {

        for (int i = 0; i < Config.CFG.numTestRepetitions(); ++i) {

            // Validator in new factory
            Validator validator = getValidatorFactory().getValidator();

            long initTime = System.nanoTime();
            for (Object o : holder.beans) {
                fpavErrors += validator.validate(o).size();
            }

            fpavTime += System.nanoTime() - initTime;

        }

    }

    @Test
    public void checkMultithreadedValidationSpeed() throws InterruptedException {

        // Build the factory and parse all the beans
        ValidatorFactory vf = getValidatorFactory();
        Validator validator = vf.getValidator();

        // Initial parse
        for (Object o : holder.beans) {
            validator.validate(o).size();
        }

        // Launch X threads
        Thread[] workers = new Thread[Config.CFG.numThreads()];
        for (int i = 0; i < Config.CFG.numThreads(); ++i) {
            workers[i] = new Thread(new ValidatorTask(vf.getValidator()));
            workers[i].start();
        }

        // Wait for all the threads to finish
        for (int i = 0; i < Config.CFG.numThreads(); ++i) {
            workers[i].join();
        }

    }


    /**
     * A Runnable that will execute N validations of the whole bean list.
     */
    public class ValidatorTask implements Runnable {

        private final Validator val;

        public ValidatorTask(Validator val) {
            this.val = val;
        }

        @Override
        public void run() {
            // Wait for the other threads
            try {
                barrier.await();
            } catch (Exception e) {
                throw new RuntimeException("Error while synchronizing threads.", e);
            }

            // Validate!
            for (int i = 0; i < Config.CFG.numTestRepetitions(); ++i) {
                for (Object o : holder.beans) {
                    this.val.validate(o);
                }
            }

            // Wait for the other threads
            try {
                barrier.await();
            } catch (Exception e) {
                throw new RuntimeException("Error while synchronizing threads.", e);
            }

        }

    }


    /**
     * Only create the bean graph once.
     */
    @BeforeClass
    public static void createBeans() {
        holder = new Holder();
    }

    /**
     * Reset the static counters every time a subclass of this executes.
     */
    @BeforeClass
    public static void resetCounters() {
        fpavTime = 0l;
        fpavErrors = 0l;
        rvTime = 0l;
        rvErrors = 0l;
        rpTime = 0l;
        rpDescs = 0l;
        mtStart = null;
        mtElapsed = 0l;
    }


    /**
     * After all tests are run, print the results.
     */
    @AfterClass
    public static void printResults() {
        // Print them to a file in CSV format
        System.out.printf("  Raw validation time:      %6dms {%d}\n", rvTime / 1000000l, rvErrors);
        System.out.printf("  Raw parsing time:         %6dms {%d}\n", rpTime / 1000000l, rpDescs);
        System.out.printf("  First parse and validate: %6dms {%d}\n", fpavTime / 1000000l, fpavErrors);
        System.out.printf("  Multithreaded validation: %6dms \n", mtElapsed / 1000000l);
    }

}
