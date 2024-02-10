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
package com.intellij.java.impl.codeInspection;

import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.MethodThrowsFix;
import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiMethod;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.java.analysis.impl.JavaQuickFixBundle;

import jakarta.annotation.Nonnull;

/**
 * @author cdr
 */
public class DeleteThrowsFix implements LocalQuickFix {
  private final MethodThrowsFix myQuickFix;

  public DeleteThrowsFix(PsiMethod method, PsiClassType exceptionClass) {
    myQuickFix = new MethodThrowsFix(method, exceptionClass, false, false);
  }

  @Override
  @Nonnull
  public String getName() {
    return myQuickFix.getText();
  }

  @Override
  @Nonnull
  public String getFamilyName() {
    return JavaQuickFixBundle.message("fix.throws.list.family");
  }

  @Override
  public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    if (element == null) return;
    final PsiFile psiFile = element.getContainingFile();
    if (myQuickFix.isAvailable(project, null, psiFile)) {
      myQuickFix.invoke(project, null, psiFile);
    }
  }
}
