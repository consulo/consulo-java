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

import jakarta.annotation.Nonnull;

import consulo.language.editor.FileModificationService;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.project.Project;
import consulo.language.psi.PsiFile;
import com.intellij.java.language.psi.PsiParameter;
import consulo.language.util.IncorrectOperationException;

/**
 * @author ven
 */
public class MakeVarargParameterLastFix implements SyntheticIntentionAction {
  public MakeVarargParameterLastFix(PsiParameter parameter) {
    myParameter = parameter;
  }

  private final PsiParameter myParameter;

  @Override
  @Nonnull
  public String getText() {
    return JavaQuickFixBundle.message("make.vararg.parameter.last.text", myParameter.getName());
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    return myParameter.isValid() && myParameter.getManager().isInProject(myParameter);
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(myParameter)) return;
    myParameter.getParent().add(myParameter);
    myParameter.delete();
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
