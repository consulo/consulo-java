/*
 * Copyright 2011-2013 Bas Leijdekkers
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
package com.intellij.java.impl.ig.migration;

import com.intellij.java.impl.ig.psiutils.LibraryUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.search.searches.SuperMethodsSearch;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.MultipleCheckboxOptionsPanel;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Nls;

import javax.swing.*;

@ExtensionImpl
public class MethodCanBeVariableArityMethodInspection extends BaseInspection {

    @SuppressWarnings({"PublicField"})
    public boolean ignoreByteAndShortArrayParameters = false;

    @SuppressWarnings("PublicField")
    public boolean ignoreOverridingMethods = false;

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.methodCanBeVariableArityMethodDisplayName();
    }

    @Nonnull
    @Override
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.methodCanBeVariableArityMethodProblemDescriptor().get();
    }

    @Override
    public JComponent createOptionsPanel() {
        MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
        panel.addCheckbox(
            InspectionGadgetsLocalize.methodCanBeVariableArityMethodIgnoreByteShortOption().get(),
            "ignoreByteAndShortArrayParameters"
        );
        panel.addCheckbox(
            InspectionGadgetsLocalize.methodCanBeVariableArityMethodIgnoreOverridingMethods().get(),
            "ignoreOverridingMethods"
        );
        return panel;
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new MethodCanBeVariableArityMethodFix();
    }

    private static class MethodCanBeVariableArityMethodFix extends InspectionGadgetsFix {

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.convertToVariableArityMethodQuickfix();
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            PsiElement parent = element.getParent();
            if (!(parent instanceof PsiMethod)) {
                return;
            }
            PsiMethod method = (PsiMethod) parent;
            PsiParameterList parameterList = method.getParameterList();
            if (parameterList.getParametersCount() == 0) {
                return;
            }
            PsiParameter[] parameters = parameterList.getParameters();
            PsiParameter lastParameter = parameters[parameters.length - 1];
            PsiType type = lastParameter.getType();
            if (!(type instanceof PsiArrayType)) {
                return;
            }
            PsiArrayType arrayType = (PsiArrayType) type;
            PsiType componentType = arrayType.getComponentType();
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            PsiTypeElement newTypeElement = factory.createTypeElementFromText(componentType.getCanonicalText() + "...", method);
            PsiTypeElement typeElement = lastParameter.getTypeElement();
            if (typeElement != null) {
                typeElement.replace(newTypeElement);
            }
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new MethodCanBeVariableArityMethodVisitor();
    }

    private class MethodCanBeVariableArityMethodVisitor extends BaseInspectionVisitor {

        @Override
        public void visitMethod(PsiMethod method) {
            if (!PsiUtil.isLanguageLevel5OrHigher(method)) {
                return;
            }
            super.visitMethod(method);
            PsiParameterList parameterList = method.getParameterList();
            if (parameterList.getParametersCount() == 0) {
                return;
            }
            PsiParameter[] parameters = parameterList.getParameters();
            PsiParameter lastParameter = parameters[parameters.length - 1];
            PsiType type = lastParameter.getType();
            if (!(type instanceof PsiArrayType)) {
                return;
            }
            if (type instanceof PsiEllipsisType) {
                return;
            }
            PsiArrayType arrayType = (PsiArrayType) type;
            PsiType componentType = arrayType.getComponentType();
            if (componentType instanceof PsiArrayType) {
                // don't report when it is multidimensional array
                return;
            }
            if (ignoreByteAndShortArrayParameters) {
                if (PsiType.BYTE.equals(componentType) || PsiType.SHORT.equals(componentType)) {
                    return;
                }
            }
            if (LibraryUtil.isOverrideOfLibraryMethod(method)) {
                return;
            }
            if (ignoreOverridingMethods && SuperMethodsSearch.search(method, null, true, false).findFirst() != null) {
                return;
            }
            registerMethodError(method);
        }
    }
}
