/*
 * Copyright 2010-2011 Bas Leijdekkers
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
package com.intellij.java.impl.ig.style;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class SimplifiableAnnotationInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.simplifiableAnnotationDisplayName();
    }

    @Nonnull
    @Override
    protected String buildErrorString(Object... infos) {
        final String replacement = (String) infos[0];
        return InspectionGadgetsLocalize.simplifiableAnnotationProblemDescriptor(replacement).get();
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        final String replacement = (String) infos[0];
        return new SimplifiableAnnotationFix(replacement);
    }

    private static class SimplifiableAnnotationFix extends InspectionGadgetsFix {

        private final String replacement;

        public SimplifiableAnnotationFix(String replacement) {
            this.replacement = replacement;
        }

        @Nonnull
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.simplifiableAnnotationQuickfix();
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            if (!(element instanceof PsiAnnotation)) {
                return;
            }
            final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            final PsiAnnotation annotation = factory.createAnnotationFromText(replacement, element);
            element.replace(annotation);
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new SimplifiableAnnotationVisitor();
    }

    private static class SimplifiableAnnotationVisitor extends BaseInspectionVisitor {

        @Override
        public void visitAnnotation(PsiAnnotation annotation) {
            super.visitAnnotation(annotation);
            final PsiAnnotationParameterList parameterList = annotation.getParameterList();
            final PsiJavaCodeReferenceElement nameReferenceElement = annotation.getNameReferenceElement();
            if (nameReferenceElement == null) {
                return;
            }
            final PsiNameValuePair[] attributes = parameterList.getAttributes();
            final PsiElement[] annotationChildren = annotation.getChildren();
            if (annotationChildren.length >= 2) {
                final PsiElement child = annotationChildren[1];
                if (child instanceof PsiWhiteSpace) {
                    final String annotationName = nameReferenceElement.getText();
                    final String replacementText;
                    if (attributes.length > 0) {
                        replacementText = '@' + annotationName + parameterList.getText();
                    }
                    else {
                        replacementText = '@' + annotationName;
                    }
                    registerError(annotation, replacementText);
                    return;
                }
            }
            if (attributes.length == 0) {
                final PsiElement[] children = parameterList.getChildren();
                if (children.length <= 0) {
                    return;
                }
                final String annotationName = nameReferenceElement.getText();
                registerError(annotation, '@' + annotationName);
            }
            else if (attributes.length == 1) {
                final PsiNameValuePair attribute = attributes[0];
                @NonNls final String name = attribute.getName();
                final PsiAnnotationMemberValue attributeValue = attribute.getValue();
                if (attributeValue == null) {
                    return;
                }
                final String attributeValueText;
                if (!"value".equals(name)) {
                    if (!(attributeValue instanceof PsiArrayInitializerMemberValue)) {
                        return;
                    }
                    final PsiArrayInitializerMemberValue arrayValue = (PsiArrayInitializerMemberValue) attributeValue;
                    final PsiAnnotationMemberValue[] initializers = arrayValue.getInitializers();
                    if (initializers.length != 1) {
                        return;
                    }
                    if (name == null) {
                        attributeValueText = initializers[0].getText();
                    }
                    else {
                        attributeValueText = name + '=' + initializers[0].getText();
                    }
                }
                else {
                    attributeValueText = getAttributeValueText(attributeValue);
                }
                final String annotationName = nameReferenceElement.getText();
                final String replacementText = '@' + annotationName + '(' + attributeValueText + ')';
                registerError(annotation, replacementText);
            }
        }

        private static String getAttributeValueText(PsiAnnotationMemberValue value) {
            if (value instanceof PsiArrayInitializerMemberValue) {
                final PsiArrayInitializerMemberValue arrayValue = (PsiArrayInitializerMemberValue) value;
                final PsiAnnotationMemberValue[] initializers = arrayValue.getInitializers();
                if (initializers.length == 1) {
                    return initializers[0].getText();
                }
            }
            return value.getText();
        }
    }
}
