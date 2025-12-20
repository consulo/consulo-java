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
package com.intellij.java.impl.ig.controlflow;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class UnnecessaryLabelOnBreakStatementInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.unnecessaryLabelOnBreakStatementDisplayName();
    }

    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.unnecessaryLabelOnBreakStatementProblemDescriptor().get();
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    public InspectionGadgetsFix buildFix(Object... infos) {
        return new UnnecessaryLabelOnBreakStatementFix();
    }

    private static class UnnecessaryLabelOnBreakStatementFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.unnecessaryLabelRemoveQuickfix();
        }

        public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiElement breakKeywordElement = descriptor.getPsiElement();
            PsiBreakStatement breakStatement = (PsiBreakStatement) breakKeywordElement.getParent();
            PsiIdentifier identifier = breakStatement.getLabelIdentifier();
            if (identifier == null) {
                return;
            }
            identifier.delete();
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryLabelOnBreakStatementVisitor();
    }

    private static class UnnecessaryLabelOnBreakStatementVisitor extends BaseInspectionVisitor {
        @Override
        public void visitBreakStatement(@Nonnull PsiBreakStatement statement) {
            PsiIdentifier labelIdentifier = statement.getLabelIdentifier();
            if (labelIdentifier == null) {
                return;
            }
            String labelText = labelIdentifier.getText();
            if (labelText == null || labelText.length() == 0) {
                return;
            }
            PsiStatement exitedStatement = statement.findExitedStatement();
            if (exitedStatement == null) {
                return;
            }
            PsiStatement labelEnabledParent =
                PsiTreeUtil.getParentOfType(statement,
                    PsiForStatement.class, PsiDoWhileStatement.class,
                    PsiForeachStatement.class, PsiWhileStatement.class,
                    PsiSwitchStatement.class
                );
            if (labelEnabledParent == null) {
                return;
            }
            if (exitedStatement.equals(labelEnabledParent)) {
                registerStatementError(statement);
            }
        }
    }
}