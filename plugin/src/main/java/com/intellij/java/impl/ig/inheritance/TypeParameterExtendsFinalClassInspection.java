/*
 * Copyright 2006-2012 Bas Leijdekkers
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

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.query.Query;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
public class TypeParameterExtendsFinalClassInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.typeParameterExtendsFinalClassDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        Integer problemType = (Integer) infos[1];
        PsiNamedElement namedElement = (PsiNamedElement) infos[0];
        String name = namedElement.getName();
        return problemType == 1
            ? InspectionGadgetsLocalize.typeParameterExtendsFinalClassProblemDescriptor1(name).get()
            : InspectionGadgetsLocalize.typeParameterExtendsFinalClassProblemDescriptor2(name).get();
    }

    @Override
    @Nullable
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new TypeParameterExtendsFinalClassFix();
    }

    private static class TypeParameterExtendsFinalClassFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.typeParameterExtendsFinalClassQuickfix();
        }

        @Override
        protected void doFix(@Nonnull Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiElement element = descriptor.getPsiElement();
            PsiElement parent = element.getParent();
            if (parent instanceof PsiTypeParameter) {
                PsiTypeParameter typeParameter = (PsiTypeParameter) parent;
                replaceTypeParameterAndReferencesWithType(typeParameter);
            }
            else if (parent instanceof PsiTypeElement) {
                PsiTypeElement typeElement = (PsiTypeElement) parent;
                PsiElement lastChild = typeElement.getLastChild();
                if (lastChild == null) {
                    return;
                }
                typeElement.replace(lastChild);
            }
        }

        private static void replaceTypeParameterAndReferencesWithType(PsiTypeParameter typeParameter) {
            PsiReferenceList extendsList = typeParameter.getExtendsList();
            PsiClassType[] referenceElements = extendsList.getReferencedTypes();
            if (referenceElements.length < 1) {
                return;
            }
            PsiClass finalClass = referenceElements[0].resolve();
            if (finalClass == null) {
                return;
            }
            Project project = typeParameter.getProject();
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            PsiJavaCodeReferenceElement classReference = factory.createClassReferenceElement(finalClass);
            Query<PsiReference> query = ReferencesSearch.search(typeParameter, typeParameter.getUseScope());
            for (PsiReference reference : query) {
                PsiElement referenceElement = reference.getElement();
                referenceElement.replace(classReference);
            }
            typeParameter.delete();
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new TypeParameterExtendsFinalClassVisitor();
    }

    private static class TypeParameterExtendsFinalClassVisitor extends BaseInspectionVisitor {

        @Override
        public void visitTypeParameter(PsiTypeParameter classParameter) {
            super.visitTypeParameter(classParameter);
            PsiClassType[] extendsListTypes = classParameter.getExtendsListTypes();
            if (extendsListTypes.length < 1) {
                return;
            }
            PsiClassType extendsType = extendsListTypes[0];
            PsiClass aClass = extendsType.resolve();
            if (aClass == null) {
                return;
            }
            if (!aClass.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            PsiIdentifier nameIdentifier = classParameter.getNameIdentifier();
            if (nameIdentifier != null) {
                registerError(nameIdentifier, aClass, Integer.valueOf(1));
            }
        }

        @Override
        public void visitTypeElement(PsiTypeElement typeElement) {
            super.visitTypeElement(typeElement);
            PsiType type = typeElement.getType();
            if (!(type instanceof PsiWildcardType)) {
                return;
            }
            PsiWildcardType wildcardType = (PsiWildcardType) type;
            PsiType extendsBound = wildcardType.getExtendsBound();
            if (!(extendsBound instanceof PsiClassType)) {
                return;
            }
            PsiClassType classType = (PsiClassType) extendsBound;
            PsiClass aClass = classType.resolve();
            if (aClass == null || !aClass.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            if (isPartOfOverriddenMethod(typeElement)) {
                return;
            }
            registerError(typeElement.getFirstChild(), aClass, Integer.valueOf(2));
        }

        private static boolean isPartOfOverriddenMethod(PsiTypeElement typeElement) {
            PsiMethod method = PsiTreeUtil.getParentOfType(typeElement, PsiMethod.class);
            if (method == null) {
                return false;
            }
            PsiCodeBlock body = method.getBody();
            if (PsiTreeUtil.isAncestor(body, typeElement, true)) {
                return false;
            }
            return MethodUtils.hasSuper(method);
        }
    }
}