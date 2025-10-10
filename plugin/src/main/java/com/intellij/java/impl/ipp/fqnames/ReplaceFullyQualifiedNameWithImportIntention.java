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
package com.intellij.java.impl.ipp.fqnames;

import com.intellij.java.impl.ig.style.UnnecessaryFullyQualifiedNameInspection;
import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.impl.ipp.psiutils.HighlightUtil;
import com.intellij.java.impl.ipp.psiutils.ImportUtils;
import com.intellij.java.language.psi.*;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @see UnnecessaryFullyQualifiedNameInspection
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ReplaceFullyQualifiedNameWithImportIntention", fileExtensions = "java", categories = {"Java",
    "Declaration"})
public class ReplaceFullyQualifiedNameWithImportIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.replaceFullyQualifiedNameWithImportIntentionName();
    }

    @Override
    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new FullyQualifiedNamePredicate();
    }

    @Override
    public void processIntention(@Nonnull PsiElement element) {
        PsiJavaCodeReferenceElement reference = (PsiJavaCodeReferenceElement) element;
        PsiElement target = reference.resolve();
        if (!(target instanceof PsiClass)) {
            PsiElement parent = reference.getParent();
            while (parent instanceof PsiJavaCodeReferenceElement) {
                reference = (PsiJavaCodeReferenceElement) parent;
                target = reference.resolve();
                if (target instanceof PsiClass) {
                    break;
                }
                parent = parent.getParent();
            }
        }
        if (!(target instanceof PsiClass)) {
            return;
        }
        final PsiClass aClass = (PsiClass) target;
        final String qualifiedName = aClass.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }
        final PsiJavaFile file =
            PsiTreeUtil.getParentOfType(reference, PsiJavaFile.class);
        if (file == null) {
            return;
        }
        ImportUtils.addImportIfNeeded(aClass, reference);
        final String fullyQualifiedText = reference.getText();
        final QualificationRemover qualificationRemover = new QualificationRemover(fullyQualifiedText);
        file.accept(qualificationRemover);
        final Collection<PsiJavaCodeReferenceElement> shortenedElements = qualificationRemover.getShortenedElements();
        HighlightUtil.highlightElements(shortenedElements);
    }

    private static class QualificationRemover extends JavaRecursiveElementWalkingVisitor {

        private final String fullyQualifiedText;
        private final List<PsiJavaCodeReferenceElement> shortenedElements = new ArrayList();

        QualificationRemover(String fullyQualifiedText) {
            this.fullyQualifiedText = fullyQualifiedText;
        }

        public Collection<PsiJavaCodeReferenceElement> getShortenedElements() {
            return Collections.unmodifiableCollection(shortenedElements);
        }

        @Override
        public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
            super.visitReferenceElement(reference);
            final PsiElement parent = reference.getParent();
            if (parent instanceof PsiImportStatement) {
                return;
            }
            final String text = reference.getText();
            if (!text.equals(fullyQualifiedText)) {
                return;
            }
            final PsiElement qualifier = reference.getQualifier();
            if (qualifier == null) {
                return;
            }
            try {
                qualifier.delete();
            }
            catch (IncorrectOperationException e) {
                final Class<? extends QualificationRemover> aClass = getClass();
                final String className = aClass.getName();
                final Logger logger = Logger.getInstance(className);
                logger.error(e);
            }
            shortenedElements.add(reference);
        }
    }
}