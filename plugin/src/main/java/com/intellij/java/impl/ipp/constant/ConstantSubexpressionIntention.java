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
package com.intellij.java.impl.ipp.constant;

import com.intellij.java.impl.ipp.base.MutablyNamedIntention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.impl.ipp.psiutils.HighlightUtil;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiJavaToken;
import com.intellij.java.language.psi.PsiPolyadicExpression;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ConstantSubexpressionIntention", fileExtensions = "java", categories = {"Java", "Other"})
public class ConstantSubexpressionIntention extends MutablyNamedIntention {

    @Override
    @Nonnull
    protected PsiElementPredicate getElementPredicate() {
        return new ConstantSubexpressionPredicate();
    }

    @Override
    protected LocalizeValue getTextForElement(PsiElement element) {
        final PsiJavaToken token;
        if (element instanceof PsiJavaToken) {
            token = (PsiJavaToken) element;
        }
        else {
            final PsiElement prevSibling = element.getPrevSibling();
            if (prevSibling instanceof PsiJavaToken) {
                token = (PsiJavaToken) prevSibling;
            }
            else {
                throw new AssertionError();
            }
        }
        final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression) element.getParent();
        final PsiPolyadicExpression subexpression = ConstantSubexpressionPredicate.getSubexpression(polyadicExpression, token);
        final String text = HighlightUtil.getPresentableText(subexpression);
        return IntentionPowerPackLocalize.constantExpressionIntentionName(text);
    }

    @Nonnull
    @Override
    public LocalizeValue getNeutralText() {
        return IntentionPowerPackLocalize.constantSubexpressionIntentionFamilyName();
    }

    @Override
    public void processIntention(@Nonnull PsiElement element) throws IncorrectOperationException {
        final PsiJavaToken token;
        if (element instanceof PsiJavaToken) {
            token = (PsiJavaToken) element;
        }
        else {
            final PsiElement prevSibling = element.getPrevSibling();
            if (prevSibling instanceof PsiJavaToken) {
                token = (PsiJavaToken) prevSibling;
            }
            else {
                throw new AssertionError();
            }
        }
        final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression) element.getParent();
        final PsiPolyadicExpression subexpression = ConstantSubexpressionPredicate.getSubexpression(polyadicExpression, token);
        if (subexpression == null) {
            return;
        }
        final Object value = ExpressionUtils.computeConstantExpression(subexpression);
        final StringBuilder newExpressionText = new StringBuilder();
        final PsiExpression[] operands = polyadicExpression.getOperands();
        PsiExpression prevOperand = null;
        PsiJavaToken prevToken = null;
        for (PsiExpression operand : operands) {
            final PsiJavaToken currentToken = polyadicExpression.getTokenBeforeOperand(operand);
            if (token == currentToken) {
                if (prevToken != null) {
                    newExpressionText.append(prevToken.getText());
                }
                if (newExpressionText.length() > 0) {
                    newExpressionText.append(' ');
                }
                if (value instanceof Long) {
                    newExpressionText.append(value).append('L');
                }
                else if (value instanceof Double) {
                    final double v = ((Double) value).doubleValue();
                    if (Double.isNaN(v)) {
                        newExpressionText.append("java.lang.Double.NaN");
                    }
                    else if (Double.isInfinite(v)) {
                        if (v > 0.0) {
                            newExpressionText.append("java.lang.Double.POSITIVE_INFINITY");
                        }
                        else {
                            newExpressionText.append("java.lang.Double.NEGATIVE_INFINITY");
                        }
                    }
                    else {
                        newExpressionText.append(Double.toString(v));
                    }
                }
                else if (value instanceof Float) {
                    final float v = ((Float) value).floatValue();
                    if (Float.isNaN(v)) {
                        newExpressionText.append("java.lang.Float.NaN");
                    }
                    else if (Float.isInfinite(v)) {
                        if (v > 0.0F) {
                            newExpressionText.append("java.lang.Float.POSITIVE_INFINITY");
                        }
                        else {
                            newExpressionText.append("java.lang.Float.NEGATIVE_INFINITY");
                        }
                    }
                    else {
                        newExpressionText.append(Float.toString(v)).append('f');
                    }
                }
                else {
                    newExpressionText.append(value);
                }
                prevOperand = null;
                prevToken = null;
            }
            else {
                if (prevToken != null) {
                    newExpressionText.append(prevToken.getText());
                }
                if (prevOperand != null) {
                    newExpressionText.append(prevOperand.getText());
                }
                prevOperand = operand;
                prevToken = currentToken;
            }
        }
        if (prevToken != null) {
            newExpressionText.append(prevToken.getText());
        }
        if (prevOperand != null) {
            newExpressionText.append(prevOperand.getText());
        }
        replaceExpression(newExpressionText.toString(), polyadicExpression);
    }
}