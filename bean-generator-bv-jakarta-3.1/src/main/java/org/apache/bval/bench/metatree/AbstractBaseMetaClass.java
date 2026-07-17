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

import com.sun.codemodel.ClassType;
import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;


/**
 * Wraps a {@link JDefinedClass}. This class is extended to represent classes
 * and interfaces (and may be extended to represent @interfaces and enums too).
 * 
 * @author Carlos Vara
 */
public abstract class AbstractBaseMetaClass {

    // The wrapped JDefinedClass
    private JDefinedClass generatedClass;
    
    public AbstractBaseMetaClass(JCodeModel cm, String fqn, ClassType ct) {
        try {
            this.generatedClass = cm._class(fqn, ct);
        } catch (JClassAlreadyExistsException e) {
            throw new IllegalStateException("Class: " + fqn + " already exists.", e);
        }
    }

    /**
     * @return The wrapped generated class.
     */
    public JDefinedClass getGeneratedClass() {
        return generatedClass;
    }

    /**
     * @return The unqualified name of the class.
     */
    public String getName() {
        return generatedClass.name();
    }
    
    /**
     * @return The name of the package in which this class is placed.
     */
    public String getPackageName() {
        return generatedClass.getPackage().name();
    }

    // TODO: equals and hashCode are coded in a defensive manner, with the current
    //  contract, generatedClass can never be null
    
    @Override
    public boolean equals(Object obj) {
        if ( (obj == null) || !(obj instanceof AbstractBaseMetaClass) ) {
            return false;
        }
        AbstractBaseMetaClass otherAbmc = (AbstractBaseMetaClass)obj;
        if ( this.generatedClass == null ) {
            return otherAbmc.generatedClass == null;
        }
        else {
            return this.generatedClass.equals(otherAbmc.generatedClass);
        }
    }

    @Override
    public int hashCode() {
        if ( this.generatedClass != null ) {
            return this.generatedClass.hashCode();
        }
        else {
            return super.hashCode();
        }
    }

}
