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
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.impl.source.resolve.DefaultParameterTypeInferencePolicy;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author anna
 * @since 2012-02-17
 */
public class AddTypeArgumentsConditionalFix implements SyntheticIntentionAction {
    private static final Logger LOG = Logger.getInstance(AddTypeArgumentsConditionalFix.class);

    private final PsiSubstitutor mySubstitutor;
    private final PsiMethodCallExpression myExpression;
    private final PsiMethod myMethod;

    public AddTypeArgumentsConditionalFix(
        PsiSubstitutor substitutor,
        PsiMethodCallExpression expression,
        PsiMethod method
    ) {
        mySubstitutor = substitutor;
        myExpression = expression;
        myMethod = method;
    }

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return LocalizeValue.localizeTODO("Add explicit type arguments");
    }

    @Override
    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        return mySubstitutor.isValid() && myExpression.isValid() && myMethod.isValid();
    }

    @Override
    @RequiredUIAccess
    public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        PsiTypeParameter[] typeParameters = myMethod.getTypeParameters();
        String typeArguments = "<" + StringUtil.join(
            typeParameters,
            parameter -> {
                PsiType substituteTypeParam = mySubstitutor.substitute(parameter);
                LOG.assertTrue(substituteTypeParam != null);
                return GenericsUtil.eliminateWildcards(substituteTypeParam).getCanonicalText();
            },
            ", "
        ) + ">";
        final PsiExpression expression = myExpression.getMethodExpression().getQualifierExpression();
        String withTypeArgsText;
        if (expression != null) {
            withTypeArgsText = expression.getText();
        }
        else if (isInStaticContext(myExpression, null) || myMethod.isStatic()) {
            final PsiClass aClass = myMethod.getContainingClass();
            LOG.assertTrue(aClass != null);
            withTypeArgsText = aClass.getQualifiedName();
        }
        else {
            withTypeArgsText = "this";
        }
        withTypeArgsText += "." + typeArguments + myExpression.getMethodExpression().getReferenceName();
        final PsiExpression withTypeArgs = JavaPsiFacade.getElementFactory(project)
            .createExpressionFromText(withTypeArgsText + myExpression.getArgumentList().getText(), myExpression);
        myExpression.replace(withTypeArgs);
    }

    public static boolean isInStaticContext(PsiElement element, @Nullable final PsiClass aClass) {
        return PsiUtil.getEnclosingStaticElement(element, aClass) != null;
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }

    @RequiredReadAction
    public static void register(@Nonnull HighlightInfo.Builder hlBuilder, PsiExpression expression, @Nonnull PsiType lType) {
        if (lType != PsiType.NULL && expression instanceof PsiConditionalExpression condExpr) {
            PsiExpression thenExpression = condExpr.getThenExpression();
            PsiExpression elseExpression = condExpr.getElseExpression();
            if (thenExpression != null && elseExpression != null) {
                PsiType thenType = thenExpression.getType();
                PsiType elseType = elseExpression.getType();
                if (thenType != null && elseType != null) {
                    boolean thenAssignable = TypeConversionUtil.isAssignable(lType, thenType);
                    boolean elseAssignable = TypeConversionUtil.isAssignable(lType, elseType);
                    if (!thenAssignable && thenExpression instanceof PsiMethodCallExpression) {
                        inferTypeArgs(hlBuilder, lType, thenExpression);
                    }
                    if (!elseAssignable && elseExpression instanceof PsiMethodCallExpression) {
                        inferTypeArgs(hlBuilder, lType, elseExpression);
                    }
                }
            }
        }
    }

    @RequiredReadAction
    private static void inferTypeArgs(@Nonnull HighlightInfo.Builder highlightInfo, PsiType lType, PsiExpression thenExpression) {
        PsiMethodCallExpression thenMethodCall = (PsiMethodCallExpression) thenExpression;
        JavaResolveResult result = thenMethodCall.resolveMethodGenerics();
        PsiMethod method = (PsiMethod) result.getElement();
        if (method != null) {
            PsiType returnType = method.getReturnType();
            PsiClass aClass = method.getContainingClass();
            if (returnType != null && aClass != null && aClass.getQualifiedName() != null) {
                JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(method.getProject());
                PsiDeclarationStatement variableDeclarationStatement = javaPsiFacade.getElementFactory()
                    .createVariableDeclarationStatement("xxx", lType, thenExpression);
                PsiExpression initializer = ((PsiLocalVariable) variableDeclarationStatement.getDeclaredElements()[0]).getInitializer();
                LOG.assertTrue(initializer != null);

                PsiSubstitutor substitutor = javaPsiFacade.getResolveHelper().inferTypeArguments(
                    method.getTypeParameters(),
                    method.getParameterList().getParameters(),
                    thenMethodCall.getArgumentList().getExpressions(),
                    PsiSubstitutor.EMPTY,
                    initializer,
                    DefaultParameterTypeInferencePolicy.INSTANCE
                );
                PsiType substitutedType = substitutor.substitute(returnType);
                if (substitutedType != null && TypeConversionUtil.isAssignable(lType, substitutedType)) {
                    highlightInfo.newFix(new AddTypeArgumentsConditionalFix(substitutor, thenMethodCall, method))
                        .fixRange(thenExpression.getTextRange())
                        .register();
                }
            }
        }
    }
}
