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
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ReplaceAssertEqualsWithAssertLiteralIntention", fileExtensions = "java", categories = {"Java", "JUnit"})
public class ReplaceAssertEqualsWithAssertLiteralIntention extends MutablyNamedIntention {

    @Nonnull
    @Override
    protected LocalizeValue getTextForElement(PsiElement element) {
        final PsiMethodCallExpression call = (PsiMethodCallExpression) element;
        final PsiExpressionList argumentList = call.getArgumentList();
        final PsiExpression[] args = argumentList.getExpressions();
        final String assertString;
        if (args.length == 2) {
            final String argText = args[0].getText();
            assertString = getAssertString(argText);
        }
        else {
            final String argText = args[1].getText();
            assertString = getAssertString(argText);
        }
        return IntentionPowerPackLocalize.replaceAssertEqualsWithAssertLiteralIntentionName(assertString);
    }

    @Nonnull
    @Override
    public LocalizeValue getNeutralText() {
        return IntentionPowerPackLocalize.replaceAssertEqualsWithAssertLiteralIntentionFamilyName();
    }

    @Override
    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new AssertEqualsWithLiteralPredicate();
    }

    @Override
    public void processIntention(@Nonnull PsiElement element) {
        final PsiMethodCallExpression call = (PsiMethodCallExpression) element;
        final PsiReferenceExpression expression = call.getMethodExpression();
        final PsiExpressionList argumentList = call.getArgumentList();
        final PsiExpression[] args = argumentList.getExpressions();
        final String assertString;
        final String actualArgumentText;
        if (args.length == 2) {
            @NonNls final String argText = args[0].getText();
            final PsiExpression otherArg;
            if ("true".equals(argText) || "false".equals(argText) || "null".equals(argText)) {
                otherArg = args[1];
            }
            else {
                otherArg = args[0];
            }
            actualArgumentText = otherArg.getText();
            assertString = getAssertString(argText);
        }
        else {
            @NonNls final String argText = args[1].getText();
            final PsiExpression otherArg;
            if ("true".equals(argText) || "false".equals(argText) || "null".equals(argText)) {
                otherArg = args[2];
            }
            else {
                otherArg = args[1];
            }
            actualArgumentText = args[0].getText() + ", " + otherArg.getText();
            assertString = getAssertString(argText);
        }
        final PsiElement qualifier = expression.getQualifier();
        @NonNls final StringBuilder newExpression = new StringBuilder();
        if (qualifier == null) {
            final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(call, PsiMethod.class);
            if (containingMethod != null && AnnotationUtil.isAnnotated(containingMethod, "org.junit.Test", true)) {
                if (!ImportUtils.addStaticImport("org.junit.Assert", assertString, element)) {
                    newExpression.append("org.junit.Assert.");
                }
            }
        }
        else {
            newExpression.append(qualifier.getText()).append('.');
        }
        newExpression.append(assertString).append('(').append(actualArgumentText).append(')');
        replaceExpression(newExpression.toString(), call);
    }

    @NonNls
    private static String getAssertString(@NonNls String text) {
        if ("true".equals(text)) {
            return "assertTrue";
        }
        if ("false".equals(text)) {
            return "assertFalse";
        }
        return "assertNull";
    }
}
