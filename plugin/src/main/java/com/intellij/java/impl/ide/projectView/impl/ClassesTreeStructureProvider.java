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
package com.intellij.java.impl.ide.projectView.impl;

import com.intellij.java.impl.ide.projectView.impl.nodes.ClassTreeNode;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.application.progress.ProgressManager;
import consulo.java.impl.ide.JavaModuleIconDescriptorUpdater;
import consulo.java.impl.ide.projectView.impl.JavaModuleRootTreeNode;
import consulo.java.impl.util.JavaProjectRootsUtil;
import consulo.language.file.FileViewProvider;
import consulo.language.psi.*;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.project.ui.view.tree.AbstractTreeNode;
import consulo.project.ui.view.tree.PsiFileNode;
import consulo.project.ui.view.tree.SelectableTreeStructureProvider;
import consulo.project.ui.view.tree.ViewSettings;
import consulo.ui.ex.tree.TreeHelper;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.inject.Inject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@ExtensionImpl
public class ClassesTreeStructureProvider implements SelectableTreeStructureProvider, DumbAware {
  private final Project myProject;

  @Inject
  public ClassesTreeStructureProvider(Project project) {
    myProject = project;
  }

  @Override
  @RequiredReadAction
  public Collection<AbstractTreeNode> modify(AbstractTreeNode parent, Collection<AbstractTreeNode> children, ViewSettings settings) {
    return TreeHelper.calculateYieldingToWriteAction(() -> doModify(children, settings));
  }

  @Nonnull
  private List<AbstractTreeNode> doModify(Collection<AbstractTreeNode> children, ViewSettings settings) {
    List<AbstractTreeNode> result = new ArrayList<>();
    for (final AbstractTreeNode child : children) {
      ProgressManager.checkCanceled();

      Object o = child.getValue();
      if (o instanceof PsiClassOwner/* && !(o instanceof JspFile)*/) {
        final PsiClassOwner classOwner = (PsiClassOwner) o;
        final VirtualFile file = classOwner.getVirtualFile();

        if (!(classOwner instanceof PsiCompiledElement)) {
          //do not show duplicated items if jar file contains classes and sources
          final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
          if (file != null && fileIndex.isInLibrarySource(file)) {
            final PsiElement originalElement = classOwner.getOriginalElement();
            if (originalElement instanceof PsiFile) {
              PsiFile classFile = (PsiFile) originalElement;
              final VirtualFile virtualClassFile = classFile.getVirtualFile();
              if (virtualClassFile != null && fileIndex.isInLibraryClasses(virtualClassFile) && classOwner.getManager().areElementsEquivalent(classOwner.getContainingDirectory(), classFile.getContainingDirectory())) {
                continue;
              }
            }
          }
        }

        if (fileInRoots(file)) {
          PsiClass[] classes = classOwner.getClasses();
          if (classes.length == 1 && !(classes[0] instanceof SyntheticElement) && file.getNameWithoutExtension().equals(classes[0].getName())) {
            result.add(new ClassTreeNode(myProject, classes[0], settings));
          } else {
            result.add(new PsiClassOwnerTreeNode(classOwner, settings));
          }
          continue;
        }
      } else if (o instanceof PsiDirectory && JavaModuleIconDescriptorUpdater.isModuleDirectory((PsiDirectory) o)) {
        result.add(new JavaModuleRootTreeNode(myProject, (PsiDirectory) o, settings));
      }

      result.add(child);
    }
    return result;
  }

  private boolean fileInRoots(@Nullable VirtualFile file) {
    return file != null && JavaProjectRootsUtil.isJavaSourceFile(myProject, file, true);
  }

  @Override
  @RequiredReadAction
  public PsiElement getTopLevelElement(final PsiElement element) {
    PsiFile baseRootFile = getBaseRootFile(element);
    if (baseRootFile == null) {
      return null;
    }

    if (!fileInRoots(baseRootFile.getVirtualFile())) {
      return baseRootFile;
    }

    PsiElement current = element;
    while (current != null) {

      if (isSelectable(current)) {
        break;
      }
      if (isTopLevelClass(current, baseRootFile)) {
        break;
      }

      current = current.getParent();
    }

    if (current instanceof PsiClassOwner) {
      PsiClass[] classes = ((PsiClassOwner) current).getClasses();
      if (classes.length == 1 && !(classes[0] instanceof SyntheticElement) && isTopLevelClass(classes[0], baseRootFile)) {
        current = classes[0];
      }
    }

    return current != null ? current : baseRootFile;
  }

  private boolean isSelectable(PsiElement element) {
    if (element instanceof PsiFileSystemItem) {
      return true;
    }

    if (element instanceof PsiField || element instanceof PsiClass || element instanceof PsiMethod) {
      return !(element.getParent() instanceof PsiAnonymousClass) && !(element instanceof PsiAnonymousClass);
    }

    return false;
  }

  @Nullable
  private static PsiFile getBaseRootFile(PsiElement element) {
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile == null) {
      return null;
    }

    final FileViewProvider viewProvider = containingFile.getViewProvider();
    return viewProvider.getPsi(viewProvider.getBaseLanguage());
  }

  @RequiredReadAction
  private static boolean isTopLevelClass(final PsiElement element, PsiFile baseRootFile) {
    if (!(element instanceof PsiClass)) {
      return false;
    }

    if (element instanceof PsiAnonymousClass) {
      return false;
    }

    final PsiFile parentFile = parentFileOf((PsiClass) element);
    // do not select JspClass
    return parentFile != null && parentFile.getLanguage() == baseRootFile.getLanguage();
  }

  @Nullable
  private static PsiFile parentFileOf(final PsiClass psiClass) {
    return psiClass.getContainingClass() == null ? psiClass.getContainingFile() : null;
  }

  private static class PsiClassOwnerTreeNode extends PsiFileNode {

    public PsiClassOwnerTreeNode(PsiClassOwner classOwner, ViewSettings settings) {
      super(classOwner.getProject(), classOwner, settings);
    }

    @Override
    public Collection<AbstractTreeNode> getChildrenImpl() {
      final ViewSettings settings = getSettings();
      final ArrayList<AbstractTreeNode> result = new ArrayList<>();
      for (PsiClass aClass : ((PsiClassOwner) getValue()).getClasses()) {
        if (!(aClass instanceof SyntheticElement)) {
          result.add(new ClassTreeNode(myProject, aClass, settings));
        }
      }
      return result;
    }

  }
}
