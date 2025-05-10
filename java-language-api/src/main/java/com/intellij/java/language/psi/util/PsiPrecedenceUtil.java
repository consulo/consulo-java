// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.psi.util;

import com.intellij.java.language.psi.*;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import jakarta.annotation.Nonnull;

import java.util.HashMap;
import java.util.Map;

public class PsiPrecedenceUtil {
    public static final int PARENTHESIZED_PRECEDENCE = 0;
    public static final int LITERAL_PRECEDENCE = 0;
    public static final int METHOD_CALL_PRECEDENCE = 1;
    public static final int POSTFIX_PRECEDENCE = 2;
    public static final int PREFIX_PRECEDENCE = 3;
    public static final int TYPE_CAST_PRECEDENCE = 4;
    public static final int MULTIPLICATIVE_PRECEDENCE = 5;
    public static final int ADDITIVE_PRECEDENCE = 6;
    public static final int SHIFT_PRECEDENCE = 7;
    public static final int RELATIONAL_PRECEDENCE = 8;
    public static final int EQUALITY_PRECEDENCE = 9;
    public static final int BINARY_AND_PRECEDENCE = 10;
    public static final int BINARY_XOR_PRECEDENCE = 11;
    public static final int BINARY_OR_PRECEDENCE = 12;
    public static final int AND_PRECEDENCE = 13;
    public static final int OR_PRECEDENCE = 14;
    public static final int CONDITIONAL_PRECEDENCE = 15;
    public static final int ASSIGNMENT_PRECEDENCE = 16;
    public static final int LAMBDA_PRECEDENCE = 17; // jls-15.2
    public static final int NUM_PRECEDENCES = 18;

    private static final Map<IElementType, Integer> S_BINARY_OPERATOR_PRECEDENCE = new HashMap<>(NUM_PRECEDENCES);

    static {
        S_BINARY_OPERATOR_PRECEDENCE.put(JavaTokenType.PLUS, ADDITIVE_PRECEDENCE);
        S_BINARY_OPERATOR_PRECEDENCE.put(JavaTokenType.MINUS, ADDITIVE_PRECEDENCE);
        S_BINARY_OPERATOR_PRECEDENCE.put(JavaTokenType.ASTERISK, MULTIPLICATIVE_PRECEDENCE);
        S_BINARY_OPERATOR_PRECEDENCE.put(JavaTokenType.DIV, MULTIPLICATIVE_PRECEDENCE);
        S_BINARY_OPERATOR_PRECEDENCE.put(JavaTokenType.PERC, MULTIPLICATIVE_PRECEDENCE);
        S_BINARY_OPERATOR_PRECEDENCE.put(JavaTokenType.ANDAND, AND_PRECEDENCE);
        S_BINARY_OPERATOR_PRECEDENCE.put(JavaTokenType.OROR, OR_PRECEDENCE);
        S_BINARY_OPERATOR_PRECEDENCE.put(JavaTokenType.AND, BINARY_AND_PRECEDENCE);
        S_BINARY_OPERATOR_PRECEDENCE.put(JavaTokenType.OR, BINARY_OR_PRECEDENCE);
        S_BINARY_OPERATOR_PRECEDENCE.put(JavaTokenType.XOR, BINARY_XOR_PRECEDENCE);
        S_BINARY_OPERATOR_PRECEDENCE.put(JavaTokenType.LTLT, SHIFT_PRECEDENCE);
        S_BINARY_OPERATOR_PRECEDENCE.put(JavaTokenType.GTGT, SHIFT_PRECEDENCE);
        S_BINARY_OPERATOR_PRECEDENCE.put(JavaTokenType.GTGTGT, SHIFT_PRECEDENCE);
        S_BINARY_OPERATOR_PRECEDENCE.put(JavaTokenType.GT, RELATIONAL_PRECEDENCE);
        S_BINARY_OPERATOR_PRECEDENCE.put(JavaTokenType.GE, RELATIONAL_PRECEDENCE);
        S_BINARY_OPERATOR_PRECEDENCE.put(JavaTokenType.LT, RELATIONAL_PRECEDENCE);
        S_BINARY_OPERATOR_PRECEDENCE.put(JavaTokenType.LE, RELATIONAL_PRECEDENCE);
        S_BINARY_OPERATOR_PRECEDENCE.put(JavaTokenType.EQEQ, EQUALITY_PRECEDENCE);
        S_BINARY_OPERATOR_PRECEDENCE.put(JavaTokenType.NE, EQUALITY_PRECEDENCE);
    }

