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
import java.util.Optional;
import java.util.Set;
import org.apache.bval.bench.Config;
import com.helger.jcodemodel.AbstractJClass;
import com.helger.jcodemodel.JCodeModel;


/**
 * Basic type property types.
 *
 * @author Carlos Vara
 */
public enum BasicType {

    STRING {
        @Override
        public Class<?> getTypeClass() {
            return String.class;
        }
    },
    INTEGER {
        @Override
        public Class<?> getTypeClass() {
            return Integer.class;
        }
    },
    INT {
        @Override
        public Class<?> getTypeClass() {
            return int.class;
        }
    },
    LIST_OF_STRING {
        @Override
        public Class<?> getTypeClass() {
            return String.class;
        }
        @Override
        public AbstractJClass getType(JCodeModel cm) {
            return cm.ref(List.class).narrow(cm.ref(String.class));
        }
        @Override
        public AbstractJClass getElementType(JCodeModel cm) {
            return cm.ref(String.class);
        }
        @Override
        public AbstractJClass getContainerClass(JCodeModel cm) {
            return cm.ref(List.class);
        }
        @Override
        public boolean isCollectionType() { return true; }
    },
    SET_OF_INTEGER {
        @Override
        public Class<?> getTypeClass() {
            return Integer.class;
        }
        @Override
        public AbstractJClass getType(JCodeModel cm) {
            return cm.ref(Set.class).narrow(cm.ref(Integer.class));
        }
        @Override
        public AbstractJClass getElementType(JCodeModel cm) {
            return cm.ref(Integer.class);
        }
        @Override
        public AbstractJClass getContainerClass(JCodeModel cm) {
            return cm.ref(Set.class);
        }
        @Override
        public boolean isCollectionType() { return true; }
    },
    OPTIONAL_STRING {
        @Override
        public Class<?> getTypeClass() {
            return String.class;
        }
        @Override
        public AbstractJClass getType(JCodeModel cm) {
            return cm.ref(Optional.class).narrow(cm.ref(String.class));
        }
        @Override
        public AbstractJClass getElementType(JCodeModel cm) {
            return cm.ref(String.class);
        }
        @Override
        public AbstractJClass getContainerClass(JCodeModel cm) {
            return cm.ref(Optional.class);
        }
        @Override
        public boolean isCollectionType() { return true; }
    };


    private static final List<BasicType> SCALAR_VALUES = List.of(STRING, INTEGER, INT);
    private static final List<BasicType> TYPE_USE_VALUES = List.of(LIST_OF_STRING, SET_OF_INTEGER, OPTIONAL_STRING);

    public abstract Class<?> getTypeClass();

    public AbstractJClass getType(JCodeModel cm) {
        return cm.ref(getTypeClass());
    }

    public AbstractJClass getElementType(JCodeModel cm) {
        return null;
    }

    public AbstractJClass getContainerClass(JCodeModel cm) {
        return null;
    }

    public boolean isCollectionType() {
        return false;
    }

    public static BasicType getRandom() {
        return Config.CFG.getRandom(SCALAR_VALUES);
    }

    public static BasicType getRandomTypeUse() {
        return Config.CFG.getRandom(TYPE_USE_VALUES);
    }

}
