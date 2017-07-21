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
package org.apache.bval.bench.metatree;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.GroupSequence;
import org.apache.bval.bench.Config;
import org.apache.bval.bench.Util;
import org.apache.bval.bench.jsr303.MetaGroup;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.sun.codemodel.ClassType;
import com.sun.codemodel.JAnnotationUse;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JMod;


/**
 * Models a JavaBean class:
 * <ul>
 * <li>Empty constructor</li>
 * <li>All of its fields are private and accessible via getter/setter methods.</li>
 * </ul>
 * 
 * @author Carlos Vara
 */
public class MetaJavaBean extends AbstractBaseMetaClass implements Annotable {

    // The fields in this bean, indexed by their name
    private final Map<String, AbstractMetaField> fields;

    // The set of annotations for this bean
    private final Set<MetaAnnotation> annotations = Sets.newHashSet();
    
    // The JSR-303 groups used in this bean
    private final Set<MetaGroup> groups = Sets.newHashSet();


    /**
     * Creates a new MetaJavaBean with random fields.
     * 
     * @param fqn
     *            The fully qualified name of the bean's class.
     * @param numFields
     *            Number of simple type fields that will be added.
     */
    public MetaJavaBean(JCodeModel cm, String fqn, int numFields) {

        super(cm, fqn, ClassType.CLASS);

        // Empty constructor
        getGeneratedClass().constructor(JMod.PUBLIC);

        // Init the simple fields
        this.fields = Maps.newHashMapWithExpectedSize(numFields);
        for (int i = 0; i < numFields; ++i) {
            String fieldName = "field" + Config.CFG.nextUniqueNum();
            JavaBeanBasicField field = new JavaBeanBasicField(this, fieldName);
            fields.put(fieldName, field);
        }

    }

    /**
     * Adds random fields referencing other mjb's.
     * 
     * @param beans
     *            The list of available beans.
     */
    public void interrelate(List<MetaJavaBean> beans) {

        final int numInterrelations = Config.CFG.rndNumInterrelations();

        for (int i = 0; i < numInterrelations; ++i) {
            String fieldName = "beanRef" + i;
            JavaBeanRefField field = new JavaBeanRefField(this, fieldName, Config.CFG.getRandom(beans));
            fields.put(fieldName, field);
        }
    }

    public Collection<AbstractMetaField> getFields() {
        return this.fields.values();
    }

    public void addGroup(MetaGroup group) {
        this.groups.add(group);
    }
    
    public MetaAnnotation buildGroupSequenceAnnot() {
        if ( this.groups.isEmpty() ) {
            return null; // No group sequence needed
        }
        else {
            // Build the array of groups
            JDefinedClass[] gs = new JDefinedClass[groups.size()+1];
            int i=0;
            for ( MetaGroup group : groups ) {
                gs[i++] = group.getGeneratedClass();
            }
            gs[i] = getGeneratedClass(); // Add the class itself (acts as Default)
            HashMap<String,Object> gsParams = Maps.newHashMap();
            gsParams.put("value", gs);
            MetaAnnotation gsAnnot = new MetaAnnotation(getGeneratedClass().owner(), GroupSequence.class, AnnotationType.OTHER, gsParams);
            return gsAnnot;
        }
    }

    /**
     * Generate code to initialize the basic fields.
     * <p>
     * NOTE: The fields could be initialized (or overriden) externally, giving
     * it more entropy as different instances of the bean could choose different
     * values for each field.
     */
    public void generateStaticInitCode() {
        for ( AbstractMetaField field : fields.values() ) {
            if ( field instanceof JavaBeanBasicField ) {
                JavaBeanBasicField jbbf = (JavaBeanBasicField) field;
                Object value;
                if (Config.CFG.shouldAssignValidValue()) {
                    value = Config.CFG.getRandom(jbbf.getValidValues());
                } else {
                    value = Config.CFG.getRandom(jbbf.getInvalidValues());
                }
                jbbf.generateAssignCode(value);
            }
        }
    }
    
    @Override
    public void accept(AnnotatorVisitor annotator) {
        annotator.annotate(this);
    }

    @Override
    public void addAnnotation(MetaAnnotation annot) {
        if ( this.annotations.add(annot) ) {
            JAnnotationUse genAnnot = getGeneratedClass().annotate(annot.getAnnotationClass());
            for (String paramKey : annot.getParameters().keySet()) {
                Util.addAnnotParam(genAnnot, paramKey, annot.getParameters().get(paramKey));
            }
        }
    }

    // FIXME: Keep a reference of the superclass to allow future use of it
    public void setSuperClass(MetaJavaBean random) {
        this.getGeneratedClass()._extends(random.getGeneratedClass());
    }

}
