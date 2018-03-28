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
package com.intellij.codeInsight.daemon.impl.quickfix;

import javax.annotation.Nonnull;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import consulo.java.JavaQuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;

public class EnableOptimizeImportsOnTheFlyFix implements IntentionAction, LowPriorityAction{
  @Override
  @Nonnull
  public String getText() {
    return JavaQuickFixBundle.message("enable.optimize.imports.on.the.fly");
  }

  @Override
  @Nonnull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    return file.getManager().isInProject(file)
           && file instanceof PsiJavaFile
           && !com.intellij.codeInsight.CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY
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
