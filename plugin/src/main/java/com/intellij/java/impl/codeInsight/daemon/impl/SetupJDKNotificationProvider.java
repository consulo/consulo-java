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
package com.intellij.java.impl.codeInsight.daemon.impl;

import com.intellij.java.language.JavaCoreBundle;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.impl.JavaClassFileType;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.fileEditor.EditorNotificationBuilder;
import consulo.fileEditor.EditorNotificationProvider;
import consulo.fileEditor.FileEditor;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.util.ModuleUtilCore;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import jakarta.annotation.Nonnull;

import java.util.function.Supplier;

/**
 * @author Danila Ponomarenko
 */
@ExtensionImpl
public class SetupJDKNotificationProvider implements EditorNotificationProvider {
  private final Project myProject;

  @Inject
  public SetupJDKNotificationProvider(Project project) {
    myProject = project;
  }

  @Nonnull
  @Override
  public String getId() {
    return "java-sdk-notify";
  }

  @RequiredReadAction
  @Nullable
  @Override
  public EditorNotificationBuilder buildNotification(@Nonnull VirtualFile file, @Nonnull FileEditor fileEditor, @Nonnull Supplier<EditorNotificationBuilder> supplier) {
    if (file.getFileType() == JavaClassFileType.INSTANCE) {
      return null;
    }

    final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
    if (psiFile == null) {
      return null;
    }

    if (psiFile.getLanguage() != JavaLanguage.INSTANCE) {
      return null;
    }

    final Module moduleForPsiElement = ModuleUtilCore.findModuleForPsiElement(psiFile);
    if (moduleForPsiElement == null) {
      return null;
    }
    final JavaModuleExtension extension = ModuleUtilCore.getExtension(moduleForPsiElement, JavaModuleExtension.class);
    if (extension == null) {
      return null;
    }

    if (extension.getInheritableSdk().isNull()) {
      EditorNotificationBuilder builder = supplier.get();
      createPanel(myProject, psiFile, builder);
      return builder;
    }
    return null;
  }

  private static void createPanel(final @Nonnull Project project, final @Nonnull PsiFile file, EditorNotificationBuilder builder) {
    builder.withText(LocalizeValue.localizeTODO(JavaCoreBundle.message("module.jdk.not.defined")));
    builder.withAction(LocalizeValue.localizeTODO(JavaCoreBundle.message("module.jdk.setup")), (e) ->
    {
      final Module moduleForPsiElement = ModuleUtilCore.findModuleForPsiElement(file);
      if (moduleForPsiElement == null) {
        return;
      }

      ShowSettingsUtil.getInstance().showProjectStructureDialog(project, projectStructureSelector -> {
        projectStructureSelector.select(moduleForPsiElement, true);
      });
    });
  }
}
