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
 *
 * @author Jeka
 */
package com.intellij.java.impl.refactoring.move.moveClassesOrPackages;

import consulo.ide.impl.idea.openapi.util.text.StringUtil;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.psi.PsiElement;
import consulo.usage.UsageViewBundle;
import consulo.usage.UsageViewDescriptor;
import consulo.usage.UsageViewUtil;
import jakarta.annotation.Nonnull;

public class MoveMultipleElementsViewDescriptor implements UsageViewDescriptor {
  private final PsiElement[] myPsiElements;
  private String myProcessedElementsHeader;
  private final String myCodeReferencesText;

  public MoveMultipleElementsViewDescriptor(PsiElement[] psiElements,
                                            String targetName) {
    myPsiElements = psiElements;
    if (psiElements.length == 1) {
      myProcessedElementsHeader = StringUtil.capitalize(
        RefactoringLocalize.moveSingleElementElementsHeader(UsageViewUtil.getType(psiElements[0]), targetName).get()
      );
      myCodeReferencesText =
        RefactoringLocalize.referencesInCodeTo01(UsageViewUtil.getType(psiElements[0]), UsageViewUtil.getLongName(psiElements[0])).get();
    } else {
      if (psiElements.length > 0) {
        myProcessedElementsHeader = StringUtil.capitalize(RefactoringLocalize.moveSingleElementElementsHeader(
          StringUtil.pluralize(UsageViewUtil.getType(psiElements[0])), targetName
        ).get());
      }
      myCodeReferencesText = RefactoringLocalize.referencesFoundInCode().get();
    }
  }

  @Override
  @Nonnull
  public PsiElement[] getElements() {
    return myPsiElements;
  }

  @Override
  public String getProcessedElementsHeader() {
    return myProcessedElementsHeader;
  }

  @Override
  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return myCodeReferencesText + UsageViewBundle.getReferencesString(usagesCount, filesCount);
  }

  @Override
  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return RefactoringLocalize.commentsElementsHeader(UsageViewBundle.getOccurencesString(usagesCount, filesCount)).get();
  }
}
