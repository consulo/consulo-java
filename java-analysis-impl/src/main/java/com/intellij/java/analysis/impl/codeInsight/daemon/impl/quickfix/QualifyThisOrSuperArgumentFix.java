/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Nov 13, 2002
 * Time: 3:26:50 PM
 * To change this template use Options | File Templates.
 */
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiExpression;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

public abstract class QualifyThisOrSuperArgumentFix implements SyntheticIntentionAction {
  protected static final Logger LOG = Logger.getInstance(QualifyThisOrSuperArgumentFix.class);
  protected final PsiExpression myExpression;
  protected final PsiClass myPsiClass;
  private LocalizeValue myText = LocalizeValue.of();


  public QualifyThisOrSuperArgumentFix(@Nonnull PsiExpression expression, @Nonnull PsiClass psiClass) {
    myExpression = expression;
    myPsiClass = psiClass;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Nonnull
  @Override
  public LocalizeValue getText() {
    return myText;
  }

  protected abstract String getQualifierText();

  protected abstract PsiExpression getQualifier(PsiManager manager);

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    if (!myExpression.isValid()) {
      return false;
    }
    if (!myPsiClass.isValid()) {
      return false;
    }
    myText = LocalizeValue.localizeTODO("Qualify " + getQualifierText() + " expression with \'" + myPsiClass.getQualifiedName() + "\'");
    return true;
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    myExpression.replace(getQualifier(PsiManager.getInstance(project)));
  }
}
