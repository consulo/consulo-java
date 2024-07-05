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

import com.intellij.java.impl.refactoring.util.ConflictsUtil;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.rename.UnresolvableCollisionUsageInfo;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.psi.PsiElement;

/**
 * @author dsl
 */
public class ClassHidesUnqualifiableClassUsageInfo extends UnresolvableCollisionUsageInfo {
  private final PsiClass myHiddenClass;

  public ClassHidesUnqualifiableClassUsageInfo(PsiJavaCodeReferenceElement element, PsiClass renamedClass, PsiClass hiddenClass) {
    super(element, renamedClass);
    myHiddenClass = hiddenClass;
  }

  public String getDescription() {
    final PsiElement container = ConflictsUtil.getContainer(myHiddenClass);
    return RefactoringLocalize.renamedClassWillHide0In1(
      RefactoringUIUtil.getDescription(myHiddenClass, false),
      RefactoringUIUtil.getDescription(container, false)
    ).get();
  }
}
