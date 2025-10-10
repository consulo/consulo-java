/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis;

import com.intellij.java.language.impl.refactoring.util.RefactoringChangeUtil;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiReferenceExpression;
import com.intellij.java.language.psi.PsiThisExpression;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

/**
* User: anna
*/
class QualifyWithThisFix implements SyntheticIntentionAction {
  private final PsiClass myContainingClass;
  private final PsiElement myExpression;

  public QualifyWithThisFix(PsiClass containingClass, PsiElement expression) {
    myContainingClass = containingClass;
    myExpression = expression;
  }

  @Nonnull
  @Override
  public LocalizeValue getText() {
    return LocalizeValue.localizeTODO("Qualify with " + myContainingClass.getName() + ".this");
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    return true;
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiThisExpression thisExpression =
      RefactoringChangeUtil.createThisExpression(PsiManager.getInstance(project), myContainingClass);
    ((PsiReferenceExpression)myExpression).setQualifierExpression(thisExpression);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
