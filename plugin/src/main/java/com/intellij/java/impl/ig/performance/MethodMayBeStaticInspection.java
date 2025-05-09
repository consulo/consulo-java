/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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

import com.intellij.java.impl.ig.fixes.ChangeModifierFix;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.query.Query;
import consulo.deadCodeNotWorking.impl.MultipleCheckboxOptionsPanel;
import consulo.java.analysis.codeInspection.CantBeStaticCondition;
import consulo.java.analysis.codeInspection.JavaExtensionPoints;
import consulo.language.psi.PsiElement;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.util.function.Predicate;

@ExtensionImpl
public class MethodMayBeStaticInspection extends BaseInspection {
    /**
     * @noinspection PublicField
     */
    public boolean m_onlyPrivateOrFinal = false;
    /**
     * @noinspection PublicField
     */
    public boolean m_ignoreEmptyMethods = true;

    @Override
    @Nonnull
    public String getDisplayName() {
        return InspectionGadgetsLocalize.methodMayBeStaticDisplayName().get();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.methodMayBeStaticProblemDescriptor().get();
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new ChangeModifierFix(PsiModifier.STATIC);
    }

    @Override
    public JComponent createOptionsPanel() {
        MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
        optionsPanel.addCheckbox(InspectionGadgetsLocalize.methodMayBeStaticOnlyOption().get(), "m_onlyPrivateOrFinal");
        optionsPanel.addCheckbox(InspectionGadgetsLocalize.methodMayBeStaticEmptyOption().get(), "m_ignoreEmptyMethods");
        return optionsPanel;
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new MethodCanBeStaticVisitor();
    }

    private class MethodCanBeStaticVisitor extends BaseInspectionVisitor {
        @Override
        public void visitMethod(@Nonnull PsiMethod method) {
            super.visitMethod(method);
            if (method.isStatic() ||
                method.isAbstract() ||
                method.hasModifierProperty(PsiModifier.SYNCHRONIZED) ||
                method.hasModifierProperty(PsiModifier.NATIVE)) {
                return;
            }
            if (method.isConstructor() || method.getNameIdentifier() == null) {
                return;
            }
            if (m_ignoreEmptyMethods && MethodUtils.isEmpty(method)) {
                return;
            }
            PsiClass containingClass = ClassUtils.getContainingClass(method);
            if (containingClass == null) {
                return;
            }
            for (CantBeStaticCondition addin : JavaExtensionPoints.CANT_BE_STATIC_EP_NAME.getExtensions()) {
                if (addin.cantBeStatic(method)) {
                    return;
                }
            }
            PsiElement scope = containingClass.getScope();
            if (!(scope instanceof PsiJavaFile) && !containingClass.isStatic()) {
                return;
            }
            if (m_onlyPrivateOrFinal && !method.isFinal() && !method.isPrivate()) {
                return;
            }
            if (isExcluded(method) || MethodUtils.hasSuper(method) || MethodUtils.isOverridden(method)) {
                return;
            }
            if (implementsSurprisingInterface(method)) {
                return;
            }
            MethodReferenceVisitor visitor = new MethodReferenceVisitor(method);
            method.accept(visitor);
            if (!visitor.areReferencesStaticallyAccessible()) {
                return;
            }
            registerMethodError(method);
        }

        private boolean implementsSurprisingInterface(PsiMethod method) {
            PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return false;
            }
            Query<PsiClass> search = ClassInheritorsSearch.search(containingClass, method.getUseScope(), true, true, false);
            SimpleReference<Boolean> result = SimpleReference.create(false);
            search.forEach(new Predicate<>() {
                int count = 0;

                @Override
                @RequiredReadAction
                public boolean test(PsiClass subClass) {
                    if (++count > 5) {
                        result.set(true);
                        return false;
                    }
                    PsiReferenceList list = subClass.getImplementsList();
                    if (list == null) {
                        return true;
                    }
                    PsiJavaCodeReferenceElement[] referenceElements = list.getReferenceElements();
                    for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
                        if (!(referenceElement.resolve() instanceof PsiClass aClass)
                            || !aClass.isInterface()
                            || aClass.findMethodBySignature(method, true) != null) {
                            result.set(true);
                            return false;
                        }
                    }
                    return true;
                }
            });
            return result.get();
        }

        private boolean isExcluded(PsiMethod method) {
            String name = method.getName();
            if ("writeObject".equals(name)) {
                if (!method.isPrivate()) {
                    return false;
                }
                if (!MethodUtils.hasInThrows(method, CommonClassNames.JAVA_IO_IO_EXCEPTION)) {
                    return false;
                }
                PsiType returnType = method.getReturnType();
                if (!PsiType.VOID.equals(returnType)) {
                    return false;
                }
                PsiParameterList parameterList = method.getParameterList();
                if (parameterList.getParametersCount() != 1) {
                    return false;
                }
                PsiParameter parameter = parameterList.getParameters()[0];
                PsiType type = parameter.getType();
                return type.equalsToText("java.io.ObjectOutputStream");
            }
            if ("readObject".equals(name)) {
                if (!method.isPrivate()) {
                    return false;
                }
                if (!MethodUtils.hasInThrows(method, CommonClassNames.JAVA_IO_IO_EXCEPTION, "java.lang.ClassNotFoundException")) {
                    return false;
                }
                PsiType returnType = method.getReturnType();
                if (!PsiType.VOID.equals(returnType)) {
                    return false;
                }
                PsiParameterList parameterList = method.getParameterList();
                if (parameterList.getParametersCount() != 1) {
                    return false;
                }
                PsiParameter parameter = parameterList.getParameters()[0];
                PsiType type = parameter.getType();
                return type.equalsToText("java.io.ObjectInputStream");
            }
            if ("writeReplace".equals(name) || "readResolve".equals(name)) {
                if (!MethodUtils.hasInThrows(method, "java.io.ObjectStreamException")) {
                    return false;
                }
                PsiType returnType = method.getReturnType();
                if (returnType == null || !returnType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
                    return false;
                }
                PsiParameterList parameterList = method.getParameterList();
                return parameterList.getParametersCount() == 0;
            }
            return false;
        }
    }
}
