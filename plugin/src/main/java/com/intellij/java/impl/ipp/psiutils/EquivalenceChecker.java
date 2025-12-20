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
package com.intellij.java.impl.ipp.psiutils;

import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.intellij.java.language.psi.*;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class EquivalenceChecker {

    private EquivalenceChecker() {
    }

    public static boolean statementsAreEquivalent(@Nullable PsiStatement statement1, @Nullable PsiStatement statement2) {
        return com.intellij.java.analysis.impl.codeInspection.EquivalenceChecker.getCanonicalPsiEquivalence().statementsAreEquivalent(statement1, statement2);
    }

    private static boolean declarationStatementsAreEquivalent(
        @Nonnull PsiDeclarationStatement statement1,
        @Nonnull PsiDeclarationStatement statement2) {
        PsiElement[] elements1 = statement1.getDeclaredElements();
        List<PsiLocalVariable> vars1 =
            new ArrayList<PsiLocalVariable>(elements1.length);
        for (PsiElement anElement : elements1) {
            if (anElement instanceof PsiLocalVariable) {
                vars1.add((PsiLocalVariable) anElement);
            }
        }
        PsiElement[] elements2 = statement2.getDeclaredElements();
        List<PsiLocalVariable> vars2 =
            new ArrayList<PsiLocalVariable>(elements2.length);
        for (PsiElement anElement : elements2) {
            if (anElement instanceof PsiLocalVariable) {
                vars2.add((PsiLocalVariable) anElement);
            }
        }
        int size = vars1.size();
        if (size != vars2.size()) {
            return false;
        }
        for (int i = 0; i < size; i++) {
            PsiLocalVariable var1 = vars1.get(i);
            PsiLocalVariable var2 = vars2.get(i);
            if (!localVariablesAreEquivalent(var1, var2)) {
                return false;
            }
        }
        return true;
    }

    private static boolean localVariablesAreEquivalent(
        @Nonnull PsiLocalVariable localVariable1,
        @Nonnull PsiLocalVariable localVariable2) {
        PsiType type1 = localVariable1.getType();
        PsiType type2 = localVariable2.getType();
        if (!typesAreEquivalent(type1, type2)) {
            return false;
        }
        String name1 = localVariable1.getName();
        String name2 = localVariable2.getName();
        if (name1 == null) {
            return name2 == null;
        }
        if (!name1.equals(name2)) {
            return false;
        }
        PsiExpression initializer1 = localVariable1.getInitializer();
        PsiExpression initializer2 = localVariable2.getInitializer();
        return expressionsAreEquivalent(initializer1, initializer2);
    }

    private static boolean tryStatementsAreEquivalent(@Nonnull PsiTryStatement statement1, @Nonnull PsiTryStatement statement2) {
        PsiCodeBlock tryBlock1 = statement1.getTryBlock();
        PsiCodeBlock tryBlock2 = statement2.getTryBlock();
        if (!codeBlocksAreEquivalent(tryBlock1, tryBlock2)) {
            return false;
        }
        PsiCodeBlock finallyBlock1 = statement1.getFinallyBlock();
        PsiCodeBlock finallyBlock2 = statement2.getFinallyBlock();
        if (!codeBlocksAreEquivalent(finallyBlock1, finallyBlock2)) {
            return false;
        }
        PsiCodeBlock[] catchBlocks1 = statement1.getCatchBlocks();
        PsiCodeBlock[] catchBlocks2 = statement2.getCatchBlocks();
        if (catchBlocks1.length != catchBlocks2.length) {
            return false;
        }
        for (int i = 0; i < catchBlocks2.length; i++) {
            if (!codeBlocksAreEquivalent(catchBlocks1[i], catchBlocks2[i])) {
                return false;
            }
        }
        PsiResourceList resourceList1 = statement1.getResourceList();
        PsiResourceList resourceList2 = statement2.getResourceList();
        if (resourceList1 != null) {
            if (resourceList2 == null) {
                return false;
            }
            if (resourceList1.getResourceVariablesCount() != resourceList2.getResourceVariablesCount()) {
                return false;
            }
            List<PsiResourceVariable> resourceVariables1 = resourceList1.getResourceVariables();
            List<PsiResourceVariable> resourceVariables2 = resourceList2.getResourceVariables();
            for (int i1 = 0, size = resourceVariables1.size(); i1 < size; i1++) {
                PsiResourceVariable variable1 = resourceVariables1.get(i1);
                PsiResourceVariable variable2 = resourceVariables2.get(i1);
                if (!localVariablesAreEquivalent(variable1, variable2)) {
                    return false;
                }
            }
        }
        else if (resourceList2 != null) {
            return false;
        }
        PsiParameter[] catchParameters1 = statement1.getCatchBlockParameters();
        PsiParameter[] catchParameters2 = statement2.getCatchBlockParameters();
        if (catchParameters1.length != catchParameters2.length) {
            return false;
        }
        for (int i = 0; i < catchParameters2.length; i++) {
            if (!parametersAreEquivalent(catchParameters2[i], catchParameters1[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean parametersAreEquivalent(
        @Nonnull PsiParameter parameter1,
        @Nonnull PsiParameter parameter2) {
        PsiType type1 = parameter1.getType();
        PsiType type2 = parameter2.getType();
        if (!typesAreEquivalent(type1, type2)) {
            return false;
        }
        String name1 = parameter1.getName();
        String name2 = parameter2.getName();
        if (name1 == null) {
            return name2 == null;
        }
        return name1.equals(name2);
    }

    public static boolean typesAreEquivalent(
        @Nullable PsiType type1, @Nullable PsiType type2) {
        if (type1 == null) {
            return type2 == null;
        }
        if (type2 == null) {
            return false;
        }
        String type1Text = type1.getCanonicalText();
        String type2Text = type2.getCanonicalText();
        return type1Text.equals(type2Text);
    }

    private static boolean whileStatementsAreEquivalent(
        @Nonnull PsiWhileStatement statement1,
        @Nonnull PsiWhileStatement statement2) {
        PsiExpression condition1 = statement1.getCondition();
        PsiExpression condition2 = statement2.getCondition();
        PsiStatement body1 = statement1.getBody();
        PsiStatement body2 = statement2.getBody();
        return expressionsAreEquivalent(condition1, condition2) &&
            statementsAreEquivalent(body1, body2);
    }

    private static boolean forStatementsAreEquivalent(
        @Nonnull PsiForStatement statement1,
        @Nonnull PsiForStatement statement2) {
        PsiExpression condition1 = statement1.getCondition();
        PsiExpression condition2 = statement2.getCondition();
        if (!expressionsAreEquivalent(condition1, condition2)) {
            return false;
        }
        PsiStatement initialization1 = statement1.getInitialization();
        PsiStatement initialization2 = statement2.getInitialization();
        if (!statementsAreEquivalent(initialization1, initialization2)) {
            return false;
        }
        PsiStatement update1 = statement1.getUpdate();
        PsiStatement update2 = statement2.getUpdate();
        if (!statementsAreEquivalent(update1, update2)) {
            return false;
        }
        PsiStatement body1 = statement1.getBody();
        PsiStatement body2 = statement2.getBody();
        return statementsAreEquivalent(body1, body2);
    }

    private static boolean forEachStatementsAreEquivalent(
        @Nonnull PsiForeachStatement statement1,
        @Nonnull PsiForeachStatement statement2) {
        PsiExpression value1 = statement1.getIteratedValue();
        PsiExpression value2 = statement2.getIteratedValue();
        if (!expressionsAreEquivalent(value1, value2)) {
            return false;
        }
        PsiParameter parameter1 = statement1.getIterationParameter();
        PsiParameter parameter2 = statement1.getIterationParameter();
        String name1 = parameter1.getName();
        if (name1 == null) {
            return parameter2.getName() == null;
        }
        if (!name1.equals(parameter2.getName())) {
            return false;
        }
        PsiType type1 = parameter1.getType();
        if (!type1.equals(parameter2.getType())) {
            return false;
        }
        PsiStatement body1 = statement1.getBody();
        PsiStatement body2 = statement2.getBody();
        return statementsAreEquivalent(body1, body2);
    }

    private static boolean switchStatementsAreEquivalent(
        @Nonnull PsiSwitchStatement statement1,
        @Nonnull PsiSwitchStatement statement2) {
        PsiExpression switchExpression1 = statement1.getExpression();
        PsiExpression swithcExpression2 = statement2.getExpression();
        PsiCodeBlock body1 = statement1.getBody();
        PsiCodeBlock body2 = statement2.getBody();
        return expressionsAreEquivalent(switchExpression1, swithcExpression2) &&
            codeBlocksAreEquivalent(body1, body2);
    }

    private static boolean doWhileStatementsAreEquivalent(
        @Nonnull PsiDoWhileStatement statement1,
        @Nonnull PsiDoWhileStatement statement2) {
        PsiExpression condition1 = statement1.getCondition();
        PsiExpression condition2 = statement2.getCondition();
        PsiStatement body1 = statement1.getBody();
        PsiStatement body2 = statement2.getBody();
        return expressionsAreEquivalent(condition1, condition2) &&
            statementsAreEquivalent(body1, body2);
    }

    private static boolean assertStatementsAreEquivalent(
        @Nonnull PsiAssertStatement statement1,
        @Nonnull PsiAssertStatement statement2) {
        PsiExpression condition1 = statement1.getAssertCondition();
        PsiExpression condition2 = statement2.getAssertCondition();
        PsiExpression description1 = statement1.getAssertDescription();
        PsiExpression description2 = statement2.getAssertDescription();
        return expressionsAreEquivalent(condition1, condition2) &&
            expressionsAreEquivalent(description1, description2);
    }

    private static boolean synchronizedStatementsAreEquivalent(
        @Nonnull PsiSynchronizedStatement statement1,
        @Nonnull PsiSynchronizedStatement statement2) {
        PsiExpression lock1 = statement1.getLockExpression();
        PsiExpression lock2 = statement2.getLockExpression();
        PsiCodeBlock body1 = statement1.getBody();
        PsiCodeBlock body2 = statement2.getBody();
        return expressionsAreEquivalent(lock1, lock2) &&
            codeBlocksAreEquivalent(body1, body2);
    }

    private static boolean blockStatementsAreEquivalent(
        @Nonnull PsiBlockStatement statement1,
        @Nonnull PsiBlockStatement statement2) {
        PsiCodeBlock block1 = statement1.getCodeBlock();
        PsiCodeBlock block2 = statement2.getCodeBlock();
        return codeBlocksAreEquivalent(block1, block2);
    }

    private static boolean breakStatementsAreEquivalent(
        @Nonnull PsiBreakStatement statement1,
        @Nonnull PsiBreakStatement statement2) {
        PsiIdentifier identifier1 = statement1.getLabelIdentifier();
        PsiIdentifier identifier2 = statement2.getLabelIdentifier();
        if (identifier1 == null) {
            return identifier2 == null;
        }
        if (identifier2 == null) {
            return false;
        }
        String text1 = identifier1.getText();
        String text2 = identifier2.getText();
        return text1.equals(text2);
    }

    private static boolean continueStatementsAreEquivalent(
        @Nonnull PsiContinueStatement statement1,
        @Nonnull PsiContinueStatement statement2) {
        PsiIdentifier identifier1 = statement1.getLabelIdentifier();
        PsiIdentifier identifier2 = statement2.getLabelIdentifier();
        if (identifier1 == null) {
            return identifier2 == null;
        }
        if (identifier2 == null) {
            return false;
        }
        String text1 = identifier1.getText();
        String text2 = identifier2.getText();
        return text1.equals(text2);
    }

    private static boolean switchLabelStatementsAreEquivalent(
        @Nonnull PsiSwitchLabelStatement statement1,
        @Nonnull PsiSwitchLabelStatement statement2) {
        if (statement1.isDefaultCase()) {
            return statement2.isDefaultCase();
        }
        if (statement2.isDefaultCase()) {
            return false;
        }
        PsiExpression caseExpression1 = statement1.getCaseValue();
        PsiExpression caseExpression2 = statement2.getCaseValue();
        return expressionsAreEquivalent(caseExpression1, caseExpression2);
    }

    private static boolean labeledStatementsAreEquivalent(
        @Nonnull PsiLabeledStatement statement1,
        @Nonnull PsiLabeledStatement statement2) {
        PsiIdentifier identifier1 = statement1.getLabelIdentifier();
        PsiIdentifier identifier2 = statement2.getLabelIdentifier();
        String text1 = identifier1.getText();
        String text2 = identifier2.getText();
        return text1.equals(text2);
    }

    public static boolean codeBlocksAreEquivalent(
        @Nullable PsiCodeBlock block1, @Nullable PsiCodeBlock block2) {
        if (block1 == null && block2 == null) {
            return true;
        }
        if (block1 == null || block2 == null) {
            return false;
        }
        PsiStatement[] statements1 = block1.getStatements();
        PsiStatement[] statements2 = block2.getStatements();
        if (statements2.length != statements1.length) {
            return false;
        }
        for (int i = 0; i < statements2.length; i++) {
            if (!statementsAreEquivalent(statements2[i], statements1[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean ifStatementsAreEquivalent(
        @Nonnull PsiIfStatement statement1,
        @Nonnull PsiIfStatement statement2) {
        PsiExpression condition1 = statement1.getCondition();
        PsiExpression condition2 = statement2.getCondition();
        PsiStatement thenBranch1 = statement1.getThenBranch();
        PsiStatement thenBranch2 = statement2.getThenBranch();
        PsiStatement elseBranch1 = statement1.getElseBranch();
        PsiStatement elseBranch2 = statement2.getElseBranch();
        return expressionsAreEquivalent(condition1, condition2) &&
            statementsAreEquivalent(thenBranch1, thenBranch2) &&
            statementsAreEquivalent(elseBranch1, elseBranch2);
    }

    private static boolean expressionStatementsAreEquivalent(
        @Nonnull PsiExpressionStatement statement1,
        @Nonnull PsiExpressionStatement statement2) {
        PsiExpression expression1 = statement1.getExpression();
        PsiExpression expression2 = statement2.getExpression();
        return expressionsAreEquivalent(expression1, expression2);
    }

    private static boolean returnStatementsAreEquivalent(
        @Nonnull PsiReturnStatement statement1,
        @Nonnull PsiReturnStatement statement2) {
        PsiExpression returnValue1 = statement1.getReturnValue();
        PsiExpression returnValue2 = statement2.getReturnValue();
        return expressionsAreEquivalent(returnValue1, returnValue2);
    }

    private static boolean throwStatementsAreEquivalent(
        @Nonnull PsiThrowStatement statement1,
        @Nonnull PsiThrowStatement statement2) {
        PsiExpression exception1 = statement1.getException();
        PsiExpression exception2 = statement2.getException();
        return expressionsAreEquivalent(exception1, exception2);
    }

    private static boolean expressionListStatementsAreEquivalent(
        @Nonnull PsiExpressionListStatement statement1,
        @Nonnull PsiExpressionListStatement statement2) {
        PsiExpressionList expressionList1 =
            statement1.getExpressionList();
        PsiExpression[] expressions1 = expressionList1.getExpressions();
        PsiExpressionList expressionList2 =
            statement2.getExpressionList();
        PsiExpression[] expressions2 = expressionList2.getExpressions();
        return expressionListsAreEquivalent(expressions1, expressions2);
    }

    public static boolean expressionsAreEquivalent(@Nullable PsiExpression expression1, @Nullable PsiExpression expression2) {
        expression1 = ParenthesesUtils.stripParentheses(expression1);
        expression2 = ParenthesesUtils.stripParentheses(expression2);
        if (expression1 == null) {
            return expression2 == null;
        }
        else if (expression2 == null) {
            return false;
        }
        if (expression1.getClass() != expression2.getClass()) {
            return false;
        }
        if (expression1 instanceof PsiThisExpression) {
            return true;
        }
        else if (expression1 instanceof PsiSuperExpression) {
            return true;
        }
        else if (expression1 instanceof PsiLiteralExpression) {
            return literalExpressionsAreEquivalent((PsiLiteralExpression) expression1, (PsiLiteralExpression) expression2);
        }
        else if (expression1 instanceof PsiClassObjectAccessExpression) {
            return classObjectAccessExpressionsAreEquivalent((PsiClassObjectAccessExpression) expression1,
                (PsiClassObjectAccessExpression) expression2);
        }
        else if (expression1 instanceof PsiReferenceExpression) {
            return referenceExpressionsAreEquivalent((PsiReferenceExpression) expression1, (PsiReferenceExpression) expression2);
        }
        else if (expression1 instanceof PsiMethodCallExpression) {
            return methodCallExpressionsAreEquivalent((PsiMethodCallExpression) expression1, (PsiMethodCallExpression) expression2);
        }
        else if (expression1 instanceof PsiNewExpression) {
            return newExpressionsAreEquivalent((PsiNewExpression) expression1, (PsiNewExpression) expression2);
        }
        else if (expression1 instanceof PsiArrayInitializerExpression) {
            return arrayInitializerExpressionsAreEquivalent((PsiArrayInitializerExpression) expression1,
                (PsiArrayInitializerExpression) expression2);
        }
        else if (expression1 instanceof PsiTypeCastExpression) {
            return typeCastExpressionsAreEquivalent((PsiTypeCastExpression) expression1, (PsiTypeCastExpression) expression2);
        }
        else if (expression1 instanceof PsiArrayAccessExpression) {
            return arrayAccessExpressionsAreEquivalent((PsiArrayAccessExpression) expression2, (PsiArrayAccessExpression) expression1);
        }
        else if (expression1 instanceof PsiPrefixExpression) {
            return prefixExpressionsAreEquivalent((PsiPrefixExpression) expression1, (PsiPrefixExpression) expression2);
        }
        else if (expression1 instanceof PsiPostfixExpression) {
            return postfixExpressionsAreEquivalent((PsiPostfixExpression) expression1, (PsiPostfixExpression) expression2);
        }
        else if (expression1 instanceof PsiPolyadicExpression) {
            return polyadicExpressionsAreEquivalent((PsiPolyadicExpression) expression1, (PsiPolyadicExpression) expression2);
        }
        else if (expression1 instanceof PsiAssignmentExpression) {
            return assignmentExpressionsAreEquivalent((PsiAssignmentExpression) expression1, (PsiAssignmentExpression) expression2);
        }
        else if (expression1 instanceof PsiConditionalExpression) {
            return conditionalExpressionsAreEquivalent((PsiConditionalExpression) expression1, (PsiConditionalExpression) expression2);
        }
        else if (expression1 instanceof PsiInstanceOfExpression) {
            return instanceofExpressionsAreEquivalent((PsiInstanceOfExpression) expression1, (PsiInstanceOfExpression) expression2);
        }
        return false;
    }

    private static boolean literalExpressionsAreEquivalent(PsiLiteralExpression expression1, PsiLiteralExpression expression2) {
        Object value1 = expression1.getValue();
        Object value2 = expression2.getValue();
        if (value1 == null) {
            return value2 == null;
        }
        else if (value2 == null) {
            return false;
        }
        return value1.equals(value2);
    }

    private static boolean classObjectAccessExpressionsAreEquivalent(PsiClassObjectAccessExpression expression1,
                                                                     PsiClassObjectAccessExpression expression2) {
        PsiTypeElement operand1 = expression1.getOperand();
        PsiTypeElement operand2 = expression2.getOperand();
        return typeElementsAreEquivalent(operand1, operand2);
    }

    private static boolean referenceExpressionsAreEquivalent(
        PsiReferenceExpression referenceExpression1,
        PsiReferenceExpression referenceExpression2) {
        PsiElement element1 = referenceExpression1.resolve();
        PsiElement element2 = referenceExpression2.resolve();
        if (element1 != null) {
            if (!element1.equals(element2)) {
                return false;
            }
        }
        else {
            return element2 == null;
        }
        if (element1 instanceof PsiMember) {
            PsiMember member1 = (PsiMember) element1;
            if (member1.hasModifierProperty(PsiModifier.STATIC)) {
                return true;
            }
            else if (member1 instanceof PsiClass) {
                return true;
            }
        }
        else {
            return true;
        }
        PsiExpression qualifier1 =
            referenceExpression1.getQualifierExpression();
        PsiExpression qualifier2 =
            referenceExpression2.getQualifierExpression();
        if (qualifier1 != null &&
            !(qualifier1 instanceof PsiThisExpression ||
                qualifier1 instanceof PsiSuperExpression)) {
            if (qualifier2 == null) {
                return false;
            }
            else if (!expressionsAreEquivalent(qualifier1, qualifier2)) {
                return false;
            }
        }
        else {
            if (qualifier2 != null &&
                !(qualifier2 instanceof PsiThisExpression ||
                    qualifier2 instanceof PsiSuperExpression)) {
                return false;
            }
        }
        String text1 = referenceExpression1.getText();
        String text2 = referenceExpression2.getText();
        return text1.equals(text2);
    }

    private static boolean instanceofExpressionsAreEquivalent(
        PsiInstanceOfExpression instanceOfExpression1,
        PsiInstanceOfExpression instanceOfExpression2) {
        PsiExpression operand1 = instanceOfExpression1.getOperand();
        PsiExpression operand2 = instanceOfExpression2.getOperand();
        if (!expressionsAreEquivalent(operand1, operand2)) {
            return false;
        }
        PsiTypeElement typeElement1 = instanceOfExpression1.getCheckType();
        PsiTypeElement typeElement2 = instanceOfExpression2.getCheckType();
        return typeElementsAreEquivalent(typeElement1, typeElement2);
    }

    private static boolean typeElementsAreEquivalent(PsiTypeElement typeElement1, PsiTypeElement typeElement2) {
        if (typeElement1 == null) {
            return typeElement2 == null;
        }
        else if (typeElement2 == null) {
            return false;
        }
        PsiType type1 = typeElement1.getType();
        PsiType type2 = typeElement2.getType();
        return typesAreEquivalent(type1, type2);
    }

    private static boolean methodCallExpressionsAreEquivalent(
        @Nonnull PsiMethodCallExpression methodCallExpression1,
        @Nonnull PsiMethodCallExpression methodCallExpression2) {
        PsiReferenceExpression methodExpression1 =
            methodCallExpression1.getMethodExpression();
        PsiReferenceExpression methodExpression2 =
            methodCallExpression2.getMethodExpression();
        if (!expressionsAreEquivalent(methodExpression1, methodExpression2)) {
            return false;
        }
        PsiExpressionList argumentList1 =
            methodCallExpression1.getArgumentList();
        PsiExpression[] args1 = argumentList1.getExpressions();
        PsiExpressionList argumentList2 =
            methodCallExpression2.getArgumentList();
        PsiExpression[] args2 = argumentList2.getExpressions();
        return expressionListsAreEquivalent(args1, args2);
    }

    private static boolean newExpressionsAreEquivalent(
        @Nonnull PsiNewExpression newExpression1,
        @Nonnull PsiNewExpression newExpression2) {
        PsiJavaCodeReferenceElement classReference1 =
            newExpression1.getClassReference();
        PsiJavaCodeReferenceElement classReference2 =
            newExpression2.getClassReference();
        if (classReference1 == null || classReference2 == null) {
            return false;
        }
        String text = classReference1.getText();
        if (!text.equals(classReference2.getText())) {
            return false;
        }
        PsiExpression[] arrayDimensions1 =
            newExpression1.getArrayDimensions();
        PsiExpression[] arrayDimensions2 =
            newExpression2.getArrayDimensions();
        if (!expressionListsAreEquivalent(arrayDimensions1, arrayDimensions2)) {
            return false;
        }
        PsiArrayInitializerExpression arrayInitializer1 =
            newExpression1.getArrayInitializer();
        PsiArrayInitializerExpression arrayInitializer2 =
            newExpression2.getArrayInitializer();
        if (!expressionsAreEquivalent(arrayInitializer1, arrayInitializer2)) {
            return false;
        }
        PsiExpression qualifier1 = newExpression1.getQualifier();
        PsiExpression qualifier2 = newExpression2.getQualifier();
        if (!expressionsAreEquivalent(qualifier1, qualifier2)) {
            return false;
        }
        PsiExpressionList argumentList1 = newExpression1.getArgumentList();
        PsiExpression[] args1;
        if (argumentList1 == null) {
            args1 = null;
        }
        else {
            args1 = argumentList1.getExpressions();
        }
        PsiExpressionList argumentList2 = newExpression2.getArgumentList();
        PsiExpression[] args2;
        if (argumentList2 == null) {
            args2 = null;
        }
        else {
            args2 = argumentList2.getExpressions();
        }
        return expressionListsAreEquivalent(args1, args2);
    }

    private static boolean arrayInitializerExpressionsAreEquivalent(
        @Nonnull PsiArrayInitializerExpression arrayInitializerExpression1,
        @Nonnull PsiArrayInitializerExpression arrayInitializerExpression2) {
        PsiExpression[] initializers1 =
            arrayInitializerExpression1.getInitializers();
        PsiExpression[] initializers2 =
            arrayInitializerExpression2.getInitializers();
        return expressionListsAreEquivalent(initializers1, initializers2);
    }

    private static boolean typeCastExpressionsAreEquivalent(
        @Nonnull PsiTypeCastExpression typeCastExpression1,
        @Nonnull PsiTypeCastExpression typeCastExpression2) {
        PsiTypeElement typeElement1 = typeCastExpression1.getCastType();
        PsiTypeElement typeElement2 = typeCastExpression2.getCastType();
        if (!typeElementsAreEquivalent(typeElement1, typeElement2)) {
            return false;
        }
        PsiExpression operand1 = typeCastExpression1.getOperand();
        PsiExpression operand2 = typeCastExpression2.getOperand();
        return expressionsAreEquivalent(operand1, operand2);
    }

    private static boolean arrayAccessExpressionsAreEquivalent(
        @Nonnull PsiArrayAccessExpression arrayAccessExpression1,
        @Nonnull PsiArrayAccessExpression arrayAccessExpression2) {
        PsiExpression arrayExpression2 =
            arrayAccessExpression1.getArrayExpression();
        PsiExpression arrayExpression1 =
            arrayAccessExpression2.getArrayExpression();
        PsiExpression indexExpression2 =
            arrayAccessExpression1.getIndexExpression();
        PsiExpression indexExpression1 =
            arrayAccessExpression2.getIndexExpression();
        return expressionsAreEquivalent(arrayExpression2, arrayExpression1)
            && expressionsAreEquivalent(indexExpression2, indexExpression1);
    }

    private static boolean prefixExpressionsAreEquivalent(
        @Nonnull PsiPrefixExpression prefixExpression1,
        @Nonnull PsiPrefixExpression prefixExpression2) {
        IElementType tokenType1 = prefixExpression1.getOperationTokenType();
        if (!tokenType1.equals(prefixExpression2.getOperationTokenType())) {
            return false;
        }
        PsiExpression operand1 = prefixExpression1.getOperand();
        PsiExpression operand2 = prefixExpression2.getOperand();
        return expressionsAreEquivalent(operand1, operand2);
    }

    private static boolean postfixExpressionsAreEquivalent(
        @Nonnull PsiPostfixExpression postfixExpression1,
        @Nonnull PsiPostfixExpression postfixExpression2) {
        IElementType tokenType1 = postfixExpression1.getOperationTokenType();
        if (!tokenType1.equals(postfixExpression2.getOperationTokenType())) {
            return false;
        }
        PsiExpression operand1 = postfixExpression1.getOperand();
        PsiExpression operand2 = postfixExpression2.getOperand();
        return expressionsAreEquivalent(operand1, operand2);
    }

    private static boolean polyadicExpressionsAreEquivalent(
        @Nonnull PsiPolyadicExpression polyadicExpression1,
        @Nonnull PsiPolyadicExpression polyadicExpression2) {
        IElementType tokenType1 = polyadicExpression1.getOperationTokenType();
        IElementType tokenType2 = polyadicExpression2.getOperationTokenType();
        if (!tokenType1.equals(tokenType2)) {
            return false;
        }
        PsiExpression[] operands1 = polyadicExpression1.getOperands();
        PsiExpression[] operands2 = polyadicExpression2.getOperands();
        if (operands1.length != operands2.length) {
            return false;
        }
        for (int i = 0, length = operands1.length; i < length; i++) {
            if (!expressionsAreEquivalent(operands1[i], operands2[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean assignmentExpressionsAreEquivalent(
        @Nonnull PsiAssignmentExpression assignmentExpression1,
        @Nonnull PsiAssignmentExpression assignmentExpression2) {
        IElementType tokenType1 = assignmentExpression1.getOperationTokenType();
        if (!tokenType1.equals(assignmentExpression2.getOperationTokenType())) {
            return false;
        }
        PsiExpression lhs1 = assignmentExpression1.getLExpression();
        PsiExpression lhs2 = assignmentExpression2.getLExpression();
        PsiExpression rhs1 = assignmentExpression1.getRExpression();
        PsiExpression rhs2 = assignmentExpression2.getRExpression();
        return expressionsAreEquivalent(lhs1, lhs2)
            && expressionsAreEquivalent(rhs1, rhs2);
    }

    private static boolean conditionalExpressionsAreEquivalent(
        @Nonnull PsiConditionalExpression conditionalExpression1,
        @Nonnull PsiConditionalExpression conditionalExpression2) {
        PsiExpression condition1 = conditionalExpression1.getCondition();
        PsiExpression condition2 = conditionalExpression2.getCondition();
        PsiExpression thenExpression1 =
            conditionalExpression1.getThenExpression();
        PsiExpression thenExpression2 =
            conditionalExpression2.getThenExpression();
        PsiExpression elseExpression1 =
            conditionalExpression1.getElseExpression();
        PsiExpression elseExpression2 =
            conditionalExpression2.getElseExpression();
        return expressionsAreEquivalent(condition1, condition2)
            && expressionsAreEquivalent(thenExpression1, thenExpression2)
            && expressionsAreEquivalent(elseExpression1, elseExpression2);
    }

    private static boolean expressionListsAreEquivalent(
        @Nullable PsiExpression[] expressions1,
        @Nullable PsiExpression[] expressions2) {
        if (expressions1 == null && expressions2 == null) {
            return true;
        }
        if (expressions1 == null || expressions2 == null) {
            return false;
        }
        if (expressions1.length != expressions2.length) {
            return false;
        }
        for (int i = 0; i < expressions1.length; i++) {
            if (!expressionsAreEquivalent(expressions1[i], expressions2[i])) {
                return false;
            }
        }
        return true;
    }
}