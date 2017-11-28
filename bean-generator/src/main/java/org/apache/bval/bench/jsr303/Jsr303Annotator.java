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
package org.apache.bval.bench.jsr303;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;
import javax.validation.ReportAsSingleViolation;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Null;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import org.apache.bval.bench.Config;
import org.apache.bval.bench.metatree.AbstractMetaField;
import org.apache.bval.bench.metatree.AnnotationType;
import org.apache.bval.bench.metatree.AnnotatorVisitor;
import org.apache.bval.bench.metatree.BasicType;
import org.apache.bval.bench.metatree.JavaBeanBasicField;
import org.apache.bval.bench.metatree.JavaBeanRefField;
import org.apache.bval.bench.metatree.MetaAnnotation;
import org.apache.bval.bench.metatree.MetaJavaBean;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.sun.codemodel.ClassType;
import com.sun.codemodel.JAnnotationArrayMember;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;


/**
 * A class / field annotator that produces JSR-303 annotations.
 * <p>
 * NOTE: Class is not thread-safe.
 * 
 * @author Carlos Vara
 */
public class Jsr303Annotator implements AnnotatorVisitor {
    
    // The code generator
    private final JCodeModel codeModel;

    // All the JSR-303 groups used by the annotator
    private final List<MetaGroup> groups;
    
    // Available annotations for basic types
    private final ListMultimap<BasicType, Jsr303MetaAnnotationSet> basicTypeAnnotations;
    
    // Available annotations for bean references
    private final List<MetaAnnotation> refFieldAnnotations;
    
    // Available annotations for classes
    private final List<MetaAnnotation> classAnnotations;
    
    
    // State variables (they make this class thread-unsafe)
    private boolean addGroups = false;


    public Jsr303Annotator(JCodeModel cm) {
        this.codeModel = cm;
        this.groups = generateGroups();
        this.basicTypeAnnotations = buildBasicTypeAnnotations();
        this.refFieldAnnotations = buildRefFieldAnnotations();
        this.classAnnotations = buildClassAnnotations();
    }


    // Specific annotation methods ---------------------------------------------
    
    @Override
    public void annotate(MetaJavaBean mjb) {
        
        // If the bean should have groups
        addGroups = Config.CFG.shouldAddGroupsToBeanFields();
        
        // Annotate the fields
        for (AbstractMetaField amf : mjb.getFields()) {
            amf.accept(this);
        }

        // Annotate the java bean
        mjb.addAnnotation(classAnnotations.get(0));
        
        // Build and add the GroupSequence annotation if required
        MetaAnnotation gsAnnot = mjb.buildGroupSequenceAnnot();
        if ( gsAnnot != null ) {
            mjb.addAnnotation(gsAnnot);
        }
        
    }
    
    @Override
    public void annotate(JavaBeanRefField jbrf) {
        // Always annotate with @Valid
        MetaAnnotation valid = new MetaAnnotation(codeModel, Valid.class, AnnotationType.JSR_303, new HashMap<String, Object>());
        jbrf.addAnnotation(valid);

        // And a random annotation
        MetaAnnotation annotCopy = new MetaAnnotation(Config.CFG.getRandom(refFieldAnnotations));
        if ( addGroups ) {
            MetaGroup group = Config.CFG.getRandom(groups);
            jbrf.getOwner().addGroup(group);
            annotCopy.setGroup(group);
        }
        
        jbrf.addAnnotation(annotCopy);
    }

    @Override
    public void annotate(JavaBeanBasicField jbbf) {
        
        // Select a random set of annotations for the field's type
        BasicType type = jbbf.getBasicType();
        Jsr303MetaAnnotationSet mas = Config.CFG.getRandom(basicTypeAnnotations.get(type));

        // Annotate the field
        for (MetaAnnotation ma : mas.getAnnotations()) {
            MetaAnnotation maCopy = new MetaAnnotation(ma);
            
            if ( addGroups ) {
                MetaGroup group = Config.CFG.getRandom(groups);
                jbbf.getOwner().addGroup(group);
                maCopy.setGroup(group);
            }
            
            jbbf.addAnnotation(maCopy);
        }

        jbbf.setValidValues(mas.getValidValues());
        jbbf.setInvalidValues(mas.getInvalidValues());
    }

    
    // Init methods ------------------------------------------------------------

    /**
     * @return The list of groups that the annotator will use when assigning
     *         constraints.
     */
    private List<MetaGroup> generateGroups() {
        List<MetaGroup> groups = Lists.newArrayList();
        for (int i = 0; i < Config.CFG.getNumGroups(); ++i) {
            MetaGroup mg = new MetaGroup(codeModel, Config.CFG.getGroupsPackageName() + ".Group" + i);
            groups.add(mg);
        }
        return groups;
    }

