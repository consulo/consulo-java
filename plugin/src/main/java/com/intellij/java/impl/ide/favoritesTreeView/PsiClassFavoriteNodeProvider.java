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

/*
 * User: anna
 * Date: 21-Jan-2008
 */
package com.intellij.java.impl.ide.favoritesTreeView;

import com.intellij.java.impl.ide.favoritesTreeView.smartPointerPsiNodes.ClassSmartPointerNode;
import com.intellij.java.impl.ide.projectView.impl.nodes.ClassTreeNode;
import com.intellij.java.language.impl.psi.presentation.java.ClassPresentationUtil;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.bookmark.ui.view.BookmarkNodeProvider;
import consulo.dataContext.DataContext;
import consulo.language.content.FileIndexFacade;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.project.Project;
import consulo.project.ui.view.tree.AbstractTreeNode;
import consulo.project.ui.view.tree.ViewSettings;
import consulo.util.lang.Comparing;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collection;

@ExtensionImpl
public class PsiClassFavoriteNodeProvider implements BookmarkNodeProvider {
  @Override
  public Collection<AbstractTreeNode> getFavoriteNodes(DataContext context, ViewSettings viewSettings) {
    Project project = context.getData(Project.KEY);
    if (project == null) return null;
    PsiElement[] elements = context.getData(PsiElement.KEY_OF_ARRAY);
    if (elements == null) {
      PsiElement element = context.getData(PsiElement.KEY);
      if (element != null) {
        elements = new PsiElement[]{element};
      }
    }
    if (elements != null) {
      Collection<AbstractTreeNode> result = new ArrayList<>();
      for (PsiElement element : elements) {
        if (element instanceof PsiClass && checkClassUnderSources(element, project)) {
          result.add(new ClassSmartPointerNode(project, element, viewSettings));
        }
      }
      return result.isEmpty() ? null : result;
    }
    return null;
  }

  private boolean checkClassUnderSources(PsiElement element, Project project) {
    PsiFile file = element.getContainingFile();
    if (file != null && file.getVirtualFile() != null) {
      FileIndexFacade indexFacade = FileIndexFacade.getInstance(project);
      VirtualFile vf = file.getVirtualFile();
      return indexFacade.isInSource(vf) || indexFacade.isInSourceContent(vf);
    }
    return false;
  }

  @Override
  public AbstractTreeNode createNode(Project project, Object element, ViewSettings viewSettings) {
    if (element instanceof PsiClass psiClass && checkClassUnderSources(psiClass, project)) {
      return new ClassSmartPointerNode(project, element, viewSettings);
    }
    return BookmarkNodeProvider.super.createNode(project, element, viewSettings);
  }

  @Override
  public boolean elementContainsFile(Object element, VirtualFile vFile) {
    if (element instanceof PsiClass psiClass) {
      PsiFile file = psiClass.getContainingFile();
      if (file != null && Comparing.equal(file.getVirtualFile(), vFile)) return true;
    }
    return false;
  }

  @Override
  public int getElementWeight(Object value, boolean isSortByType) {
    if (value instanceof PsiClass psiClass) {
      return isSortByType ? ClassTreeNode.getClassPosition(psiClass) : 3;
    }

    return -1;
  }

  @Override
  public String getElementLocation(Object element) {
    return element instanceof PsiClass psiClass ? ClassPresentationUtil.getNameForClass(psiClass, true) : null;
  }

  @Override
  public boolean isInvalidElement(Object element) {
    return element instanceof PsiClass psiClass && !psiClass.isValid();
  }

  @Override
  @Nonnull
  public String getFavoriteTypeId() {
    return "class";
  }

  @Override
  public String getElementUrl(Object element) {
    return element instanceof PsiClass aClass ? aClass.getQualifiedName() : null;
  }

  @Override
  @RequiredReadAction
  public String getElementModuleName(Object element) {
    if (element instanceof PsiClass) {
      PsiClass aClass = (PsiClass) element;
      Module module = ModuleUtilCore.findModuleForPsiElement(aClass);
      return module != null ? module.getName() : null;
    }
    return null;
  }

  @Override
  @RequiredReadAction
  public Object[] createPathFromUrl(Project project, String url, String moduleName) {
    GlobalSearchScope scope = null;
    if (moduleName != null) {
      Module module = ModuleManager.getInstance(project).findModuleByName(moduleName);
      if (module != null) {
        scope = GlobalSearchScope.moduleScope(module);
      }
    }
    if (scope == null) {
      scope = GlobalSearchScope.allScope(project);
    }
    PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(url, scope);
    if (aClass == null) return null;
    return new Object[]{aClass};
  }
}