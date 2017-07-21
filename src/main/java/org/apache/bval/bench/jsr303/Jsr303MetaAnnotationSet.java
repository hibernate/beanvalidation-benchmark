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

import java.util.List;
import java.util.Set;
import org.apache.bval.bench.metatree.MetaAnnotation;


/**
 * This class groups a set of JSR-303 {@link MetaAnnotation} with a list of
 * valid values and a list of invalid values for the stored combination of
 * annotations.
 * 
 * @author Carlos Vara
 */
public class Jsr303MetaAnnotationSet {

    // The annotations
    private final Set<MetaAnnotation> annotations;
    
    // A list of valid values
    private final List<Object> validValues;
    
    // A list of invalid values
    private final List<Object> invalidValues;
    
    
    public Jsr303MetaAnnotationSet(Set<MetaAnnotation> annotations, List<Object> validValues, List<Object> invalidValues) {
        this.annotations = annotations;
        this.validValues = validValues;
        this.invalidValues = invalidValues;
    }


    public Set<MetaAnnotation> getAnnotations() {
        return annotations;
    }

    public List<Object> getValidValues() {
        return validValues;
    }

    public List<Object> getInvalidValues() {
        return invalidValues;
    }    
    
}
