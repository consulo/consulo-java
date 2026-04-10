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
package com.intellij.java.analysis.impl.codeInsight.intention;

import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiTypeElement;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.java.analysis.localize.JavaAnalysisLocalize;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.lang.StringUtil;

import java.util.Collection;

public class AddTypeAnnotationFix implements LocalQuickFix {
    private final SmartPsiElementPointer<PsiTypeElement> myElement;
    private final String myAnnotationToAdd;
    private final Collection<String> myAnnotationsToRemove;

    public AddTypeAnnotationFix(PsiTypeElement element, String annotationToAdd, Collection<String> annotationsToRemove) {
        myElement = SmartPointerManager.createPointer(element);
        myAnnotationToAdd = annotationToAdd;
        myAnnotationsToRemove = annotationsToRemove;
    }

    @Override
    public LocalizeValue getName() {
        return JavaAnalysisLocalize.inspectionI18nQuickfixAnnotateAs(StringUtil.getShortName(myAnnotationToAdd));
    }

    @Override
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiTypeElement typeElement = myElement.getElement();
        if (typeElement == null || !typeElement.acceptsAnnotations()) return;

        for (PsiAnnotation annotation : typeElement.getAnnotations()) {
            if (myAnnotationsToRemove.contains(annotation.getQualifiedName())) {
                annotation.delete();
            }
        }
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(typeElement.addAnnotation(myAnnotationToAdd));
    }
}
