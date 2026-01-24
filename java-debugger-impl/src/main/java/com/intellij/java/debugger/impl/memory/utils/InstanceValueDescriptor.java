// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.memory.utils;

import com.intellij.java.debugger.DebuggerContext;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.java.language.psi.PsiExpression;
import consulo.internal.com.sun.jdi.ArrayReference;
import consulo.internal.com.sun.jdi.ObjectReference;
import consulo.internal.com.sun.jdi.Value;
import consulo.project.Project;

public class InstanceValueDescriptor extends ValueDescriptorImpl {

    public InstanceValueDescriptor(Project project, Value value) {
        super(project, value);
    }

    @Override
    public String calcValueName() {
        ObjectReference ref = ((ObjectReference) getValue());
        if (ref instanceof ArrayReference arrayReference) {
            return NamesUtils.getArrayUniqueName(arrayReference);
        }
        return NamesUtils.getUniqueName(ref);
    }

    @Override
    public Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException {
        return getValue();
    }

    @Override
    public boolean isShowIdLabel() {
        return false;
    }

    @Override
    public PsiExpression getDescriptorEvaluation(DebuggerContext debuggerContext) throws EvaluateException {
        throw new NeedMarkException((ObjectReference) getValue());
    }
}
