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

package com.intellij.java.impl.refactoring.rename;

import com.intellij.java.impl.psi.impl.beanProperties.BeanProperty;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.util.PropertyUtil;
import consulo.codeEditor.Editor;
import consulo.dataContext.DataContext;
import consulo.language.editor.refactoring.RefactoringFactory;
import consulo.language.editor.refactoring.RenameRefactoring;
import consulo.language.editor.refactoring.rename.RenameDialog;
import consulo.language.editor.refactoring.rename.RenameHandler;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.ui.ex.awt.DialogWrapper;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Dmitry Avdeev
 */
public abstract class BeanPropertyRenameHandler implements RenameHandler {

  public boolean isAvailableOnDataContext(DataContext dataContext) {
    return false;
  }

  public boolean isRenaming(DataContext dataContext) {
    return getProperty(dataContext) != null;
  }

  public void invoke(@Nonnull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    performInvoke(editor, dataContext);
  }

  public void invoke(@Nonnull Project project, @Nonnull PsiElement[] elements, DataContext dataContext) {
    performInvoke(null, dataContext);
  }

  private void performInvoke(@Nullable Editor editor, DataContext dataContext) {
    final BeanProperty property = getProperty(dataContext);
    new PropertyRenameDialog(property, editor).show();
  }

  public static void doRename(@Nonnull final BeanProperty property, final String newName, final boolean searchInComments, boolean isPreview) {
    final PsiElement psiElement = property.getPsiElement();
    final RenameRefactoring rename = RefactoringFactory.getInstance(psiElement.getProject()).createRename(psiElement, newName, searchInComments, false);
    rename.setPreviewUsages(isPreview);

    final PsiMethod setter = property.getSetter();
    if (setter != null) {
      final String setterName = PropertyUtil.suggestSetterName(newName);
      rename.addElement(setter, setterName);
    }

    final PsiMethod getter = property.getGetter();
    if (getter != null) {
      final String getterName = PropertyUtil.suggestGetterName(newName, getter.getReturnType());
      rename.addElement(getter, getterName);
    }

    rename.run();
  }

  @Nullable
  protected abstract BeanProperty getProperty(DataContext context);

  private static class PropertyRenameDialog extends RenameDialog {

    private final BeanProperty myProperty;

    protected PropertyRenameDialog(BeanProperty property, final Editor editor) {
      super(property.getMethod().getProject(), property.getPsiElement(), null, editor);
      myProperty = property;
    }

    protected void doAction() {
      final String newName = getNewName();
      final boolean searchInComments = isSearchInComments();
      doRename(myProperty, newName, searchInComments, isPreviewUsages());
      close(DialogWrapper.OK_EXIT_CODE);
    }
  }
}
