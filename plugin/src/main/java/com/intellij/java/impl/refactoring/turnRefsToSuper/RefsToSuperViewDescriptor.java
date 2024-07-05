
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
package com.intellij.java.impl.refactoring.turnRefsToSuper;

import com.intellij.java.language.psi.PsiClass;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.psi.PsiElement;
import consulo.usage.UsageViewBundle;
import consulo.usage.UsageViewDescriptor;
import jakarta.annotation.Nonnull;

class RefsToSuperViewDescriptor implements UsageViewDescriptor {
  private final PsiClass myClass;
  private final PsiClass mySuper;

  public RefsToSuperViewDescriptor(
    PsiClass aClass,
    PsiClass anInterface
  ) {
    myClass = aClass;
    mySuper = anInterface;
  }

  @Nonnull
  public PsiElement[] getElements() {
    return new PsiElement[] {myClass, mySuper};
  }

  public String getProcessedElementsHeader() {
    return null;
  }

  public String getCodeReferencesText(int usagesCount, int filesCount) {
    StringBuilder buffer = new StringBuilder();
    buffer.append(RefactoringLocalize.referencesTo0ToBeReplacedWithReferencesTo1(myClass.getName(), mySuper.getName()).get());
    buffer.append(" ");
    buffer.append(UsageViewBundle.getReferencesString(usagesCount, filesCount));
    return buffer.toString();
  }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return null;
  }

}
