/*
 * Copyright 2007-2011 Bas Leijdekkers
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
package com.intellij.java.impl.ig.bugs;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class ImplicitArrayToStringInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.implicitArrayToStringDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        if ((Boolean) infos[1]) {
            return InspectionGadgetsLocalize.explicitArrayToStringProblemDescriptor().get();
        }
        else if (infos[0] instanceof PsiMethodCallExpression) {
            return InspectionGadgetsLocalize.implicitArrayToStringMethodCallProblemDescriptor().get();
        }
        else {
            return InspectionGadgetsLocalize.implicitArrayToStringProblemDescriptor().get();
        }
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    @Nullable
    protected InspectionGadgetsFix buildFix(Object... infos) {
        PsiExpression expression = (PsiExpression) infos[0];
        boolean removeToString = ((Boolean) infos[1]).booleanValue();
        PsiArrayType type = (PsiArrayType) expression.getType();
        if (type != null) {
            PsiType componentType = type.getComponentType();
            if (componentType instanceof PsiArrayType) {
                return new ImplicitArrayToStringFix(true, removeToString);
            }
        }
        return new ImplicitArrayToStringFix(false, removeToString);
    }

    private static class ImplicitArrayToStringFix extends InspectionGadgetsFix {

        private final boolean deepString;
        private final boolean removeToString;

        ImplicitArrayToStringFix(boolean deepString, boolean removeToString) {
            this.deepString = deepString;
            this.removeToString = removeToString;
        }

        @Nonnull
        public LocalizeValue getName() {
            String expressionText;
            if (deepString) {
                expressionText = "java.util.Arrays.deepToString()";
            }
            else {
                expressionText = "java.util.Arrays.toString()";
            }
            return InspectionGadgetsLocalize.implicitArrayToStringQuickfix(expressionText);
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor)
            throws IncorrectOperationException {
            PsiElement element = descriptor.getPsiElement();
            PsiExpression expression;
            if (element instanceof PsiExpression) {
                expression = (PsiExpression) element;
            }
            else {
                expression = (PsiExpression) element.getParent().getParent();
            }
            String expressionText;
            if (removeToString) {
                PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression) expression;
                PsiReferenceExpression methodExpression =
                    methodCallExpression.getMethodExpression();
                PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
                if (qualifier == null) {
                    return;
                }
                expressionText = qualifier.getText();
            }
            else {
                expressionText = expression.getText();
            }
            @NonNls String newExpressionText;
            if (deepString) {
                newExpressionText =
                    "java.util.Arrays.deepToString(" + expressionText + ')';
            }
            else {
                newExpressionText =
                    "java.util.Arrays.toString(" + expressionText + ')';
            }
            PsiElement parent = expression.getParent();
            if (parent instanceof PsiExpressionList) {
                PsiElement grandParent = parent.getParent();
                if (grandParent instanceof PsiMethodCallExpression) {
                    PsiMethodCallExpression methodCallExpression =
                        (PsiMethodCallExpression) grandParent;
                    PsiReferenceExpression methodExpression =
                        methodCallExpression.getMethodExpression();
                    if ("valueOf".equals(methodExpression.getReferenceName())) {
                        replaceExpressionAndShorten(
                            methodCallExpression,
                            newExpressionText
                        );
                        return;
                    }
                }
            }
            replaceExpressionAndShorten(expression, newExpressionText);
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ImplicitArrayToStringVisitor();
    }

    private static class ImplicitArrayToStringVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitReferenceExpression(
            PsiReferenceExpression expression
        ) {
            super.visitReferenceExpression(expression);
            if (!isImplicitArrayToStringCall(expression)) {
                return;
            }
            registerError(expression, expression, Boolean.FALSE);
        }

        @Override
        public void visitNewExpression(PsiNewExpression expression) {
            super.visitNewExpression(expression);
            if (!isImplicitArrayToStringCall(expression)) {
                return;
            }
            registerError(expression, expression, Boolean.FALSE);
        }

        @Override
        public void visitMethodCallExpression(
            PsiMethodCallExpression expression
        ) {
            super.visitMethodCallExpression(expression);
            if (isExplicitArrayToStringCall(expression)) {
                PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
                PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
                registerMethodCallError(expression, qualifier, Boolean.TRUE);
                return;
            }
            if (!isImplicitArrayToStringCall(expression)) {
                return;
            }
            registerError(expression, expression, Boolean.FALSE);
        }

        private static boolean isExplicitArrayToStringCall(
            PsiMethodCallExpression expression
        ) {
            PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
            String methodName = methodExpression.getReferenceName();
            if (!HardcodedMethodConstants.TO_STRING.equals(methodName)) {
                return false;
            }
            PsiExpressionList argumentList = expression.getArgumentList();
            PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length != 0) {
                return false;
            }
            PsiExpression qualifier =
                methodExpression.getQualifierExpression();
            if (qualifier == null) {
                return false;
            }
            PsiType type = qualifier.getType();
            return type instanceof PsiArrayType;
        }

        private static boolean isImplicitArrayToStringCall(
            PsiExpression expression
        ) {
            PsiType type = expression.getType();
            if (!(type instanceof PsiArrayType)) {
                return false;
            }
            if (ExpressionUtils.isStringConcatenationOperand(expression)) {
                return true;
            }
            PsiElement parent = expression.getParent();
            if (parent instanceof PsiExpressionList) {
                PsiExpressionList expressionList =
                    (PsiExpressionList) parent;
                PsiArrayType arrayType = (PsiArrayType) type;
                PsiType componentType = arrayType.getComponentType();
                if (componentType.equals(PsiType.CHAR)) {
                    return false;
                }
                PsiElement grandParent = expressionList.getParent();
                if (!(grandParent instanceof PsiMethodCallExpression)) {
                    return false;
                }
                PsiExpression[] arguments =
                    expressionList.getExpressions();
                PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression) grandParent;
                PsiReferenceExpression methodExpression =
                    methodCallExpression.getMethodExpression();
                @NonNls String methodName =
                    methodExpression.getReferenceName();
                PsiMethod method =
                    methodCallExpression.resolveMethod();
                if (method == null) {
                    return false;
                }
                PsiClass containingClass = method.getContainingClass();
                if (containingClass == null) {
                    return false;
                }
                if ("append".equals(methodName)) {
                    if (arguments.length != 1) {
                        return false;
                    }
                    return InheritanceUtil.isInheritor(containingClass, CommonClassNames.JAVA_LANG_ABSTRACT_STRING_BUILDER);
                }
                else if ("valueOf".equals(methodName)) {
                    if (arguments.length != 1) {
                        return false;
                    }
                    String qualifiedName =
                        containingClass.getQualifiedName();
                    return CommonClassNames.JAVA_LANG_STRING.equals(qualifiedName);
                }
                if (!"print".equals(methodName) &&
                    !"println".equals(methodName)) {
                    if (!"printf".equals(methodName) &&
                        !"format".equals(methodName)) {
                        return false;
                    }
                    else {
                        if (arguments.length < 1) {
                            return false;
                        }
                        PsiParameterList parameterList =
                            method.getParameterList();
                        PsiParameter[] parameters =
                            parameterList.getParameters();
                        PsiParameter parameter = parameters[0];
                        PsiType firstParameterType = parameter.getType();
                        if (firstParameterType.equalsToText(
                            "java.util.Locale")) {
                            if (arguments.length < 4) {
                                return false;
                            }
                        }
                        else {
                            if (arguments.length < 3) {
                                return false;
                            }
                        }
                    }
                }
                String qualifiedName = containingClass.getQualifiedName();
                if ("java.util.Formatter".equals(qualifiedName) ||
                    CommonClassNames.JAVA_LANG_STRING.equals(qualifiedName)) {
                    return true;
                }
                if (InheritanceUtil.isInheritor(containingClass, CommonClassNames.JAVA_IO_PRINT_STREAM)) {
                    return true;
                }
                else if (InheritanceUtil.isInheritor(containingClass, CommonClassNames.JAVA_IO_PRINT_WRITER)) {
                    return true;
                }
            }
            return false;
        }
    }
}