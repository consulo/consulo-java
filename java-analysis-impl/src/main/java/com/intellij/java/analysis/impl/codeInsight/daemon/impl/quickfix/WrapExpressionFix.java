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
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author ven
 */
public class WrapExpressionFix implements SyntheticIntentionAction {
    private final PsiExpression myExpression;
    private final PsiClassType myExpectedType;
    private final boolean myPrimitiveExpected;

    public WrapExpressionFix(PsiType expectedType, PsiExpression expression) {
        myExpression = expression;
        myExpectedType = getClassType(expectedType, expression);
        myPrimitiveExpected = expectedType instanceof PsiPrimitiveType;
    }

    @Nullable
    private static PsiClassType getClassType(PsiType type, PsiElement place) {
        if (type instanceof PsiClassType classType) {
            return classType;
        }
        else if (type instanceof PsiPrimitiveType primitiveType) {
            return primitiveType.getBoxedType(place.getManager(), GlobalSearchScope.allScope(place.getProject()));
        }
        return null;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public LocalizeValue getText() {
        PsiMethod wrapper = myExpression.isValid() && myExpectedType != null
            ? findWrapper(myExpression.getType(), myExpectedType, myPrimitiveExpected)
            : null;
        String methodPresentation = wrapper != null ? wrapper.getContainingClass().getName() + "." + wrapper.getName() : "";
        return JavaQuickFixLocalize.wrapExpressionUsingStaticAccessorText(methodPresentation);
    }

    @Nullable
    private static PsiMethod findWrapper(PsiType type, @Nonnull PsiClassType expectedType, boolean primitiveExpected) {
        PsiClass aClass = expectedType.resolve();
        if (aClass != null) {
            PsiType expectedReturnType = expectedType;
            if (primitiveExpected) {
                expectedReturnType = PsiPrimitiveType.getUnboxedType(expectedType);
            }
            if (expectedReturnType == null) {
                return null;
            }
            PsiMethod[] methods = aClass.getMethods();
            Set<PsiMethod> wrapperMethods = new LinkedHashSet<>();
            for (PsiMethod method : methods) {
                if (method.isStatic()
                    && method.getParameterList().getParametersCount() == 1
                    && method.getParameterList().getParameters()[0].getType().isAssignableFrom(type)
                    && method.getReturnType() != null
                    && expectedReturnType.equals(method.getReturnType())) {
                    String methodName = method.getName();
                    if (methodName.startsWith("parse") || methodName.equals("valueOf")) {
                        return method;
                    }
                    wrapperMethods.add(method);
                }
            }
            if (!wrapperMethods.isEmpty()) {
                return wrapperMethods.iterator().next();
            }
        }

        return null;
    }

    @Override
    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        return myExpression.isValid()
            && myExpression.getManager().isInProject(myExpression)
            && myExpectedType != null
            && myExpectedType.isValid()
            && myExpression.getType() != null
            && findWrapper(myExpression.getType(), myExpectedType, myPrimitiveExpected) != null;
    }

    @Override
    @RequiredWriteAction
    public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        if (!FileModificationService.getInstance().prepareFileForWrite(file)) {
            return;
        }
        PsiMethod wrapper = findWrapper(myExpression.getType(), myExpectedType, myPrimitiveExpected);
        assert wrapper != null;
        PsiElementFactory factory = JavaPsiFacade.getInstance(file.getProject()).getElementFactory();
        String methodCallText = "Foo." + wrapper.getName() + "()";
        PsiMethodCallExpression call = (PsiMethodCallExpression)factory.createExpressionFromText(methodCallText, null);
        call.getArgumentList().add(myExpression);
        ((PsiReferenceExpression)call.getMethodExpression().getQualifierExpression())
            .bindToElement(wrapper.getContainingClass());
        myExpression.replace(call);
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }

    @RequiredReadAction
    public static void registerWrapAction(
        JavaResolveResult[] candidates,
        PsiExpression[] expressions,
        @Nullable HighlightInfo.Builder hlBuilder
    ) {
        if (hlBuilder == null) {
            return;
        }
        PsiType expectedType = null;
        PsiExpression expr = null;

        nextMethod:
        for (int i = 0; i < candidates.length && expectedType == null; i++) {
            JavaResolveResult candidate = candidates[i];
            PsiSubstitutor substitutor = candidate.getSubstitutor();
            PsiElement element = candidate.getElement();
            assert element != null;
            PsiMethod method = (PsiMethod)element;
            PsiParameter[] parameters = method.getParameterList().getParameters();
            if (!method.isVarArgs() && parameters.length != expressions.length) {
                continue;
            }
            for (int j = 0; j < expressions.length; j++) {
                PsiExpression expression = expressions[j];
                PsiType exprType = expression.getType();
                if (exprType != null) {
                    PsiType paramType = parameters[Math.min(j, parameters.length - 1)].getType();
                    if (paramType instanceof PsiEllipsisType ellipsisType) {
                        paramType = ellipsisType.getComponentType();
                    }
                    paramType = substitutor != null ? substitutor.substitute(paramType) : paramType;
                    if (paramType.isAssignableFrom(exprType)) {
                        continue;
                    }
                    PsiClassType classType = getClassType(paramType, expression);
                    if (expectedType == null && classType != null
                        && findWrapper(exprType, classType, paramType instanceof PsiPrimitiveType) != null) {
                        expectedType = paramType;
                        expr = expression;
                    }
                    else {
                        expectedType = null;
                        expr = null;
                        continue nextMethod;
                    }
                }
            }
        }

        if (expectedType != null) {
            hlBuilder.registerFix(new WrapExpressionFix(expectedType, expr), expr.getTextRange());
        }
    }
}
