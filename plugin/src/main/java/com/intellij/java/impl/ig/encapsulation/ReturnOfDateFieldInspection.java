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
package com.intellij.java.impl.ig.encapsulation;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;

@ExtensionImpl
public class ReturnOfDateFieldInspection extends BaseInspection {
    @SuppressWarnings({"PublicField"})
    public boolean ignorePrivateMethods = false;

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.returnDateCalendarFieldDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        String type = (String) infos[0];
        return InspectionGadgetsLocalize.returnDateCalendarFieldProblemDescriptor(type).get();
    }

    @Nullable
    @Override
    public JComponent createOptionsPanel() {
        LocalizeValue message = InspectionGadgetsLocalize.returnOfNullIgnorePrivateOption();
        return new SingleCheckboxOptionsPanel(message.get(), this, "ignorePrivateMethods");
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new ReturnOfDateFieldFix((String) infos[0]);
    }

    private static class ReturnOfDateFieldFix extends InspectionGadgetsFix {
        private final String myType;

        public ReturnOfDateFieldFix(String type) {
            myType = type;
        }

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.returnDateCalendarFieldQuickfix(myType);
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiElement element = descriptor.getPsiElement();
            if (!(element instanceof PsiReferenceExpression)) {
                return;
            }
            PsiReferenceExpression referenceExpression = (PsiReferenceExpression) element;
            String type =
                TypeUtils.expressionHasTypeOrSubtype(
                    referenceExpression,
                    CommonClassNames.JAVA_UTIL_DATE,
                    CommonClassNames.JAVA_UTIL_CALENDAR
                );
            if (type == null) {
                return;
            }
            replaceExpression(referenceExpression, '(' + type + ')' + referenceExpression.getText() + ".clone()");
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ReturnOfDateFieldVisitor();
    }

    private class ReturnOfDateFieldVisitor extends BaseInspectionVisitor {

        @Override
        public void visitReturnStatement(@Nonnull PsiReturnStatement statement) {
            super.visitReturnStatement(statement);
            PsiExpression returnValue = statement.getReturnValue();
            if (!(returnValue instanceof PsiReferenceExpression)) {
                return;
            }
            PsiMethod method = PsiTreeUtil.getParentOfType(statement, PsiMethod.class, true, PsiClass.class);
            if (method == null || (ignorePrivateMethods && method.hasModifierProperty(PsiModifier.PRIVATE))) {
                return;
            }
            PsiReferenceExpression fieldReference = (PsiReferenceExpression) returnValue;
            PsiElement element = fieldReference.resolve();
            if (!(element instanceof PsiField)) {
                return;
            }
            String type =
                TypeUtils.expressionHasTypeOrSubtype(returnValue, CommonClassNames.JAVA_UTIL_DATE, CommonClassNames.JAVA_UTIL_CALENDAR);
            if (type == null) {
                return;
            }
            registerError(returnValue, type);
        }
    }
}