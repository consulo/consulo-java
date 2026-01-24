// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.psi.impl;

import com.intellij.debugger.streams.psi.ChainDetector;
import com.intellij.java.language.jvm.JvmModifier;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;

/**
 * @author Vitaliy.Bibaev
 */
public class InheritanceBasedChainDetector implements ChainDetector {
    private final String baseClassName;

    public InheritanceBasedChainDetector(String baseClassName) {
        this.baseClassName = baseClassName;
    }

    @Override
    public boolean isTerminationCall(PsiMethodCallExpression callExpression) {
        PsiMethod method = callExpression.resolveMethod();
        if (method == null) {
            return false;
        }
        PsiExpression qualifierExpression = callExpression.getMethodExpression().getQualifierExpression();
        PsiType qualifierType = qualifierExpression != null ? qualifierExpression.getType() : null;
        PsiClass parentClass = method.getParent() instanceof PsiClass ? (PsiClass) method.getParent() : null;
        return (isStreamType(qualifierType) || isStreamType(parentClass)) && !isStreamType(method.getReturnType());
    }

    @Override
    public boolean isIntermediateCall(PsiMethodCallExpression callExpression) {
        PsiMethod method = callExpression.resolveMethod();
        if (method == null) {
            return false;
        }
        PsiClass parentClass = method.getParent() instanceof PsiClass ? (PsiClass) method.getParent() : null;
        PsiMethod resolvedMethod = callExpression.resolveMethod();
        PsiType returnType = resolvedMethod != null ? resolvedMethod.getReturnType() : null;
        return !isStatic(method) && isStreamType(parentClass) && isStreamType(returnType);
    }

    private boolean isStreamType(PsiType type) {
        return InheritanceUtil.isInheritor(type, baseClassName);
    }

    private boolean isStreamType(PsiClass type) {
        return InheritanceUtil.isInheritor(type, baseClassName);
    }

    private static boolean isStatic(PsiMethod method) {
        return method.hasModifier(JvmModifier.STATIC);
    }
}
