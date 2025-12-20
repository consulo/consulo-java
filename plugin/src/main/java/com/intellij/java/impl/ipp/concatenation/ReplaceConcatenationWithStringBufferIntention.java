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
package com.intellij.java.impl.ipp.concatenation;

import com.intellij.java.impl.ipp.base.MutablyNamedIntention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.impl.ipp.psiutils.ConcatenationUtils;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ReplaceConcatenationWithStringBufferIntention", fileExtensions = "java", categories = {"Java", "Strings"})
public class ReplaceConcatenationWithStringBufferIntention extends MutablyNamedIntention {

    @Override
    protected LocalizeValue getTextForElement(PsiElement element) {
        if (PsiUtil.isLanguageLevel5OrHigher(element)) {
            return IntentionPowerPackLocalize.replaceConcatenationWithStringBuilderIntentionName();
        }
        else {
            return IntentionPowerPackLocalize.replaceConcatenationWithStringBufferIntentionName();
        }
    }

    @Nonnull
    @Override
    public LocalizeValue getNeutralText() {
        return IntentionPowerPackLocalize.replaceConcatenationWithStringBufferIntentionFamilyName();
    }

    @Override
    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new SimpleStringConcatenationPredicate(true);
    }

    @Override
    public void processIntention(@Nonnull PsiElement element) throws IncorrectOperationException {
        PsiPolyadicExpression expression = (PsiPolyadicExpression) element;
        PsiElement parent = expression.getParent();
        while (ConcatenationUtils.isConcatenation(parent)) {
            expression = (PsiPolyadicExpression) parent;
            parent = expression.getParent();
        }
        @NonNls StringBuilder newExpression = new StringBuilder();
        if (isPartOfStringBufferAppend(expression)) {
            PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) parent.getParent();
            assert methodCallExpression != null;
            PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
            PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
            if (qualifierExpression != null) {
                String qualifierText = qualifierExpression.getText();
                newExpression.append(qualifierText);
            }
            turnExpressionIntoChainedAppends(expression, newExpression);
            replaceExpression(newExpression.toString(), methodCallExpression);
        }
        else {
            if (!PsiUtil.isLanguageLevel5OrHigher(expression)) {
                newExpression.append("new StringBuffer()");
            }
            else {
                newExpression.append("new StringBuilder()");
            }
            turnExpressionIntoChainedAppends(expression, newExpression);
            newExpression.append(".toString()");
            replaceExpression(newExpression.toString(), expression);
        }
    }

    private static boolean isPartOfStringBufferAppend(PsiExpression expression) {
        PsiElement parent = expression.getParent();
        if (!(parent instanceof PsiExpressionList)) {
            return false;
        }
        parent = parent.getParent();
        if (!(parent instanceof PsiMethodCallExpression)) {
            return false;
        }
        PsiMethodCallExpression methodCall = (PsiMethodCallExpression) parent;
        PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
        PsiType type = methodExpression.getType();
        if (type == null) {
            return false;
        }
        String className = type.getCanonicalText();
        if (!CommonClassNames.JAVA_LANG_STRING_BUFFER.equals(className) && !CommonClassNames.JAVA_LANG_STRING_BUILDER.equals(className)) {
            return false;
        }
        @NonNls String methodName = methodExpression.getReferenceName();
        return "append".equals(methodName);
    }

    private static void turnExpressionIntoChainedAppends(PsiExpression expression, @NonNls StringBuilder result) {
        if (expression instanceof PsiPolyadicExpression) {
            PsiPolyadicExpression concatenation = (PsiPolyadicExpression) expression;
            PsiType type = concatenation.getType();
            if (type != null && !type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
                result.append(".append(").append(concatenation.getText()).append(')');
                return;
            }
            PsiExpression[] operands = concatenation.getOperands();
            PsiType startType = operands[0].getType();
            if (startType == null || startType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
                for (PsiExpression operand : operands) {
                    turnExpressionIntoChainedAppends(operand, result);
                }
                return;
            }
            StringBuilder newExpressionText = new StringBuilder(operands[0].getText());
            boolean string = false;
            for (int i = 1; i < operands.length; i++) {
                PsiExpression operand = operands[i];
                if (!string) {
                    PsiType operandType = operand.getType();
                    if (operandType == null || operandType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
                        PsiElementFactory factory = JavaPsiFacade.getElementFactory(expression.getProject());
                        PsiExpression newExpression = factory.createExpressionFromText(newExpressionText.toString(), expression);
                        turnExpressionIntoChainedAppends(newExpression, result);
                        turnExpressionIntoChainedAppends(operand, result);
                        string = true;
                    }
                    newExpressionText.append('+').append(operand.getText());
                }
                else {
                    turnExpressionIntoChainedAppends(operand, result);
                }
            }
        }
        else {
            PsiExpression strippedExpression = ParenthesesUtils.stripParentheses(expression);
            result.append(".append(");
            if (strippedExpression != null) {
                result.append(strippedExpression.getText());
            }
            result.append(')');
        }
    }
}
