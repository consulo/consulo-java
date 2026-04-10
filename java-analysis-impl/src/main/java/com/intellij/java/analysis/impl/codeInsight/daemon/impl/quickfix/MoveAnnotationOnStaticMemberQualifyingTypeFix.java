// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*
 * Copyright 2013-2026 consulo.io
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
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.psiutils.CommentTracker;
import consulo.java.analysis.localize.JavaAnalysisLocalize;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

public class MoveAnnotationOnStaticMemberQualifyingTypeFix implements LocalQuickFix {
    @Override
    public LocalizeValue getName() {
        return JavaAnalysisLocalize.annotationOnStaticMemberQualifyingTypeFamilyName();
    }

    @Override
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiAnnotation annotation = descriptor.getPsiElement() instanceof PsiAnnotation a ? a : null;
        if (annotation == null) return;

        PsiTypeElement psiTypeElement = getTypeElement(annotation);
        if (psiTypeElement == null) return;

        PsiJavaCodeReferenceElement innermostParent = psiTypeElement.getInnermostComponentReferenceElement();
        if (innermostParent == null) return;

        PsiElement rightmostDot = getRightmostDot(innermostParent.getLastChild());
        if (rightmostDot == null) return;

        innermostParent.addAfter(annotation, rightmostDot);

        CommentTracker ct = new CommentTracker();
        ct.markUnchanged(annotation);
        ct.deleteAndRestoreComments(annotation);
    }

    private static @Nullable PsiElement getRightmostDot(@Nullable PsiElement element) {
        if (element == null) {
            return null;
        }
        PsiElement sibling = element.getPrevSibling();
        while (sibling != null) {
            if (sibling.getNode() != null && sibling.getNode().getElementType() == JavaTokenType.DOT) {
                return sibling;
            }
            sibling = sibling.getPrevSibling();
        }
        return null;
    }

    private static @Nullable PsiTypeElement getTypeElement(PsiElement startElement) {
        PsiElement parent = PsiTreeUtil.getParentOfType(startElement, PsiTypeElement.class, PsiVariable.class, PsiMethod.class);
        if (parent instanceof PsiTypeElement typeElement) {
            return typeElement;
        }
        if (parent instanceof PsiVariable variable) {
            return variable.getTypeElement();
        }
        if (parent instanceof PsiMethod method) {
            return method.getReturnTypeElement();
        }
        return null;
    }
}
