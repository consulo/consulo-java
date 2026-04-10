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
import consulo.java.analysis.localize.JavaAnalysisLocalize;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.lang.ObjectUtil;

public class MoveAnnotationToArrayFix implements LocalQuickFix {
    @Override
    public LocalizeValue getName() {
        return JavaAnalysisLocalize.intentionFamilyNameMoveAnnotationToArray();
    }

    @Override
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiAnnotation annotation = ObjectUtil.tryCast(descriptor.getPsiElement(), PsiAnnotation.class);
        if (annotation == null) return;
        String qualifiedName = annotation.getQualifiedName();
        if (qualifiedName == null) return;
        PsiModifierList owner = ObjectUtil.tryCast(annotation.getOwner(), PsiModifierList.class);
        if (owner == null) return;
        PsiModifierListOwner member = ObjectUtil.tryCast(owner.getParent(), PsiModifierListOwner.class);
        PsiTypeElement typeElement = member instanceof PsiMethod method ? method.getReturnTypeElement()
            : member instanceof PsiVariable variable ? variable.getTypeElement() : null;
        if (typeElement == null || !(typeElement.getType() instanceof PsiArrayType)) return;
        PsiAnnotation addedAnnotation = typeElement.addAnnotation(qualifiedName);
        addedAnnotation.replace(annotation);
        annotation.delete();
    }
}
