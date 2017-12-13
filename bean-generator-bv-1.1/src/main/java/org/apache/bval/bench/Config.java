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

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.Random;


// TODO
// % of ref beans that reference already existing beans (circular deps)


/**
 * Stores the generator configuration parameters and provides an easy way of
 * accessing those parameters and the random values they can generate.
 * 
 * @author Carlos Vara
 */
public enum Config {

    CFG();

    private static final String DEFAULT_PROPERTIES_RES = "generator.default.properties";
    private static final String USER_PROPERTIES_RES = "generator.properties";

    private final Random r;
    private final Properties p;
    private int uniqueSeq = 0;

    private Config() {

        // Read the config
        p = readConfigProperties();

        // Init random generator
        r = new Random(new Long(p.getProperty("generator.rnd.seed")));
        
        // Extra info: print the properties which will be in use
        printProperties();
    }

    /**
     * @return A unique sequence number.
     */
    public int nextUniqueNum() {
        return uniqueSeq++;
    }

    /**
     * @return The base directory where the generated classes will be output.
     */
    public String getOutputDir() {
        return p.getProperty("generator.outputdir");
    }

    /**
     * @return The name of the package where the generated beans will be placed.
     */
    public String getBeansPackageName() {
        return p.getProperty("generator.pkg.beans");
    }

    /**
     * @return The name of the package where the generated groups will be
     *         placed.
     */
    public String getGroupsPackageName() {
        return p.getProperty("generator.pkg.groups");
    }

    /**
     * @return The name of the package where other generated resources will be
     *         placed.
     */
    public String getBasePackageName() {
        return p.getProperty("generator.pkg.base");
    }

    /**
     * @return The total number of beans to generate.
     */
    public int getNumBeans() {
        return new Integer(p.getProperty("generator.numbeans"));
    }

    /**
     * @return The total number of groups to generate.
     */
    public int getNumGroups() {
        return new Integer(p.getProperty("generator.numgroups"));
    }

    /**
     * @return The total number of base beans to generate.
     */
    public int getNumBaseBeans() {
        return new Integer(p.getProperty("generator.numbasebeans"));
    }

    /**
     * @param <T>
     *            The type of elements of the list.
     * @param elements
     *            List of possible choices.
     * @return A random element from the list of elements.
     */
    public <T> T getRandom(List<T> elements) {
        return elements.get(r.nextInt(elements.size()));
    }

    /**
     * @return The number of basic fields that a bean should have.
     */
    public int rndNumFields() {
        int min = new Integer(p.getProperty("generator.basicfields.min"));
        int dif = new Integer(p.getProperty("generator.basicfields.max")) - min;
        return min + r.nextInt(dif);
    }

    /**
     * @return The number of fields referencing other beans that a bean should
     *         have.
     */
    public int rndNumInterrelations() {
        int min = new Integer(p.getProperty("generator.reffields.min"));
        int dif = new Integer(p.getProperty("generator.reffields.max")) - min;
        return min + r.nextInt(dif);
    }

    /**
     * @param level
     *            The level of nesting.
     * @return <code>true</code> if it's determined that a nested bean should be
     *         added, <code>false</code> otherwise.
     */
    public boolean shouldAddNestedBean(int level) {
        if (level == 0) {
            return r.nextDouble() < new Double(p.getProperty("generator.level1.fillrate"));
        } else if (level == 1) {
            return r.nextDouble() < new Double(p.getProperty("generator.level2.fillrate"));
        } else if (level == 2) {
            return r.nextDouble() < new Double(p.getProperty("generator.level3.fillrate"));
        } else {
            return false;
        }
    }

    /**
     * @return <code>true</code> if it's determined that the fields in the bean
     *         being annotated should have group parameters, <code>false</code>
     *         otherwise.
     */
    public boolean shouldAddGroupsToBeanFields() {
        return r.nextDouble() < new Double(p.getProperty("generator.beanswithgroups"));
    }

    /**
     * @return <code>true</code> if it's determined that a valid value should be
     *         assigned to the current field, <code>false</code> otherwise.
     */
    public boolean shouldAssignValidValue() {
        return r.nextDouble() < new Double(p.getProperty("generator.validvalues"));
    }

    /**
     * @return <code>true</code> if it's determined that the current bean should
     *         inherit from one of the base beans, <code>false</code> otherwise.
     */
    public boolean shouldInherit() {
        return r.nextDouble() < new Double(p.getProperty("generator.inheritanceratio"));
    }
    
    /**
     * Prints in {@link System.out} the generator properties.
     */
    public void printProperties() {
        p.list(System.out);
    }
    
    /**
     * @return The number of times each test is repeated.
     */
    public int numTestRepetitions() {
        return new Integer(p.getProperty("tester.repetitions"));
    }

    /**
     * @return The number of concurrent threads to execute in the multi-threaded
     *         tests.
     */
    public int numThreads() {
        return new Integer(p.getProperty("tester.numthreads"));
    }


    /**
     * @return A properties object with the default configuration values
     *         overriden by any user set values.
     */
    private Properties readConfigProperties() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            throw new RuntimeException("Could not obtain a class loader.");
        }
        Properties tmp = loadDefaultProperties(cl);
        tmp = overrideDefaultProperties(cl, tmp);
        tmp = overrideWithSystemProperties(tmp);
        return tmp;
    }

    /**
     * Load the file {@link #DEFAULT_PROPERTIES_RES}.
     * 
     * @param cl
     *            A classloader from which the properties file will be read.
     * @return A properties object with the default configuration values.
     */
    private Properties loadDefaultProperties(ClassLoader cl) {
        Properties defaultProperties = new Properties();
        InputStream dpStream = cl.getResourceAsStream(DEFAULT_PROPERTIES_RES);
        if (dpStream == null) {
            throw new RuntimeException("Could not locate default properties file: " + DEFAULT_PROPERTIES_RES + " in the classpath.");
        }
        try {
            defaultProperties.load(dpStream);
            dpStream.close(); // No finally, an exception here terminates the program
        } catch (IOException e) {
            throw new RuntimeException("Error while reading default properties file.", e);
        }
        return defaultProperties;
    }

    /**
     * Load the file {@link #USER_PROPERTIES_RES}.
     * 
     * @param cl
     *            A classloader from which the properties file will be read.
     * @param defaultProperties
     *            A properties object with the default configuration values.
     * @return A properties object with the default values overriden by the
     *         user.
     */
    private Properties overrideDefaultProperties(ClassLoader cl, Properties defaultProperties) {
        InputStream upStream = cl.getResourceAsStream(USER_PROPERTIES_RES);
        if (upStream == null) {
            return defaultProperties;
        }

        Properties userProperties = new Properties(defaultProperties);
        try {
            userProperties.load(upStream);
            upStream.close(); // No finally, an exception here terminates the program
        } catch (IOException e) {
            throw new RuntimeException("Error while reading user properties file.", e);
        }
        return userProperties;
    }

    /**
     * Overrides the read properties with the system set properties.
     * 
     * @param prop
     *            A properties object holding the read properties.
     * @return A properties object with the system set properties overriden.
     */
    private Properties overrideWithSystemProperties(Properties prop) {
        Properties ret = new Properties(prop);
        for ( String key : prop.stringPropertyNames() ) {
            if ( System.getProperty(key) != null ) {
                ret.setProperty(key, System.getProperty(key));
            }
        }
        return ret;
    }

}
