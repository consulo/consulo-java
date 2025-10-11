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

import com.intellij.java.language.psi.PsiCodeBlock;
import com.intellij.java.language.psi.PsiTryStatement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class EmptyTryBlockInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.emptyTryBlockDisplayName();
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.emptyTryBlockProblemDescriptor().get();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new EmptyTryBlockVisitor();
    }

    private static class EmptyTryBlockVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitTryStatement(@Nonnull PsiTryStatement statement) {
            super.visitTryStatement(statement);
            /*if (JspPsiUtil.isInJspFile(statement.getContainingFile())) {
                return;
            }*/
            final PsiCodeBlock finallyBlock = statement.getTryBlock();
            if (finallyBlock == null) {
                return;
            }
            if (finallyBlock.getStatements().length != 0) {
                return;
            }
            registerStatementError(statement);
        }
    }
}