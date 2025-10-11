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
package com.intellij.java.impl.ig.errorhandling;

import com.intellij.java.language.psi.PsiThrowStatement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class ThrowFromFinallyBlockInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.throwFromFinallyBlockDisplayName();
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.throwFromFinallyBlockProblemDescriptor().get();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ThrowFromFinallyBlockVisitor();
    }

    private static class ThrowFromFinallyBlockVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitThrowStatement(PsiThrowStatement statement) {
            super.visitThrowStatement(statement);
            if (!ControlFlowUtils.isInFinallyBlock(statement)) {
                return;
            }
            registerStatementError(statement);
        }
    }
}