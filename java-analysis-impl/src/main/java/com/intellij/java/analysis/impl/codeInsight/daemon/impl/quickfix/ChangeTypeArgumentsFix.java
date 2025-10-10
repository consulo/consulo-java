/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.impl.source.resolve.DefaultParameterTypeInferencePolicy;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.codeEditor.Editor;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.HighPriorityAction;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class ChangeTypeArgumentsFix implements SyntheticIntentionAction, HighPriorityAction {
    private final PsiMethod myTargetMethod;
    private final PsiClass myPsiClass;
    private final PsiExpression[] myExpressions;
    private static final Logger LOG = Logger.getInstance(ChangeTypeArgumentsFix.class);
    private final PsiNewExpression myNewExpression;

    ChangeTypeArgumentsFix(
        @Nonnull PsiMethod targetMethod,
        PsiClass psiClass,
        @Nonnull PsiExpression[] expressions,
        @Nonnull PsiElement context
    ) {
        myTargetMethod = targetMethod;
        myPsiClass = psiClass;
        myExpressions = expressions;
        myNewExpression = PsiTreeUtil.getParentOfType(context, PsiNewExpression.class);
    }

    @Nonnull
    @Override
    public LocalizeValue getText() {
        PsiSubstitutor substitutor = inferTypeArguments();
        return LocalizeValue.localizeTODO("Change type arguments to <" + StringUtil.join(
            myPsiClass.getTypeParameters(),
            typeParameter -> {
                PsiType substituted = substitutor.substitute(typeParameter);
                return substituted != null ? substituted.getPresentableText() : CommonClassNames.JAVA_LANG_OBJECT;
            },
            ", "
        ) + ">");
    }

    @Override
    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        PsiTypeParameter[] typeParameters = myPsiClass.getTypeParameters();
        if (typeParameters.length > 0
            && myNewExpression != null && myNewExpression.isValid() && myNewExpression.getArgumentList() != null) {
            PsiJavaCodeReferenceElement reference = myNewExpression.getClassOrAnonymousClassReference();
            if (reference != null) {
                PsiReferenceParameterList parameterList = reference.getParameterList();
                if (parameterList != null) {
                    PsiSubstitutor substitutor = inferTypeArguments();
                    PsiParameter[] parameters = myTargetMethod.getParameterList().getParameters();
                    if (parameters.length != myExpressions.length) {
                        return false;
                    }
                    for (int i = 0, length = parameters.length; i < length; i++) {
                        PsiParameter parameter = parameters[i];
                        PsiType expectedType = substitutor.substitute(parameter.getType());
                        if (!myExpressions[i].isValid()) {
                            return false;
                        }
                        PsiType actualType = myExpressions[i].getType();
                        if (expectedType == null || actualType == null || !TypeConversionUtil.isAssignable(expectedType, actualType)) {
                            return false;
                        }
                    }
                    for (PsiTypeParameter parameter : typeParameters) {
                        if (substitutor.substitute(parameter) == null) {
                            return false;
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    @RequiredUIAccess
    public void invoke(@Nonnull Project project, Editor editor, PsiFile file) {
        if (!FileModificationService.getInstance().prepareFileForWrite(file)) {
            return;
        }

        PsiTypeParameter[] typeParameters = myPsiClass.getTypeParameters();
        PsiSubstitutor psiSubstitutor = inferTypeArguments();
        PsiJavaCodeReferenceElement reference = myNewExpression.getClassOrAnonymousClassReference();
        LOG.assertTrue(reference != null, myNewExpression);
        PsiReferenceParameterList parameterList = reference.getParameterList();
        LOG.assertTrue(parameterList != null, myNewExpression);
        PsiTypeElement[] elements = parameterList.getTypeParameterElements();
        for (int i = elements.length - 1; i >= 0; i--) {
            PsiTypeElement typeElement = elements[i];
            PsiType typeArg = psiSubstitutor.substitute(typeParameters[i]);
            typeElement.replace(JavaPsiFacade.getElementFactory(project).createTypeElement(typeArg));
        }
    }

    private PsiSubstitutor inferTypeArguments() {
        JavaPsiFacade facade = JavaPsiFacade.getInstance(myNewExpression.getProject());
        PsiResolveHelper resolveHelper = facade.getResolveHelper();
        PsiParameter[] parameters = myTargetMethod.getParameterList().getParameters();
        PsiExpressionList argumentList = myNewExpression.getArgumentList();
        LOG.assertTrue(argumentList != null);
        PsiExpression[] expressions = argumentList.getExpressions();
        return resolveHelper.inferTypeArguments(myPsiClass.getTypeParameters(), parameters, expressions,
            PsiSubstitutor.EMPTY,
            myNewExpression.getParent(),
            DefaultParameterTypeInferencePolicy.INSTANCE
        );
    }

    public static void registerIntentions(
        @Nonnull JavaResolveResult[] candidates,
        @Nonnull PsiExpressionList list,
        @Nullable HighlightInfo.Builder highlightInfo,
        PsiClass psiClass
    ) {
        if (highlightInfo == null || candidates.length == 0) {
            return;
        }
        PsiExpression[] expressions = list.getExpressions();
        for (JavaResolveResult candidate : candidates) {
            registerIntention(expressions, highlightInfo, psiClass, candidate, list);
        }
    }

    private static void registerIntention(
        @Nonnull PsiExpression[] expressions,
        @Nonnull HighlightInfo.Builder highlightInfo,
        PsiClass psiClass,
        @Nonnull JavaResolveResult candidate,
        @Nonnull PsiElement context
    ) {
        if (!candidate.isStaticsScopeCorrect()) {
            return;
        }
        PsiMethod method = (PsiMethod)candidate.getElement();
        if (method != null && context.getManager().isInProject(method)) {
            highlightInfo.registerFix(new ChangeTypeArgumentsFix(method, psiClass, expressions, context));
        }
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }
}
