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
 * User: anna
 * Date: 21-Mar-2008
 */
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.codeEditor.Editor;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.TargetElementUtil;
import consulo.language.editor.intention.PsiElementBaseIntentionAction;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;

public class SurroundWithArrayFix extends PsiElementBaseIntentionAction implements SyntheticIntentionAction {
    private final PsiCall myMethodCall;
    @Nullable
    private final PsiExpression myExpression;

    public SurroundWithArrayFix(@Nullable PsiCall methodCall, @Nullable PsiExpression expression) {
        myMethodCall = methodCall;
        myExpression = expression;
    }

    @Override
    @Nonnull
    public LocalizeValue getText() {
        return LocalizeValue.localizeTODO("Surround with array initialization");
    }

    @Override
    public boolean isAvailable(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) {
        return getExpression(element) != null;
    }

    @Nullable
    protected PsiExpression getExpression(PsiElement element) {
        if (myMethodCall == null || !myMethodCall.isValid()) {
            return myExpression == null || !myExpression.isValid() ? null : myExpression;
        }
        PsiElement method = myMethodCall.resolveMethod();
        if (method != null) {
            PsiMethod psiMethod = (PsiMethod) method;
            return checkMethod(element, psiMethod);
        }
        else if (myMethodCall instanceof PsiMethodCallExpression) {
            Collection<PsiElement> psiElements = TargetElementUtil.getTargetCandidates(((PsiMethodCallExpression) myMethodCall).getMethodExpression());
            for (PsiElement psiElement : psiElements) {
                if (psiElement instanceof PsiMethod) {
                    PsiExpression expression = checkMethod(element, (PsiMethod) psiElement);
                    if (expression != null) {
                        return expression;
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    private PsiExpression checkMethod(PsiElement element, PsiMethod psiMethod) {
        PsiParameter[] psiParameters = psiMethod.getParameterList().getParameters();
        PsiExpressionList argumentList = myMethodCall.getArgumentList();
        int idx = 0;
        for (PsiExpression expression : argumentList.getExpressions()) {
            if (element != null && PsiTreeUtil.isAncestor(expression, element, false)) {
                if (psiParameters.length > idx) {
                    PsiType paramType = psiParameters[idx].getType();
                    if (paramType instanceof PsiArrayType) {
                        PsiType expressionType = expression.getType();
                        if (expressionType != null) {
                            PsiType componentType = ((PsiArrayType) paramType).getComponentType();
                            if (expressionType.isAssignableFrom(componentType)) {
                                return expression;
                            }
                            PsiClass psiClass = PsiUtil.resolveClassInType(componentType);
                            if (ArrayUtil.find(psiMethod.getTypeParameters(), psiClass) != -1) {
                                for (PsiClassType superType : psiClass.getSuperTypes()) {
                                    if (TypeConversionUtil.isAssignable(superType, expressionType)) {
                                        return expression;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            idx++;
        }
        return null;
    }

    @Override
    public void invoke(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) throws IncorrectOperationException {
        if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) {
            return;
        }
        PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
        PsiExpression expression = getExpression(element);
        assert expression != null;
        PsiExpression toReplace = elementFactory.createExpressionFromText(getArrayCreation(expression), element);
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(expression.replace(toReplace));
    }

    private static String getArrayCreation(@Nonnull PsiExpression expression) {
        PsiType expressionType = expression.getType();
        assert expressionType != null;
        return "new " + expressionType.getCanonicalText() + "[]{" + expression.getText() + "}";
    }
}