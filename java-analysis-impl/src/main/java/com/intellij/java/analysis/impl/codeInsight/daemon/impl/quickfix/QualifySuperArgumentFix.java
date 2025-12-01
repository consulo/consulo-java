/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/**
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Nov 13, 2002
 * Time: 3:26:50 PM
 * To change this template use Options | File Templates.
 */
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.language.impl.refactoring.util.RefactoringChangeUtil;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.psi.PsiManager;
import consulo.language.psi.util.PsiTreeUtil;
import jakarta.annotation.Nonnull;

public class QualifySuperArgumentFix extends QualifyThisOrSuperArgumentFix {
    public QualifySuperArgumentFix(@Nonnull PsiExpression expression, @Nonnull PsiClass psiClass) {
        super(expression, psiClass);
    }

    @Override
    protected String getQualifierText() {
        return "super";
    }

    @Override
    @RequiredWriteAction
    protected PsiExpression getQualifier(PsiManager manager) {
        return RefactoringChangeUtil.createSuperExpression(manager, myPsiClass);
    }

    @RequiredWriteAction
    public static void registerQuickFixAction(@Nonnull PsiSuperExpression expr, HighlightInfo.Builder hlBuilder) {
        LOG.assertTrue(expr.getQualifier() == null);
        PsiClass containingClass = PsiTreeUtil.getParentOfType(expr, PsiClass.class);
        if (containingClass != null && containingClass.isInterface()) {
            PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(expr, PsiMethodCallExpression.class);
            if (callExpression != null) {
                PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(callExpression.getProject());
                for (PsiClass superClass : containingClass.getSupers()) {
                    if (superClass.isInterface()) {
                        PsiMethodCallExpression copy = (PsiMethodCallExpression) callExpression.copy();
                        PsiExpression superQualifierCopy = copy.getMethodExpression().getQualifierExpression();
                        LOG.assertTrue(superQualifierCopy != null);
                        superQualifierCopy.delete();
                        PsiMethodCallExpression expressionFromText =
                            (PsiMethodCallExpression) elementFactory.createExpressionFromText(copy.getText(), superClass);
                        if (expressionFromText.resolveMethod() != null) {
                            hlBuilder.registerFix(new QualifySuperArgumentFix(expr, superClass));
                        }
                    }
                }
            }
        }
    }
}
