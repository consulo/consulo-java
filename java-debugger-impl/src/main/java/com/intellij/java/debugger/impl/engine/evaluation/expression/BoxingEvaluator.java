/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.debugger.impl.engine.evaluation.expression;

import java.util.ArrayList;
import java.util.List;

import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.JVMNameUtil;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.engine.evaluation.expression.Modifier;
import consulo.internal.com.sun.jdi.*;

import consulo.java.language.module.util.JavaClassNames;
import jakarta.annotation.Nullable;

/**
 * @author Eugene Zhuravlev
 * @since 2010-02-08
 */
public class BoxingEvaluator implements Evaluator {
    private final Evaluator myOperand;

    public BoxingEvaluator(Evaluator operand) {
        myOperand = DisableGC.create(operand);
    }

    @Override
    public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
        Object result = myOperand.evaluate(context);
        if (result == null || result instanceof ObjectReference) {
            return result;
        }

        if (result instanceof BooleanValue booleanValue) {
            return convertToWrapper(context, booleanValue, JavaClassNames.JAVA_LANG_BOOLEAN);
        }
        if (result instanceof ByteValue byteValue) {
            return convertToWrapper(context, byteValue, JavaClassNames.JAVA_LANG_BYTE);
        }
        if (result instanceof CharValue charValue) {
            return convertToWrapper(context, charValue, JavaClassNames.JAVA_LANG_CHARACTER);
        }
        if (result instanceof ShortValue shortValue) {
            return convertToWrapper(context, shortValue, JavaClassNames.JAVA_LANG_SHORT);
        }
        if (result instanceof IntegerValue integerValue) {
            return convertToWrapper(context, integerValue, JavaClassNames.JAVA_LANG_INTEGER);
        }
        if (result instanceof LongValue longValue) {
            return convertToWrapper(context, longValue, JavaClassNames.JAVA_LANG_LONG);
        }
        if (result instanceof FloatValue floatValue) {
            return convertToWrapper(context, floatValue, JavaClassNames.JAVA_LANG_FLOAT);
        }
        if (result instanceof DoubleValue doubleValue) {
            return convertToWrapper(context, doubleValue, JavaClassNames.JAVA_LANG_DOUBLE);
        }
        throw new EvaluateException("Cannot perform boxing conversion for a value of type " + ((Value)result).type().name());
    }

    @Nullable
    @Override
    public Modifier getModifier() {
        return null;
    }

    private static Value convertToWrapper(EvaluationContextImpl context, PrimitiveValue value, String wrapperTypeName)
        throws EvaluateException {
        DebugProcessImpl process = context.getDebugProcess();
        ClassType wrapperClass = (ClassType)process.findClass(context, wrapperTypeName, null);
        String methodSignature = "(" + JVMNameUtil.getPrimitiveSignature(value.type().name()) + ")" +
            "L" + wrapperTypeName.replace('.', '/') + ";";

        List<Method> methods = wrapperClass.methodsByName("valueOf", methodSignature);
        if (methods.size() == 0) { // older JDK version
            methods = wrapperClass.methodsByName("<init>", methodSignature);
        }
        if (methods.size() == 0) {
            throw new EvaluateException(
                "Cannot construct wrapper object for value of type " + value.type() +
                    ": Unable to find either valueOf() or constructor method"
            );
        }

        Method factoryMethod = methods.get(0);

        List<Value> args = new ArrayList<>();
        args.add(value);

        return process.invokeMethod(context, wrapperClass, factoryMethod, args);
    }
}
