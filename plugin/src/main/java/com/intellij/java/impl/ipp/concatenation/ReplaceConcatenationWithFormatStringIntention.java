/*
 * Copyright 2008-2012 Bas Leijdekkers
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
package com.intellij.java.impl.ipp.concatenation;

import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.impl.ipp.psiutils.ConcatenationUtils;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiConcatenationUtil;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ReplaceConcatenationWithFormatStringIntention", fileExtensions = "java", categories = {"Java", "Strings"})
public class ReplaceConcatenationWithFormatStringIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.replaceConcatenationWithFormatStringIntentionName();
    }

    @Override
    @Nonnull
    protected PsiElementPredicate getElementPredicate() {
        return new Jdk5StringConcatenationPredicate();
    }

    @Override
    protected void processIntention(@Nonnull PsiElement element) throws IncorrectOperationException {
        PsiPolyadicExpression expression = (PsiPolyadicExpression) element;
        PsiElement parent = expression.getParent();
        while (ConcatenationUtils.isConcatenation(parent)) {
            expression = (PsiPolyadicExpression) parent;
            parent = expression.getParent();
        }
        StringBuilder formatString = new StringBuilder();
        List<PsiExpression> formatParameters = new ArrayList();
        PsiConcatenationUtil.buildFormatString(expression, formatString, formatParameters, true);
        if (replaceWithPrintfExpression(expression, formatString, formatParameters)) {
            return;
        }
        StringBuilder newExpression = new StringBuilder();
        newExpression.append("java.lang.String.format(\"");
        newExpression.append(formatString);
        newExpression.append('\"');
        for (PsiExpression formatParameter : formatParameters) {
            newExpression.append(", ");
            newExpression.append(formatParameter.getText());
        }
        newExpression.append(')');
        replaceExpression(newExpression.toString(), expression);
    }

    private static boolean replaceWithPrintfExpression(PsiExpression expression, CharSequence formatString,
                                                       List<PsiExpression> formatParameters) throws IncorrectOperationException {
        PsiElement expressionParent = expression.getParent();
        if (!(expressionParent instanceof PsiExpressionList)) {
            return false;
        }
        PsiElement grandParent = expressionParent.getParent();
        if (!(grandParent instanceof PsiMethodCallExpression)) {
            return false;
        }
        PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) grandParent;
        PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        String name = methodExpression.getReferenceName();
        boolean insertNewline;
        if ("println".equals(name)) {
            insertNewline = true;
        }
        else if ("print".equals(name)) {
            insertNewline = false;
        }
        else {
            return false;
        }
        PsiMethod method = methodCallExpression.resolveMethod();
        if (method == null) {
            return false;
        }
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return false;
        }
        String qualifiedName = containingClass.getQualifiedName();
        if (!CommonClassNames.JAVA_IO_PRINT_STREAM.equals(qualifiedName)
            && !CommonClassNames.JAVA_IO_PRINT_WRITER.equals(qualifiedName)) {
            return false;
        }
        StringBuilder newExpression = new StringBuilder();
        PsiExpression qualifier = methodExpression.getQualifierExpression();
        if (qualifier != null) {
            newExpression.append(qualifier.getText());
            newExpression.append('.');
        }
        newExpression.append("printf(\"");
        newExpression.append(formatString);
        if (insertNewline) {
            newExpression.append("%n");
        }
        newExpression.append('\"');
        for (PsiExpression formatParameter : formatParameters) {
            newExpression.append(", ");
            newExpression.append(formatParameter.getText());
        }
        newExpression.append(')');
        replaceExpression(newExpression.toString(), methodCallExpression);
        return true;
    }
}
