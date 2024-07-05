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

/**
 * created at Sep 17, 2001
 * @author Jeka
 */
package com.intellij.java.impl.refactoring.changeSignature;

import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.util.lang.StringUtil;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.usage.UsageViewBundle;
import consulo.usage.UsageViewDescriptor;
import consulo.usage.UsageViewUtil;
import jakarta.annotation.Nonnull;

public class ChangeSignatureViewDescriptor implements UsageViewDescriptor {

  private final PsiMethod myMethod;
  private final String myProcessedElementsHeader;

  public ChangeSignatureViewDescriptor(PsiMethod method) {
    myMethod = method;
    myProcessedElementsHeader = StringUtil.capitalize(RefactoringLocalize.zeroToChangeSignature(UsageViewUtil.getType(method)).get());
  }

  @Nonnull
  public PsiElement[] getElements() {
    return new PsiElement[] {myMethod};
  }

  public String getProcessedElementsHeader() {
    return myProcessedElementsHeader;
  }

  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringLocalize.referencesToBeChanged(UsageViewBundle.getReferencesString(usagesCount, filesCount)).get();
  }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return null;
  }

}
