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

import com.intellij.codeInsight.FileModificationService;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import consulo.logging.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;

public class OptimizeImportsFix implements IntentionAction{
  private static final Logger LOG = Logger.getInstance(OptimizeImportsFix.class);

  @Override
  @Nonnull
  public String getText() {
    return JavaQuickFixBundle.message("optimize.imports.fix");
  }

  @Override
  @Nonnull
  public String getFamilyName() {
    return JavaQuickFixBundle.message("optimize.imports.fix");
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    return file.getManager().isInProject(file) && file instanceof PsiJavaFile;
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PsiJavaFile)) return;
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;

    try{
      JavaCodeStyleManager.getInstance(project).optimizeImports(file);
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
