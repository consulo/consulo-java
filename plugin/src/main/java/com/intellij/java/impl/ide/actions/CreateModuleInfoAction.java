/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.impl.ide.actions;

import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.impl.psi.impl.light.AutomaticJavaModule;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.dataContext.DataContext;
import consulo.fileTemplate.AttributesDefaults;
import consulo.fileTemplate.FileTemplate;
import consulo.fileTemplate.FileTemplateManager;
import consulo.ide.IdeView;
import consulo.ide.action.CreateFromTemplateActionBase;
import consulo.java.impl.JavaBundle;
import consulo.language.content.LanguageContentFolderScopes;
import consulo.language.editor.LangDataKeys;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.FilenameIndex;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.ui.ex.action.AnActionEvent;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static com.intellij.java.language.impl.codeInsight.template.JavaTemplateUtil.INTERNAL_MODULE_INFO_TEMPLATE_NAME;
import static com.intellij.java.language.psi.PsiJavaModule.MODULE_INFO_CLASS;
import static com.intellij.java.language.psi.PsiJavaModule.MODULE_INFO_FILE;

public class CreateModuleInfoAction extends CreateFromTemplateActionBase {
  public CreateModuleInfoAction() {
    super(JavaBundle.message("action.create.new.module-info.title"), JavaBundle.message("action.create.new.module-info.description"), JavaFileType.INSTANCE.getIcon());
  }

  @Override
  public void update(@Nonnull AnActionEvent e) {
    if (!e.getPresentation().isVisible()) {
      return;
    }
    DataContext ctx = e.getDataContext();
    boolean available = Optional.ofNullable(ctx.getData(IdeView.KEY)).map(view -> getTargetDirectory(ctx, view)).filter(PsiUtil::isLanguageLevel9OrHigher).map
        (ModuleUtilCore::findModuleForPsiElement).map(module -> FilenameIndex.getVirtualFilesByName(module.getProject(), MODULE_INFO_FILE, GlobalSearchScope.moduleScope(module)).isEmpty()).orElse(false);
    e.getPresentation().setEnabledAndVisible(available);
  }

  @Nullable
  @Override
  protected PsiDirectory getTargetDirectory(DataContext ctx, IdeView view) {
    PsiDirectory[] directories = view.getDirectories();
    if (directories.length == 1) {
      PsiDirectory psiDir = directories[0];
      VirtualFile vDir = psiDir.getVirtualFile();
      ProjectFileIndex index = ProjectRootManager.getInstance(psiDir.getProject()).getFileIndex();
      if (vDir.equals(index.getSourceRootForFile(vDir)) && index.isUnderContentFolderType(vDir, LanguageContentFolderScopes.onlyProduction())) {
        return psiDir;
      }
    }

    return null;
  }

  @Override
  protected FileTemplate getTemplate(@Nonnull Project project, @Nonnull PsiDirectory dir) {
    return FileTemplateManager.getInstance(project).getInternalTemplate(INTERNAL_MODULE_INFO_TEMPLATE_NAME);
  }

  @Override
  protected AttributesDefaults getAttributesDefaults(@Nonnull DataContext ctx) {
    return new AttributesDefaults(MODULE_INFO_CLASS).withFixedName(true);
  }

  @Override
  protected Map<String, String> getLiveTemplateDefaults(@jakarta.annotation.Nonnull DataContext ctx, @Nonnull PsiFile file) {
    Module module = ctx.getData(LangDataKeys.MODULE);
    return Collections.singletonMap("MODULE_NAME", module != null ? AutomaticJavaModule.moduleName(module.getName()) : "module_name");
  }
}