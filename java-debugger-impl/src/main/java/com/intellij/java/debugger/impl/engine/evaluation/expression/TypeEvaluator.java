/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Class TypeEvaluator
 * @author Jeka
 */
package com.intellij.java.debugger.impl.engine.evaluation.expression;

import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.java.debugger.engine.evaluation.expression.Modifier;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.JVMName;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import consulo.internal.com.sun.jdi.ReferenceType;

public class TypeEvaluator implements Evaluator {
    private final JVMName myTypeName;

    public TypeEvaluator(JVMName typeName) {
        myTypeName = typeName;
    }

    @Override
    public Modifier getModifier() {
        return null;
    }

    /**
     * @return ReferenceType in the target VM, with the given fully qualified name
     */
    public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
        DebugProcessImpl debugProcess = context.getDebugProcess();
        String typeName = myTypeName.getName(debugProcess);
        final ReferenceType type = debugProcess.findClass(context, typeName, context.getClassLoader());
        if (type == null) {
            throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerLocalize.errorClassNotLoaded(typeName));
        }
        return type;
    }
}
