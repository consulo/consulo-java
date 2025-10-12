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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.language.psi.PsiJavaFile;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.CodeInsightSettings;
import consulo.language.editor.DaemonCodeAnalyzer;
import consulo.language.editor.intention.LowPriorityAction;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.project.Project;

import jakarta.annotation.Nonnull;

public class EnableOptimizeImportsOnTheFlyFix implements SyntheticIntentionAction, LowPriorityAction{
  @Override
  @Nonnull
  public LocalizeValue getText() {
    return JavaQuickFixLocalize.enableOptimizeImportsOnTheFly();
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    return file.getManager().isInProject(file)
           && file instanceof PsiJavaFile
           && !CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY
      ;
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) {
    CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY = true;
    DaemonCodeAnalyzer.getInstance(project).restart();
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
