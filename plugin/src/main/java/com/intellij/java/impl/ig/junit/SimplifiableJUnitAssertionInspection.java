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
package com.intellij.java.impl.ig.junit;

import com.intellij.java.impl.ig.psiutils.ImportUtils;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class SimplifiableJUnitAssertionInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.simplifiableJunitAssertionDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.simplifiableJunitAssertionProblemDescriptor(infos[0]).get();
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new SimplifyJUnitAssertFix();
    }

    private static class SimplifyJUnitAssertFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.simplifyJunitAssertionSimplifyQuickfix();
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiElement methodNameIdentifier = descriptor.getPsiElement();
            PsiElement parent = methodNameIdentifier.getParent();
            if (parent == null) {
                return;
            }
            PsiMethodCallExpression callExpression = (PsiMethodCallExpression) parent.getParent();
            if (isAssertThatCouldBeAssertNull(callExpression)) {
                replaceAssertWithAssertNull(callExpression);
            }
            else if (isAssertThatCouldBeAssertSame(callExpression)) {
                replaceAssertWithAssertSame(callExpression);
            }
            else if (isAssertTrueThatCouldBeAssertEquals(callExpression)) {
                replaceAssertTrueWithAssertEquals(callExpression);
            }
            else if (isAssertEqualsThatCouldBeAssertLiteral(callExpression)) {
                replaceAssertEqualsWithAssertLiteral(callExpression);
            }
            else if (isAssertThatCouldBeFail(callExpression)) {
                replaceAssertWithFail(callExpression);
            }
        }

        private static void replaceAssertWithFail(PsiMethodCallExpression callExpression) throws IncorrectOperationException {
            PsiMethod method = callExpression.resolveMethod();
            if (method == null) {
                return;
            }
            PsiExpressionList argumentList = callExpression.getArgumentList();
            PsiExpression[] arguments = argumentList.getExpressions();
            PsiExpression message;
            if (arguments.length == 2) {
                message = arguments[0];
            }
            else {
                message = null;
            }
            @NonNls StringBuilder newExpression = new StringBuilder();
            if (!ImportUtils.addStaticImport("org.junit.Assert", "fail", callExpression)) {
                newExpression.append("org.junit.Assert.");
            }
            newExpression.append("fail(");
            if (message != null) {
                newExpression.append(message.getText());
            }
            newExpression.append(')');
            replaceExpressionAndShorten(callExpression, newExpression.toString());
        }

        private static void replaceAssertTrueWithAssertEquals(PsiMethodCallExpression callExpression) throws IncorrectOperationException {
            PsiMethod method = callExpression.resolveMethod();
            if (method == null) {
                return;
            }
            PsiParameterList parameterList = method.getParameterList();
            PsiParameter[] parameters = parameterList.getParameters();
            PsiType stringType = TypeUtils.getStringType(callExpression);
            PsiType paramType1 = parameters[0].getType();
            PsiExpressionList argumentList = callExpression.getArgumentList();
            PsiExpression[] arguments = argumentList.getExpressions();
            int testPosition;
            PsiExpression message;
            if (paramType1.equals(stringType) && parameters.length >= 2) {
                testPosition = 1;
                message = arguments[0];
            }
            else {
                testPosition = 0;
                message = null;
            }
            PsiExpression testArgument = arguments[testPosition];
            PsiExpression lhs = null;
            PsiExpression rhs = null;
            if (testArgument instanceof PsiBinaryExpression) {
                PsiBinaryExpression binaryExpression = (PsiBinaryExpression) testArgument;
                lhs = binaryExpression.getLOperand();
                rhs = binaryExpression.getROperand();
            }
            else if (testArgument instanceof PsiMethodCallExpression) {
                PsiMethodCallExpression call = (PsiMethodCallExpression) testArgument;
                PsiReferenceExpression equalityMethodExpression = call.getMethodExpression();
                PsiExpressionList equalityArgumentList = call.getArgumentList();
                PsiExpression[] equalityArgs = equalityArgumentList.getExpressions();
                rhs = equalityArgs[0];
                lhs = equalityMethodExpression.getQualifierExpression();
            }
            if (!(lhs instanceof PsiLiteralExpression) && rhs instanceof PsiLiteralExpression) {
                PsiExpression temp = lhs;
                lhs = rhs;
                rhs = temp;
            }
            if (lhs == null || rhs == null) {
                return;
            }
            @NonNls StringBuilder newExpression = new StringBuilder();
            if (!ImportUtils.addStaticImport("org.junit.Assert", "assertEquals", callExpression)) {
                newExpression.append("org.junit.Assert.");
            }
            newExpression.append("assertEquals(");
            if (message != null) {
                newExpression.append(message.getText()).append(',');
            }
            newExpression.append(lhs.getText()).append(',').append(rhs.getText());
            if (TypeUtils.hasFloatingPointType(lhs) || TypeUtils.hasFloatingPointType(rhs)) {
                newExpression.append(",0.0");
            }
            newExpression.append(')');
            replaceExpressionAndShorten(callExpression, newExpression.toString());
        }

        private static void replaceAssertWithAssertNull(PsiMethodCallExpression callExpression) throws IncorrectOperationException {
            PsiMethod method = callExpression.resolveMethod();
            if (method == null) {
                return;
            }
            PsiParameterList parameterList = method.getParameterList();
            PsiParameter[] parameters = parameterList.getParameters();
            PsiType stringType = TypeUtils.getStringType(callExpression);
            PsiType paramType1 = parameters[0].getType();
            PsiExpressionList argumentList = callExpression.getArgumentList();
            PsiExpression[] arguments = argumentList.getExpressions();
            int testPosition;
            PsiExpression message;
            if (paramType1.equals(stringType) && parameters.length >= 2) {
                testPosition = 1;
                message = arguments[0];
            }
            else {
                testPosition = 0;
                message = null;
            }
            PsiBinaryExpression binaryExpression = (PsiBinaryExpression) arguments[testPosition];
            PsiExpression lhs = binaryExpression.getLOperand();
            PsiExpression rhs = binaryExpression.getROperand();
            if (rhs == null) {
                return;
            }
            IElementType tokenType = binaryExpression.getOperationTokenType();
            if (!(lhs instanceof PsiLiteralExpression) && rhs instanceof PsiLiteralExpression) {
                rhs = lhs;
            }
            @NonNls StringBuilder newExpression = new StringBuilder();
            PsiReferenceExpression methodExpression = callExpression.getMethodExpression();
            @NonNls String methodName = methodExpression.getReferenceName();
            @NonNls String memberName;
            if ("assertFalse".equals(methodName) ^ tokenType.equals(JavaTokenType.NE)) {
                memberName = "assertNotNull";
            }
            else {
                memberName = "assertNull";
            }
            if (!ImportUtils.addStaticImport("org.junit.Assert", memberName, callExpression)) {
                newExpression.append("org.junit.Assert.");
            }
            newExpression.append(memberName).append('(');
            if (message != null) {
                newExpression.append(message.getText()).append(',');
            }
            newExpression.append(rhs.getText()).append(')');
            replaceExpressionAndShorten(callExpression, newExpression.toString());
        }

        private static void replaceAssertWithAssertSame(PsiMethodCallExpression callExpression) throws IncorrectOperationException {
            PsiMethod method = callExpression.resolveMethod();
            if (method == null) {
                return;
            }
            PsiParameterList parameterList = method.getParameterList();
            PsiParameter[] parameters = parameterList.getParameters();
            PsiType stringType = TypeUtils.getStringType(callExpression);
            PsiType paramType1 = parameters[0].getType();
            PsiExpressionList argumentList = callExpression.getArgumentList();
            PsiExpression[] arguments = argumentList.getExpressions();
            int testPosition;
            PsiExpression message;
            if (paramType1.equals(stringType) && parameters.length >= 2) {
                testPosition = 1;
                message = arguments[0];
            }
            else {
                testPosition = 0;
                message = null;
            }
            PsiBinaryExpression binaryExpression = (PsiBinaryExpression) arguments[testPosition];
            PsiExpression lhs = binaryExpression.getLOperand();
            PsiExpression rhs = binaryExpression.getROperand();
            IElementType tokenType = binaryExpression.getOperationTokenType();
            if (!(lhs instanceof PsiLiteralExpression) && rhs instanceof PsiLiteralExpression) {
                PsiExpression temp = lhs;
                lhs = rhs;
                rhs = temp;
            }
            if (rhs == null) {
                return;
            }
            @NonNls StringBuilder newExpression = new StringBuilder();
            PsiReferenceExpression methodExpression = callExpression.getMethodExpression();
            @NonNls String methodName = methodExpression.getReferenceName();
            @NonNls String memberName;
            if ("assertFalse".equals(methodName) ^ tokenType.equals(JavaTokenType.NE)) {
                memberName = "assertNotSame";
            }
            else {
                memberName = "assertSame";
            }
            if (!ImportUtils.addStaticImport("org.junit.Assert", memberName, callExpression)) {
                newExpression.append("org.junit.Assert.");
            }
            newExpression.append(memberName).append('(');
            if (message != null) {
                newExpression.append(message.getText()).append(',');
            }
            newExpression.append(lhs.getText()).append(',').append(rhs.getText()).append(')');
            replaceExpressionAndShorten(callExpression, newExpression.toString());
        }

        private static void replaceAssertEqualsWithAssertLiteral(
            PsiMethodCallExpression callExpression
        )
            throws IncorrectOperationException {
            PsiMethod method = callExpression.resolveMethod();
            if (method == null) {
                return;
            }
            PsiParameterList parameterList = method.getParameterList();
            PsiParameter[] parameters = parameterList.getParameters();
            PsiType stringType = TypeUtils.getStringType(callExpression);
            PsiType paramType1 = parameters[0].getType();
            PsiExpressionList argumentList = callExpression.getArgumentList();
            PsiExpression[] arguments = argumentList.getExpressions();
            int firstTestPosition;
            int secondTestPosition;
            PsiExpression message;
            if (paramType1.equals(stringType) && parameters.length >= 3) {
                firstTestPosition = 1;
                secondTestPosition = 2;
                message = arguments[0];
            }
            else {
                firstTestPosition = 0;
                secondTestPosition = 1;
                message = null;
            }
            PsiExpression firstTestArgument = arguments[firstTestPosition];
            PsiExpression secondTestArgument = arguments[secondTestPosition];
            String literalValue;
            String compareValue;
            if (isSimpleLiteral(firstTestArgument, secondTestArgument)) {
                literalValue = firstTestArgument.getText();
                compareValue = secondTestArgument.getText();
            }
            else {
                literalValue = secondTestArgument.getText();
                compareValue = firstTestArgument.getText();
            }
            String uppercaseLiteralValue = Character.toUpperCase(literalValue.charAt(0)) + literalValue.substring(1);
            @NonNls StringBuilder newExpression = new StringBuilder();
            @NonNls String methodName = "assert" + uppercaseLiteralValue;
            if (!ImportUtils.addStaticImport("org.junit.Assert", methodName, callExpression)) {
                newExpression.append("org.junit.Assert.");
            }
            newExpression.append(methodName).append('(');
            if (message != null) {
                newExpression.append(message.getText()).append(',');
            }
            newExpression.append(compareValue).append(')');
            replaceExpressionAndShorten(callExpression, newExpression.toString());
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new SimplifiableJUnitAssertionVisitor();
    }

    private static class SimplifiableJUnitAssertionVisitor extends BaseInspectionVisitor {

        @Override
        public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if (isAssertThatCouldBeAssertNull(expression)) {
                if (hasEqEqExpressionArgument(expression)) {
                    registerMethodCallError(expression, "assertNull()");
                }
                else {
                    registerMethodCallError(expression, "assertNotNull()");
                }
            }
            else if (isAssertThatCouldBeAssertSame(expression)) {
                if (hasEqEqExpressionArgument(expression)) {
                    registerMethodCallError(expression, "assertSame()");
                }
                else {
                    registerMethodCallError(expression, "assertNotSame()");
                }
            }
            else if (isAssertTrueThatCouldBeAssertEquals(expression)) {
                registerMethodCallError(expression, "assertEquals()");
            }
            else if (isAssertEqualsThatCouldBeAssertLiteral(expression)) {
                registerMethodCallError(expression, getReplacementMethodName(expression));
            }
            else if (isAssertThatCouldBeFail(expression)) {
                registerMethodCallError(expression, "fail()");
            }
        }

        @NonNls
        private static String getReplacementMethodName(PsiMethodCallExpression expression) {
            PsiExpressionList argumentList = expression.getArgumentList();
            PsiExpression[] arguments = argumentList.getExpressions();
            PsiExpression firstArgument = arguments[0];
            if (firstArgument instanceof PsiLiteralExpression) {
                PsiLiteralExpression literalExpression = (PsiLiteralExpression) firstArgument;
                Object value = literalExpression.getValue();
                if (value == Boolean.TRUE) {
                    return "assertTrue()";
                }
                else if (value == Boolean.FALSE) {
                    return "assertFalse()";
                }
                else if (value == null) {
                    return "assertNull()";
                }
            }
            PsiExpression secondArgument = arguments[1];
            if (secondArgument instanceof PsiLiteralExpression) {
                PsiLiteralExpression literalExpression = (PsiLiteralExpression) secondArgument;
                Object value = literalExpression.getValue();
                if (value == Boolean.TRUE) {
                    return "assertTrue()";
                }
                else if (value == Boolean.FALSE) {
                    return "assertFalse()";
                }
                else if (value == null) {
                    return "assertNull()";
                }
            }
            return "";
        }

        private static boolean hasEqEqExpressionArgument(PsiMethodCallExpression expression) {
            PsiExpressionList list = expression.getArgumentList();
            PsiExpression[] arguments = list.getExpressions();
            PsiExpression argument = arguments[0];
            if (!(argument instanceof PsiBinaryExpression)) {
                return false;
            }
            PsiBinaryExpression binaryExpression = (PsiBinaryExpression) argument;
            IElementType tokenType = binaryExpression.getOperationTokenType();
            return JavaTokenType.EQEQ.equals(tokenType);
        }
    }

    static boolean isAssertTrueThatCouldBeAssertEquals(
        PsiMethodCallExpression expression
    ) {
        if (!isAssertTrue(expression)) {
            return false;
        }
        PsiReferenceExpression methodExpression = expression.getMethodExpression();
        PsiMethod method = (PsiMethod) methodExpression.resolve();
        if (method == null) {
            return false;
        }
        PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() < 1) {
            return false;
        }
        PsiType stringType = TypeUtils.getStringType(expression);
        PsiParameter[] parameters = parameterList.getParameters();
        PsiType paramType1 = parameters[0].getType();
        int testPosition;
        if (paramType1.equals(stringType) && parameters.length > 1) {
            testPosition = 1;
        }
        else {
            testPosition = 0;
        }
        PsiExpressionList argumentList = expression.getArgumentList();
        PsiExpression[] arguments = argumentList.getExpressions();
        PsiExpression testArgument = arguments[testPosition];
        return testArgument != null && isEqualityComparison(testArgument);
    }

    static boolean isAssertThatCouldBeAssertSame(PsiMethodCallExpression expression) {
        if (!isAssertTrue(expression) && !isAssertFalse(expression)) {
            return false;
        }
        PsiReferenceExpression methodExpression = expression.getMethodExpression();
        PsiMethod method = (PsiMethod) methodExpression.resolve();
        if (method == null) {
            return false;
        }
        PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() < 1) {
            return false;
        }
        PsiType stringType = TypeUtils.getStringType(expression);
        PsiParameter[] parameters = parameterList.getParameters();
        PsiType paramType1 = parameters[0].getType();
        int testPosition;
        if (paramType1.equals(stringType) && parameters.length > 1) {
            testPosition = 1;
        }
        else {
            testPosition = 0;
        }
        PsiExpressionList argumentList = expression.getArgumentList();
        PsiExpression[] arguments = argumentList.getExpressions();
        PsiExpression testArgument = arguments[testPosition];
        return testArgument != null && isIdentityComparison(testArgument);
    }

    static boolean isAssertThatCouldBeAssertNull(PsiMethodCallExpression expression) {
        if (!isAssertTrue(expression) && !isAssertFalse(expression)) {
            return false;
        }
        PsiReferenceExpression methodExpression = expression.getMethodExpression();
        PsiMethod method = (PsiMethod) methodExpression.resolve();
        if (method == null) {
            return false;
        }
        PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() < 1) {
            return false;
        }
        PsiType stringType = TypeUtils.getStringType(expression);
        PsiParameter[] parameters = parameterList.getParameters();
        PsiType paramType1 = parameters[0].getType();
        int testPosition;
        if (paramType1.equals(stringType) && parameters.length > 1) {
            testPosition = 1;
        }
        else {
            testPosition = 0;
        }
        PsiExpressionList argumentList = expression.getArgumentList();
        PsiExpression[] arguments = argumentList.getExpressions();
        PsiExpression testArgument = arguments[testPosition];
        return testArgument != null && isNullComparison(testArgument);
    }


    static boolean isAssertThatCouldBeFail(PsiMethodCallExpression expression) {
        boolean checkTrue;
        if (isAssertFalse(expression)) {
            checkTrue = true;
        }
        else if (isAssertTrue(expression)) {
            checkTrue = false;
        }
        else {
            return false;
        }
        PsiReferenceExpression methodExpression = expression.getMethodExpression();
        PsiMethod method = (PsiMethod) methodExpression.resolve();
        if (method == null) {
            return false;
        }
        PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() < 1) {
            return false;
        }
        PsiType stringType = TypeUtils.getStringType(expression);
        PsiParameter[] parameters = parameterList.getParameters();
        PsiType paramType1 = parameters[0].getType();
        int testPosition;
        if (paramType1.equals(stringType) && parameters.length > 1) {
            testPosition = 1;
        }
        else {
            testPosition = 0;
        }
        PsiExpressionList argumentList = expression.getArgumentList();
        PsiExpression[] arguments = argumentList.getExpressions();
        PsiExpression testArgument = arguments[testPosition];
        if (testArgument == null) {
            return false;
        }
        String testArgumentText = testArgument.getText();
        if (checkTrue) {
            return PsiKeyword.TRUE.equals(testArgumentText);
        }
        else {
            return PsiKeyword.FALSE.equals(testArgumentText);
        }
    }

    static boolean isAssertEqualsThatCouldBeAssertLiteral(PsiMethodCallExpression expression) {
        if (!isAssertEquals(expression)) {
            return false;
        }
        PsiReferenceExpression methodExpression = expression.getMethodExpression();
        PsiMethod method = (PsiMethod) methodExpression.resolve();
        if (method == null) {
            return false;
        }
        PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() < 2) {
            return false;
        }
        PsiType stringType = TypeUtils.getStringType(expression);
        PsiParameter[] parameters = parameterList.getParameters();
        PsiType paramType1 = parameters[0].getType();
        int firstTestPosition;
        int secondTestPosition;
        if (paramType1.equals(stringType) && parameters.length > 2) {
            firstTestPosition = 1;
            secondTestPosition = 2;
        }
        else {
            firstTestPosition = 0;
            secondTestPosition = 1;
        }
        PsiExpressionList argumentList = expression.getArgumentList();
        PsiExpression[] arguments = argumentList.getExpressions();
        PsiExpression firstTestArgument = arguments[firstTestPosition];
        PsiExpression secondTestArgument = arguments[secondTestPosition];
        if (firstTestArgument == null || secondTestArgument == null) {
            return false;
        }
        return isSimpleLiteral(firstTestArgument, secondTestArgument) ||
            isSimpleLiteral(secondTestArgument, firstTestArgument);
    }

    static boolean isSimpleLiteral(PsiExpression expression1, PsiExpression expression2) {
        if (!(expression1 instanceof PsiLiteralExpression)) {
            return false;
        }
        String text = expression1.getText();
        if (PsiKeyword.NULL.equals(text)) {
            return true;
        }
        if (!PsiKeyword.TRUE.equals(text) && !PsiKeyword.FALSE.equals(text)) {
            return false;
        }
        PsiType type = expression2.getType();
        return PsiType.BOOLEAN.equals(type);
    }

    private static boolean isEqualityComparison(PsiExpression expression) {
        if (expression instanceof PsiBinaryExpression) {
            PsiBinaryExpression binaryExpression = (PsiBinaryExpression) expression;
            IElementType tokenType = binaryExpression.getOperationTokenType();
            if (!tokenType.equals(JavaTokenType.EQEQ)) {
                return false;
            }
            PsiExpression lhs = binaryExpression.getLOperand();
            PsiExpression rhs = binaryExpression.getROperand();
            if (rhs == null) {
                return false;
            }
            PsiType type = lhs.getType();
            return type != null && ClassUtils.isPrimitive(type);
        }
        else if (expression instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression call = (PsiMethodCallExpression) expression;
            if (!MethodCallUtils.isEqualsCall(call)) {
                return false;
            }
            PsiReferenceExpression methodExpression = call.getMethodExpression();
            return methodExpression.getQualifierExpression() != null;
        }
        return false;
    }

    private static boolean isIdentityComparison(PsiExpression expression) {
        if (!(expression instanceof PsiBinaryExpression)) {
            return false;
        }
        PsiBinaryExpression binaryExpression = (PsiBinaryExpression) expression;
        if (!ComparisonUtils.isEqualityComparison(binaryExpression)) {
            return false;
        }
        PsiExpression rhs = binaryExpression.getROperand();
        if (rhs == null) {
            return false;
        }
        PsiExpression lhs = binaryExpression.getLOperand();
        PsiType lhsType = lhs.getType();
        if (lhsType instanceof PsiPrimitiveType) {
            return false;
        }
        PsiType rhsType = rhs.getType();
        return !(rhsType instanceof PsiPrimitiveType);
    }

    private static boolean isNullComparison(PsiExpression expression) {
        if (!(expression instanceof PsiBinaryExpression)) {
            return false;
        }
        PsiBinaryExpression binaryExpression = (PsiBinaryExpression) expression;
        if (!ComparisonUtils.isEqualityComparison(binaryExpression)) {
            return false;
        }
        PsiExpression rhs = binaryExpression.getROperand();
        if (rhs == null) {
            return false;
        }
        PsiExpression lhs = binaryExpression.getLOperand();
        return PsiKeyword.NULL.equals(lhs.getText()) || PsiKeyword.NULL.equals(rhs.getText());
    }

    private static boolean isAssertTrue(@Nonnull PsiMethodCallExpression expression) {
        return isAssertMethodCall(expression, "assertTrue");
    }

    private static boolean isAssertFalse(@Nonnull PsiMethodCallExpression expression) {
        return isAssertMethodCall(expression, "assertFalse");
    }

    private static boolean isAssertEquals(@Nonnull PsiMethodCallExpression expression) {
        return isAssertMethodCall(expression, "assertEquals");
    }

    private static boolean isAssertMethodCall(
        @Nonnull PsiMethodCallExpression expression,
        @NonNls @Nonnull String assertMethodName
    ) {
        PsiReferenceExpression methodExpression = expression.getMethodExpression();
        @NonNls String methodName = methodExpression.getReferenceName();
        if (!assertMethodName.equals(methodName)) {
            return false;
        }
        PsiMethod method = (PsiMethod) methodExpression.resolve();
        if (method == null) {
            return false;
        }
        PsiClass targetClass = method.getContainingClass();
        if (targetClass == null) {
            return false;
        }
        String qualifiedName = targetClass.getQualifiedName();
        return "junit.framework.Assert".equals(qualifiedName) || "org.junit.Assert".equals(qualifiedName);
    }
}
