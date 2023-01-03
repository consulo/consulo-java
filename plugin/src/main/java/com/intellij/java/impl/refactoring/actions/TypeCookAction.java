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
package com.intellij.java.impl.refactoring.actions;

import consulo.dataContext.DataManager;
import com.intellij.java.impl.refactoring.typeCook.TypeCookHandler;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.language.Language;
import consulo.language.editor.CommonDataKeys;
import consulo.dataContext.DataContext;
import consulo.project.Project;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.action.BaseRefactoringAction;

import javax.annotation.Nonnull;

public class TypeCookAction extends BaseRefactoringAction {

  @Override
  protected boolean isAvailableInEditorOnly() {
    return false;
  }

  @Override
  public boolean isAvailableForLanguage(Language language) {
    return language.equals(JavaLanguage.INSTANCE);
  }

  @Override
  public boolean isEnabledOnElements(@Nonnull PsiElement[] elements) {
    Project project = DataManager.getInstance().getDataContext().getData(CommonDataKeys.PROJECT);

    if (project == null) {
      return false;
    }

    for (int i = 0; i < elements.length; i++) {
      PsiElement element = elements[i];

      if (!(element instanceof PsiClass || element instanceof PsiJavaFile || element instanceof PsiDirectory || element instanceof PsiJavaPackage)) {
        return false;
      }
    }

    return true;
  }

  @Override
  public RefactoringActionHandler getHandler(@Nonnull DataContext dataContext) {
    return getHandler();
  }

  public RefactoringActionHandler getHandler() {
    return new TypeCookHandler();
  }
}
