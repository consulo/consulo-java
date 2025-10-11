/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import javax.swing.*;

@ExtensionImpl
public class StringReplaceableByStringBufferInspection extends BaseInspection {
    @SuppressWarnings({"PublicField"})
    public boolean onlyWarnOnLoop = true;

    @Override
    @Nonnull
    public String getID() {
        return "NonConstantStringShouldBeStringBuffer";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.stringReplaceableByStringBufferDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.stringReplaceableByStringBufferProblemDescriptor().get();
    }

    @Override
    public JComponent createOptionsPanel() {
        LocalizeValue message = InspectionGadgetsLocalize.stringReplaceableByStringBufferInLoopOption();
        return new SingleCheckboxOptionsPanel(message.get(), this, "onlyWarnOnLoop");
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new StringReplaceableByStringBufferVisitor();
    }

    private class StringReplaceableByStringBufferVisitor extends BaseInspectionVisitor {
        @Override
        public void visitLocalVariable(@Nonnull PsiLocalVariable variable) {
            super.visitLocalVariable(variable);
            final PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
            if (codeBlock == null) {
                return;
            }
            final PsiType type = variable.getType();
            if (!TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_STRING, type)) {
                return;
            }
            if (!variableIsAppendedTo(variable, codeBlock)) {
                return;
            }
            registerVariableError(variable);
        }

        public boolean variableIsAppendedTo(PsiVariable variable, PsiElement context) {
            final StringVariableIsAppendedToVisitor visitor = new StringVariableIsAppendedToVisitor(variable, onlyWarnOnLoop);
            context.accept(visitor);
            return visitor.isAppendedTo();
        }
    }
}