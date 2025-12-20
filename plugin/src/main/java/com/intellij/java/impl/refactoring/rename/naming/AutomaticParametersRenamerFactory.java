/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiParameter;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.rename.AutomaticRenamer;
import consulo.language.editor.refactoring.rename.AutomaticRenamerFactory;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.usage.UsageInfo;

import java.util.Collection;

@ExtensionImpl
public class AutomaticParametersRenamerFactory implements AutomaticRenamerFactory {
  public boolean isApplicable(PsiElement element) {
    if (element instanceof PsiParameter) {
      PsiElement declarationScope = ((PsiParameter) element).getDeclarationScope();
      if (declarationScope instanceof PsiMethod && !((PsiMethod) declarationScope).hasModifierProperty(PsiModifier.STATIC)) {
        return true;
      }
    }
    return false;
  }

  public LocalizeValue getOptionName() {
    return RefactoringLocalize.renameParametersHierarchy();
  }

  public boolean isEnabled() {
    return JavaRefactoringSettings.getInstance().isRenameParameterInHierarchy();
  }

  public void setEnabled(boolean enabled) {
    JavaRefactoringSettings.getInstance().setRenameParameterInHierarchy(enabled);
  }

  public AutomaticRenamer createRenamer(PsiElement element, String newName, Collection<UsageInfo> usages) {
    return new AutomaticParametersRenamer((PsiParameter) element, newName);
  }
}