    public static boolean isCommutativeOperator(@Nonnull IElementType token) {
        return token == JavaTokenType.PLUS || token == JavaTokenType.ASTERISK ||
            token == JavaTokenType.EQEQ || token == JavaTokenType.NE ||
            token == JavaTokenType.AND || token == JavaTokenType.OR || token == JavaTokenType.XOR;
    }

    public static boolean isCommutativeOperation(PsiPolyadicExpression expression) {
        IElementType tokenType = expression.getOperationTokenType();
        if (!isCommutativeOperator(tokenType)) {
            return false;
        }
        PsiType type = expression.getType();
        return type != null && !type.equalsToText(CommonClassNames.JAVA_LANG_STRING);
    }

    public static boolean isAssociativeOperation(PsiPolyadicExpression expression) {
        IElementType tokenType = expression.getOperationTokenType();
        PsiType type = expression.getType();
        PsiPrimitiveType primitiveType;
        if (type instanceof PsiClassType) {
            primitiveType = PsiPrimitiveType.getUnboxedType(type);
            if (primitiveType == null) {
                return false;
            }
        }
        else if (type instanceof PsiPrimitiveType primitiveType1) {
            primitiveType = primitiveType1;
        }
        else {
            return false;
        }

        if (JavaTokenType.PLUS == tokenType || JavaTokenType.ASTERISK == tokenType) {
            return !PsiType.FLOAT.equals(primitiveType) && !PsiType.DOUBLE.equals(primitiveType);
        }
        else if (JavaTokenType.EQEQ == tokenType || JavaTokenType.NE == tokenType) {
            return PsiType.BOOLEAN.equals(primitiveType);
        }
        else if (JavaTokenType.AND == tokenType || JavaTokenType.OR == tokenType || JavaTokenType.XOR == tokenType) {
            return true;
        }
        else if (JavaTokenType.OROR == tokenType || JavaTokenType.ANDAND == tokenType) {
            return true;
        }
        return false;
    }

    public static int getPrecedence(PsiExpression expression) {
        if (expression instanceof PsiThisExpression
            || expression instanceof PsiLiteralExpression
            || expression instanceof PsiSuperExpression
            || expression instanceof PsiClassObjectAccessExpression
            || expression instanceof PsiArrayAccessExpression
            || expression instanceof PsiArrayInitializerExpression) {
            return LITERAL_PRECEDENCE;
        }
        if (expression instanceof PsiReferenceExpression refExpr) {
            if (refExpr.getQualifier() != null) {
                return METHOD_CALL_PRECEDENCE;
            }
            else {
                return LITERAL_PRECEDENCE;
            }
        }
        if (expression instanceof PsiMethodCallExpression || expression instanceof PsiNewExpression) {
            return METHOD_CALL_PRECEDENCE;
        }
        if (expression instanceof PsiTypeCastExpression) {
            return TYPE_CAST_PRECEDENCE;
        }
        if (expression instanceof PsiPrefixExpression) {
            return PREFIX_PRECEDENCE;
        }
        if (expression instanceof PsiPostfixExpression || expression instanceof PsiSwitchExpression) {
            return POSTFIX_PRECEDENCE;
        }
        if (expression instanceof PsiPolyadicExpression polyadicExpression) {
            return getPrecedenceForOperator(polyadicExpression.getOperationTokenType());
        }
        if (expression instanceof PsiInstanceOfExpression) {
            return RELATIONAL_PRECEDENCE;
        }
        if (expression instanceof PsiConditionalExpression) {
            return CONDITIONAL_PRECEDENCE;
        }
        if (expression instanceof PsiAssignmentExpression) {
            return ASSIGNMENT_PRECEDENCE;
        }
        if (expression instanceof PsiParenthesizedExpression) {
            return PARENTHESIZED_PRECEDENCE;
        }
        if (expression instanceof PsiLambdaExpression) {
            return LAMBDA_PRECEDENCE;
        }
        return -1;
    }

    public static int getPrecedenceForOperator(@Nonnull IElementType operator) {
        Integer precedence = S_BINARY_OPERATOR_PRECEDENCE.get(operator);
        if (precedence == null) {
            throw new IllegalArgumentException("unknown operator: " + operator);
        }
        return precedence;
    }

