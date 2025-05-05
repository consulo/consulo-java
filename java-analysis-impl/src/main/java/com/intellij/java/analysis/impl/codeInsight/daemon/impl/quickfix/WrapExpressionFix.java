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
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.QuickFixAction;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import org.jetbrains.annotations.NonNls;

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
        if (type instanceof PsiClassType) {
            return (PsiClassType)type;
        }
        else if (type instanceof PsiPrimitiveType) {
            return ((PsiPrimitiveType)type).getBoxedType(place.getManager(), GlobalSearchScope.allScope(place.getProject()));
        }
        return null;
    }

    @Override
    @Nonnull
    public String getText() {
        final PsiMethod wrapper = myExpression.isValid() && myExpectedType != null
            ? findWrapper(myExpression.getType(), myExpectedType, myPrimitiveExpected)
            : null;
        final String methodPresentation = wrapper != null ? wrapper.getContainingClass().getName() + "." + wrapper.getName() : "";
        return JavaQuickFixBundle.message("wrap.expression.using.static.accessor.text", methodPresentation);
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
            final Set<PsiMethod> wrapperMethods = new LinkedHashSet<PsiMethod>();
            for (PsiMethod method : methods) {
                if (method.hasModifierProperty(PsiModifier.STATIC)
                    && method.getParameterList().getParametersCount() == 1
                    && method.getParameterList().getParameters()[0].getType().isAssignableFrom(type)
                    && method.getReturnType() != null
                    && expectedReturnType.equals(method.getReturnType())) {
                    final String methodName = method.getName();
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
    public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        if (!FileModificationService.getInstance().prepareFileForWrite(file)) {
            return;
        }
        PsiMethod wrapper = findWrapper(myExpression.getType(), myExpectedType, myPrimitiveExpected);
        assert wrapper != null;
        PsiElementFactory factory = JavaPsiFacade.getInstance(file.getProject()).getElementFactory();
        @NonNls String methodCallText = "Foo." + wrapper.getName() + "()";
        PsiMethodCallExpression call =
            (PsiMethodCallExpression)factory.createExpressionFromText(methodCallText, null);
        call.getArgumentList().add(myExpression);
        ((PsiReferenceExpression)call.getMethodExpression().getQualifierExpression())
            .bindToElement(wrapper.getContainingClass());
        myExpression.replace(call);
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }

    public static void registerWrapAction(JavaResolveResult[] candidates, PsiExpression[] expressions, HighlightInfo highlightInfo) {
        PsiType expectedType = null;
        PsiExpression expr = null;

        nextMethod:
        for (int i = 0; i < candidates.length && expectedType == null; i++) {
            final JavaResolveResult candidate = candidates[i];
            final PsiSubstitutor substitutor = candidate.getSubstitutor();
            final PsiElement element = candidate.getElement();
            assert element != null;
            final PsiMethod method = (PsiMethod)element;
            final PsiParameter[] parameters = method.getParameterList().getParameters();
            if (!method.isVarArgs() && parameters.length != expressions.length) {
                continue;
            }
            for (int j = 0; j < expressions.length; j++) {
                PsiExpression expression = expressions[j];
                final PsiType exprType = expression.getType();
                if (exprType != null) {
                    PsiType paramType = parameters[Math.min(j, parameters.length - 1)].getType();
                    if (paramType instanceof PsiEllipsisType) {
                        paramType = ((PsiEllipsisType)paramType).getComponentType();
                    }
                    paramType = substitutor != null ? substitutor.substitute(paramType) : paramType;
                    if (paramType.isAssignableFrom(exprType)) {
                        continue;
                    }
                    final PsiClassType classType = getClassType(paramType, expression);
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
            QuickFixAction.registerQuickFixAction(highlightInfo, expr.getTextRange(), new WrapExpressionFix(expectedType, expr));
        }
    }
}
