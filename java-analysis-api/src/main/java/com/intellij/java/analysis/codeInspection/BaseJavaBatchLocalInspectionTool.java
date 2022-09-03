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

import consulo.language.editor.rawHighlight.HighlightDisplayKey;
import consulo.language.editor.inspection.BatchSuppressableTool;
import consulo.language.editor.inspection.LocalInspectionTool;
import consulo.language.editor.inspection.SuppressQuickFix;
import consulo.language.psi.PsiElement;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public abstract class BaseJavaBatchLocalInspectionTool extends AbstractBaseJavaLocalInspectionTool implements BatchSuppressableTool
{
  @Nonnull
  @Override
  public SuppressQuickFix[] getBatchSuppressActions(@Nullable PsiElement element) {
    return BatchSuppressManager.SERVICE.getInstance().createBatchSuppressActions(HighlightDisplayKey.find(getShortName()));
  }

  @Override
  public boolean isSuppressedFor(@Nonnull PsiElement element) {
    return isSuppressedFor(element, this);
  }

  public static boolean isSuppressedFor(@Nonnull PsiElement element, @Nonnull LocalInspectionTool tool) {
    BatchSuppressManager manager = BatchSuppressManager.SERVICE.getInstance();
    String alternativeId;
    String toolId = tool.getID();
    return manager.isSuppressedFor(element, toolId) ||
           (alternativeId = tool.getAlternativeID()) != null &&
           !alternativeId.equals(toolId) && manager.isSuppressedFor(element, alternativeId);
  }
}
