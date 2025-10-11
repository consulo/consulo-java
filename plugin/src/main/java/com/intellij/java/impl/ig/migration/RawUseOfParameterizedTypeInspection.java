/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.MultipleCheckboxOptionsPanel;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;

@ExtensionImpl
public class RawUseOfParameterizedTypeInspection extends BaseInspection {

    @SuppressWarnings("PublicField")
    public boolean ignoreObjectConstruction = true;

    @SuppressWarnings("PublicField")
    public boolean ignoreTypeCasts = false;

    @SuppressWarnings("PublicField")
    public boolean ignoreUncompilable = false;

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.rawUseOfParameterizedTypeDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.rawUseOfParameterizedTypeProblemDescriptor().get();
    }

    @Override
    @Nullable
    public JComponent createOptionsPanel() {
        final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
        optionsPanel.addCheckbox(
            InspectionGadgetsLocalize.rawUseOfParameterizedTypeIgnoreNewObjectsOption().get(),
            "ignoreObjectConstruction"
        );
        optionsPanel.addCheckbox(
            InspectionGadgetsLocalize.rawUseOfParameterizedTypeIgnoreTypeCastsOption().get(),
            "ignoreTypeCasts"
        );
        optionsPanel.addCheckbox(
            InspectionGadgetsLocalize.rawUseOfParameterizedTypeIgnoreUncompilableOption().get(),
            "ignoreUncompilable"
        );
        return optionsPanel;
    }

    @Override
    public String getAlternativeID() {
        return "rawtypes";
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new RawUseOfParameterizedTypeVisitor();
    }

    private class RawUseOfParameterizedTypeVisitor extends BaseInspectionVisitor {

        @Override
        public void visitNewExpression(@Nonnull PsiNewExpression expression) {
            if (!hasNeededLanguageLevel(expression)) {
                return;
            }
            super.visitNewExpression(expression);
            if (ignoreObjectConstruction) {
                return;
            }
            final PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
            checkReferenceElement(classReference);
        }

        @Override
        public void visitTypeElement(@Nonnull PsiTypeElement typeElement) {
            if (!hasNeededLanguageLevel(typeElement)) {
                return;
            }
            final PsiType type = typeElement.getType();
            if (type instanceof PsiArrayType) {
                return;
            }
            super.visitTypeElement(typeElement);
            final PsiElement parent = typeElement.getParent();
            if (parent instanceof PsiInstanceOfExpression || parent instanceof PsiClassObjectAccessExpression) {
                return;
            }
            if (ignoreTypeCasts && parent instanceof PsiTypeCastExpression) {
                return;
            }
            if (PsiTreeUtil.getParentOfType(typeElement, PsiComment.class) != null) {
                return;
            }
            final PsiAnnotationMethod annotationMethod =
                PsiTreeUtil.getParentOfType(typeElement, PsiAnnotationMethod.class, true, PsiClass.class);
            if (ignoreUncompilable && annotationMethod != null) {
                // type of class type parameter cannot be parameterized if annotation method has default value
                final PsiAnnotationMemberValue defaultValue = annotationMethod.getDefaultValue();
                if (defaultValue != null && parent != annotationMethod) {
                    return;
                }
            }
            final PsiJavaCodeReferenceElement referenceElement = typeElement.getInnermostComponentReferenceElement();
            checkReferenceElement(referenceElement);
        }

        @Override
        public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
            if (!hasNeededLanguageLevel(reference)) {
                return;
            }
            super.visitReferenceElement(reference);
            final PsiElement referenceParent = reference.getParent();
            if (!(referenceParent instanceof PsiReferenceList)) {
                return;
            }
            final PsiReferenceList referenceList = (PsiReferenceList) referenceParent;
            final PsiElement listParent = referenceList.getParent();
            if (!(listParent instanceof PsiClass)) {
                return;
            }
            checkReferenceElement(reference);
        }

        private void checkReferenceElement(PsiJavaCodeReferenceElement reference) {
            if (reference == null) {
                return;
            }
            final PsiType[] typeParameters = reference.getTypeParameters();
            if (typeParameters.length > 0) {
                return;
            }
            final PsiElement element = reference.resolve();
            if (!(element instanceof PsiClass)) {
                return;
            }
            final PsiClass aClass = (PsiClass) element;
            final PsiElement qualifier = reference.getQualifier();
            if (qualifier instanceof PsiJavaCodeReferenceElement) {
                final PsiJavaCodeReferenceElement qualifierReference = (PsiJavaCodeReferenceElement) qualifier;
                if (!aClass.hasModifierProperty(PsiModifier.STATIC) && !aClass.isInterface() && !aClass.isEnum()) {
                    checkReferenceElement(qualifierReference);
                }
            }
            if (!aClass.hasTypeParameters()) {
                return;
            }
            registerError(reference);
        }

        private boolean hasNeededLanguageLevel(PsiElement element) {
            if (element.getLanguage() != JavaLanguage.INSTANCE) {
                return false;
            }
            return PsiUtil.isLanguageLevel5OrHigher(element);
        }
    }
}

