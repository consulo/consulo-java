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
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author anna
 * @since 2012-02-10
 */
public class ConvertDoubleToFloatFix implements SyntheticIntentionAction {
    private final PsiExpression myExpression;

    public ConvertDoubleToFloatFix(PsiExpression expression) {
        myExpression = expression;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public String getText() {
        return "Convert '" + myExpression.getText() + "' to float";
    }

    @Override
    @RequiredReadAction
    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        if (myExpression.isValid()) {
            if (!StringUtil.endsWithIgnoreCase(myExpression.getText(), "f")) {
                PsiLiteralExpression expression = (PsiLiteralExpression)createFloatingPointExpression(project);
                final Object value = expression.getValue();
                return value instanceof Float floatValue
                    && !floatValue.isInfinite()
                    && !(floatValue == 0 && !TypeConversionUtil.isFPZero(expression.getText()));
            }
        }
        return false;
    }

    @Override
    @RequiredWriteAction
    public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        myExpression.replace(createFloatingPointExpression(project));
    }

    @RequiredReadAction
    private PsiExpression createFloatingPointExpression(Project project) {
        final String text = myExpression.getText();
        if (StringUtil.endsWithIgnoreCase(text, "d")) {
            return JavaPsiFacade.getElementFactory(project)
                .createExpressionFromText(text.substring(0, text.length() - 1) + "f", myExpression);
        }
        else {
            return JavaPsiFacade.getElementFactory(project).createExpressionFromText(text + "f", myExpression);
        }
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }

    public static void registerIntentions(
        @Nonnull JavaResolveResult[] candidates,
        @Nonnull PsiExpressionList list,
        @Nullable HighlightInfo.Builder hlBuilder,
        TextRange fixRange
    ) {
        if (hlBuilder == null || candidates.length == 0) {
            return;
        }
        PsiExpression[] expressions = list.getExpressions();
        for (JavaResolveResult candidate : candidates) {
            registerIntention(expressions, hlBuilder, fixRange, candidate, list);
        }
    }

    private static void registerIntention(
        @Nonnull PsiExpression[] expressions,
        @Nonnull HighlightInfo.Builder hlBuilder,
        TextRange fixRange,
        @Nonnull JavaResolveResult candidate,
        @Nonnull PsiElement context
    ) {
        if (!candidate.isStaticsScopeCorrect()) {
            return;
        }
        PsiMethod method = (PsiMethod)candidate.getElement();
        if (method != null && context.getManager().isInProject(method)) {
            PsiParameter[] parameters = method.getParameterList().getParameters();
            if (parameters.length == expressions.length) {
                for (int i = 0, length = parameters.length; i < length; i++) {
                    PsiParameter parameter = parameters[i];
                    PsiExpression expression = expressions[i];
                    if (expression instanceof PsiLiteralExpression
                        && PsiType.FLOAT.equals(parameter.getType())
                        && PsiType.DOUBLE.equals(expression.getType())) {
                        hlBuilder.registerFix(new ConvertDoubleToFloatFix(expression), fixRange);
                    }
                }
            }
        }
    }
}
