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

import com.intellij.java.analysis.impl.codeInspection.reference.RefMethodImpl;
import com.intellij.java.impl.ide.favoritesTreeView.smartPointerPsiNodes.MethodSmartPointerNode;
import com.intellij.java.language.impl.psi.presentation.java.ClassPresentationUtil;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.bookmark.ui.view.BookmarkNodeProvider;
import consulo.dataContext.DataContext;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.project.Project;
import consulo.project.ui.view.tree.AbstractTreeNode;
import consulo.project.ui.view.tree.ViewSettings;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collection;

@ExtensionImpl
public class PsiMethodFavoriteNodeProvider implements BookmarkNodeProvider {
  @Override
  public Collection<AbstractTreeNode> getFavoriteNodes(final DataContext context, final ViewSettings viewSettings) {
    final Project project = context.getData(Project.KEY);
    if (project == null) {
      return null;
    }
    PsiElement[] elements = context.getData(PsiElement.KEY_OF_ARRAY);
    if (elements == null) {
      final PsiElement element = context.getData(PsiElement.KEY);
      if (element != null) {
        elements = new PsiElement[]{element};
      }
    }
    if (elements != null) {
      final Collection<AbstractTreeNode> result = new ArrayList<>();
      for (PsiElement element : elements) {
        if (element instanceof PsiMethod) {
          result.add(new MethodSmartPointerNode(project, element, viewSettings));
        }
      }
      return result.isEmpty() ? null : result;
    }
    return null;
  }

  @Override
  public AbstractTreeNode createNode(final Project project, final Object element, final ViewSettings viewSettings) {
    return element instanceof PsiMethod
      ? new MethodSmartPointerNode(project, element, viewSettings)
      : BookmarkNodeProvider.super.createNode(project, element, viewSettings);
  }

  @Override
  public boolean elementContainsFile(final Object element, final VirtualFile vFile) {
    return false;
  }

  @Override
  public int getElementWeight(final Object value, final boolean isSortByType) {
    if (value instanceof PsiMethod) {
      return 5;
    }
    return -1;
  }

  @Override
  public String getElementLocation(final Object element) {
    if (element instanceof PsiMethod method) {
      final PsiClass parent = method.getContainingClass();
      if (parent != null) {
        return ClassPresentationUtil.getNameForClass(parent, true);
      }
    }
    return null;
  }

  @Override
  public boolean isInvalidElement(final Object element) {
    return element instanceof PsiMethod method && !method.isValid();
  }

  @Override
  @Nonnull
  public String getFavoriteTypeId() {
    return "method";
  }

  @Override
  public String getElementUrl(final Object element) {
    return element instanceof PsiMethod method ? PsiFormatUtil.getExternalName(method) : null;
  }

  @Override
  @RequiredReadAction
  public String getElementModuleName(final Object element) {
    if (element instanceof PsiMethod aMethod) {
      Module module = ModuleUtilCore.findModuleForPsiElement(aMethod);
      return module != null ? module.getName() : null;
    }
    return null;
  }

  @Override
  public Object[] createPathFromUrl(final Project project, final String url, final String moduleName) {
    final PsiMethod method = RefMethodImpl.findPsiMethod(PsiManager.getInstance(project), url);
    if (method == null) {
      return null;
    }
    return new Object[]{method};
  }
}