/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.java.analysis.impl.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.java.language.codeInsight.Nullability;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.inspection.LocalQuickFixAndIntentionActionOnPsiElement;
import consulo.language.editor.intention.BaseIntentionAction;
import consulo.language.editor.intention.HighPriorityAction;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;

/**
 * @author Dmitry Batkovich
 */
public class WrapObjectWithOptionalOfNullableFix extends MethodArgumentFix implements HighPriorityAction {
    public static final ArgumentFixerActionFactory REGISTAR = new MyFixerActionFactory();

    protected WrapObjectWithOptionalOfNullableFix(
        @Nonnull PsiExpressionList list,
        int i,
        @Nonnull PsiType toType,
        @Nonnull ArgumentFixerActionFactory fixerActionFactory
    ) {
        super(list, i, toType, fixerActionFactory);
    }

    @Nonnull
    @Override
    public LocalizeValue getText() {
        if (myArgList.getExpressionCount() == 1) {
            return JavaQuickFixLocalize.wrapWithOptionalSingleParameterText();
        }
        else {
            return JavaQuickFixLocalize.wrapWithOptionalParameterText(myIndex + 1);
        }
    }

    @Override
    @RequiredReadAction
    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        return PsiUtil.isLanguageLevel8OrHigher(file) && super.isAvailable(project, editor, file);
    }

    public static IntentionAction createFix(@Nullable PsiType type, @Nonnull PsiExpression expression) {
        class MyFix extends LocalQuickFixAndIntentionActionOnPsiElement implements HighPriorityAction {
            protected MyFix(@Nullable PsiElement element) {
                super(element);
            }

            @RequiredReadAction
            @Override
            public void invoke(
                @Nonnull Project project,
                @Nonnull PsiFile file,
                @Nullable Editor editor,
                @Nonnull PsiElement startElement,
                @Nonnull PsiElement endElement
            ) {
                startElement.replace(getModifiedExpression((PsiExpression)getStartElement()));
            }

            @Override
            @RequiredReadAction
            public boolean isAvailable(
                @Nonnull Project project,
                @Nonnull PsiFile file,
                @Nonnull PsiElement startElement,
                @Nonnull PsiElement endElement
            ) {
                return BaseIntentionAction.canModify(startElement)
                    && PsiUtil.isLanguageLevel8OrHigher(startElement)
                    && areConvertible(((PsiExpression)startElement).getType(), type);
            }

            @Nonnull
            @Override
            public LocalizeValue getText() {
                return JavaQuickFixLocalize.wrapWithOptionalSingleParameterText();
            }
        }
        return new MyFix(expression);
    }

    public static class MyFixerActionFactory extends ArgumentFixerActionFactory {

        @Nullable
        @Override
        @RequiredReadAction
        protected PsiExpression getModifiedArgument(PsiExpression expression, PsiType toType) throws IncorrectOperationException {
            return getModifiedExpression(expression);
        }

        @Override
        public boolean areTypesConvertible(
            @Nonnull PsiType exprType,
            @Nonnull PsiType parameterType,
            @Nonnull PsiElement context
        ) {
            return parameterType.isConvertibleFrom(exprType) || areConvertible(exprType, parameterType);
        }

        @Override
        public MethodArgumentFix createFix(PsiExpressionList list, int i, PsiType toType) {
            return new WrapObjectWithOptionalOfNullableFix(list, i, toType, this);
        }
    }

    private static boolean areConvertible(@Nullable PsiType exprType, @Nullable PsiType parameterType) {
        if (exprType == null
            || !exprType.isValid()
            || !(parameterType instanceof PsiClassType)
            || !parameterType.isValid()) {
            return false;
        }
        PsiClassType.ClassResolveResult resolve = ((PsiClassType)parameterType).resolveGenerics();
        PsiClass resolvedClass = resolve.getElement();
        if (resolvedClass == null || !CommonClassNames.JAVA_UTIL_OPTIONAL.equals(resolvedClass.getQualifiedName())) {
            return false;
        }

        Collection<PsiType> values = resolve.getSubstitutor().getSubstitutionMap().values();
        if (values.isEmpty()) {
            return true;
        }
        if (values.size() > 1) {
            return false;
        }
        PsiType optionalTypeParameter = ContainerUtil.getFirstItem(values);
        return optionalTypeParameter != null && TypeConversionUtil.isAssignable(optionalTypeParameter, exprType);
    }

    @Nonnull
    @RequiredReadAction
    private static PsiExpression getModifiedExpression(PsiExpression expression) {
        Project project = expression.getProject();
        Nullability nullability = NullabilityUtil.getExpressionNullability(expression, true);
        String methodName = nullability == Nullability.NOT_NULL ? "of" : "ofNullable";
        String newExpressionText = CommonClassNames.JAVA_UTIL_OPTIONAL + "." + methodName + "(" + expression.getText() + ")";
        return JavaPsiFacade.getElementFactory(project).createExpressionFromText(newExpressionText, expression);
    }
}
