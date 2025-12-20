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
package com.intellij.java.impl.ig.classlayout;

import com.intellij.java.impl.ig.fixes.MoveClassFix;
import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiTypeParameter;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.Pattern;

import javax.swing.*;

@ExtensionImpl
public class InnerClassOnInterfaceInspection extends BaseInspection {
    /**
     * @noinspection PublicField
     */
    public boolean m_ignoreInnerInterfaces = false;

    @Nonnull
    @Override
    @Pattern(VALID_ID_PATTERN)
    public String getID() {
        return "InnerClassOfInterface";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.innerClassOnInterfaceDisplayName();
    }

    public JComponent createOptionsPanel() {
        LocalizeValue message = InspectionGadgetsLocalize.innerClassOnInterfaceIgnoreOption();
        return new SingleCheckboxOptionsPanel(message.get(), this, "m_ignoreInnerInterfaces");
    }

    @Nonnull
    public String buildErrorString(Object... infos) {
        PsiClass parentInterface = (PsiClass) infos[0];
        String interfaceName = parentInterface.getName();
        return InspectionGadgetsLocalize.innerClassOnInterfaceProblemDescriptor(interfaceName).get();
    }

    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new MoveClassFix();
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new InnerClassOnInterfaceVisitor();
    }

    private class InnerClassOnInterfaceVisitor extends BaseInspectionVisitor {

        @Override
        public void visitClass(@Nonnull PsiClass aClass) {
            // no call to super, so that it doesn't drill down to inner classes
            if (!aClass.isInterface() || aClass.isAnnotationType()) {
                return;
            }
            PsiClass[] innerClasses = aClass.getInnerClasses();
            for (PsiClass innerClass : innerClasses) {
                if (isInnerClass(innerClass)) {
                    registerClassError(innerClass, aClass);
                }
            }
        }

        private boolean isInnerClass(PsiClass innerClass) {
            if (innerClass.isEnum()) {
                return false;
            }
            if (innerClass.isAnnotationType()) {
                return false;
            }
            if (innerClass instanceof PsiTypeParameter ||
                innerClass instanceof PsiAnonymousClass) {
                return false;
            }
            return !(innerClass.isInterface() && m_ignoreInnerInterfaces);
        }
    }
}