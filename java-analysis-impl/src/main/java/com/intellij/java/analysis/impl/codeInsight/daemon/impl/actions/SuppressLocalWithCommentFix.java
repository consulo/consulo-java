/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import consulo.language.editor.rawHighlight.HighlightDisplayKey;
import com.intellij.java.analysis.impl.codeInspection.JavaSuppressionUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * User: anna
 */
public class SuppressLocalWithCommentFix extends SuppressByJavaCommentFix {
  public SuppressLocalWithCommentFix(@Nonnull HighlightDisplayKey key) {
    super(key);
  }

  @Nullable
  @Override
  public PsiElement getContainer(PsiElement context) {
    final PsiElement container = super.getContainer(context);
    if (container != null) {
      final PsiElement elementToAnnotate = JavaSuppressionUtil.getElementToAnnotate(context, container);
      if (elementToAnnotate == null) return null;
    }
    return container;
  }

  @Override
  protected void createSuppression(@Nonnull Project project, @Nonnull PsiElement element, @Nonnull PsiElement container)
    throws IncorrectOperationException {
    suppressWithComment(project, element, container);
  }

  @Nonnull
  @Override
  public LocalizeValue getText() {
    return LocalizeValue.localizeTODO("Suppress for statement with comment");
  }
}
