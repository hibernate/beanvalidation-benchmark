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

import java.util.ArrayList;
import java.util.List;
import org.apache.bval.bench.Util;
import com.helger.jcodemodel.AbstractJClass;
import com.helger.jcodemodel.JAnnotationUse;
import com.helger.jcodemodel.JCodeModel;
import com.helger.jcodemodel.JDefinedClass;
import com.helger.jcodemodel.JExpr;
import com.helger.jcodemodel.JMethod;
import com.helger.jcodemodel.JMod;
import com.helger.jcodemodel.JVar;


/**
 * A field with getter/setter methods for a basic type.
 *
 * @author Carlos Vara
 */
public class JavaBeanBasicField extends AbstractMetaField implements Annotable {

    private final BasicType basicType;
    private final boolean typeUse;
    private final JCodeModel cm;

    private JMethod getter;
    private JMethod setter;

    private List<Object> validValues;
    private List<Object> invalidValues;

    private final List<MetaAnnotation> accumulatedTypeUseAnnotations = new ArrayList<>();


    /**
     * Creates a random MetaField.
     *
     * @param owner
     *            The class that owns this field.
     * @param name
     *            The name of the meta field.
     * @param cm
     *            The code model used for type resolution.
     * @param typeUse
     *            Whether this field should use type-use constraints.
     */
    public JavaBeanBasicField(MetaJavaBean owner, String name, JCodeModel cm, boolean typeUse) {

        super(owner, name);

        this.cm = cm;
        this.typeUse = typeUse;
        this.basicType = typeUse ? BasicType.getRandomTypeUse() : BasicType.getRandom();

        JDefinedClass ownerClass = owner.getGeneratedClass();
        String getterName = "get" + name.substring(0, 1).toUpperCase() + name.substring(1);
        String setterName = "set" + name.substring(0, 1).toUpperCase() + name.substring(1);

        JVar setterParam;
        if (typeUse) {
            AbstractJClass fieldType = basicType.getType(cm);
            this.generatedField = ownerClass.field(JMod.PRIVATE, fieldType, name);
            getter = ownerClass.method(JMod.PUBLIC, fieldType, getterName);
            setter = ownerClass.method(JMod.PUBLIC, void.class, setterName);
            setterParam = setter.param(fieldType, name);
        } else {
            Class<?> fieldClass = basicType.getTypeClass();
            this.generatedField = ownerClass.field(JMod.PRIVATE, fieldClass, name);
            getter = ownerClass.method(JMod.PUBLIC, fieldClass, getterName);
            setter = ownerClass.method(JMod.PUBLIC, void.class, setterName);
            setterParam = setter.param(fieldClass, name);
        }
        getter.body()._return(this.generatedField);
        setter.body().assign(JExpr._this().ref(this.generatedField), setterParam);
    }


    public void generateAssignCode(Object value) {
        if (typeUse && basicType.isCollectionType()) {
            this.generatedField.init(Util.collectionLiteralExpr(basicType.getContainerClass(cm), value));
        } else {
            this.generatedField.init(Util.literalExpr(value));
        }
    }


    public BasicType getBasicType() {
        return basicType;
    }

    public boolean isTypeUse() {
        return typeUse;
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
        if (this.annotations.add(annot)) {
            if (typeUse) {
                accumulatedTypeUseAnnotations.add(annot);
            } else {
                JAnnotationUse genAnnot = getter.annotate(annot.getAnnotationClass());
                for (String paramKey : annot.getParameters().keySet()) {
                    Util.addAnnotParam(genAnnot, paramKey, annot.getParameters().get(paramKey));
                }
            }
        }
    }

    public void finalizeTypeUseAnnotations() {
        if (!typeUse || accumulatedTypeUseAnnotations.isEmpty()) {
            return;
        }

        AbstractJClass elementType = basicType.getElementType(cm);

        for (MetaAnnotation annot : accumulatedTypeUseAnnotations) {
            JAnnotationUse annotUse = new JAnnotationUse(annot.getAnnotationClass());
            for (String paramKey : annot.getParameters().keySet()) {
                Util.addAnnotParam(annotUse, paramKey, annot.getParameters().get(paramKey));
            }
            elementType = elementType.annotated(annotUse);
        }

        AbstractJClass annotatedType = basicType.getContainerClass(cm).narrow(elementType);

        generatedField.type(annotatedType);
        getter.type(annotatedType);
        setter.params().get(0).type(annotatedType);
    }

}
