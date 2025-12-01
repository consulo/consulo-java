/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.java.language.psi.infos.CandidateInfo;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.editor.intention.PsiElementBaseIntentionAction;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

import java.util.HashSet;
import java.util.Set;

public class QualifyThisArgumentFix extends PsiElementBaseIntentionAction {
    private final PsiThisExpression myExpression;
    private final PsiClass myPsiClass;

    public QualifyThisArgumentFix(@Nonnull PsiThisExpression expression, @Nonnull PsiClass psiClass) {
        myExpression = expression;
        myPsiClass = psiClass;
    }

    @Override
    @RequiredReadAction
    public boolean isAvailable(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) {
        if (!myExpression.isValid()) {
            return false;
        }
        if (!myPsiClass.isValid()) {
            return false;
        }
        setText(LocalizeValue.localizeTODO("Qualify this expression with \'" + myPsiClass.getQualifiedName() + "\'"));
        return true;
    }

    @Override
    @RequiredWriteAction
    public void invoke(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) throws IncorrectOperationException {
        myExpression.replace(RefactoringChangeUtil.createThisExpression(PsiManager.getInstance(project), myPsiClass));
    }

    public static void registerQuickFixAction(
        CandidateInfo[] candidates,
        PsiCall call,
        HighlightInfo.Builder highlightInfo,
        TextRange fixRange
    ) {
        if (candidates.length == 0) {
            return;
        }

        Set<PsiClass> containingClasses = new HashSet<>();
        PsiClass parentClass = PsiTreeUtil.getParentOfType(call, PsiClass.class);
        while (parentClass != null) {
            if (parentClass.isStatic()) {
                break;
            }
            if (!(parentClass instanceof PsiAnonymousClass)) {
                containingClasses.add(parentClass);
            }
            parentClass = PsiTreeUtil.getParentOfType(parentClass, PsiClass.class, true);
        }
        if (containingClasses.isEmpty()) {
            return;
        }

        PsiExpressionList list = call.getArgumentList();
        PsiExpression[] expressions = list.getExpressions();
        if (expressions.length == 0) {
            return;
        }

        for (int i1 = 0, expressionsLength = expressions.length; i1 < expressionsLength; i1++) {
            if (expressions[i1] instanceof PsiThisExpression thisExpr) {
                PsiType exprType = thisExpr.getType();
                for (CandidateInfo candidate : candidates) {
                    PsiMethod method = (PsiMethod)candidate.getElement();
                    PsiSubstitutor substitutor = candidate.getSubstitutor();
                    PsiParameter[] parameters = method.getParameterList().getParameters();
                    if (expressions.length != parameters.length) {
                        continue;
                    }

                    PsiParameter parameter = parameters[i1];

                    PsiType parameterType = substitutor.substitute(parameter.getType());
                    if (exprType == null || parameterType == null) {
                        continue;
                    }

                    if (!TypeConversionUtil.isAssignable(parameterType, exprType)) {
                        PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(parameterType);
                        if (psiClass != null && containingClasses.contains(psiClass)) {
                            highlightInfo.newFix(new QualifyThisArgumentFix(thisExpr, psiClass)).fixRange(fixRange).register();
                        }
                    }
                }
            }
        }
    }
}
