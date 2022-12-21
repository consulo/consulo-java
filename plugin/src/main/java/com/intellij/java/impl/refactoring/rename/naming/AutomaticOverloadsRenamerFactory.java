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

/*
 * User: anna
 * Date: 12-Jan-2010
 */
package com.intellij.java.impl.refactoring.rename.naming;

import com.intellij.java.impl.refactoring.JavaRefactoringSettings;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.editor.refactoring.rename.AutomaticRenamer;
import consulo.language.editor.refactoring.rename.AutomaticRenamerFactory;
import consulo.usage.UsageInfo;
import consulo.java.impl.refactoring.JavaRefactoringBundle;

import java.util.Collection;

@ExtensionImpl
public class AutomaticOverloadsRenamerFactory implements AutomaticRenamerFactory {
  @Override
  public boolean isApplicable(PsiElement element) {
    if (element instanceof PsiMethod && !((PsiMethod) element).isConstructor()) {
      final PsiClass containingClass = ((PsiMethod) element).getContainingClass();
      return containingClass != null && containingClass.findMethodsByName(((PsiMethod) element).getName(),
          false).length > 1;
    }
    return false;
  }

  @Override
  public String getOptionName() {
    return JavaRefactoringBundle.message("rename.overloads");
  }

  @Override
  public boolean isEnabled() {
    return JavaRefactoringSettings.getInstance().isRenameOverloads();
  }

  @Override
  public void setEnabled(boolean enabled) {
    JavaRefactoringSettings.getInstance().setRenameOverloads(enabled);
  }

  @Override
  public AutomaticRenamer createRenamer(PsiElement element, String newName, Collection<UsageInfo> usages) {
    return new AutomaticOverloadsRenamer((PsiMethod) element, newName);
  }
}