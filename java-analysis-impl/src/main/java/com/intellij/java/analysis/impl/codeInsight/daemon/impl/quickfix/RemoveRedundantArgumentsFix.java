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

import com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Arrays;

/**
 * @author Danila Ponomarenko
 */
public class RemoveRedundantArgumentsFix implements SyntheticIntentionAction {
    private final PsiMethod myTargetMethod;
    private final PsiExpression[] myArguments;
    private final PsiSubstitutor mySubstitutor;

    private RemoveRedundantArgumentsFix(
        @Nonnull PsiMethod targetMethod,
        @Nonnull PsiExpression[] arguments,
        @Nonnull PsiSubstitutor substitutor
    ) {
        myTargetMethod = targetMethod;
        myArguments = arguments;
        mySubstitutor = substitutor;
    }

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return JavaQuickFixLocalize.removeRedundantArgumentsText(JavaHighlightUtil.formatMethod(myTargetMethod));
    }

    @Override
    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        if (!myTargetMethod.isValid() || myTargetMethod.getContainingClass() == null) {
            return false;
        }
        for (PsiExpression expression : myArguments) {
            if (!expression.isValid()) {
                return false;
            }
        }
        //noinspection SimplifiableIfStatement
        if (!mySubstitutor.isValid()) {
            return false;
        }

        return findRedundantArgument(myArguments, myTargetMethod.getParameterList().getParameters(), mySubstitutor) != null;
    }

    @Nullable
    private static PsiExpression[] findRedundantArgument(
        @Nonnull PsiExpression[] arguments,
        @Nonnull PsiParameter[] parameters,
        @Nonnull PsiSubstitutor substitutor
    ) {
        if (arguments.length <= parameters.length) {
            return null;
        }

        for (int i = 0; i < parameters.length; i++) {
            final PsiExpression argument = arguments[i];
            final PsiParameter parameter = parameters[i];

            final PsiType argumentType = argument.getType();
            if (argumentType == null) {
                return null;
            }
            final PsiType parameterType = substitutor.substitute(parameter.getType());

            if (!TypeConversionUtil.isAssignable(parameterType, argumentType)) {
                return null;
            }
        }

        return Arrays.copyOfRange(arguments, parameters.length, arguments.length);
    }

    @Override
    public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        if (!FileModificationService.getInstance().prepareFileForWrite(file)) {
            return;
        }
        final PsiExpression[] redundantArguments =
            findRedundantArgument(myArguments, myTargetMethod.getParameterList().getParameters(), mySubstitutor);
        if (redundantArguments != null) {
            for (PsiExpression argument : redundantArguments) {
                argument.delete();
            }
        }
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }

    public static void registerIntentions(
        @Nonnull JavaResolveResult[] candidates,
        @Nonnull PsiExpressionList arguments,
        @Nullable HighlightInfo.Builder hlBuilder,
        @Nonnull TextRange fixRange
    ) {
        if (hlBuilder == null) {
            return;
        }
        for (JavaResolveResult candidate : candidates) {
            registerIntention(arguments, hlBuilder, fixRange, candidate, arguments);
        }
    }

    private static void registerIntention(
        @Nonnull PsiExpressionList arguments,
        @Nonnull HighlightInfo.Builder hlBuilder,
        @Nonnull TextRange fixRange,
        @Nonnull JavaResolveResult candidate,
        @Nonnull PsiElement context
    ) {
        if (!candidate.isStaticsScopeCorrect()) {
            return;
        }
        PsiMethod method = (PsiMethod)candidate.getElement();
        PsiSubstitutor substitutor = candidate.getSubstitutor();
        if (method != null && context.getManager().isInProject(method)) {
            hlBuilder.newFix(new RemoveRedundantArgumentsFix(method, arguments.getExpressions(), substitutor))
                .fixRange(fixRange)
                .register();
        }
    }
}