    public static boolean areParenthesesNeeded(PsiParenthesizedExpression expression, boolean ignoreClarifyingParentheses) {
        PsiElement parent = expression.getParent();
        if (!(parent instanceof PsiExpression)) {
            return false;
        }
        PsiExpression child = expression.getExpression();
        return child == null || areParenthesesNeeded(child, (PsiExpression)parent, ignoreClarifyingParentheses);
    }

    public static boolean areParenthesesNeeded(
        PsiExpression expression,
        PsiExpression parentExpression,
        boolean ignoreClarifyingParentheses
    ) {
        if (parentExpression instanceof PsiParenthesizedExpression || parentExpression instanceof PsiArrayInitializerExpression) {
            return false;
        }
        if (parentExpression instanceof PsiArrayAccessExpression arrayAccess) {
            return PsiTreeUtil.isAncestor(arrayAccess.getArrayExpression(), expression, false);
        }
        int parentPrecedence = getPrecedence(parentExpression);
        int childPrecedence = getPrecedence(expression);
        if (parentPrecedence > childPrecedence) {
            if (ignoreClarifyingParentheses) {
                if (expression instanceof PsiPolyadicExpression) {
                    if (parentExpression instanceof PsiPolyadicExpression
                        || parentExpression instanceof PsiConditionalExpression
                        || parentExpression instanceof PsiInstanceOfExpression) {
                        return true;
                    }
                }
                else if (expression instanceof PsiInstanceOfExpression) {
                    return true;
                }
            }
            return false;
        }
        if (parentExpression instanceof PsiPolyadicExpression parentPolyadic && expression instanceof PsiPolyadicExpression childPolyadic) {
            PsiType parentType = parentPolyadic.getType();
            if (parentType == null) {
                return true;
            }
            PsiType childType = childPolyadic.getType();
            if (!parentType.equals(childType)) {
                return true;
            }
            if (childType.equalsToText(CommonClassNames.JAVA_LANG_STRING)
                && !PsiTreeUtil.isAncestor(parentPolyadic.getOperands()[0], childPolyadic, true)) {
                PsiExpression[] operands = childPolyadic.getOperands();
                for (PsiExpression operand : operands) {
                    if (!childType.equals(operand.getType())) {
                        return true;
                    }
                }
            }
            else if (childType.equals(PsiType.BOOLEAN)) {
                PsiExpression[] operands = childPolyadic.getOperands();
                for (PsiExpression operand : operands) {
                    if (!PsiType.BOOLEAN.equals(operand.getType())) {
                        return true;
                    }
                }
            }
            IElementType parentOperator = parentPolyadic.getOperationTokenType();
            IElementType childOperator = childPolyadic.getOperationTokenType();
            if (ignoreClarifyingParentheses) {
                if (!childOperator.equals(parentOperator)) {
                    return true;
                }
            }
            PsiExpression[] parentOperands = parentPolyadic.getOperands();
            if (!PsiTreeUtil.isAncestor(parentOperands[0], expression, false)) {
                if (!isAssociativeOperation(parentPolyadic) ||
                    JavaTokenType.DIV == childOperator || JavaTokenType.PERC == childOperator) {
                    return true;
                }
            }
        }
        else if (parentExpression instanceof PsiConditionalExpression parentConditional && expression instanceof PsiConditionalExpression) {
            PsiExpression condition = parentConditional.getCondition();
            return PsiTreeUtil.isAncestor(condition, expression, true);
        }
        else if (expression instanceof PsiLambdaExpression) { // jls-15.16
            if (parentExpression instanceof PsiTypeCastExpression) {
                return false;
            }
            else if (parentExpression instanceof PsiConditionalExpression conditional) { // jls-15.25
                return PsiTreeUtil.isAncestor(conditional.getCondition(), expression, true);
            }
        }
        return parentPrecedence < childPrecedence;
    }

    public static boolean areParenthesesNeeded(PsiJavaToken compoundAssignmentToken, PsiExpression rhs) {
        if (rhs instanceof PsiPolyadicExpression binaryExpression) {
            int precedence1 = getPrecedenceForOperator(binaryExpression.getOperationTokenType());
            IElementType signTokenType = compoundAssignmentToken.getTokenType();
            IElementType newOperatorToken = TypeConversionUtil.convertEQtoOperation(signTokenType);
            int precedence2 = getPrecedenceForOperator(newOperatorToken);
            return precedence1 >= precedence2 || !isCommutativeOperator(newOperatorToken);
        }
        else {
            return rhs instanceof PsiConditionalExpression;
        }
    }
}
