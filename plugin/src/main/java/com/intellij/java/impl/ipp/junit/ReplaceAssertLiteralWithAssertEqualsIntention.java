/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ipp.junit;

import com.intellij.java.impl.ipp.base.MutablyNamedIntention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.impl.ipp.psiutils.ImportUtils;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.*;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ReplaceAssertLiteralWithAssertEqualsIntention", fileExtensions = "java", categories = {"Java", "JUnit"})
public class ReplaceAssertLiteralWithAssertEqualsIntention extends MutablyNamedIntention {

    @Override
    protected LocalizeValue getTextForElement(PsiElement element) {
        final PsiMethodCallExpression call = (PsiMethodCallExpression) element;
        final PsiExpressionList argumentList = call.getArgumentList();
        final PsiExpression[] arguments = argumentList.getExpressions();
        final PsiReferenceExpression methodExpression = call.getMethodExpression();
        final String methodName = methodExpression.getReferenceName();
        assert methodName != null;
        final String postfix = methodName.substring("assert".length());
        final PsiExpression lastArgument = arguments[arguments.length - 1];
        if (lastArgument instanceof PsiBinaryExpression) {
            final PsiBinaryExpression binaryExpression = (PsiBinaryExpression) lastArgument;
            final IElementType tokenType = binaryExpression.getOperationTokenType();
            if (("assertTrue".equals(methodName) && JavaTokenType.EQEQ.equals(tokenType)) ||
                ("assertFalse".equals(methodName) && JavaTokenType.NE.equals(tokenType))) {
                return IntentionPowerPackLocalize.replaceAssertLiteralWithAssertEqualsIntentionName2(methodName);
            }
        }
        final String literal = postfix.toLowerCase();
        if (arguments.length == 1) {
            return IntentionPowerPackLocalize.replaceAssertLiteralWithAssertEqualsIntentionName(methodName, literal);
        }
        else {
            return IntentionPowerPackLocalize.replaceAssertLiteralWithAssertEqualsIntentionName1(methodName, literal);
        }
    }

    @Nonnull
    @Override
    public LocalizeValue getNeutralText() {
        return IntentionPowerPackLocalize.replaceAssertLiteralWithAssertEqualsIntentionFamilyName();
    }

    @Override
    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new AssertLiteralPredicate();
    }

    @Override
    public void processIntention(@Nonnull PsiElement element) {
        final PsiMethodCallExpression call = (PsiMethodCallExpression) element;
        final PsiReferenceExpression methodExpression = call.getMethodExpression();
        @NonNls final String methodName = methodExpression.getReferenceName();
        if (methodName == null) {
            return;
        }
        @NonNls final StringBuilder newExpression = new StringBuilder();
        final PsiElement qualifier = methodExpression.getQualifier();
        if (qualifier == null) {
            final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(call, PsiMethod.class);
            if (containingMethod != null && AnnotationUtil.isAnnotated(containingMethod, "org.junit.Test", true)) {
                if (!ImportUtils.addStaticImport("org.junit.Assert", "assertEquals", element)) {
                    newExpression.append("org.junit.Assert.");
                }
            }
        }
        else {
            newExpression.append(qualifier.getText());
            newExpression.append('.');
        }
        newExpression.append("assertEquals(");
        final String postfix = methodName.substring("assert".length());
        final String literal = postfix.toLowerCase();
        final PsiExpressionList argumentList = call.getArgumentList();
        final PsiExpression[] arguments = argumentList.getExpressions();
        if (arguments.length > 1) {
            newExpression.append(arguments[0].getText()).append(", ");
        }
        final PsiExpression lastArgument = arguments[arguments.length - 1];
        if (lastArgument instanceof PsiBinaryExpression) {
            final PsiBinaryExpression binaryExpression = (PsiBinaryExpression) lastArgument;
            final IElementType tokenType = binaryExpression.getOperationTokenType();
            if (("assertTrue".equals(methodName) && JavaTokenType.EQEQ.equals(tokenType)) ||
                ("assertFalse".equals(methodName) && JavaTokenType.NE.equals(tokenType))) {
                final PsiExpression lhs = binaryExpression.getLOperand();
                newExpression.append(lhs.getText()).append(", ");
                final PsiExpression rhs = binaryExpression.getROperand();
                if (rhs != null) {
                    newExpression.append(rhs.getText());
                }
            }
            else {
                newExpression.append(literal).append(", ").append(lastArgument.getText());
            }
        }
        else {
            newExpression.append(literal).append(", ").append(lastArgument.getText());
        }
        newExpression.append(')');
        replaceExpression(newExpression.toString(), call);
    }
}