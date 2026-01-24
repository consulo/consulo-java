/*
 * Copyright 2013-2026 consulo.io
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

package com.intellij.java.impl.refactoring.util;

import com.intellij.java.analysis.impl.codeInspection.SideEffectChecker;
import com.intellij.java.impl.refactoring.introduceField.ElementToWorkOn;
import com.intellij.java.impl.refactoring.introduceVariable.IntroduceVariableHandler;
import com.intellij.java.language.psi.*;
import consulo.application.Application;
import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;

import java.util.ArrayList;
import java.util.List;

public class LambdaRefactoringUtilImpl {

    /**
     * Works for expression lambdas/one statement code block lambdas to ensures equivalent method ref -> lambda transformation.
     */
    public static void removeSideEffectsFromLambdaBody(Editor editor, PsiLambdaExpression lambdaExpression) {
        if (lambdaExpression != null && lambdaExpression.isValid()) {
            PsiElement body = lambdaExpression.getBody();
            PsiExpression methodCall = LambdaUtil.extractSingleExpressionFromBody(body);
            PsiExpression qualifierExpression = null;
            if (methodCall instanceof PsiMethodCallExpression methodCallExpression) {
                qualifierExpression = methodCallExpression.getMethodExpression().getQualifierExpression();
            }
            else if (methodCall instanceof PsiNewExpression newExpression) {
                qualifierExpression = newExpression.getQualifier();
            }

            if (qualifierExpression != null) {
                List<PsiElement> sideEffects = new ArrayList<>();
                SideEffectChecker.checkSideEffects(qualifierExpression, sideEffects);
                if (!sideEffects.isEmpty()) {
                    if (Application.get().isUnitTestMode() || Messages.showYesNoDialog(
                        lambdaExpression.getProject(),
                        "There are possible side effects found in method reference qualifier.\nIntroduce local variable?",
                        "Side Effects Detected",
                        UIUtil.getQuestionIcon()
                    ) == Messages.YES) {
                        //ensure introduced before lambda
                        qualifierExpression.putUserData(ElementToWorkOn.PARENT, lambdaExpression);
                        new IntroduceVariableHandler().invoke(qualifierExpression.getProject(), editor, qualifierExpression);
                    }
                }
            }
        }
    }
}
