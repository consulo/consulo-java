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
package com.intellij.java.impl.ig.errorhandling;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.javadoc.PsiDocTag;
import com.intellij.java.language.psi.javadoc.PsiDocTagValue;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.documentation.CodeDocumentationProvider;
import consulo.language.editor.documentation.CompositeDocumentationProvider;
import consulo.language.editor.documentation.DocumentationProvider;
import consulo.language.editor.documentation.LanguageDocumentationProvider;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class ThrowsRuntimeExceptionInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.throwsRuntimeExceptionDisplayName();
    }

    @Nonnull
    @Override
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.throwsRuntimeExceptionProblemDescriptor().get();
    }

    @Nonnull
    @Override
    protected InspectionGadgetsFix[] buildFixes(Object... infos) {
        final String exceptionName = (String) infos[0];
        if (MoveExceptionToJavadocFix.isApplicable((PsiJavaCodeReferenceElement) infos[1])) {
            return new InspectionGadgetsFix[]{
                new ThrowsRuntimeExceptionFix(exceptionName),
                new MoveExceptionToJavadocFix(exceptionName)
            };
        }
        return new InspectionGadgetsFix[]{new ThrowsRuntimeExceptionFix(exceptionName)};
    }

    private static class MoveExceptionToJavadocFix extends InspectionGadgetsFix {

        private final String myExceptionName;

        private MoveExceptionToJavadocFix(String exceptionName) {
            myExceptionName = exceptionName;
        }

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.throwsRuntimeExceptionMoveQuickfix(myExceptionName);
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiElement parent = element.getParent();
            final PsiElement grandParent = parent.getParent();
            if (!(grandParent instanceof PsiMethod)) {
                return;
            }
            final PsiMethod method = (PsiMethod) grandParent;
            final PsiDocComment comment = method.getDocComment();
            final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            if (comment != null) {
                final PsiDocTag docTag = factory.createDocTagFromText("@throws " + element.getText());
                comment.add(docTag);
            }
            else {
                final PsiDocComment docComment = factory.createDocCommentFromText("/** */");
                final PsiComment resultComment = (PsiComment) method.addBefore(docComment, method.getModifierList());
                final DocumentationProvider documentationProvider =
                    LanguageDocumentationProvider.forLanguageComposite(method.getLanguage());
                final CodeDocumentationProvider codeDocumentationProvider;
                if (documentationProvider instanceof CodeDocumentationProvider) {
                    codeDocumentationProvider = (CodeDocumentationProvider) documentationProvider;
                }
                else if (documentationProvider instanceof CompositeDocumentationProvider) {
                    final CompositeDocumentationProvider compositeDocumentationProvider =
                        (CompositeDocumentationProvider) documentationProvider;
                    codeDocumentationProvider = compositeDocumentationProvider.getFirstCodeDocumentationProvider();
                    if (codeDocumentationProvider == null) {
                        return;
                    }
                }
                else {
                    return;
                }
                final String commentStub = codeDocumentationProvider.generateDocumentationContentStub(resultComment);
                final PsiDocComment newComment = factory.createDocCommentFromText("/**\n" + commentStub + "*/");
                resultComment.replace(newComment);
            }
            element.delete();
        }

        public static boolean isApplicable(@Nonnull PsiJavaCodeReferenceElement reference) {
            final PsiElement parent = reference.getParent();
            final PsiElement grandParent = parent.getParent();
            if (!(grandParent instanceof PsiMethod)) {
                return false;
            }
            final PsiMethod method = (PsiMethod) grandParent;
            final PsiDocComment docComment = method.getDocComment();
            if (docComment == null) {
                return true;
            }
            final PsiElement throwsTarget = reference.resolve();
            if (throwsTarget == null) {
                return true;
            }
            final PsiDocTag[] tags = docComment.findTagsByName("throws");
            for (PsiDocTag tag : tags) {
                final PsiDocTagValue valueElement = tag.getValueElement();
                if (valueElement == null) {
                    continue;
                }
                final PsiElement child = valueElement.getFirstChild();
                if (child == null) {
                    continue;
                }
                final PsiElement grandChild = child.getFirstChild();
                if (!(grandChild instanceof PsiJavaCodeReferenceElement)) {
                    continue;
                }
                final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement) grandChild;
                final PsiElement target = referenceElement.resolve();
                if (throwsTarget.equals(target)) {
                    return false;
                }
            }
            return true;
        }
    }

    private static class ThrowsRuntimeExceptionFix extends InspectionGadgetsFix {

        private final String myClassName;

        public ThrowsRuntimeExceptionFix(String className) {
            myClassName = className;
        }

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.throwsRuntimeExceptionQuickfix(myClassName);
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            descriptor.getPsiElement().delete();
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ThrowsRuntimeExceptionVisitor();
    }

    private static class ThrowsRuntimeExceptionVisitor extends BaseInspectionVisitor {

        @Override
        public void visitMethod(PsiMethod method) {
            super.visitMethod(method);
            final PsiReferenceList throwsList = method.getThrowsList();
            final PsiJavaCodeReferenceElement[] referenceElements = throwsList.getReferenceElements();
            for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
                final PsiElement target = referenceElement.resolve();
                if (!(target instanceof PsiClass)) {
                    continue;
                }
                final PsiClass aClass = (PsiClass) target;
                if (!InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION)) {
                    continue;
                }
                final String className = aClass.getName();
                registerError(referenceElement, className, referenceElement);
            }
        }
    }
}
