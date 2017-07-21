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

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.bval.bench.jsr303.Jsr303Annotator;
import org.apache.bval.bench.metatree.AbstractMetaField;
import org.apache.bval.bench.metatree.AnnotatorVisitor;
import org.apache.bval.bench.metatree.JavaBeanRefField;
import org.apache.bval.bench.metatree.MetaJavaBean;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JVar;


/**
 * Entry point for bean graph generation.
 * <p>
 * NOTE: Class is not thread-safe.
 * 
 * @author Carlos Vara
 */
public class Generator {

    // The code model root
    private final JCodeModel cm;

    // The list of generated rich beans
    private final List<MetaJavaBean> beans;

    // The list of root simple beans used to inherit from
    private final List<MetaJavaBean> baseBeans;

    // The annotators used to spice the bean graph
    private final List<? extends AnnotatorVisitor> annotators;


    public Generator() {
        this.cm = new JCodeModel();
        this.beans = Lists.newArrayListWithExpectedSize(Config.CFG.getNumBeans());
        this.baseBeans = Lists.newArrayListWithExpectedSize(Config.CFG.getNumBaseBeans());
        this.annotators = ImmutableList.of(new Jsr303Annotator(cm));
    }


    /**
     * Generates a bean graph according to the configuration in
     * {@link Config#CFG}.
     */
    public void generateBeanGraph() {

        // PHASE 1: Basic beans with basic type fields
        generateBasicBeans();

        // PHASE 2: Enrich beans with references to other beans
        enrichBasicBeans();

        // PHASE 3: Inheritance
        createInheritanceGraph();

        // PHASE 4: Annotate the beans
        annotateBeanGraph();

        // PHASE 5: Generate population code
        generatePopulationCode();

        // PHASE 6: Output the bean graph .java files
        outputJavaFiles();
    }


    /**
     * Starts from an empty code model and creates the beans in their initial
     * state (only basic attributes).
     * <p>
     * POST: The {@link #beans} list is filled with simple beans.
     */
    private void generateBasicBeans() {
        for (int i = 0; i < Config.CFG.getNumBeans(); ++i) {
            MetaJavaBean mjb = new MetaJavaBean(cm, Config.CFG.getBeansPackageName() + ".Bean" + i, Config.CFG.rndNumFields());
            beans.add(mjb);
        }
    }

    /**
     * Adds fields referencing other beans to the list of beans. The target of
     * these references are also beans in the list.
     * <p>
     * POST: The beans in the {@link #beans} list get reference fields.
     */
    private void enrichBasicBeans() {
        for (MetaJavaBean mjb : beans) {
            mjb.interrelate(beans);
        }
    }

    /**
     * Creates a list of base beans used as root of the inheritance tree, and
     * sets them as superclass of some of the beans in {@link #beans}.
     * <p>
     * POST: The {@link #baseBeans} list is filled with simple beans and some
     * beans in {@link #beans} mark them as their superclass.
     */
    private void createInheritanceGraph() {
        for (int i = 0; i < Config.CFG.getNumBaseBeans(); ++i) {
            MetaJavaBean mjb = new MetaJavaBean(cm, Config.CFG.getBeansPackageName() + ".BaseBean" + i, Config.CFG.rndNumFields());
            baseBeans.add(mjb);
        }
        for (MetaJavaBean mjb : beans) {
            if (Config.CFG.shouldInherit()) {
                mjb.setSuperClass(Config.CFG.getRandom(baseBeans));
            }
        }
    }

    /**
     * POST: The bean graph pending from the {@link #beans} and
     * {@link #baseBeans} gets annotated with the available annotators in
     * {@link #annotators}.
     */
    private void annotateBeanGraph() {
        for (AnnotatorVisitor annotator : annotators) {
            for (MetaJavaBean mjb : beans) {
                mjb.accept(annotator);
            }
            for (MetaJavaBean bmjb : baseBeans) {
                bmjb.accept(annotator);
            }
        }
    }


    /**
     * Generates a Holder class that will hold an {@link ArrayList} with all the
     * first level beans in the graph (contents of {@link #beans}) on it.
     */
    private void generatePopulationCode() {
        try {
            // Generate the holder class
            JDefinedClass holderClass = cm._class(Config.CFG.getBasePackageName() + ".Holder");
            JClass alObject = (JClass) cm._ref(ArrayList.class);
            alObject = alObject.narrow(Object.class);
            JFieldVar beansField = holderClass.field(JMod.PUBLIC, alObject, "beans", JExpr._new(alObject));
            JMethod defConstructor = holderClass.constructor(JMod.PUBLIC);
            JBlock body = defConstructor.body();

            // Init the beans and add them to the array
            for (MetaJavaBean mjb : beans) {
                mjb.generateStaticInitCode();
                JVar beanDecl = generateBeanNonStaticInitCode(mjb, body, 0);
                body.add(beansField.invoke("add").arg(beanDecl));
            }

            // Init the base beans but don't add them to the array
            for (MetaJavaBean bmjb : baseBeans) {
                bmjb.generateStaticInitCode();
            }

        } catch (JClassAlreadyExistsException e) {
            throw new RuntimeException("Error generating the holder class.", e);
        }
    }


    /**
     * Outputs the meta-contents of {@link #cm} to .java files generated on
     * {@link Config.CFG#getOutputDir()}.
     */
    private void outputJavaFiles() {
        try {
            File file = new File(Config.CFG.getOutputDir());
            file.mkdirs();
            cm.build(file, (PrintStream) null);
        } catch (IOException e) {
            throw new RuntimeException("Error generating the java files", e);
        }
    }


    /**
     * Recursive method that handles the creation of reference beans at
     * different depth levels.
     * 
     * @param mjb
     *            The target bean to create an instance of.
     * @param body
     *            The current block of code.
     * @param level
     *            The current depth level.
     * @return A generated variable referencing the created bean.
     */
    private JVar generateBeanNonStaticInitCode(MetaJavaBean mjb, JBlock body, int level) {

        JVar beanDecl = body.decl(mjb.getGeneratedClass(), "lvl" + level + mjb.getName() + "_" + Config.CFG.nextUniqueNum());
        body.assign(beanDecl, JExpr._new(mjb.getGeneratedClass()));

        for (AbstractMetaField amf : mjb.getFields()) {
            if (amf instanceof JavaBeanRefField) {

                JavaBeanRefField jbrf = (JavaBeanRefField) amf;

                // Should a nested bean be created?
                if (Config.CFG.shouldAddNestedBean(level)) {
                    JVar nestedBeanDecl = generateBeanNonStaticInitCode(jbrf.getRefBean(), body, level + 1);
                    jbrf.generateAssignCode(body, beanDecl, nestedBeanDecl);
                }
            }

        }

        return beanDecl;
    }


    /**
     * Creates a new {@link Generator} object and calls
     * {@link #generateBeanGraph()} on it.
     * 
     * @param args
     *            Unused.
     */
    public static void main(String[] args) {
        System.out.println("Starting bean graph generation...");
        new Generator().generateBeanGraph();
        System.out.println("Generated " + Config.CFG.getNumBeans() + " beans.");
    }

}
