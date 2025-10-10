/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.abstraction;

import com.intellij.java.language.psi.PsiCatchSection;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiTypeElement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.Pattern;

import javax.swing.*;

@ExtensionImpl
public class ParameterOfConcreteClassInspection extends BaseInspection {
    @SuppressWarnings("PublicField")
    public boolean ignoreAbstractClasses = false;

    @Nonnull
    @Override
    @Pattern("[a-zA-Z_0-9.]+")
    public String getID() {
        return "MethodParameterOfConcreteClass";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.concreteClassMethodParameterDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.concreteClassMethodParameterProblemDescriptor(infos).get();
    }

    @Override
    public JComponent createOptionsPanel() {
        LocalizeValue message = InspectionGadgetsLocalize.parameterOfConcreteClassOption();
        return new SingleCheckboxOptionsPanel(message.get(), this, "ignoreAbstractClasses");
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ParameterOfConcreteClassVisitor();
    }

    private class ParameterOfConcreteClassVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitParameter(@Nonnull PsiParameter parameter) {
            super.visitParameter(parameter);

            if (parameter.getDeclarationScope() instanceof PsiCatchSection) {
                return;
            }
            final PsiTypeElement typeElement = parameter.getTypeElement();
            if (typeElement == null) {
                return;
            }
            if (!ConcreteClassUtil.typeIsConcreteClass(
                typeElement,
                ignoreAbstractClasses
            )) {
                return;
            }
            final String variableName = parameter.getName();
            registerError(typeElement, variableName);
        }
    }
}