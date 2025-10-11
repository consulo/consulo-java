/*
 * Copyright 2006-2007 Bas Leijdekkers
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
package com.intellij.java.impl.ig.inheritance;

import com.intellij.java.impl.ig.psiutils.InheritanceUtil;
import com.intellij.java.language.psi.PsiClass;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;

@ExtensionImpl
public class InterfaceNeverImplementedInspection extends BaseInspection {
    /**
     * @noinspection PublicField
     */
    public boolean ignoreInterfacesThatOnlyDeclareConstants = false;

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.interfaceNeverImplementedDisplayName();
    }

    @Nullable
    public JComponent createOptionsPanel() {
        LocalizeValue message = InspectionGadgetsLocalize.interfaceNeverImplementedOption();
        return new SingleCheckboxOptionsPanel(message.get(), this, "ignoreInterfacesThatOnlyDeclareConstants");
    }

    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.interfaceNeverImplementedProblemDescriptor().get();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new InterfaceNeverImplementedVisitor();
    }

    private class InterfaceNeverImplementedVisitor extends BaseInspectionVisitor {
        @Override
        public void visitClass(@Nonnull PsiClass aClass) {
            if (!aClass.isInterface() || aClass.isAnnotationType()) {
                return;
            }
            if (ignoreInterfacesThatOnlyDeclareConstants &&
                aClass.getMethods().length == 0) {
                if (aClass.getFields().length != 0) {
                    return;
                }
            }
            if (InheritanceUtil.hasImplementation(aClass)) {
                return;
            }
            registerClassError(aClass);
        }
    }
}