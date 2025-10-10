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
import com.intellij.java.language.psi.infos.CandidateInfo;
import com.intellij.java.language.psi.infos.MethodCandidateInfo;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.HashSet;
import java.util.Set;

public class ChangeStringLiteralToCharInMethodCallFix implements SyntheticIntentionAction {
    private final PsiLiteralExpression myLiteral;
    private final PsiCall myCall;

    public ChangeStringLiteralToCharInMethodCallFix(PsiLiteralExpression literal, final PsiCall methodCall) {
        myLiteral = literal;
        myCall = methodCall;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public LocalizeValue getText() {
        String convertedValue = convertedValue();
        boolean isString = isString(myLiteral.getType());
        return JavaQuickFixLocalize.fixSingleCharacterStringToCharLiteralText(
            myLiteral.getText(),
            quote(convertedValue, !isString),
            isString ? PsiType.CHAR.getCanonicalText() : "String"
        );
    }

    @Override
    public boolean isAvailable(@Nonnull final Project project, final Editor editor, final PsiFile file) {
        return myCall.isValid() && myLiteral.isValid() && myCall.getManager().isInProject(myCall);
    }

    @Override
    public void invoke(@Nonnull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
        if (!FileModificationService.getInstance().prepareFileForWrite(file)) {
            return;
        }

        final Object value = myLiteral.getValue();
        if (value != null && value.toString().length() == 1) {
            final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();

            final PsiExpression newExpression = factory.createExpressionFromText(
                quote(convertedValue(), !isString(myLiteral.getType())),
                myLiteral.getParent()
            );
            myLiteral.replace(newExpression);
        }
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }

    private static String quote(final String value, final boolean doubleQuotes) {
        final char quote = doubleQuotes ? '"' : '\'';
        return quote + value + quote;
    }

    private String convertedValue() {
        String value = String.valueOf(myLiteral.getValue());
        final StringBuilder builder = new StringBuilder();
        StringUtil.escapeStringCharacters(value.length(), value, "\"'", builder);
        return builder.toString();
    }

    public static void registerFixes(
        @Nonnull PsiMethod[] candidates,
        @Nonnull PsiConstructorCall call,
        @Nonnull HighlightInfo.Builder hlBuilder
    ) {
        Set<PsiLiteralExpression> literals = new HashSet<>();
        if (call.getArgumentList() == null) {
            return;
        }
        boolean exactMatch = false;
        for (PsiMethod method : candidates) {
            exactMatch |= findMatchingExpressions(call.getArgumentList().getExpressions(), method, literals);
        }
        if (!exactMatch) {
            processLiterals(literals, call, hlBuilder);
        }
    }

    public static void registerFixes(
        @Nonnull CandidateInfo[] candidates,
        @Nonnull PsiMethodCallExpression methodCall,
        @Nullable HighlightInfo.Builder hlBuilder
    ) {
        if (hlBuilder == null) {
            return;
        }
        Set<PsiLiteralExpression> literals = new HashSet<>();
        boolean exactMatch = false;
        for (CandidateInfo candidate : candidates) {
            if (candidate instanceof MethodCandidateInfo methodCandidateInfo) {
                PsiMethod method = methodCandidateInfo.getElement();
                exactMatch |= findMatchingExpressions(methodCall.getArgumentList().getExpressions(), method, literals);
            }
        }
        if (!exactMatch) {
            processLiterals(literals, methodCall, hlBuilder);
        }
    }

    private static void processLiterals(
        @Nonnull Set<PsiLiteralExpression> literals,
        @Nonnull PsiCall call,
        @Nonnull HighlightInfo.Builder hlBuilder
    ) {
        for (PsiLiteralExpression literal : literals) {
            hlBuilder.registerFix(new ChangeStringLiteralToCharInMethodCallFix(literal, call));
        }
    }

    /**
     * @return <code>true</code> if exact TYPEs match
     */
    private static boolean findMatchingExpressions(
        PsiExpression[] arguments,
        PsiMethod existingMethod,
        Set<PsiLiteralExpression> result
    ) {
        PsiParameterList parameterList = existingMethod.getParameterList();
        PsiParameter[] parameters = parameterList.getParameters();

        if (arguments.length != parameters.length) {
            return false;
        }

        boolean typeMatch = true;
        for (int i = 0; i < parameters.length && i < arguments.length; i++) {
            PsiParameter parameter = parameters[i];
            PsiType parameterType = parameter.getType();
            PsiType argumentType = arguments[i].getType();

            typeMatch &= Comparing.equal(parameterType, argumentType);

            if (arguments[i] instanceof PsiLiteralExpression && !result.contains(arguments[i])
                && (charToString(parameterType, argumentType) || charToString(argumentType, parameterType))) {

                String value = String.valueOf(((PsiLiteralExpression)arguments[i]).getValue());
                if (value != null && value.length() == 1) {
                    result.add((PsiLiteralExpression)arguments[i]);
                }
            }
        }
        return typeMatch;
    }

    private static boolean charToString(PsiType firstType, PsiType secondType) {
        return Comparing.equal(PsiType.CHAR, firstType) && isString(secondType);
    }

    private static boolean isString(PsiType type) {
        return type != null && CommonClassNames.JAVA_LANG_STRING.equals(type.getCanonicalText());
    }
}
