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

import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import org.hibernate.validator.HibernateValidator;
import org.junit.AfterClass;


/**
 * Tests Hibernate Validator performance.
 * 
 * @author Carlos Vara
 */
public class HibernateTest extends AbstractBenchTest {

    @Override
    public ValidatorFactory getValidatorFactory() {
        return Validation.byProvider(HibernateValidator.class).configure().buildValidatorFactory();
    }

    @AfterClass
    public static void printValidatorName() {
        System.out.println("HIBERNATE");
    }
    
    @AfterClass
    public static void printResultsToFile() throws IOException {
        FileWriter fw = new FileWriter("target/"+HibernateTest.class.getSimpleName()+"-results.txt", true);
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        fw.write(dateFormat.format(new Date()));
        fw.write(" " + (rvTime / 1000000l));
        fw.write(" " + (rpTime / 1000000l));
        fw.write(" " + (fpavTime / 1000000l));
        fw.write(" " + (mtElapsed / 1000000l));
        fw.write("\n");
        fw.flush();
        fw.close(); 
    }

}
