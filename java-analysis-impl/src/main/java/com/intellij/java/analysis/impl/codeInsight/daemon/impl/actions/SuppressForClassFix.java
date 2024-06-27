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
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.actions;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiDeclarationStatement;
import com.intellij.java.language.psi.PsiDocCommentOwner;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.rawHighlight.HighlightDisplayKey;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * User: anna
 * Date: May 13, 2005
 */
public class SuppressForClassFix extends SuppressFix {
  public SuppressForClassFix(final HighlightDisplayKey key) {
    super(key);
  }

  public SuppressForClassFix(final String id) {
    super(id);
  }

  @RequiredReadAction
  @Override
  @Nullable
  public PsiDocCommentOwner getContainer(final PsiElement element) {
    PsiDocCommentOwner container = super.getContainer(element);
    if (container == null || container instanceof PsiClass) {
      return null;
    }
    while (container != null) {
      final PsiClass parentClass = PsiTreeUtil.getParentOfType(container, PsiClass.class);
      if ((parentClass == null || container.getParent() instanceof PsiDeclarationStatement || container.getParent() instanceof PsiClass)
        && container instanceof PsiClass) {
        return container;
      }
      container = parentClass;
    }
    return container;
  }

  @Override
  @Nonnull
  public String getText() {
    return InspectionLocalize.suppressInspectionClass().get();
  }
}
