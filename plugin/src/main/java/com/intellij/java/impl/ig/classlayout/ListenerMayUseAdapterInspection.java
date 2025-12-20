/*
 * Copyright 2009 Bas Leijdekkers
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

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

import javax.swing.*;

@ExtensionImpl
public class ListenerMayUseAdapterInspection extends BaseInspection {
    public boolean checkForEmptyMethods = true;

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.listenerMayUseAdapterDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        PsiClass aClass = (PsiClass) infos[0];
        String className = aClass.getName();
        PsiClass adapterClass = (PsiClass) infos[1];
        String adapterName = adapterClass.getName();
        return InspectionGadgetsLocalize.listenerMayUseAdapterProblemDescriptor(className, adapterName).get();
    }

    @Override
    public JComponent createOptionsPanel() {
        LocalizeValue message = InspectionGadgetsLocalize.listenerMayUseAdapterEmtpyMethodsOption();
        return new SingleCheckboxOptionsPanel(message.get(), this, "checkForEmptyMethods");
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        PsiClass adapterClass = (PsiClass) infos[1];
        return new ListenerMayUseAdapterFix(adapterClass);
    }

    private static class ListenerMayUseAdapterFix extends InspectionGadgetsFix {
        private final PsiClass adapterClass;

        ListenerMayUseAdapterFix(@Nonnull PsiClass adapterClass) {
            this.adapterClass = adapterClass;
        }

        @Nonnull
        @RequiredReadAction
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.listenerMayUseAdapterQuickfix(adapterClass.getName());
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor)
            throws IncorrectOperationException {
            PsiJavaCodeReferenceElement element = (PsiJavaCodeReferenceElement) descriptor.getPsiElement();
            PsiClass aClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
            if (aClass == null) {
                return;
            }
            PsiReferenceList extendsList = aClass.getExtendsList();
            if (extendsList == null) {
                return;
            }
            PsiMethod[] methods = aClass.getMethods();
            if (methods.length > 0) {
                PsiElement target = element.resolve();
                if (!(target instanceof PsiClass)) {
                    return;
                }
                PsiClass interfaceClass = (PsiClass) target;
                for (PsiMethod method : methods) {
                    PsiCodeBlock body = method.getBody();
                    if (body == null) {
                        continue;
                    }
                    PsiStatement[] statements = body.getStatements();
                    if (statements.length != 0) {
                        continue;
                    }
                    PsiMethod[] superMethods = method.findSuperMethods(
                        interfaceClass);
                    if (superMethods.length > 0) {
                        method.delete();
                    }
                }
            }
            element.delete();
            JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
            PsiElementFactory elementFactory =
                psiFacade.getElementFactory();
            PsiJavaCodeReferenceElement referenceElement =
                elementFactory.createClassReferenceElement(adapterClass);
            extendsList.add(referenceElement);
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ListenerMayUseAdapterVisitor();
    }

    private class ListenerMayUseAdapterVisitor extends BaseInspectionVisitor {

        @Override
        public void visitClass(PsiClass aClass) {
            PsiReferenceList extendsList = aClass.getExtendsList();
            if (extendsList == null) {
                return;
            }
            PsiJavaCodeReferenceElement[] extendsReferences =
                extendsList.getReferenceElements();
            if (extendsReferences.length > 0) {
                return;
            }
            PsiReferenceList implementsList = aClass.getImplementsList();
            if (implementsList == null) {
                return;
            }
            PsiJavaCodeReferenceElement[] implementsReferences =
                implementsList.getReferenceElements();
            for (PsiJavaCodeReferenceElement implementsReference :
                implementsReferences) {
                checkReference(aClass, implementsReference);
            }
        }

        private void checkReference(
            @Nonnull PsiClass aClass,
            @Nonnull PsiJavaCodeReferenceElement implementsReference
        ) {
            PsiElement target = implementsReference.resolve();
            if (!(target instanceof PsiClass)) {
                return;
            }
            PsiClass implementsClass = (PsiClass) target;
            String className = implementsClass.getQualifiedName();
            if (className == null || !className.endsWith("Listener")) {
                return;
            }
            String adapterName = className.substring(
                0,
                className.length() - 8
            ) + "Adapter";
            JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(
                aClass.getProject());
            GlobalSearchScope scope =
                implementsClass.getResolveScope();
            PsiClass adapterClass = psiFacade.findClass(
                adapterName,
                scope
            );
            if (adapterClass == null) {
                return;
            }
            if (aClass.equals(adapterClass)) {
                return;
            }
            if (!adapterClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                return;
            }
            PsiReferenceList implementsList =
                adapterClass.getImplementsList();
            if (implementsList == null) {
                return;
            }
            PsiJavaCodeReferenceElement[] referenceElements =
                implementsList.getReferenceElements();
            boolean adapterImplementsListener = false;
            for (PsiJavaCodeReferenceElement referenceElement :
                referenceElements) {
                PsiElement implementsTarget = referenceElement.resolve();
                if (!implementsClass.equals(implementsTarget)) {
                    continue;
                }
                adapterImplementsListener = true;
            }
            if (!adapterImplementsListener) {
                return;
            }
            if (checkForEmptyMethods) {
                boolean emptyMethodFound = false;
                PsiMethod[] methods = aClass.getMethods();
                for (PsiMethod method : methods) {
                    PsiCodeBlock body = method.getBody();
                    if (body == null) {
                        continue;
                    }
                    PsiStatement[] statements = body.getStatements();
                    if (statements.length != 0) {
                        continue;
                    }
                    PsiMethod[] superMethods =
                        method.findSuperMethods(implementsClass);
                    if (superMethods.length == 0) {
                        continue;
                    }
                    emptyMethodFound = true;
                    break;
                }
                if (!emptyMethodFound) {
                    return;
                }
            }
            registerError(implementsReference, aClass, adapterClass);
        }
    }
}
