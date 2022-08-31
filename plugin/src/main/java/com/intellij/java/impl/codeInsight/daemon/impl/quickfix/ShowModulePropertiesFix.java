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

import com.intellij.codeInspection.IntentionAndQuickFixAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ShowModulePropertiesFix extends IntentionAndQuickFixAction {
  private final String myModuleName;

  public ShowModulePropertiesFix(@Nonnull PsiElement context) {
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
  @Nonnull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@Nonnull final Project project, final Editor editor, final PsiFile file) {
    return myModuleName != null;
  }

  @Override
  public void applyFix(@Nonnull Project project, PsiFile file, @javax.annotation.Nullable Editor editor) {
    ProjectSettingsService.getInstance(project).showModuleConfigurationDialog(myModuleName, null);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}