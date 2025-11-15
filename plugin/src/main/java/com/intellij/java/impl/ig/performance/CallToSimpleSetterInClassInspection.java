/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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

import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PropertyUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.query.Query;
import consulo.deadCodeNotWorking.impl.MultipleCheckboxOptionsPanel;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;

@ExtensionImpl
public class CallToSimpleSetterInClassInspection extends BaseInspection {
    @SuppressWarnings("UnusedDeclaration")
    public boolean ignoreSetterCallsOnOtherObjects = false;

    @SuppressWarnings("UnusedDeclaration")
    public boolean onlyReportPrivateSetter = false;

    @Nonnull
    @Override
    public String getID() {
        return "CallToSimpleSetterFromWithinClass";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.callToSimpleSetterInClassDisplayName();
    }

    @Nonnull
    @Override
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.callToSimpleSetterInClassProblemDescriptor().get();
    }

    @Nullable
    @Override
    public JComponent createOptionsPanel() {
        MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
        optionsPanel.addCheckbox(
            InspectionGadgetsLocalize.callToSimpleSetterInClassIgnoreOption().get(),
            "ignoreSetterCallsOnOtherObjects"
        );
        optionsPanel.addCheckbox(
            InspectionGadgetsLocalize.callToPrivateSetterInClassOption().get(),
            "onlyReportPrivateSetter"
        );
        return optionsPanel;
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new InlineCallFix();
    }

    private static class InlineCallFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.callToSimpleSetterInClassInlineQuickfix();
        }

        @Override
        @RequiredWriteAction
        public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiElement methodIdentifier = descriptor.getPsiElement();
            PsiReferenceExpression methodExpression = (PsiReferenceExpression) methodIdentifier.getParent();
            if (methodExpression == null) {
                return;
            }
            PsiMethodCallExpression call = (PsiMethodCallExpression) methodExpression.getParent();
            if (call == null) {
                return;
            }
            PsiExpressionList argumentList = call.getArgumentList();
            PsiExpression[] arguments = argumentList.getExpressions();
            PsiExpression argument = arguments[0];
            PsiMethod method = call.resolveMethod();
            if (method == null) {
                return;
            }
            PsiCodeBlock body = method.getBody();
            if (body == null) {
                return;
            }
            PsiStatement[] statements = body.getStatements();
            PsiExpressionStatement assignmentStatement = (PsiExpressionStatement) statements[0];
            PsiAssignmentExpression assignment = (PsiAssignmentExpression) assignmentStatement.getExpression();
            PsiExpression qualifier = methodExpression.getQualifierExpression();
            PsiReferenceExpression lhs = (PsiReferenceExpression) assignment.getLExpression();
            PsiField field = (PsiField) lhs.resolve();
            if (field == null) {
                return;
            }
            String fieldName = field.getName();
            if (qualifier == null) {
                PsiResolveHelper resolveHelper = PsiResolveHelper.getInstance(call.getProject());
                PsiVariable variable = resolveHelper.resolveReferencedVariable(fieldName, call);
                if (variable == null) {
                    return;
                }
                String newExpression;
                if (variable.equals(field)) {
                    newExpression = fieldName + " = " + argument.getText();
                }
                else {
                    newExpression = "this." + fieldName + " = " + argument.getText();
                }
                replaceExpression(call, newExpression);
            }
            else {
                String newExpression = qualifier.getText() + '.' + fieldName + " = " + argument.getText();
                replaceExpression(call, newExpression);
            }
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new CallToSimpleSetterInClassVisitor();
    }

    private class CallToSimpleSetterInClassVisitor extends BaseInspectionVisitor {
        @Override
        public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression call) {
            super.visitMethodCallExpression(call);
            PsiClass containingClass = ClassUtils.getContainingClass(call);
            if (containingClass == null) {
                return;
            }
            PsiMethod method = call.resolveMethod();
            if (method == null) {
                return;
            }
            if (!containingClass.equals(method.getContainingClass())) {
                return;
            }
            PsiReferenceExpression methodExpression = call.getMethodExpression();
            PsiExpression qualifier = methodExpression.getQualifierExpression();
            if (qualifier != null && !(qualifier instanceof PsiThisExpression)) {
                if (ignoreSetterCallsOnOtherObjects) {
                    return;
                }
                PsiType type = qualifier.getType();
                if (!(type instanceof PsiClassType classType)) {
                    return;
                }
                PsiClass qualifierClass = classType.resolve();
                if (!containingClass.equals(qualifierClass)) {
                    return;
                }
            }
            if (!PropertyUtil.isSimpleSetter(method)) {
                return;
            }
            if (onlyReportPrivateSetter && !method.isPrivate()) {
                return;
            }
            Query<PsiMethod> query = OverridingMethodsSearch.search(method, true);
            PsiMethod overridingMethod = query.findFirst();
            if (overridingMethod != null) {
                return;
            }
            registerMethodCallError(call);
        }
    }
}