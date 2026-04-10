/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.analysis.impl.codeInspection;

import com.intellij.java.language.codeInsight.ExternalAnnotationsManager;
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiModifierListOwner;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * @author yole
 */
public class RemoveAnnotationQuickFix implements LocalQuickFix {
    private static final Logger LOG = Logger.getInstance(RemoveAnnotationQuickFix.class);
    private final SmartPsiElementPointer<PsiAnnotation> myAnnotation;
    private final SmartPsiElementPointer<PsiModifierListOwner> myListOwner;
    private final boolean myRemoveInheritors;

    public RemoveAnnotationQuickFix(@NotNull PsiAnnotation annotation, @Nullable PsiModifierListOwner listOwner) {
        this(annotation, listOwner, false);
    }

    public RemoveAnnotationQuickFix(@NotNull PsiAnnotation annotation, @Nullable PsiModifierListOwner listOwner, boolean removeInheritors) {
        Project project = annotation.getProject();
        SmartPointerManager pm = SmartPointerManager.getInstance(project);
        myAnnotation = pm.createSmartPsiElementPointer(annotation);
        myListOwner = listOwner == null ? null : pm.createSmartPsiElementPointer(listOwner);
        myRemoveInheritors = removeInheritors;
    }

    @Override
    public LocalizeValue getName() {
        return CodeInsightLocalize.removeAnnotation();
    }

    @Override
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiAnnotation annotation = myAnnotation.getElement();
        if (annotation == null) {
            return;
        }

        if (annotation.isPhysical()) {
            try {
                if (!FileModificationService.getInstance().preparePsiElementForWrite(annotation)) {
                    return;
                }
                annotation.delete();
            }
            catch (IncorrectOperationException e) {
                LOG.error(e);
            }
        }
        else {
            ExternalAnnotationsManager.getInstance(project).deannotate(myListOwner.getElement(), annotation.getQualifiedName());
        }
    }
}