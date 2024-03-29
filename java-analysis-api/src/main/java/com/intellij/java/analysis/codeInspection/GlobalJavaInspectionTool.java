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

/*
 * User: anna
 * Date: 19-Dec-2007
 */
package com.intellij.java.analysis.codeInspection;

import com.intellij.java.language.JavaLanguage;
import consulo.language.Language;
import consulo.language.editor.inspection.CustomSuppressableInspectionTool;
import consulo.language.editor.inspection.GlobalInspectionContext;
import consulo.language.editor.inspection.GlobalInspectionTool;
import consulo.language.editor.inspection.ProblemDescriptionsProcessor;
import consulo.language.editor.inspection.reference.RefManager;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.intention.SuppressIntentionAction;
import consulo.language.editor.rawHighlight.HighlightDisplayKey;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public abstract class GlobalJavaInspectionTool extends GlobalInspectionTool implements CustomSuppressableInspectionTool {
  @Override
  public boolean queryExternalUsagesRequests(final InspectionManager manager,
                                             final GlobalInspectionContext globalContext,
                                             final ProblemDescriptionsProcessor problemDescriptionsProcessor,
                                             Object state) {
    return queryExternalUsagesRequests(globalContext.getRefManager(), globalContext.getExtension(GlobalJavaInspectionContext.CONTEXT), problemDescriptionsProcessor,
            state);
  }

  protected boolean queryExternalUsagesRequests(RefManager manager, GlobalJavaInspectionContext globalContext, ProblemDescriptionsProcessor processor, Object state) {
    return false;
  }

  @Override
  @Nullable
  public SuppressIntentionAction[] getSuppressActions(final PsiElement element) {
    return SuppressManager.getInstance().createSuppressActions(HighlightDisplayKey.find(getShortName()));
  }

  @Override                                                           
  public boolean isSuppressedFor(@Nonnull final PsiElement element) {
    return BatchSuppressManager.getInstance().isSuppressedFor(element, getShortName());
  }

  @Nonnull
  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nullable
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}