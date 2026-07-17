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
import java.util.Optional;
import java.util.Set;
import jakarta.validation.Valid;
import org.apache.bval.bench.Config;
import org.apache.bval.bench.Util;
import com.helger.jcodemodel.AbstractJClass;
import com.helger.jcodemodel.JAnnotationUse;
import com.helger.jcodemodel.JBlock;
import com.helger.jcodemodel.JCodeModel;
import com.helger.jcodemodel.JDefinedClass;
import com.helger.jcodemodel.JExpr;
import com.helger.jcodemodel.JMethod;
import com.helger.jcodemodel.JMod;
import com.helger.jcodemodel.JVar;


/**
 * A field with getter/setter methods that references another bean.
 *
 * @author Carlos Vara
 */
public class JavaBeanRefField extends AbstractMetaField implements Annotable {

    private static final List<Class<?>> CONTAINER_TYPES = List.of(List.class, Set.class, Optional.class);

    private final MetaJavaBean refBean;
    private final boolean typeUse;
    private final JCodeModel cm;
    private AbstractJClass containerClass;

    private JMethod getter;
    private JMethod setter;

    private final List<MetaAnnotation> accumulatedTypeUseAnnotations = new ArrayList<>();

    public JavaBeanRefField(MetaJavaBean owner, String name, MetaJavaBean refBean, JCodeModel cm, boolean typeUse) {
        super(owner, name);

        this.refBean = refBean;
        this.cm = cm;
        this.typeUse = typeUse;

        JDefinedClass ownerClass = owner.getGeneratedClass();
        String getterName = "get" + name.substring(0, 1).toUpperCase() + name.substring(1);
        String setterName = "set" + name.substring(0, 1).toUpperCase() + name.substring(1);

        if (typeUse) {
            Class<?> containerType = Config.CFG.getRandom(CONTAINER_TYPES);
            this.containerClass = cm.ref(containerType);
            AbstractJClass fieldType = containerClass.narrow(refBean.getGeneratedClass());
            this.generatedField = ownerClass.field(JMod.PRIVATE, fieldType, name);
            getter = ownerClass.method(JMod.PUBLIC, fieldType, getterName);
            setter = ownerClass.method(JMod.PUBLIC, void.class, setterName);
            setter.param(fieldType, name);
        } else {
            this.generatedField = ownerClass.field(JMod.PRIVATE, refBean.getGeneratedClass(), name);
            getter = ownerClass.method(JMod.PUBLIC, refBean.getGeneratedClass(), getterName);
            setter = ownerClass.method(JMod.PUBLIC, void.class, setterName);
            setter.param(refBean.getGeneratedClass(), name);
        }
        getter.body()._return(this.generatedField);
        setter.body().assign(JExpr._this().ref(this.generatedField), setter.params().get(0));
    }

    @Override
    public void accept(AnnotatorVisitor annotator) {
        annotator.annotate(this);
    }

    @Override
    public void addAnnotation(MetaAnnotation annot) {
        if (this.annotations.add(annot)) {
            if (typeUse && annot.getAnnotationClass().equals(cm.ref(Valid.class))) {
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

        AbstractJClass elementType = refBean.getGeneratedClass();

        for (MetaAnnotation annot : accumulatedTypeUseAnnotations) {
            JAnnotationUse annotUse = new JAnnotationUse(annot.getAnnotationClass());
            for (String paramKey : annot.getParameters().keySet()) {
                Util.addAnnotParam(annotUse, paramKey, annot.getParameters().get(paramKey));
            }
            elementType = elementType.annotated(annotUse);
        }

        AbstractJClass annotatedType = containerClass.narrow(elementType);

        generatedField.type(annotatedType);
        getter.type(annotatedType);
        setter.params().get(0).type(annotatedType);
    }

    public boolean isTypeUse() {
        return typeUse;
    }

    public void generateAssignCode(JBlock body, JVar var, JVar value) {
        if (typeUse) {
            body.add(var.invoke(this.setter).arg(containerClass.staticInvoke("of").arg(value)));
        } else {
            body.add(var.invoke(this.setter).arg(value));
        }
    }

    public MetaJavaBean getRefBean() {
        return refBean;
    }

}
