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
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.HighPriorityAction;
import consulo.language.editor.intention.QuickFixAction;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.project.Project;
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

    @Override
    @Nonnull
    public String getText() {
        final PsiSubstitutor substitutor = inferTypeArguments();
        return "Change type arguments to <" + StringUtil.join(myPsiClass.getTypeParameters(), typeParameter -> {
            final PsiType substituted = substitutor.substitute(typeParameter);
            return substituted != null ? substituted.getPresentableText() : JavaClassNames.JAVA_LANG_OBJECT;
        }, ", ") + ">";
    }

    @Override
    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        final PsiTypeParameter[] typeParameters = myPsiClass.getTypeParameters();
        if (typeParameters.length > 0) {
            if (myNewExpression != null && myNewExpression.isValid() && myNewExpression.getArgumentList() != null) {
                final PsiJavaCodeReferenceElement reference = myNewExpression.getClassOrAnonymousClassReference();
                if (reference != null) {
                    final PsiReferenceParameterList parameterList = reference.getParameterList();
                    if (parameterList != null) {
                        final PsiSubstitutor substitutor = inferTypeArguments();
                        final PsiParameter[] parameters = myTargetMethod.getParameterList().getParameters();
                        if (parameters.length != myExpressions.length) {
                            return false;
                        }
                        for (int i = 0, length = parameters.length; i < length; i++) {
                            PsiParameter parameter = parameters[i];
                            final PsiType expectedType = substitutor.substitute(parameter.getType());
                            if (!myExpressions[i].isValid()) {
                                return false;
                            }
                            final PsiType actualType = myExpressions[i].getType();
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
        }
        return false;
    }

    @Override
    public void invoke(@Nonnull final Project project, Editor editor, final PsiFile file) {
        if (!FileModificationService.getInstance().prepareFileForWrite(file)) {
            return;
        }

        final PsiTypeParameter[] typeParameters = myPsiClass.getTypeParameters();
        final PsiSubstitutor psiSubstitutor = inferTypeArguments();
        final PsiJavaCodeReferenceElement reference = myNewExpression.getClassOrAnonymousClassReference();
        LOG.assertTrue(reference != null, myNewExpression);
        final PsiReferenceParameterList parameterList = reference.getParameterList();
        LOG.assertTrue(parameterList != null, myNewExpression);
        PsiTypeElement[] elements = parameterList.getTypeParameterElements();
        for (int i = elements.length - 1; i >= 0; i--) {
            PsiTypeElement typeElement = elements[i];
            final PsiType typeArg = psiSubstitutor.substitute(typeParameters[i]);
            typeElement.replace(JavaPsiFacade.getElementFactory(project).createTypeElement(typeArg));
        }
    }

    private PsiSubstitutor inferTypeArguments() {
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(myNewExpression.getProject());
        final PsiResolveHelper resolveHelper = facade.getResolveHelper();
        final PsiParameter[] parameters = myTargetMethod.getParameterList().getParameters();
        final PsiExpressionList argumentList = myNewExpression.getArgumentList();
        LOG.assertTrue(argumentList != null);
        final PsiExpression[] expressions = argumentList.getExpressions();
        return resolveHelper.inferTypeArguments(myPsiClass.getTypeParameters(), parameters, expressions,
            PsiSubstitutor.EMPTY,
            myNewExpression.getParent(),
            DefaultParameterTypeInferencePolicy.INSTANCE
        );
    }

    public static void registerIntentions(
        @Nonnull JavaResolveResult[] candidates,
        @Nonnull PsiExpressionList list,
        @Nullable HighlightInfo highlightInfo,
        PsiClass psiClass
    ) {
        if (candidates.length == 0) {
            return;
        }
        PsiExpression[] expressions = list.getExpressions();
        for (JavaResolveResult candidate : candidates) {
            registerIntention(expressions, highlightInfo, psiClass, candidate, list);
        }
    }

    private static void registerIntention(
        @Nonnull PsiExpression[] expressions,
        @Nullable HighlightInfo highlightInfo,
        PsiClass psiClass,
        @Nonnull JavaResolveResult candidate,
        @Nonnull PsiElement context
    ) {
        if (!candidate.isStaticsScopeCorrect()) {
            return;
        }
        PsiMethod method = (PsiMethod)candidate.getElement();
        if (method != null && context.getManager().isInProject(method)) {
            final ChangeTypeArgumentsFix fix = new ChangeTypeArgumentsFix(method, psiClass, expressions, context);
            QuickFixAction.registerQuickFixAction(highlightInfo, null, fix);
        }
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }
}
