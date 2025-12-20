/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.intellij.java.analysis.impl.codeInspection.ControlFlowUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class EqualsWhichDoesntCheckParameterClassInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.equalsDoesntCheckClassParameterDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.equalsDoesntCheckClassParameterProblemDescriptor().get();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new EqualsWhichDoesntCheckParameterClassVisitor();
    }

    private static class EqualsWhichDoesntCheckParameterClassVisitor extends BaseInspectionVisitor {

        @Override
        public void visitMethod(@Nonnull PsiMethod method) {
            // note: no call to super
            if (!MethodUtils.isEquals(method)) {
                return;
            }
            PsiParameterList parameterList = method.getParameterList();
            PsiParameter[] parameters = parameterList.getParameters();
            PsiParameter parameter = parameters[0];
            PsiCodeBlock body = method.getBody();
            if (body == null || isParameterChecked(body, parameter) || isParameterCheckNotNeeded(body, parameter)) {
                return;
            }
            registerMethodError(method);
        }

        private static boolean isParameterChecked(PsiCodeBlock body, PsiParameter parameter) {
            ParameterClassCheckVisitor visitor = new ParameterClassCheckVisitor(parameter);
            body.accept(visitor);
            return visitor.isChecked();
        }

        private static boolean isParameterCheckNotNeeded(PsiCodeBlock body, PsiParameter parameter) {
            if (ControlFlowUtils.isEmptyCodeBlock(body)) {
                return true; // incomplete code
            }
            PsiStatement statement = ControlFlowUtils.getOnlyStatementInBlock(body);
            if (statement == null) {
                return false;
            }
            if (!(statement instanceof PsiReturnStatement)) {
                return true; // incomplete code
            }
            PsiReturnStatement returnStatement = (PsiReturnStatement) statement;
            PsiExpression returnValue = returnStatement.getReturnValue();
            Object constant = ExpressionUtils.computeConstantExpression(returnValue);
            if (Boolean.FALSE.equals(constant)) {
                return true; // incomplete code
            }
            if (isEqualsBuilderReflectionEquals(returnValue)) {
                return true;
            }
            if (isIdentityEquals(returnValue, parameter)) {
                return true;
            }
            return false;
        }

        private static boolean isIdentityEquals(PsiExpression expression, PsiParameter parameter) {
            if (!(expression instanceof PsiBinaryExpression)) {
                return false;
            }
            PsiBinaryExpression binaryExpression = (PsiBinaryExpression) expression;
            PsiExpression lhs = binaryExpression.getLOperand();
            PsiExpression rhs = binaryExpression.getROperand();
            return isIdentityEquals(lhs, rhs, parameter) || isIdentityEquals(rhs, lhs, parameter);
        }

        private static boolean isIdentityEquals(PsiExpression lhs, PsiExpression rhs, PsiParameter parameter) {
            if (!(lhs instanceof PsiReferenceExpression)) {
                return false;
            }
            PsiReferenceExpression referenceExpression = (PsiReferenceExpression) lhs;
            PsiElement target = referenceExpression.resolve();
            if (target != parameter) {
                return false;
            }
            if (!(rhs instanceof PsiThisExpression)) {
                return false;
            }
            PsiThisExpression thisExpression = (PsiThisExpression) rhs;
            return thisExpression.getQualifier() == null;
        }

        private static boolean isEqualsBuilderReflectionEquals(PsiExpression expression) {
            if (!(expression instanceof PsiMethodCallExpression)) {
                return false;
            }
            PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) expression;
            PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
            @NonNls String referenceName = methodExpression.getReferenceName();
            if (!"reflectionEquals".equals(referenceName)) {
                return false;
            }
            PsiExpression qualifier = methodExpression.getQualifierExpression();
            if (!(qualifier instanceof PsiReferenceExpression)) {
                return false;
            }
            PsiReferenceExpression referenceExpression = (PsiReferenceExpression) qualifier;
            PsiElement target = referenceExpression.resolve();
            if (!(target instanceof PsiClass)) {
                return false;
            }
            PsiClass aClass = (PsiClass) target;
            String className = aClass.getQualifiedName();
            return "org.apache.commons.lang.builder.EqualsBuilder".equals(className);
        }
    }
}