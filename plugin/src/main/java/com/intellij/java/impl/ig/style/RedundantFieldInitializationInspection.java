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
package com.intellij.java.impl.ig.style;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;

@ExtensionImpl
public class RedundantFieldInitializationInspection extends BaseInspection {
    @SuppressWarnings("PublicField")
    public boolean onlyWarnOnNull = false;

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.redundantFieldInitializationDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.redundantFieldInitializationProblemDescriptor().get();
    }

    @Nullable
    @Override
    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel("Only warn on initialization to null", this, "onlyWarnOnNull");
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new RedundantFieldInitializationFix();
    }

    private static class RedundantFieldInitializationFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.redundantFieldInitializationRemoveQuickfix();
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            descriptor.getPsiElement().delete();
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new RedundantFieldInitializationVisitor();
    }

    private class RedundantFieldInitializationVisitor extends BaseInspectionVisitor {
        @Override
        public void visitField(@Nonnull PsiField field) {
            super.visitField(field);
            if (!field.hasInitializer() || field.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            final PsiExpression initializer = field.getInitializer();
            if (initializer == null) {
                return;
            }
            final String text = initializer.getText();
            final PsiType type = field.getType();
            if (PsiType.BOOLEAN.equals(type)) {
                if (onlyWarnOnNull || !PsiKeyword.FALSE.equals(text)) {
                    return;
                }
            }
            else if (type instanceof PsiPrimitiveType) {
                if (onlyWarnOnNull || !ExpressionUtils.isZero(initializer)) {
                    return;
                }
            }
            else if (!PsiType.NULL.equals(initializer.getType())) {
                return;
            }
            registerError(initializer, ProblemHighlightType.LIKE_UNUSED_SYMBOL);
        }
    }
}