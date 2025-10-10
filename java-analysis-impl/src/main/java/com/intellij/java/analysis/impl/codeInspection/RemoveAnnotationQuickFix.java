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
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

/**
 * @author yole
 */
public class RemoveAnnotationQuickFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("com.intellij.codeInsight.i18n.AnnotateNonNlsQuickfix");
  private final PsiAnnotation myAnnotation;
  private final PsiModifierListOwner myListOwner;

  public RemoveAnnotationQuickFix(PsiAnnotation annotation, final PsiModifierListOwner listOwner) {
    myAnnotation = annotation;
    myListOwner = listOwner;
  }

  @Override
  @Nonnull
  public LocalizeValue getName() {
    return CodeInsightLocalize.removeAnnotation();
  }

  @Override
  public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
    if (myAnnotation.isPhysical()) {
      try {
        if (!FileModificationService.getInstance().preparePsiElementForWrite(myAnnotation)) return;
        myAnnotation.delete();
      } catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    } else {
      ExternalAnnotationsManager.getInstance(project).deannotate(myListOwner, myAnnotation.getQualifiedName());
    }
  }
}