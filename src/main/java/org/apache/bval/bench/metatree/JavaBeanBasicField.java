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

import java.util.List;
import org.apache.bval.bench.Util;
import com.sun.codemodel.JAnnotationUse;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JVar;


/**
 * A field with getter/setter methods for a basic type.
 * 
 * @author Carlos Vara
 */
public class JavaBeanBasicField extends AbstractMetaField implements Annotable {

    // The singleType of the field
    private final BasicType basicType;

    private JMethod getter;
    private JMethod setter;

    // The value to which the field is initialized (may be a valid or invalid value)
    private List<Object> validValues;
    private List<Object> invalidValues;


    /**
     * Creates a random MetaField
     * 
     * @param owner
     *            The class that owns this field.
     * @param name
     *            The name of the meta field.
     */
    public JavaBeanBasicField(MetaJavaBean owner, String name) {
        
        super(owner, name);
        
        this.basicType = BasicType.getRandom();

        // Generate the field declaration
        JDefinedClass ownerClass = owner.getGeneratedClass();
        this.generatedField = ownerClass.field(JMod.PRIVATE, basicType.getTypeClass(), name);
        
        // The getter
        getter = ownerClass.method(JMod.PUBLIC, basicType.getTypeClass(), "get" + name.substring(0, 1).toUpperCase() + name.substring(1));
        getter.body()._return(this.generatedField);
        
        // And the setter
        setter = ownerClass.method(JMod.PUBLIC, void.class, "set" + name.substring(0, 1).toUpperCase() + name.substring(1));
        JVar setterParam = setter.param(basicType.getTypeClass(), name);
        setter.body().assign(JExpr._this().ref(this.generatedField), setterParam);
    }


    // statically assign the code in the field
    public void generateAssignCode(Object value) {
        this.generatedField.init(Util.literalExpr(value));
    }


    public BasicType getBasicType() {
        return basicType;
    }

    public void setValidValues(List<Object> validValues) {
        this.validValues = validValues;
    }

    public List<Object> getValidValues() {
        return validValues;
    }

    public void setInvalidValues(List<Object> invalidValues) {
        this.invalidValues = invalidValues;
    }

    public List<Object> getInvalidValues() {
        return invalidValues;
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

}
