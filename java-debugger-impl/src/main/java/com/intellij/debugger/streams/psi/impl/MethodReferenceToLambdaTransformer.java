// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.psi.impl;

import com.intellij.debugger.streams.psi.PsiElementTransformer;
import com.intellij.java.analysis.impl.refactoring.util.LambdaRefactoringUtil;
import com.intellij.java.language.psi.JavaRecursiveElementVisitor;
import com.intellij.java.language.psi.PsiMethodReferenceExpression;
import consulo.language.psi.PsiElementVisitor;

/**
 * @author Vitaliy.Bibaev
 */
public final class MethodReferenceToLambdaTransformer extends PsiElementTransformer.Base {
    public static final MethodReferenceToLambdaTransformer INSTANCE = new MethodReferenceToLambdaTransformer();

    private MethodReferenceToLambdaTransformer() {
    }

    @Override
    protected PsiElementVisitor getVisitor() {
        return new JavaRecursiveElementVisitor() {
            @Override
            public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
                super.visitMethodReferenceExpression(expression);
                LambdaRefactoringUtil.convertMethodReferenceToLambda(expression, false, true);
            }
        };
    }
}
