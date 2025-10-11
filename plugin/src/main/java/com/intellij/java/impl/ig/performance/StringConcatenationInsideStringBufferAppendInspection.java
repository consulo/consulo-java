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
package com.intellij.java.impl.ig.performance;

import com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix.ChangeToAppendFix;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class StringConcatenationInsideStringBufferAppendInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.stringConcatenationInsideStringBufferAppendDisplayName();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    @Nonnull
    @RequiredReadAction
    public String buildErrorString(Object... infos) {
        final PsiClass aClass = (PsiClass) infos[0];
        final String className = aClass.getName();
        return InspectionGadgetsLocalize.stringConcatenationInsideStringBufferAppendProblemDescriptor(className).get();
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new StringConcatenationInsideStringBufferAppendVisitor();
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new ReplaceWithChainedAppendFix();
    }

    private static class ReplaceWithChainedAppendFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.stringConcatenationInsideStringBufferAppendReplaceQuickfix();
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            final PsiElement methodNameElement = descriptor.getPsiElement();
            final PsiReferenceExpression methodExpression = (PsiReferenceExpression) methodNameElement.getParent();
            if (methodExpression == null) {
                return;
            }
            final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) methodExpression.getParent();
            if (methodCallExpression == null) {
                return;
            }
            final PsiExpression qualifier = methodExpression.getQualifierExpression();
            if (qualifier == null) {
                return;
            }
            final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            final PsiExpression argument = arguments[0];
            final PsiExpression appendExpression = ChangeToAppendFix.buildAppendExpression(qualifier, argument);
            if (appendExpression == null) {
                return;
            }
            methodCallExpression.replace(appendExpression);
        }
    }

    private static class StringConcatenationInsideStringBufferAppendVisitor extends BaseInspectionVisitor {
        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            if (!"append".equals(methodName)) {
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length != 1) {
                return;
            }
            final PsiExpression argument = arguments[0];
            if (!isConcatenation(argument)) {
                return;
            }
            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return;
            }
            final String className = containingClass.getQualifiedName();
            if (CommonClassNames.JAVA_LANG_STRING_BUFFER.equals(className)
                || CommonClassNames.JAVA_LANG_STRING_BUILDER.equals(className)) {
                registerMethodCallError(expression, containingClass);
                return;
            }
            final Project project = containingClass.getProject();
            final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
            final PsiClass appendableClass = psiFacade.findClass("java.lang.Appendable", GlobalSearchScope.allScope(project));
            if (appendableClass == null) {
                return;
            }
            if (!InheritanceUtil.isInheritorOrSelf(containingClass, appendableClass, true)) {
                return;
            }
            registerMethodCallError(expression, containingClass);
        }

        private static boolean isConcatenation(PsiExpression expression) {
            if (expression instanceof PsiParenthesizedExpression) {
                final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression) expression;
                return isConcatenation(parenthesizedExpression.getExpression());
            }
            if (!(expression instanceof PsiPolyadicExpression)) {
                return false;
            }
            if (PsiUtil.isConstantExpression(expression)) {
                return false;
            }
            final PsiType type = expression.getType();
            if (type == null) {
                return false;
            }
            return type.equalsToText(CommonClassNames.JAVA_LANG_STRING);
        }
    }
}