/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.naming;

import com.intellij.java.impl.ig.fixes.RenameParameterFix;
import com.intellij.java.impl.ig.psiutils.LibraryUtil;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiParameterList;
import com.intellij.java.language.psi.search.searches.SuperMethodsSearch;
import com.intellij.java.language.psi.util.MethodSignatureBackedByPsiMethod;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.query.Query;
import consulo.deadCodeNotWorking.impl.MultipleCheckboxOptionsPanel;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;

@ExtensionImpl
public class ParameterNameDiffersFromOverriddenParameterInspection extends BaseInspection {
    /**
     * @noinspection PublicField
     */
    public boolean m_ignoreSingleCharacterNames = false;

    /**
     * @noinspection PublicField
     */
    public boolean m_ignoreOverridesOfLibraryMethods = false;

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.parameterNameDiffersFromOverriddenParameterDisplayName();
    }

    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.parameterNameDiffersFromOverriddenParameterProblemDescriptor(infos[0]).get();
    }

    public JComponent createOptionsPanel() {
        MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
        optionsPanel.addCheckbox(
            InspectionGadgetsLocalize.parameterNameDiffersFromOverriddenParameterIgnoreCharacterOption().get(),
            "m_ignoreSingleCharacterNames"
        );
        optionsPanel.addCheckbox(
            InspectionGadgetsLocalize.parameterNameDiffersFromOverriddenParameterIgnoreLibraryOption().get(),
            "m_ignoreOverridesOfLibraryMethods"
        );
        return optionsPanel;
    }

    @Nullable
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new RenameParameterFix((String) infos[0]);
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ParameterNameDiffersFromOverriddenParameterVisitor();
    }

    private class ParameterNameDiffersFromOverriddenParameterVisitor extends BaseInspectionVisitor {
        @Override
        public void visitMethod(@Nonnull PsiMethod method) {
            PsiParameterList parameterList = method.getParameterList();
            if (parameterList.getParametersCount() == 0) {
                return;
            }
            Query<MethodSignatureBackedByPsiMethod> query =
                SuperMethodsSearch.search(method, method.getContainingClass(), true, false);
            MethodSignatureBackedByPsiMethod methodSignature = query.findFirst();
            if (methodSignature == null) {
                return;
            }
            PsiMethod superMethod = methodSignature.getMethod();
            PsiParameter[] parameters = parameterList.getParameters();
            checkParameters(superMethod, parameters);
        }

        private void checkParameters(PsiMethod superMethod, PsiParameter[] parameters) {
            if (m_ignoreOverridesOfLibraryMethods) {
                PsiClass containingClass = superMethod.getContainingClass();
                if (containingClass != null && LibraryUtil.classIsInLibrary(containingClass)) {
                    return;
                }
            }
            PsiParameterList superParameterList = superMethod.getParameterList();
            PsiParameter[] superParameters = superParameterList.getParameters();
            for (int i = 0; i < parameters.length; i++) {
                PsiParameter parameter = parameters[i];
                String parameterName = parameter.getName();
                String superParameterName = superParameters[i].getName();
                if (superParameterName == null) {
                    continue;
                }
                if (superParameterName.equals(parameterName)) {
                    continue;
                }
                if (m_ignoreSingleCharacterNames && superParameterName.length() == 1) {
                    continue;
                }
                registerVariableError(parameter, superParameterName);
            }
        }
    }
}