    /**
     * @return A map of lists of annotation constraints available for every
     *         basic type.
     */
    private ListMultimap<BasicType, Jsr303MetaAnnotationSet> buildBasicTypeAnnotations() {

        ListMultimap<BasicType, Jsr303MetaAnnotationSet> anns = ArrayListMultimap.create();

        HashSet<MetaAnnotation> annotationsSet;
        HashMap<String, Object> annotParams;
        Jsr303MetaAnnotationSet maSet;

        // ### STRING ----------------------------------------------------------

        // @NotNull / "good" / null
        annotationsSet = Sets.newHashSet();
        annotParams = Maps.newHashMap();
        annotParams.put("message", "cannot be null");
        annotationsSet.add(new MetaAnnotation(codeModel, NotNull.class, AnnotationType.JSR_303, annotParams));
        anns.put(BasicType.STRING, new Jsr303MetaAnnotationSet(annotationsSet, Lists.newArrayList((Object) "good"), Lists.newArrayList((Object) null)));

        // [@NotNull, @Size(min=6,max=12)] / [ "goodgood" ] / [ null, "bad" ]
        annotationsSet = Sets.newHashSet();
        annotParams = Maps.newHashMap();
        annotParams.put("message", "cannot be null");
        annotationsSet.add(new MetaAnnotation(codeModel, NotNull.class, AnnotationType.JSR_303, annotParams));
        annotParams = Maps.newHashMap();
        annotParams.put("min", 6);
        annotParams.put("max", 12);
        annotationsSet.add(new MetaAnnotation(codeModel, Size.class, AnnotationType.JSR_303, annotParams));
        anns.put(BasicType.STRING, new Jsr303MetaAnnotationSet(annotationsSet, Lists.newArrayList((Object) "goodgood"), Lists.newArrayList((Object) null, "bad")));
        
        // @GoodCode
        annotationsSet = Sets.newHashSet();
        annotParams = Maps.newHashMap();
        JDefinedClass goodCode = buildTemplateConstraint("GoodCode");
        goodCode.annotate(Constraint.class).paramArray("validatedBy");
        goodCode.annotate(Pattern.class).param("regexp", ".*");
        JAnnotationArrayMember sizes = goodCode.annotate(Size.List.class).paramArray("value");
        sizes.annotate(Size.class).param("min", 5);
        sizes.annotate(Size.class).param("max", 8);
        goodCode.annotate(NotNull.class);
        annotationsSet.add(new MetaAnnotation(goodCode, AnnotationType.JSR_303, annotParams));
        anns.put(BasicType.STRING, new Jsr303MetaAnnotationSet(annotationsSet, Lists.newArrayList((Object) "goodcode", "1234123"), Lists.newArrayList((Object) null, "bad", "acodetoolong")));

        
        // ### INTEGER & INT ---------------------------------------------------

        // @NotNull / 3 / null
        annotationsSet = Sets.newHashSet();
        annotParams = Maps.newHashMap();
        annotParams.put("message", "cannot be null");
        annotationsSet.add(new MetaAnnotation(codeModel, NotNull.class, AnnotationType.JSR_303, annotParams));
        maSet = new Jsr303MetaAnnotationSet(annotationsSet, Lists.newArrayList((Object) 3), Lists.newArrayList((Object) null));
        anns.put(BasicType.INTEGER, maSet);
        // This one makes no sense for int

        // @Min(100) @Max(200) / [ 155, null ] / [ -100, 4000 ]
        annotationsSet = Sets.newHashSet();
        annotParams = Maps.newHashMap();
        annotParams.put("message", "must be bigger than {value}");
        annotParams.put("value", 100);
        annotationsSet.add(new MetaAnnotation(codeModel, Min.class, AnnotationType.JSR_303, annotParams));
        annotParams = Maps.newHashMap();
        annotParams.put("message", "must be less than {value}");
        annotParams.put("value", 200);
        annotationsSet.add(new MetaAnnotation(codeModel, Max.class, AnnotationType.JSR_303, annotParams));
        maSet =  new Jsr303MetaAnnotationSet(annotationsSet, Lists.newArrayList((Object) 155, null), Lists.newArrayList((Object) (-100), 4000));
        anns.put(BasicType.INTEGER, maSet);
        maSet.getValidValues().remove(null);
        anns.put(BasicType.INT, maSet);
        
        // @RangedInt
        annotationsSet = Sets.newHashSet();
        annotParams = Maps.newHashMap();
        JDefinedClass ranged = buildTemplateConstraint("RangedInt");
        ranged.annotate(Constraint.class).paramArray("validatedBy");
        ranged.annotate(Max.class).param("value", 3000);
        ranged.annotate(Min.class).param("value", 1000);
        ranged.annotate(ReportAsSingleViolation.class);
        annotationsSet.add(new MetaAnnotation(ranged, AnnotationType.JSR_303, annotParams));
        maSet = new Jsr303MetaAnnotationSet(annotationsSet, Lists.newArrayList((Object) 2500, 3000), Lists.newArrayList((Object) null, -20, 40000000));
        anns.put(BasicType.INTEGER, maSet);
        maSet.getInvalidValues().remove(null);
        anns.put(BasicType.INT, maSet);

        return anns;
    }
    
