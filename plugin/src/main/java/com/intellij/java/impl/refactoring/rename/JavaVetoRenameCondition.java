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

import com.intellij.java.impl.lang.java.JavaRefactoringSupportProvider;
import com.intellij.java.language.psi.PsiJavaFile;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.impl.util.JavaProjectRootsUtil;
import consulo.language.editor.refactoring.rename.VetoRenameCondition;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;

@ExtensionImpl
public class JavaVetoRenameCondition implements VetoRenameCondition {

  @RequiredReadAction
  @Override
  public boolean isVetoed(PsiElement element) {
    return JavaRefactoringSupportProvider.isDisableRefactoringForLightElement(element) ||
      element instanceof PsiJavaFile &&
        //  !JspPsiUtil.isInJspFile(element) &&
        !JavaProjectRootsUtil.isOutsideSourceRoot((PsiFile)element) &&
        ((PsiJavaFile)element).getClasses().length > 0;
  }
}
