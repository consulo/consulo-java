/*
 * Copyright 2007-2013 Bas Leijdekkers
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
package com.intellij.java.impl.ipp.opassign;

import com.intellij.java.impl.ipp.base.MutablyNamedIntention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import java.util.HashMap;
import java.util.Map;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ReplaceOperatorAssignmentWithAssignmentIntention", fileExtensions = "java", categories = {"Java", "Other"})
public class ReplaceOperatorAssignmentWithAssignmentIntention extends MutablyNamedIntention {

    private static final Map<IElementType, IElementType> tokenMap = new HashMap<IElementType, IElementType>();

    static {
        tokenMap.put(JavaTokenType.PLUSEQ, JavaTokenType.PLUS);
        tokenMap.put(JavaTokenType.MINUSEQ, JavaTokenType.MINUS);
        tokenMap.put(JavaTokenType.ASTERISKEQ, JavaTokenType.ASTERISK);
        tokenMap.put(JavaTokenType.DIVEQ, JavaTokenType.DIV);
        tokenMap.put(JavaTokenType.ANDEQ, JavaTokenType.AND);
        tokenMap.put(JavaTokenType.OREQ, JavaTokenType.OR);
        tokenMap.put(JavaTokenType.XOREQ, JavaTokenType.XOR);
        tokenMap.put(JavaTokenType.PERCEQ, JavaTokenType.PERC);
        tokenMap.put(JavaTokenType.LTLTEQ, JavaTokenType.LTLT);
        tokenMap.put(JavaTokenType.GTGTEQ, JavaTokenType.GTGT);
        tokenMap.put(JavaTokenType.GTGTGTEQ, JavaTokenType.GTGTGT);
    }

    @Override
    @Nonnull
    protected PsiElementPredicate getElementPredicate() {
        return new OperatorAssignmentPredicate();
    }

    @Nonnull
    @Override
    public LocalizeValue getNeutralText() {
        return IntentionPowerPackLocalize.replaceWithOperatorAssignmentIntentionFamilyName();
    }

    @Override
    protected LocalizeValue getTextForElement(PsiElement element) {
        PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression) element;
        PsiJavaToken sign = assignmentExpression.getOperationSign();
        String operator = sign.getText();
        return IntentionPowerPackLocalize.replaceOperatorAssignmentWithAssignmentIntentionName(operator);
    }

    @Override
    protected void processIntention(@Nonnull PsiElement element) {
        PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression) element;
        PsiJavaToken sign = assignmentExpression.getOperationSign();
        PsiExpression lhs = assignmentExpression.getLExpression();
        PsiExpression rhs = assignmentExpression.getRExpression();
        String operator = sign.getText();
        String newOperator = operator.substring(0, operator.length() - 1);
        String lhsText = lhs.getText();
        String rhsText = (rhs == null) ? "" : rhs.getText();
        boolean parentheses;
        if (rhs instanceof PsiPolyadicExpression) {
            PsiPolyadicExpression binaryExpression = (PsiPolyadicExpression) rhs;
            int precedence1 = ParenthesesUtils.getPrecedenceForOperator(binaryExpression.getOperationTokenType());
            IElementType signTokenType = sign.getTokenType();
            IElementType newOperatorToken = tokenMap.get(signTokenType);
            int precedence2 = ParenthesesUtils.getPrecedenceForOperator(newOperatorToken);
            parentheses = precedence1 >= precedence2 || !ParenthesesUtils.isCommutativeOperator(newOperatorToken);
        }
        else {
            parentheses = false;
        }
        String cast = getCastString(lhs, rhs);
        StringBuilder newExpression = new StringBuilder(lhsText);
        newExpression.append('=').append(cast);
        if (!cast.isEmpty()) {
            newExpression.append('(');
        }
        newExpression.append(lhsText).append(newOperator);
        if (parentheses) {
            newExpression.append('(').append(rhsText).append(')');
        }
        else {
            newExpression.append(rhsText);
        }
        if (!cast.isEmpty()) {
            newExpression.append(')');
        }
        replaceExpression(newExpression.toString(), assignmentExpression);
    }

    private static String getCastString(PsiExpression lhs, PsiExpression rhs) {
        PsiType lType = lhs.getType();
        PsiType rType = rhs.getType();
        if (lType == null || rType == null ||
            TypeConversionUtil.isAssignable(lType, rType) || !TypeConversionUtil.areTypesConvertible(lType, rType)) {
            return "";
        }
        return '(' + lType.getCanonicalText() + ')';
    }
}