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
package com.intellij.java.analysis.codeInspection;

import com.intellij.java.language.psi.PsiDocCommentOwner;
import com.intellij.java.language.psi.PsiModifierListOwner;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.ide.ServiceManager;
import consulo.language.editor.inspection.SuppressQuickFix;
import consulo.language.editor.rawHighlight.HighlightDisplayKey;
import consulo.language.psi.PsiElement;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collection;

@ServiceAPI(ComponentScope.APPLICATION)
public interface BatchSuppressManager {
  String SUPPRESS_INSPECTIONS_ANNOTATION_NAME = "java.lang.SuppressWarnings";

  public static BatchSuppressManager getInstance() {
    return ServiceManager.getService(BatchSuppressManager.class);
  }

  @Deprecated
  class SERVICE {
    public static BatchSuppressManager getInstance() {
      return BatchSuppressManager.getInstance();
    }
  }

  @Nonnull
  SuppressQuickFix[] createBatchSuppressActions(@Nonnull HighlightDisplayKey key);

  boolean isSuppressedFor(@Nonnull PsiElement element, String toolId);

  PsiElement getElementMemberSuppressedIn(@Nonnull PsiDocCommentOwner owner, String inspectionToolID);

  @Nullable
  PsiElement getAnnotationMemberSuppressedIn(@Nonnull PsiModifierListOwner owner, String inspectionToolID);

  @Nullable
  PsiElement getDocCommentToolSuppressedIn(@Nonnull PsiDocCommentOwner owner, String inspectionToolID);

  @Nonnull
  Collection<String> getInspectionIdsSuppressedInAnnotation(@Nonnull PsiModifierListOwner owner);

  @Nullable
  String getSuppressedInspectionIdsIn(@Nonnull PsiElement element);

  @Nullable
  PsiElement getElementToolSuppressedIn(@Nonnull PsiElement place, String toolId);

  boolean canHave15Suppressions(@Nonnull PsiElement file);

  boolean alreadyHas14Suppressions(@Nonnull PsiDocCommentOwner commentOwner);
}