    /**
     * @return The list of constraints for fields that reference other beans.
     */
    private List<MetaAnnotation> buildRefFieldAnnotations() {
        List<MetaAnnotation> anns = Lists.newArrayList();
        HashMap<String, Object> annotParams;
        
        annotParams = Maps.newHashMap();
        anns.add(new MetaAnnotation(codeModel, NotNull.class, AnnotationType.JSR_303, annotParams));
        annotParams = Maps.newHashMap();
        anns.add(new MetaAnnotation(codeModel, Null.class, AnnotationType.JSR_303, annotParams));
        return anns;
    }

    /**
     * @return A list of the class level annotations that the annotator will
     *         use.
     */
    private List<MetaAnnotation> buildClassAnnotations() {

        List<MetaAnnotation> anns = Lists.newArrayList();
        HashMap<String, Object> annotParams;

        // AlwaysValid
        JDefinedClass alwaysValid = buildTemplateConstraint("AlwaysValid");
        JDefinedClass alwaysValidValidator = buildTemplateConstraintValidator("AlwaysValidValidator", alwaysValid, Object.class);
        JMethod isValid = getIsValidMethod(alwaysValidValidator);
        isValid.body()._return(JExpr.TRUE);
        alwaysValid.annotate(Constraint.class).param("validatedBy", alwaysValidValidator);
        
        annotParams = Maps.newHashMap();
        anns.add(new MetaAnnotation(alwaysValid, AnnotationType.JSR_303, annotParams));
        
        return anns;
    }
    
    
    private JMethod getIsValidMethod(JDefinedClass alwaysValidValidator) {
        for ( JMethod method : alwaysValidValidator.methods() ) {
            if ( method.name().equals("isValid") ) {
                return method;
            }
        }
        return null;
    }


    private JDefinedClass buildTemplateConstraint(String name) {
        try {
            JDefinedClass tplConstraint = codeModel._class(Config.CFG.getBasePackageName() + ".annot."+name, ClassType.ANNOTATION_TYPE_DECL);
            tplConstraint.annotate(Documented.class);
            tplConstraint.annotate(Retention.class).param("value", RetentionPolicy.RUNTIME);
            tplConstraint.annotate(Target.class).paramArray("value").param(ElementType.TYPE).param(ElementType.ANNOTATION_TYPE).param(ElementType.FIELD).param(ElementType.METHOD);
            
            // Using direct as I don't know how to build default { } with code model
            tplConstraint.direct("\n" + "    Class<?>[] groups() default {};\n" + "    String message() default \"Invalid value\";\n" + "    Class<? extends Payload>[] payload() default {};\n");
            
            // Hack to force the import of javax.validation.Payload
            tplConstraint.javadoc().addThrows((JClass) codeModel._ref(Payload.class)).add("Force import");
            
            return tplConstraint;
        } catch (JClassAlreadyExistsException e) {
            throw new RuntimeException("Tried to create an already existing class: " + name, e);
        }
    }
    
    private JDefinedClass buildTemplateConstraintValidator(String name, JDefinedClass constraint, Class<?> param) {
        try {
            JClass cv = (JClass) codeModel._ref(ConstraintValidator.class);
            cv = cv.narrow(constraint, (JClass) codeModel._ref(param));
            JDefinedClass validator = constraint._class(JMod.STATIC | JMod.PUBLIC, name);
            validator._implements(cv);
            validator.method(JMod.PUBLIC, void.class, "initialize").param(constraint, "parameters");
            JMethod isValid = validator.method(JMod.PUBLIC, boolean.class, "isValid");
            isValid.param(Object.class, "value");
            isValid.param(ConstraintValidatorContext.class, "context");
            return validator;
        } catch (JClassAlreadyExistsException e) {
            throw new RuntimeException("Tried to create an already existing class: " + name, e);
        }
    }

}
