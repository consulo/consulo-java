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

package com.intellij.java.impl.codeInsight.generation.actions;

import consulo.language.editor.action.CodeInsightActionHandler;
import consulo.language.editor.action.CodeInsightAction;
import consulo.language.editor.generation.GenerateActionPopupTemplateInjector;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaFile;
import consulo.language.editor.refactoring.ContextAwareActionHandler;
import consulo.ui.ex.action.AnAction;
import consulo.dataContext.DataContext;
import consulo.ui.ex.action.Presentation;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import com.intellij.psi.*;
import consulo.language.psi.util.PsiTreeUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class BaseGenerateAction extends CodeInsightAction implements GenerateActionPopupTemplateInjector {
  private final CodeInsightActionHandler myHandler;

  public BaseGenerateAction(CodeInsightActionHandler handler) {
    myHandler = handler;
  }

  @Override
  protected void update(@Nonnull Presentation presentation, @Nonnull Project project, @Nonnull Editor editor, @Nonnull PsiFile file, @Nonnull DataContext dataContext, @Nullable String actionPlace) {
    super.update(presentation, project, editor, file, dataContext, actionPlace);
    if (myHandler instanceof ContextAwareActionHandler && presentation.isEnabled()) {
      presentation.setEnabled(((ContextAwareActionHandler) myHandler).isAvailableForQuickList(editor, file, dataContext));
    }
  }

  @Override
  @Nullable
  public AnAction createEditTemplateAction(DataContext dataContext) {
    return null;
  }

  @Nonnull
  @Override
  protected final CodeInsightActionHandler getHandler() {
    return myHandler;
  }

  @Nullable
  protected PsiClass getTargetClass(Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null) {
      return null;
    }
    final PsiClass target = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    return target instanceof SyntheticElement ? null : target;
  }

  @Override
  protected boolean isValidForFile(@Nonnull Project project, @Nonnull Editor editor, @Nonnull PsiFile file) {
    if (!(file instanceof PsiJavaFile)) {
      return false;
    }
    if (file instanceof PsiCompiledElement) {
      return false;
    }

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiClass targetClass = getTargetClass(editor, file);
    return targetClass != null && isValidForClass(targetClass);
  }

  protected boolean isValidForClass(final PsiClass targetClass) {
    return !targetClass.isInterface();
  }
}
