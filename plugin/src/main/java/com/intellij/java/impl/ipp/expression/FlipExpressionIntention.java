/*
 * Copyright 2007-2013 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ipp.expression;

import com.intellij.java.impl.ipp.base.MutablyNamedIntention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.impl.ipp.psiutils.ConcatenationUtils;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiJavaToken;
import com.intellij.java.language.psi.PsiPolyadicExpression;
import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.CaretModel;
import consulo.codeEditor.Editor;
import consulo.language.ast.IElementType;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.FlipExpressionIntention", fileExtensions = "java", categories = {"Java", "Other"})
public class FlipExpressionIntention extends MutablyNamedIntention {

    @Nonnull
    @Override
    public LocalizeValue getTextForElement(PsiElement element) {
        PsiPolyadicExpression expression = (PsiPolyadicExpression) element.getParent();
        PsiExpression[] operands = expression.getOperands();
        PsiJavaToken sign = expression.getTokenBeforeOperand(operands[1]);
        String operatorText = sign == null ? "" : sign.getText();
        IElementType tokenType = expression.getOperationTokenType();
        boolean commutative = ParenthesesUtils.isCommutativeOperator(tokenType);
        if (commutative && !ConcatenationUtils.isConcatenation(expression)) {
            return IntentionPowerPackLocalize.flipSmthIntentionName(operatorText);
        }
        else {
            return IntentionPowerPackLocalize.flipSmthIntentionName1(operatorText);
        }
    }

    @Nonnull
    @Override
    public LocalizeValue getNeutralText() {
        return IntentionPowerPackLocalize.flipExpressionIntentionFamilyName();
    }

    @Override
    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new ExpressionPredicate();
    }

    @Override
    public void processIntention(@Nonnull PsiElement element) {
        PsiJavaToken token = (PsiJavaToken) element;
        PsiElement parent = token.getParent();
        if (!(parent instanceof PsiPolyadicExpression)) {
            return;
        }
        PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression) parent;
        PsiExpression[] operands = polyadicExpression.getOperands();
        StringBuilder newExpression = new StringBuilder();
        String prevOperand = null;
        String tokenText = token.getText() + ' '; // 2- -1 without the space is not legal
        for (PsiExpression operand : operands) {
            PsiJavaToken token1 = polyadicExpression.getTokenBeforeOperand(operand);
            if (token == token1) {
                newExpression.append(operand.getText()).append(tokenText);
                continue;
            }
            if (prevOperand != null) {
                newExpression.append(prevOperand).append(tokenText);
            }
            prevOperand = operand.getText();
        }
        newExpression.append(prevOperand);
        replaceExpression(newExpression.toString(), polyadicExpression);
    }

    @Override
    protected void processIntention(Editor editor, @Nonnull PsiElement element) {
        CaretModel caretModel = editor.getCaretModel();
        int offset = caretModel.getOffset();
        super.processIntention(editor, element);
        caretModel.moveToOffset(offset);
    }
}