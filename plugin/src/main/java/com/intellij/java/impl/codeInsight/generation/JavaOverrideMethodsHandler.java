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
package com.intellij.java.impl.codeInsight.generation;

import javax.annotation.Nonnull;

import consulo.language.editor.hint.HintManager;
import consulo.language.editor.action.LanguageCodeInsightActionHandler;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiFile;
import com.intellij.java.language.psi.PsiJavaFile;

/**
 * @author yole
 */
public class JavaOverrideMethodsHandler implements LanguageCodeInsightActionHandler {
  @Override
  public boolean isValidFor(final Editor editor, final PsiFile file) {
    if (!(file instanceof PsiJavaFile)) {
      return false;
    }

    PsiClass aClass = OverrideImplementUtil.getContextClass(file.getProject(), editor, file, true);
    return aClass != null && !OverrideImplementUtil.getMethodSignaturesToOverride(aClass).isEmpty();
  }

  @Override
  public void invoke(@Nonnull final Project project, @Nonnull final Editor editor, @Nonnull final PsiFile file) {
    PsiClass aClass = OverrideImplementUtil.getContextClass(project, editor, file, true);
    if (aClass == null) return;

    if (OverrideImplementUtil.getMethodSignaturesToOverride(aClass).isEmpty()) {
      HintManager.getInstance().showErrorHint(editor, "No methods to override have been found");
      return;
    }
    OverrideImplementUtil.chooseAndOverrideMethods(project, editor, aClass);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
