/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.assignment;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CollectionUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.intellij.lang.annotations.Pattern;

import javax.swing.*;

@ExtensionImpl
public class AssignmentToCollectionFieldFromParameterInspection extends BaseInspection {
    /**
     * @noinspection PublicField
     */
    public boolean ignorePrivateMethods = true;

    @Nonnull
    @Override
    @Pattern("[a-zA-Z_0-9.]+")
    public String getID() {
        return "AssignmentToCollectionOrArrayFieldFromParameter";
    }

    @Nonnull
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.assignmentCollectionArrayFieldFromParameterDisplayName();
    }

    @Nonnull
    @RequiredReadAction
    public String buildErrorString(Object... infos) {
        PsiExpression rhs = (PsiExpression) infos[0];
        PsiField field = (PsiField) infos[1];
        PsiType type = field.getType();
        return type instanceof PsiArrayType
            ? InspectionGadgetsLocalize.assignmentCollectionArrayFieldFromParameterProblemDescriptorArray(rhs.getText()).get()
            : InspectionGadgetsLocalize.assignmentCollectionArrayFieldFromParameterProblemDescriptorCollection(rhs.getText()).get();
    }

    @Nullable
    public JComponent createOptionsPanel() {
        LocalizeValue message = InspectionGadgetsLocalize.assignmentCollectionArrayFieldOption();
        return new SingleCheckboxOptionsPanel(message.get(), this, "ignorePrivateMethods");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new AssignmentToCollectionFieldFromParameterVisitor();
    }

    private class AssignmentToCollectionFieldFromParameterVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitAssignmentExpression(
            @Nonnull
            PsiAssignmentExpression expression
        ) {
            super.visitAssignmentExpression(expression);
            PsiExpression rhs = expression.getRExpression();
            if (!(rhs instanceof PsiReferenceExpression)) {
                return;
            }
            IElementType tokenType = expression.getOperationTokenType();
            if (!tokenType.equals(JavaTokenType.EQ)) {
                return;
            }
            PsiExpression lhs = expression.getLExpression();
            if (!(lhs instanceof PsiReferenceExpression)) {
                return;
            }
            if (ignorePrivateMethods) {
                PsiMethod containingMethod =
                    PsiTreeUtil.getParentOfType(
                        expression,
                        PsiMethod.class
                    );
                if (containingMethod == null ||
                    containingMethod.hasModifierProperty(
                        PsiModifier.PRIVATE)) {
                    return;
                }
            }
            PsiElement element = ((PsiReference) rhs).resolve();
            if (!(element instanceof PsiParameter)) {
                return;
            }
            if (!(element.getParent() instanceof PsiParameterList)) {
                return;
            }
            PsiReferenceExpression referenceExpression =
                (PsiReferenceExpression) lhs;
            PsiElement referent = referenceExpression.resolve();
            if (!(referent instanceof PsiField)) {
                return;
            }
            PsiField field = (PsiField) referent;
            if (!CollectionUtils.isArrayOrCollectionField(field)) {
                return;
            }
            registerError(lhs, rhs, field);
        }
    }
}