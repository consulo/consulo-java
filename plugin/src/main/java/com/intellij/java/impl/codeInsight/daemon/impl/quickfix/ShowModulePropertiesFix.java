/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import consulo.ide.setting.ShowSettingsUtil;
import consulo.language.editor.inspection.IntentionAndQuickFixAction;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.IdeActions;
import consulo.codeEditor.Editor;
import consulo.module.Module;
import consulo.language.util.ModuleUtilCore;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class ShowModulePropertiesFix extends IntentionAndQuickFixAction implements SyntheticIntentionAction {
  private final String myModuleName;

  public ShowModulePropertiesFix(@jakarta.annotation.Nonnull PsiElement context) {
    this(ModuleUtilCore.findModuleForPsiElement(context));
  }

  public ShowModulePropertiesFix(@Nullable Module module) {
    myModuleName = module == null ? null : module.getName();
  }

  @Nonnull
  @Override
  public String getName() {
    AnAction action = ActionManager.getInstance().getAction(IdeActions.MODULE_SETTINGS);
    return action.getTemplatePresentation().getText();
  }

  @Override
  @jakarta.annotation.Nonnull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@jakarta.annotation.Nonnull final Project project, final Editor editor, final PsiFile file) {
    return myModuleName != null;
  }

  @Override
  public void applyFix(@jakarta.annotation.Nonnull Project project, PsiFile file, @Nullable Editor editor) {
    ShowSettingsUtil.getInstance().showProjectStructureDialog(project, projectStructureSelector -> {
      projectStructureSelector.select(myModuleName, null, true);
    });
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}