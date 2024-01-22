/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.impl.ide.hierarchy.type;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassOwner;
import com.intellij.java.language.psi.PsiSyntheticClass;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.dataContext.DataContext;
import consulo.ide.impl.idea.ide.hierarchy.TypeHierarchyBrowserBase;
import consulo.language.Language;
import consulo.language.editor.*;
import consulo.language.editor.hierarchy.HierarchyBrowser;
import consulo.language.editor.hierarchy.TypeHierarchyProvider;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;

import jakarta.annotation.Nonnull;
import java.util.Set;

/**
 * @author yole
 */
@ExtensionImpl
public class JavaTypeHierarchyProvider implements TypeHierarchyProvider {
  @Override
  @RequiredUIAccess
  public PsiElement getTarget(@Nonnull final DataContext dataContext) {
    final Project project = dataContext.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return null;
    }

    final Editor editor = dataContext.getData(PlatformDataKeys.EDITOR);
    if (editor != null) {
      final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file == null) {
        return null;
      }

      final PsiElement targetElement = TargetElementUtil.findTargetElement(editor, Set.of(TargetElementUtilExtender
          .ELEMENT_NAME_ACCEPTED, TargetElementUtilExtender.REFERENCED_ELEMENT_ACCEPTED, TargetElementUtilExtender.LOOKUP_ITEM_ACCEPTED));
      if (targetElement instanceof PsiClass) {
        return targetElement;
      }

      final int offset = editor.getCaretModel().getOffset();
      PsiElement element = file.findElementAt(offset);
      while (element != null) {
        if (element instanceof PsiFile) {
          if (!(element instanceof PsiClassOwner)) {
            return null;
          }
          final PsiClass[] classes = ((PsiClassOwner) element).getClasses();
          return classes.length == 1 ? classes[0] : null;
        }
        if (element instanceof PsiClass && !(element instanceof PsiAnonymousClass) && !(element instanceof PsiSyntheticClass)) {
          return element;
        }
        element = element.getParent();
      }

      return null;
    } else {
      final PsiElement element = dataContext.getData(LangDataKeys.PSI_ELEMENT);
      return element instanceof PsiClass ? (PsiClass) element : null;
    }
  }

  @Override
  @Nonnull
  public HierarchyBrowser createHierarchyBrowser(final PsiElement target) {
    return new TypeHierarchyBrowser(target.getProject(), (PsiClass) target);
  }

  @Override
  public void browserActivated(@Nonnull final HierarchyBrowser hierarchyBrowser) {
    final TypeHierarchyBrowser browser = (TypeHierarchyBrowser) hierarchyBrowser;
    final String typeName = browser.isInterface() ? TypeHierarchyBrowserBase.SUBTYPES_HIERARCHY_TYPE : TypeHierarchyBrowserBase
        .TYPE_HIERARCHY_TYPE;
    browser.changeView(typeName);
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
