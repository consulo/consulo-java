/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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

import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.CreateAssertIntention", fileExtensions = "java", categories = {"Java", "JUnit"})
public class CreateAssertIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.createAssertIntentionName();
    }

    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new CreateAssertPredicate();
    }

    public void processIntention(PsiElement element)
        throws IncorrectOperationException {
        PsiExpressionStatement statement =
            (PsiExpressionStatement) element;
        assert statement != null;
        PsiExpression expression = statement.getExpression();
        PsiMethod containingMethod =
            PsiTreeUtil.getParentOfType(statement, PsiMethod.class);
        String specifierString;
        if (containingMethod != null &&
            AnnotationUtil.isAnnotated(containingMethod,
                "org.junit.Test", true)) {
            specifierString = "org.junit.Assert.";
        }
        else {
            specifierString = "";
        }
        if (BoolUtils.isNegation(expression)) {
            @NonNls String newExpression =
                specifierString + "assertFalse(" +
                    BoolUtils.getNegatedExpressionText(expression) + ");";
            replaceStatementAndShorten(newExpression,
                statement);
        }
        else if (isNullComparison(expression)) {
            PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression) expression;
            PsiExpression lhs = binaryExpression.getLOperand();
            PsiExpression rhs = binaryExpression.getROperand();
            PsiExpression comparedExpression;
            if (isNull(lhs)) {
                comparedExpression = rhs;
            }
            else {
                comparedExpression = lhs;
            }
            assert comparedExpression != null;
            @NonNls String newExpression = specifierString +
                "assertNull(" + comparedExpression.getText() + ");";
            replaceStatementAndShorten(newExpression,
                statement);
        }
        else if (isEqualityComparison(expression)) {
            PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression) expression;
            PsiExpression lhs = binaryExpression.getLOperand();
            PsiExpression rhs = binaryExpression.getROperand();
            PsiExpression comparedExpression;
            PsiExpression comparingExpression;
            if (rhs instanceof PsiLiteralExpression) {
                comparedExpression = rhs;
                comparingExpression = lhs;
            }
            else {
                comparedExpression = lhs;
                comparingExpression = rhs;
            }
            assert comparingExpression != null;
            PsiType type = lhs.getType();
            @NonNls String newExpression;
            if (PsiType.DOUBLE.equals(type) || PsiType.FLOAT.equals(type)) {
                newExpression = specifierString + "assertEquals(" +
                    comparedExpression.getText() + ", " +
                    comparingExpression.getText() + ", 0.0);";
            }
            else if (type instanceof PsiPrimitiveType) {
                newExpression = specifierString + "assertEquals(" +
                    comparedExpression.getText() + ", " +
                    comparingExpression.getText() + ");";
            }
            else {
                newExpression = specifierString + "assertSame(" +
                    comparedExpression.getText() + ", " +
                    comparingExpression.getText() + ");";
            }
            replaceStatementAndShorten(newExpression,
                statement);
        }
        else if (isEqualsExpression(expression)) {
            PsiMethodCallExpression call =
                (PsiMethodCallExpression) expression;
            PsiReferenceExpression methodExpression =
                call.getMethodExpression();
            PsiExpression comparedExpression =
                methodExpression.getQualifierExpression();
            assert comparedExpression != null;
            PsiExpressionList argList = call.getArgumentList();
            PsiExpression comparingExpression =
                argList.getExpressions()[0];
            @NonNls String newExpression;
            if (comparingExpression instanceof PsiLiteralExpression) {
                newExpression = specifierString + "assertEquals(" +
                    comparingExpression.getText() + ", " +
                    comparedExpression.getText() + ");";
            }
            else {
                newExpression = specifierString + "assertEquals(" +
                    comparedExpression.getText() + ", " +
                    comparingExpression.getText() + ");";
            }
            replaceStatementAndShorten(newExpression,
                statement);
        }
        else {
            @NonNls String newExpression =
                specifierString + "assertTrue(" + expression.getText() + ");";
            replaceStatementAndShorten(newExpression,
                statement);
        }
    }

    private static boolean isEqualsExpression(PsiExpression expression) {
        if (!(expression instanceof PsiMethodCallExpression)) {
            return false;
        }
        PsiMethodCallExpression call =
            (PsiMethodCallExpression) expression;
        PsiReferenceExpression methodExpression =
            call.getMethodExpression();
        @NonNls String methodName = methodExpression.getReferenceName();
        if (!"equals".equals(methodName)) {
            return false;
        }
        PsiExpression qualifier =
            methodExpression.getQualifierExpression();
        if (qualifier == null) {
            return false;
        }
        PsiExpressionList argList = call.getArgumentList();
        PsiExpression[] expressions = argList.getExpressions();
        return expressions.length == 1 && expressions[0] != null;
    }

    private static boolean isEqualityComparison(PsiExpression expression) {
        if (!(expression instanceof PsiBinaryExpression)) {
            return false;
        }
        PsiBinaryExpression binaryExpression =
            (PsiBinaryExpression) expression;
        IElementType tokenType = binaryExpression.getOperationTokenType();
        return JavaTokenType.EQEQ.equals(tokenType);
    }

    private static boolean isNullComparison(PsiExpression expression) {
        if (!(expression instanceof PsiBinaryExpression)) {
            return false;
        }
        PsiBinaryExpression binaryExpression =
            (PsiBinaryExpression) expression;
        IElementType tokenType = binaryExpression.getOperationTokenType();
        if (!JavaTokenType.EQEQ.equals(tokenType)) {
            return false;
        }
        PsiExpression lhs = binaryExpression.getLOperand();
        if (isNull(lhs)) {
            return true;
        }
        PsiExpression Rhs = binaryExpression.getROperand();
        return isNull(Rhs);
    }

    private static boolean isNull(PsiExpression expression) {
        if (!(expression instanceof PsiLiteralExpression)) {
            return false;
        }
        @NonNls String text = expression.getText();
        return PsiKeyword.NULL.equals(text);
    }
}
