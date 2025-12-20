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
package com.intellij.java.impl.ig.javabeans;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiParameterList;
import com.intellij.java.language.psi.PsiTypeParameter;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import javax.swing.*;

@ExtensionImpl
public class ClassWithoutNoArgConstructorInspection extends BaseInspection {
    /**
     * @noinspection PublicField
     */
    public boolean m_ignoreClassesWithNoConstructors = true;

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.classWithoutNoArgConstructorDisplayName();
    }

    public JComponent createOptionsPanel() {
        LocalizeValue message = InspectionGadgetsLocalize.classWithoutNoArgConstructorIgnoreOption();
        return new SingleCheckboxOptionsPanel(message.get(), this, "m_ignoreClassesWithNoConstructors");
    }

    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.classWithoutNoArgConstructorProblemDescriptor().get();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ClassWithoutNoArgConstructorVisitor();
    }

    private class ClassWithoutNoArgConstructorVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitClass(@Nonnull PsiClass aClass) {
            // no call to super, so it doesn't drill down
            if (aClass.isInterface() || aClass.isEnum() ||
                aClass.isAnnotationType()) {
                return;
            }
            if (aClass instanceof PsiTypeParameter) {
                return;
            }
            if (m_ignoreClassesWithNoConstructors &&
                !classHasConstructor(aClass)) {
                return;
            }
            if (classHasNoArgConstructor(aClass)) {
                return;
            }
            registerClassError(aClass);
        }

        private boolean classHasNoArgConstructor(PsiClass aClass) {
            PsiMethod[] constructors = aClass.getConstructors();
            for (PsiMethod constructor : constructors) {
                PsiParameterList parameterList =
                    constructor.getParameterList();
                if (parameterList.getParametersCount() == 0) {
                    return true;
                }
            }
            return false;
        }

        private boolean classHasConstructor(PsiClass aClass) {
            PsiMethod[] constructors = aClass.getConstructors();
            return constructors.length != 0;
        }
    }
}