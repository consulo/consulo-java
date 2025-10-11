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
package com.intellij.java.impl.ig.errorhandling;

import com.intellij.java.language.psi.PsiReturnStatement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.Pattern;

@ExtensionImpl
public class ReturnFromFinallyBlockInspection extends BaseInspection {
    @Nonnull
    @Override
    @Pattern(VALID_ID_PATTERN)
    public String getID() {
        return "ReturnInsideFinallyBlock";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.returnFromFinallyBlockDisplayName();
    }

    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.returnFromFinallyBlockProblemDescriptor().get();
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ReturnFromFinallyBlockVisitor();
    }

    private static class ReturnFromFinallyBlockVisitor extends BaseInspectionVisitor {

        @Override
        public void visitReturnStatement(@Nonnull PsiReturnStatement statement) {
            super.visitReturnStatement(statement);
            if (!ControlFlowUtils.isInFinallyBlock(statement)) {
                return;
            }
            registerStatementError(statement);
        }
    }
}