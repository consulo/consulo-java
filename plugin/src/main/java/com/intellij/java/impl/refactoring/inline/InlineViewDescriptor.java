
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
package com.intellij.java.impl.refactoring.inline;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiVariable;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.psi.PsiElement;
import consulo.usage.UsageViewBundle;
import consulo.usage.UsageViewDescriptor;
import jakarta.annotation.Nonnull;

class InlineViewDescriptor implements UsageViewDescriptor{

  private final PsiElement myElement;

  public InlineViewDescriptor(PsiElement element) {
    myElement = element;
  }

  @Nonnull
  public PsiElement[] getElements() {
    return new PsiElement[] {myElement};
  }

  public String getProcessedElementsHeader() {
    if (myElement instanceof PsiField) {
      return RefactoringLocalize.inlineFieldElementsHeader().get();
    }
    if (myElement instanceof PsiVariable) {
      return RefactoringLocalize.inlineVarsElementsHeader().get();
    }
    if (myElement instanceof PsiClass) {
      return RefactoringLocalize.inlineClassElementsHeader().get();
    }
    if (myElement instanceof PsiMethod) {
      return RefactoringLocalize.inlineMethodElementsHeader().get();
    }
    return "Unknown element";
  }

  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringLocalize.invocationsToBeInlined(UsageViewBundle.getReferencesString(usagesCount, filesCount)).get();
  }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return RefactoringLocalize.commentsElementsHeader(UsageViewBundle.getOccurencesString(usagesCount, filesCount)).get();
  }
}
