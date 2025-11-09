/*
 * Copyright 2003-2016 Dave Griffith, Bas Leijdekkers
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

import com.intellij.java.impl.ig.psiutils.LibraryUtil;
import com.intellij.java.impl.ig.psiutils.WeakestTypeFinder;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.CollectionUtils;
import com.siyeh.ig.psiutils.DeclarationSearchUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.MultipleCheckboxOptionsPanel;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@ExtensionImpl
public class DeclareCollectionAsInterfaceInspection extends BaseInspection {
    /**
     * @noinspection PublicField
     */
    public boolean ignoreLocalVariables = false;
    /**
     * @noinspection PublicField
     */
    public boolean ignorePrivateMethodsAndFields = false;

    @Nonnull
    @Override
    public String getID() {
        return "CollectionDeclaredAsConcreteClass";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.collectionDeclaredByClassDisplayName();
    }

    @Nonnull
    @Override
    public String buildErrorString(Object... infos) {
        String type = (String) infos[0];
        return InspectionGadgetsLocalize.collectionDeclaredByClassProblemDescriptor(type).get();
    }

    @Override
    @Nullable
    public JComponent createOptionsPanel() {
        MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
        optionsPanel.addCheckbox(
            InspectionGadgetsLocalize.collectionDeclaredByClassIgnoreLocalsOption().get(),
            "ignoreLocalVariables"
        );
        optionsPanel.addCheckbox(
            InspectionGadgetsLocalize.collectionDeclaredByClassIgnorePrivateMembersOption().get(),
            "ignorePrivateMethodsAndFields"
        );
        return optionsPanel;
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new DeclareCollectionAsInterfaceFix((String) infos[0]);
    }

    private static class DeclareCollectionAsInterfaceFix extends InspectionGadgetsFix {

        private final String typeString;

        DeclareCollectionAsInterfaceFix(String typeString) {
            this.typeString = typeString;
        }

        @Override
        @Nonnull
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.declareCollectionAsInterfaceQuickfix(typeString);
        }

        @Override
        @RequiredWriteAction
        protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiElement element = descriptor.getPsiElement();
            PsiElement parent = element.getParent();
            if (!(parent instanceof PsiJavaCodeReferenceElement referenceElement)) {
                return;
            }
            StringBuilder newElementText = new StringBuilder(typeString);
            PsiReferenceParameterList parameterList = referenceElement.getParameterList();
            if (parameterList != null) {
                newElementText.append(parameterList.getText());
            }
            PsiElement grandParent = parent.getParent();
            if (!(grandParent instanceof PsiTypeElement)) {
                return;
            }
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            PsiTypeElement newTypeElement = factory.createTypeElementFromText(newElementText.toString(), element);
            JavaCodeStyleManager.getInstance(project).shortenClassReferences(grandParent.replace(newTypeElement));
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new DeclareCollectionAsInterfaceVisitor();
    }

    private class DeclareCollectionAsInterfaceVisitor extends BaseInspectionVisitor {

        @Override
        public void visitVariable(@Nonnull PsiVariable variable) {
            if (isOnTheFly() && DeclarationSearchUtils.isTooExpensiveToSearch(variable, false)) {
                return;
            }
            if (ignoreLocalVariables && variable instanceof PsiLocalVariable) {
                return;
            }
            if (ignorePrivateMethodsAndFields) {
                if (variable instanceof PsiField) {
                    if (variable.hasModifierProperty(PsiModifier.PRIVATE)) {
                        return;
                    }
                }
            }
            if (variable instanceof PsiParameter parameter) {
                if (parameter.getDeclarationScope() instanceof PsiMethod method) {
                    if (ignorePrivateMethodsAndFields && method.isPrivate()) {
                        return;
                    }
                }
                else if (ignoreLocalVariables) {
                    return;
                }
            }
            PsiType type = variable.getType();
            if (!CollectionUtils.isConcreteCollectionClass(type) || LibraryUtil.isOverrideOfLibraryMethodParameter(variable)) {
                return;
            }

            checkToWeaken(type, variable.getTypeElement(), variable);
        }

        @Override
        public void visitMethod(@Nonnull PsiMethod method) {
            super.visitMethod(method);
            if (ignorePrivateMethodsAndFields && method.isPrivate()) {
                return;
            }
            if (isOnTheFly() && DeclarationSearchUtils.isTooExpensiveToSearch(method, false)) {
                return;
            }
            PsiType type = method.getReturnType();
            if (!CollectionUtils.isConcreteCollectionClass(type) || LibraryUtil.isOverrideOfLibraryMethod(method)) {
                return;
            }

            checkToWeaken(type, method.getReturnTypeElement(), method);
        }

        private void checkToWeaken(PsiType type, PsiTypeElement typeElement, PsiElement variable) {
            if (typeElement == null) {
                return;
            }
            PsiJavaCodeReferenceElement reference = typeElement.getInnermostComponentReferenceElement();
            if (reference == null) {
                return;
            }
            PsiElement nameElement = reference.getReferenceNameElement();
            if (nameElement == null) {
                return;
            }
            Collection<PsiClass> weaklings = WeakestTypeFinder.calculateWeakestClassesNecessary(variable, false, true);
            if (weaklings.isEmpty()) {
                return;
            }
            PsiClassType javaLangObject = PsiType.getJavaLangObject(nameElement.getManager(), nameElement.getResolveScope());
            List<PsiClass> weaklingList = new ArrayList<>(weaklings);
            PsiClass objectClass = javaLangObject.resolve();
            weaklingList.remove(objectClass);
            if (weaklingList.isEmpty()) {
                String typeText = type.getCanonicalText();
                String interfaceText = CollectionUtils.getInterfaceForClass(typeText);
                if (interfaceText == null) {
                    return;
                }
                registerError(nameElement, interfaceText);
            }
            else {
                PsiClass weakling = weaklingList.get(0);
                String qualifiedName = weakling.getQualifiedName();
                registerError(nameElement, qualifiedName);
            }
        }
    }
}