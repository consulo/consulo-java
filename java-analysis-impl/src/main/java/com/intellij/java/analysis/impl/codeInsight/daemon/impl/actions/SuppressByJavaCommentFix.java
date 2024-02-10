/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.actions;

import com.intellij.java.analysis.impl.codeInspection.JavaSuppressionUtil;
import com.intellij.java.language.psi.PsiLocalVariable;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiStatement;
import consulo.language.editor.inspection.SuppressByCommentFix;
import consulo.language.editor.rawHighlight.HighlightDisplayKey;
import consulo.language.psi.PsiElement;
import consulo.language.psi.SyntheticElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author yole
 */
public class SuppressByJavaCommentFix extends SuppressByCommentFix
{
  public SuppressByJavaCommentFix(@Nonnull HighlightDisplayKey key) {
    super(key, PsiStatement.class);
  }

  @Override
  @Nullable
  public PsiElement getContainer(PsiElement context) {
    if (hasJspMethodCallAsParent(context)) return null;
    return PsiTreeUtil.getParentOfType(context, PsiStatement.class, false);
  }

  private static boolean hasJspMethodCallAsParent(PsiElement context) {
    while (true) {
      PsiMethod method = PsiTreeUtil.getParentOfType(context, PsiMethod.class);
      if (method == null) return false;
      if (method instanceof SyntheticElement) return true;
      context = method;
    }
  }

  @Override
  protected void createSuppression(@Nonnull final Project project,
                                   @Nonnull final PsiElement element,
                                   @Nonnull final PsiElement container) throws IncorrectOperationException {
    PsiElement declaredElement = JavaSuppressionUtil.getElementToAnnotate(element, container);
    if (declaredElement == null) {
      suppressWithComment(project, element, container);
    }
    else {
      JavaSuppressionUtil.addSuppressAnnotation(project, container, (PsiLocalVariable)declaredElement, myID);
    }
  }

  protected void suppressWithComment(Project project, PsiElement element, PsiElement container) {
    super.createSuppression(project, element, container);
  }
}
