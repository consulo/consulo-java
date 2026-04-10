// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package com.intellij.java.analysis.impl.codeInspection.nullable;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.java.analysis.localize.JavaAnalysisLocalize;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.lang.ObjectUtil;
import org.jspecify.annotations.Nullable;

public class MoveAnnotationToBoundFix implements LocalQuickFix {
    @Override
    public LocalizeValue getName() {
        return JavaAnalysisLocalize.intentionFamilyNameMoveAnnotationToUpperBound();
    }

    @Override
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiAnnotation annotation = ObjectUtil.tryCast(descriptor.getPsiElement(), PsiAnnotation.class);
        if (annotation == null) return;
        String qualifiedName = annotation.getQualifiedName();
        if (qualifiedName == null) return;
        PsiWildcardType owner = ObjectUtil.tryCast(annotation.getOwner(), PsiWildcardType.class);
        if (owner == null) return;
        PsiTypeElement typeElement = ObjectUtil.tryCast(annotation.getParent(), PsiTypeElement.class);
        if (typeElement == null) return;
        StringBuilder newText = new StringBuilder();
        boolean added = false;
        for (PsiElement child : typeElement.getChildren()) {
            if (child == annotation) continue;
            newText.append(child.getText());
            if (PsiUtil.isJavaToken(child, JavaTokenType.EXTENDS_KEYWORD) || PsiUtil.isJavaToken(child, JavaTokenType.SUPER_KEYWORD)) {
                newText.append(" ").append(annotation.getText());
                added = true;
            }
        }
        if (!added) {
            newText.append(" extends ").append(annotation.getText()).append(" ").append(CommonClassNames.JAVA_LANG_OBJECT);
        }
        PsiTypeElement newTypeElement = JavaPsiFacade.getElementFactory(project).createTypeElementFromText(newText.toString().trim(), typeElement);
        typeElement.replace(newTypeElement);
    }

    static @Nullable MoveAnnotationToBoundFix create(PsiAnnotation annotation) {
        PsiWildcardType owner = ObjectUtil.tryCast(annotation.getOwner(), PsiWildcardType.class);
        if (owner == null) return null;
        PsiType bound = owner.getBound();
        String qualifiedName = annotation.getQualifiedName();
        if (qualifiedName == null) return null;
        if (bound != null && bound.hasAnnotation(qualifiedName)) return null;
        return new MoveAnnotationToBoundFix();
    }
}
