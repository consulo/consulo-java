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
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.FlipAssertLiteralIntention", fileExtensions = "java", categories = {"Java", "JUnit"})
public class FlipAssertLiteralIntention extends MutablyNamedIntention {

    @Nonnull
    @Override
    protected LocalizeValue getTextForElement(PsiElement element) {
        final PsiMethodCallExpression call = (PsiMethodCallExpression) element;
        final PsiReferenceExpression methodExpression = call.getMethodExpression();
        final String fromMethodName = methodExpression.getReferenceName();
        final String toMethodName;
        if ("assertTrue".equals(fromMethodName)) {
            toMethodName = "assertFalse";
        }
        else {
            toMethodName = "assertTrue";
        }
        return IntentionPowerPackLocalize.flipAssertLiteralIntentionName(fromMethodName, toMethodName);
    }

    @Nonnull
    @Override
    public LocalizeValue getNeutralText() {
        return IntentionPowerPackLocalize.flipAssertLiteralIntentionFamilyName();
    }

    @Override
    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new AssertTrueOrFalsePredicate();
    }

    @Override
    public void processIntention(@Nonnull PsiElement element) {
        final PsiMethodCallExpression call = (PsiMethodCallExpression) element;
        final PsiReferenceExpression methodExpression = call.getMethodExpression();
        @NonNls final String fromMethodName = methodExpression.getReferenceName();
        @NonNls final String toMethodName;
        if ("assertTrue".equals(fromMethodName)) {
            toMethodName = "assertFalse";
        }
        else {
            toMethodName = "assertTrue";
        }
        @NonNls final StringBuilder newCall = new StringBuilder();
        final PsiElement qualifier = methodExpression.getQualifier();
        if (qualifier == null) {
            final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(call, PsiMethod.class);
            if (containingMethod != null && AnnotationUtil.isAnnotated(containingMethod, "org.junit.Test", true)) {
                if (!ImportUtils.addStaticImport("org.junit.Assert", toMethodName, element)) {
                    newCall.append("org.junit.Assert.");
                }
            }
        }
        else {
            newCall.append(qualifier.getText()).append('.');
        }
        newCall.append(toMethodName).append('(');
        final PsiExpressionList argumentList = call.getArgumentList();
        final PsiExpression[] args = argumentList.getExpressions();
        if (args.length == 1) {
            newCall.append(BoolUtils.getNegatedExpressionText(args[0]));
        }
        else {
            newCall.append(BoolUtils.getNegatedExpressionText(args[1]));
        }
        newCall.append(')');
        replaceExpression(newCall.toString(), call);
    }
}