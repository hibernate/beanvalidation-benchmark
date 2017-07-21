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

import org.apache.bval.bench.Util;
import com.sun.codemodel.JAnnotationUse;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JVar;


/**
 * A field with getter/setter methods that references another bean.
 * 
 * @author Carlos Vara
 */
public class JavaBeanRefField extends AbstractMetaField implements Annotable {

    // Referenced bean
    private final MetaJavaBean refBean;
    
    private JMethod getter;
    private JMethod setter;
    
    public JavaBeanRefField(MetaJavaBean owner, String name, MetaJavaBean refBean) {
        super(owner, name);
        
        this.refBean = refBean;
        
        // Generate the field declaration
        JDefinedClass ownerClass = owner.getGeneratedClass();
        this.generatedField = ownerClass.field(JMod.PRIVATE, refBean.getGeneratedClass(), name);
        
        // The getter
        getter = ownerClass.method(JMod.PUBLIC, refBean.getGeneratedClass(), "get"+name.substring(0, 1).toUpperCase()+name.substring(1));
        getter.body()._return(this.generatedField);
        
        // The setter
        setter = ownerClass.method(JMod.PUBLIC, void.class, "set"+name.substring(0, 1).toUpperCase()+name.substring(1));
        JVar setterParam = setter.param(refBean.getGeneratedClass(), name);
        setter.body().assign(JExpr._this().ref(this.generatedField), setterParam);
    }

    @Override
    public void accept(AnnotatorVisitor annotator) {
        annotator.annotate(this);
    }
    
    @Override
    public void addAnnotation(MetaAnnotation annot) {
        // Add the annotation to the set of annotations
        if ( this.annotations.add(annot) ) {
            // Generate the source
            JAnnotationUse genAnnot = getter.annotate(annot.getAnnotationClass());
            for (String paramKey : annot.getParameters().keySet()) {
                Util.addAnnotParam(genAnnot, paramKey, annot.getParameters().get(paramKey));
            }
        }
    }    
    
    public void generateAssignCode(JBlock body, JVar var, JVar value) {
        body.add(var.invoke(this.setter).arg(value));
    }

    public MetaJavaBean getRefBean() {
        return refBean;
    }
    
}
