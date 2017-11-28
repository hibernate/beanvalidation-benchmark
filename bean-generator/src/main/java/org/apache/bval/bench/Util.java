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
package org.apache.bval.bench;

import com.sun.codemodel.JAnnotationArrayMember;
import com.sun.codemodel.JAnnotationUse;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JType;


/**
 * Convenience methods to facilitate the work with primitive types and
 * CodeModel's literal expresions.
 * 
 * @author Carlos Vara
 */
public final class Util {

    private Util() {
        // No instances please
    }

    /**
     * Calls the correct {@link JExpr} <code>lit</code> method.
     * 
     * @param value
     *            The literal value that must be output in an expresion.
     * @return The matching expression for the real type of value.
     */
    public static JExpression literalExpr(Object value) {
        if (value == null) {
            return JExpr._null();
        } else if (value instanceof String) {
            return JExpr.lit((String) value);
        } else if (value instanceof Integer) {
            return JExpr.lit((Integer) value);
        }

        throw new RuntimeException("Impossible to construct initial value for: " + value);
    }


    /**
     * Calls the correct {@link JAnnotationUse} <code>param</code> method.
     * 
     * @param annot
     *            The annotation in which the parameter will be added.
     * @param key
     *            The parameter's key.
     * @param value
     *            The parameter's value.
     */
    public static void addAnnotParam(JAnnotationUse annot, String key, Object value) {
        if (value == null) {
            // Null values not accepted
            throw new RuntimeException("Null values not supported as annotation parameters");
        } else if (value instanceof Boolean) {
            annot.param(key, (Boolean) value);
        } else if (value instanceof Integer) {
            annot.param(key, (Integer) value);
        } else if (value instanceof String) {
            annot.param(key, (String) value);
        } else if (value instanceof Class<?>) {
            annot.param(key, (Class<?>) value);
        } else if (value instanceof JType) {
            annot.param(key, (JType) value);
        } else if (value.getClass().isArray()) {
            Object[] valueArr = (Object[]) value;
            JAnnotationArrayMember aux = annot.paramArray(key);
            for (int i = 0; i < valueArr.length; ++i) {
                addAnnotArrayParam(aux, valueArr[i]);
            }
        } else {
            throw new RuntimeException("Impossible to construct annotation param for: " + value);
        }
    }

    private static void addAnnotArrayParam(JAnnotationArrayMember aux, Object value) {
        if (value instanceof JType) {
            aux.param((JType) value);
            // return aux.param((JType)value); ?? No Javadoc defining how to use the API
        } else {
            throw new RuntimeException("Impossible to construct annotation param array for: " + value);
        }
    }

}
