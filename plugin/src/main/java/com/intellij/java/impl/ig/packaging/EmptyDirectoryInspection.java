/*
 * Copyright 2011 Bas Leijdekkers
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
package com.intellij.java.impl.ig.packaging;

import com.intellij.java.impl.ig.BaseGlobalInspection;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.application.util.function.Computable;
import consulo.content.ContentIterator;
import consulo.content.scope.SearchScope;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
import consulo.language.editor.inspection.CommonProblemDescriptor;
import consulo.language.editor.inspection.GlobalInspectionContext;
import consulo.language.editor.inspection.ProblemDescriptionsProcessor;
import consulo.language.editor.inspection.QuickFix;
import consulo.language.editor.inspection.reference.RefElement;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.scope.AnalysisScope;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.localize.LocalizeValue;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Nls;

import javax.swing.*;

public abstract class EmptyDirectoryInspection extends BaseGlobalInspection {

  @SuppressWarnings("PublicField")
  public boolean onlyReportDirectoriesUnderSourceRoots = false;

  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsLocalize.emptyDirectoryDisplayName().get();
  }

  @Override
  public JComponent createOptionsPanel() {
    LocalizeValue message = InspectionGadgetsLocalize.emptyDirectoriesOnlyUnderSourceRootsOption();
    return new SingleCheckboxOptionsPanel(message.get(), this, "onlyReportDirectoriesUnderSourceRoots");
  }

  @Override
  public void runInspection(
    final AnalysisScope scope,
    @Nonnull final InspectionManager manager,
    final GlobalInspectionContext context,
    @Nonnull final ProblemDescriptionsProcessor processor,
    @Nonnull Object state
  ) {
    final Project project = context.getProject();
    final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
    final SearchScope searchScope = scope.toSearchScope();
    if (!(searchScope instanceof GlobalSearchScope)) {
      return;
    }
    final GlobalSearchScope globalSearchScope = (GlobalSearchScope)searchScope;
    final PsiManager psiManager = PsiManager.getInstance(project);
    index.iterateContent(new ContentIterator() {
      @Override
      public boolean processFile(final VirtualFile fileOrDir) {
        if (!fileOrDir.isDirectory()) {
          return true;
        }
        if (!globalSearchScope.contains(fileOrDir)) {
          return true;
        }
        if (onlyReportDirectoriesUnderSourceRoots && !index.isInSourceContent(fileOrDir)) {
          return true;
        }
        final VirtualFile[] children = fileOrDir.getChildren();
        if (children.length != 0) {
          return true;
        }
        final Application application = ApplicationManager.getApplication();
        final PsiDirectory directory = application.runReadAction(
          new Computable<PsiDirectory>() {
            @Override
            public PsiDirectory compute() {
              return psiManager.findDirectory(fileOrDir);
            }
          });
        final RefElement refDirectory = context.getRefManager().getReference(directory);
        if (context.shouldCheck(refDirectory, EmptyDirectoryInspection.this)) {
          return true;
        }
        final String relativePath = getPathRelativeToModule(fileOrDir, project);
        if (relativePath == null) {
          return true;
        }
        processor.addProblemElement(
          refDirectory,
          manager.createProblemDescriptor(
            InspectionGadgetsLocalize.emptyDirectoriesProblemDescriptor(relativePath).get(),
            new EmptyPackageFix(fileOrDir.getUrl(), fileOrDir.getName())
          )
        );
        return true;
      }
    });
  }

  @Nullable
  private static String getPathRelativeToModule(VirtualFile file, Project project) {
    final ProjectRootManager rootManager = ProjectRootManager.getInstance(project);
    final Application application = ApplicationManager.getApplication();
    final VirtualFile[] contentRoots = application.runReadAction(
      new Computable<VirtualFile[]>() {
        @Override
        public VirtualFile[] compute() {
          return rootManager.getContentRootsFromAllModules();
        }
      });
    for (VirtualFile otherRoot : contentRoots) {
      if (VfsUtilCore.isAncestor(otherRoot, file, false)) {
        return VfsUtilCore.getRelativePath(file, otherRoot, '/');
      }
    }
    return null;
  }

  private static class EmptyPackageFix implements QuickFix {

    private final String url;
    private final String name;

    public EmptyPackageFix(String url, String name) {
      this.url = url;
      this.name = name;
    }

    @Nonnull
    @Override
    public String getName() {
      return InspectionGadgetsLocalize.emptyDirectoriesDeleteQuickfix(name).get();
    }

    @Nonnull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@Nonnull Project project, @Nonnull CommonProblemDescriptor descriptor) {
      final VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
      if (file == null) {
        return;
      }
      final PsiManager psiManager = PsiManager.getInstance(project);
      final PsiDirectory directory = psiManager.findDirectory(file);
      if (directory == null) {
        return;
      }
      directory.delete();
    }
  }
}
