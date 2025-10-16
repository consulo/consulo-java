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
@IntentionMetaData(ignoreId = "java.ReplaceEqualityWithEqualsIntention", fileExtensions = "java", categories = {"Java", "Boolean"})
public class ReplaceEqualityWithEqualsIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.replaceEqualityWithEqualsIntentionName();
    }

    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new ObjectEqualityPredicate();
    }

    public void processIntention(PsiElement element)
        throws IncorrectOperationException {
        final PsiBinaryExpression exp =
            (PsiBinaryExpression) element;
        final PsiExpression lhs = exp.getLOperand();
        final PsiExpression rhs = exp.getROperand();
        if (rhs == null) {
            return;
        }
        final PsiExpression strippedLhs =
            ParenthesesUtils.stripParentheses(lhs);
        if (strippedLhs == null) {
            return;
        }
        final PsiExpression strippedRhs =
            ParenthesesUtils.stripParentheses(rhs);
        if (strippedRhs == null) {
            return;
        }
        final IElementType tokenType = exp.getOperationTokenType();
        @NonNls final String expString;
        if (tokenType.equals(JavaTokenType.EQEQ)) {
            if (ParenthesesUtils.getPrecedence(strippedLhs) >
                ParenthesesUtils.METHOD_CALL_PRECEDENCE) {
                expString = '(' + strippedLhs.getText() + ").equals(" +
                    strippedRhs.getText() + ')';
            }
            else {
                expString = strippedLhs.getText() + ".equals(" +
                    strippedRhs.getText() + ')';
            }
        }
        else {
            if (ParenthesesUtils.getPrecedence(strippedLhs) >
                ParenthesesUtils.METHOD_CALL_PRECEDENCE) {
                expString = "!(" + strippedLhs.getText() + ").equals(" +
                    strippedRhs.getText() + ')';
            }
            else {
                expString = '!' + strippedLhs.getText() + ".equals(" +
                    strippedRhs.getText() + ')';
            }
        }
        replaceExpression(expString, exp);
    }
}