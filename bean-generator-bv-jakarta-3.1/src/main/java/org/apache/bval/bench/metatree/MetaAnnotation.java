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

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;
import org.apache.bval.bench.jsr303.MetaGroup;
import com.google.common.collect.Maps;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;


/**
 * Wraps a JClass that represents an annotation and its parameters.
 * 
 * @author Carlos Vara
 */
public class MetaAnnotation {

    // An annotation type: JSR-303, OTHER
    private final AnnotationType annotationType;

    // The class of the annotation, which is what it defines its equals()
    private JClass annotationJClass;

    // A HashMap of the attributes
    private Map<String, Object> parameters;


    public MetaAnnotation(JCodeModel cm, Class<? extends Annotation> annotationClass, AnnotationType annotationType, Map<String, Object> parameters) {
        this.annotationJClass = (JClass) cm._ref(annotationClass);
        this.annotationType = annotationType;
        this.parameters = parameters;
    }
    
    public MetaAnnotation(JDefinedClass annotationJClass, AnnotationType annotationType, HashMap<String, Object> parameters) {
        this.annotationJClass = annotationJClass;
        this.annotationType = annotationType;
        this.parameters = parameters;
    }

    public MetaAnnotation(MetaAnnotation src) {
        this.annotationJClass = src.annotationJClass;
        this.annotationType = src.annotationType;
        this.parameters = Maps.newHashMap(src.parameters);
    }

    public JClass getAnnotationClass() {
        return annotationJClass;
    }

    public AnnotationType getAnnotationType() {
        return this.annotationType;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }
    
    public void setGroup(MetaGroup group) {
        this.parameters.put("groups", group.getGeneratedClass());
    }
    
    // TODO: equals + hashCode

}
