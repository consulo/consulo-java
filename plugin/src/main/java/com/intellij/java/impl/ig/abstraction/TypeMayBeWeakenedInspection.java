/*
 * Copyright 2006-2013 Bas Leijdekkers
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

import com.intellij.java.impl.ig.psiutils.WeakestTypeFinder;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.query.Query;
import consulo.deadCodeNotWorking.impl.MultipleCheckboxOptionsPanel;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.util.*;

@ExtensionImpl
public class TypeMayBeWeakenedInspection extends BaseInspection {
    @SuppressWarnings({"PublicField"})
    public boolean useRighthandTypeAsWeakestTypeInAssignments = true;

    @SuppressWarnings({"PublicField"})
    public boolean useParameterizedTypeForCollectionMethods = true;

    @SuppressWarnings({"PublicField"})
    public boolean doNotWeakenToJavaLangObject = true;

    @SuppressWarnings({"PublicField"})
    public boolean onlyWeakenToInterface = true;

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.typeMayBeWeakenedDisplayName();
    }

    @Nonnull
    @Override
    protected String buildErrorString(Object... infos) {
        Iterable<PsiClass> weakerClasses = (Iterable<PsiClass>) infos[1];
        StringBuilder builder = new StringBuilder();
        Iterator<PsiClass> iterator = weakerClasses.iterator();
        if (iterator.hasNext()) {
            builder.append('\'').append(iterator.next().getQualifiedName()).append('\'');
            while (iterator.hasNext()) {
                builder.append(", '").append(iterator.next().getQualifiedName()).append('\'');
            }
        }
        Object info = infos[0];
        if (info instanceof PsiField) {
            return InspectionGadgetsLocalize.typeMayBeWeakenedFieldProblemDescriptor(builder.toString()).get();
        }
        else if (info instanceof PsiParameter) {
            return InspectionGadgetsLocalize.typeMayBeWeakenedParameterProblemDescriptor(builder.toString()).get();
        }
        else if (info instanceof PsiMethod) {
            return InspectionGadgetsLocalize.typeMayBeWeakenedMethodProblemDescriptor(builder.toString()).get();
        }
        return InspectionGadgetsLocalize.typeMayBeWeakenedProblemDescriptor(builder.toString()).get();
    }

    @Override
    @Nullable
    public JComponent createOptionsPanel() {
        MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
        optionsPanel.addCheckbox(
            InspectionGadgetsLocalize.typeMayBeWeakenedIgnoreOption().get(),
            "useRighthandTypeAsWeakestTypeInAssignments"
        );
        optionsPanel.addCheckbox(
            InspectionGadgetsLocalize.typeMayBeWeakenedCollectionMethodOption().get(),
            "useParameterizedTypeForCollectionMethods"
        );
        optionsPanel.addCheckbox(
            InspectionGadgetsLocalize.typeMayBeWeakenedDoNotWeakenToObjectOption().get(),
            "doNotWeakenToJavaLangObject"
        );
        optionsPanel.addCheckbox(
            InspectionGadgetsLocalize.onlyWeakenToAnInterface().get(),
            "onlyWeakentoInterface"
        );
        return optionsPanel;
    }

    @Nonnull
    @Override
    protected InspectionGadgetsFix[] buildFixes(Object... infos) {
        Iterable<PsiClass> weakerClasses = (Iterable<PsiClass>) infos[1];
        Collection<InspectionGadgetsFix> fixes = new ArrayList<>();
        for (PsiClass weakestClass : weakerClasses) {
            String qualifiedName = weakestClass.getQualifiedName();
            if (qualifiedName == null) {
                continue;
            }
            fixes.add(new TypeMayBeWeakenedFix(qualifiedName));
        }
        return fixes.toArray(new InspectionGadgetsFix[fixes.size()]);
    }

    private static class TypeMayBeWeakenedFix extends InspectionGadgetsFix {
        private final String fqClassName;

        TypeMayBeWeakenedFix(@Nonnull String fqClassName) {
            this.fqClassName = fqClassName;
        }

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.typeMayBeWeakenedQuickfix(fqClassName);
        }

        @Override
        @RequiredWriteAction
        protected void doFix(Project project, ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            PsiElement parent = element.getParent();
            PsiTypeElement typeElement;
            if (parent instanceof PsiVariable variable) {
                typeElement = variable.getTypeElement();
            }
            else if (parent instanceof PsiMethod method) {
                typeElement = method.getReturnTypeElement();
            }
            else {
                return;
            }
            if (typeElement == null) {
                return;
            }
            PsiJavaCodeReferenceElement componentReferenceElement = typeElement.getInnermostComponentReferenceElement();
            if (componentReferenceElement == null) {
                return;
            }
            PsiType oldType = typeElement.getType();
            if (!(oldType instanceof PsiClassType classType)) {
                return;
            }
            PsiType[] parameterTypes = classType.getParameters();
            GlobalSearchScope scope = element.getResolveScope();
            JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
            PsiClass aClass = facade.findClass(fqClassName, scope);
            if (aClass == null) {
                return;
            }
            PsiTypeParameter[] typeParameters = aClass.getTypeParameters();
            PsiElementFactory factory = facade.getElementFactory();
            PsiClassType type;
            if (typeParameters.length != 0 && typeParameters.length == parameterTypes.length) {
                Map<PsiTypeParameter, PsiType> typeParameterMap = new HashMap<>();
                for (int i = 0; i < typeParameters.length; i++) {
                    PsiTypeParameter typeParameter = typeParameters[i];
                    PsiType parameterType = parameterTypes[i];
                    typeParameterMap.put(typeParameter, parameterType);
                }
                PsiSubstitutor substitutor = factory.createSubstitutor(typeParameterMap);
                type = factory.createType(aClass, substitutor);
            }
            else {
                type = factory.createTypeByFQClassName(fqClassName, scope);
            }
            PsiJavaCodeReferenceElement referenceElement = factory.createReferenceElementByType(type);
            componentReferenceElement.replace(referenceElement);
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new TypeMayBeWeakenedVisitor();
    }

    private class TypeMayBeWeakenedVisitor extends BaseInspectionVisitor {

        @Override
        public void visitVariable(@Nonnull PsiVariable variable) {
            super.visitVariable(variable);
            if (variable instanceof PsiParameter parameter) {
                PsiElement declarationScope = parameter.getDeclarationScope();
                if (declarationScope instanceof PsiCatchSection) {
                    // do not weaken catch block parameters
                    return;
                }
                else if (declarationScope instanceof PsiMethod method) {
                    PsiClass containingClass = method.getContainingClass();
                    if (containingClass == null || containingClass.isInterface()) {
                        return;
                    }
                    if (MethodUtils.hasSuper(method)) {
                        // do not try to weaken parameters of methods with
                        // super methods
                        return;
                    }
                    Query<PsiMethod> overridingSearch = OverridingMethodsSearch.search(method);
                    if (overridingSearch.findFirst() != null) {
                        // do not try to weaken parameters of methods with
                        // overriding methods.
                        return;
                    }
                }
            }
            if (isOnTheFly() && variable instanceof PsiField) {
                // checking variables with greater visibility is too expensive
                // for error checking in the editor
                if (!variable.hasModifierProperty(PsiModifier.PRIVATE)) {
                    return;
                }
            }
            if (useRighthandTypeAsWeakestTypeInAssignments) {
                if (variable instanceof PsiParameter) {
                    if (variable.getParent() instanceof PsiForeachStatement foreachStatement) {
                        PsiExpression iteratedValue = foreachStatement.getIteratedValue();
                        if (!(iteratedValue instanceof PsiNewExpression) && !(iteratedValue instanceof PsiTypeCastExpression)) {
                            return;
                        }
                    }
                }
                else {
                    PsiExpression initializer = variable.getInitializer();
                    if (!(initializer instanceof PsiNewExpression) && !(initializer instanceof PsiTypeCastExpression)) {
                        return;
                    }
                }
            }
            Collection<PsiClass> weakestClasses = WeakestTypeFinder.calculateWeakestClassesNecessary(
                variable,
                useRighthandTypeAsWeakestTypeInAssignments,
                useParameterizedTypeForCollectionMethods
            );
            if (doNotWeakenToJavaLangObject) {
                Project project = variable.getProject();
                JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
                PsiClass javaLangObjectClass = facade.findClass(CommonClassNames.JAVA_LANG_OBJECT, variable.getResolveScope());
                weakestClasses.remove(javaLangObjectClass);
            }
            if (onlyWeakenToInterface) {
                for (Iterator<PsiClass> iterator = weakestClasses.iterator(); iterator.hasNext(); ) {
                    PsiClass weakestClass = iterator.next();
                    if (!weakestClass.isInterface()) {
                        iterator.remove();
                    }
                }
            }
            if (weakestClasses.isEmpty()) {
                return;
            }
            registerVariableError(variable, variable, weakestClasses);
        }

        @Override
        public void visitMethod(@Nonnull PsiMethod method) {
            super.visitMethod(method);
            if (isOnTheFly() && !method.isPrivate()) {
                // checking methods with greater visibility is too expensive.
                // for error checking in the editor
                return;
            }
            if (MethodUtils.hasSuper(method)) {
                // do not try to weaken methods with super methods
                return;
            }
            Query<PsiMethod> overridingSearch = OverridingMethodsSearch.search(method);
            if (overridingSearch.findFirst() != null) {
                // do not try to weaken methods with overriding methods.
                return;
            }
            Collection<PsiClass> weakestClasses = WeakestTypeFinder.calculateWeakestClassesNecessary(
                method,
                useRighthandTypeAsWeakestTypeInAssignments,
                useParameterizedTypeForCollectionMethods
            );
            if (doNotWeakenToJavaLangObject) {
                Project project = method.getProject();
                JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
                PsiClass javaLangObjectClass = facade.findClass(CommonClassNames.JAVA_LANG_OBJECT, method.getResolveScope());
                weakestClasses.remove(javaLangObjectClass);
            }
            if (onlyWeakenToInterface) {
                for (Iterator<PsiClass> iterator = weakestClasses.iterator(); iterator.hasNext(); ) {
                    PsiClass weakestClass = iterator.next();
                    if (!weakestClass.isInterface()) {
                        iterator.remove();
                    }
                }
            }
            if (weakestClasses.isEmpty()) {
                return;
            }
            registerMethodError(method, method, weakestClasses);
        }
    }
}