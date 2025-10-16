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
import com.intellij.java.language.psi.PsiKeyword;
import com.intellij.java.language.psi.PsiTryStatement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.intellij.java.analysis.impl.codeInspection.ControlFlowUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.Pattern;

@ExtensionImpl
public class FinallyBlockCannotCompleteNormallyInspection extends BaseInspection {
    @Nonnull
    @Override
    @Pattern(VALID_ID_PATTERN)
    public String getID() {
        return "finally";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.finallyBlockCannotCompleteNormallyDisplayName();
    }

    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.finallyBlockCannotCompleteNormallyProblemDescriptor().get();
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new FinallyBlockCannotCompleteNormallyVisitor();
    }

    private static class FinallyBlockCannotCompleteNormallyVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitTryStatement(@Nonnull PsiTryStatement statement) {
            super.visitTryStatement(statement);
            final PsiCodeBlock finallyBlock = statement.getFinallyBlock();
            if (finallyBlock == null) {
                return;
            }
            if (ControlFlowUtils.codeBlockMayCompleteNormally(finallyBlock)) {
                return;
            }
            final PsiElement[] children = statement.getChildren();
            for (final PsiElement child : children) {
                final String childText = child.getText();
                if (PsiKeyword.FINALLY.equals(childText)) {
                    registerError(child);
                    return;
                }
            }
        }
    }
}