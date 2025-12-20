/*
 * Copyright 2003-2005 Dave Griffith
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
package com.intellij.java.impl.ipp.equality;

import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiBinaryExpression;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiJavaToken;
import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ReplaceEqualityWithSafeEqualsIntention", fileExtensions = "java", categories = {"Java", "Boolean"})
public class ReplaceEqualityWithSafeEqualsIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.replaceEqualityWithSafeEqualsIntentionName();
    }

    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new ObjectEqualityPredicate();
    }

    public void processIntention(PsiElement element)
        throws IncorrectOperationException {
        PsiBinaryExpression exp =
            (PsiBinaryExpression) element;
        PsiExpression lhs = exp.getLOperand();
        PsiExpression rhs = exp.getROperand();
        if (rhs == null) {
            return;
        }
        PsiExpression strippedLhs =
            ParenthesesUtils.stripParentheses(lhs);
        if (strippedLhs == null) {
            return;
        }
        PsiExpression strippedRhs =
            ParenthesesUtils.stripParentheses(rhs);
        if (strippedRhs == null) {
            return;
        }
        String lhsText = strippedLhs.getText();
        String rhsText = strippedRhs.getText();
        PsiJavaToken operationSign = exp.getOperationSign();
        IElementType tokenType = operationSign.getTokenType();
        String signText = operationSign.getText();
        @NonNls StringBuilder buffer = new StringBuilder(lhsText);
        buffer.append("==null?");
        buffer.append(rhsText);
        buffer.append(signText);
        buffer.append(" null:");
        if (tokenType.equals(JavaTokenType.NE)) {
            buffer.append('!');
        }
        if (ParenthesesUtils.getPrecedence(strippedLhs) >
            ParenthesesUtils.METHOD_CALL_PRECEDENCE) {
            buffer.append('(');
            buffer.append(lhsText);
            buffer.append(')');
        }
        else {
            buffer.append(lhsText);
        }
        buffer.append(".equals(");
        buffer.append(rhsText);
        buffer.append(')');
        replaceExpression(buffer.toString(), exp);
    }
}