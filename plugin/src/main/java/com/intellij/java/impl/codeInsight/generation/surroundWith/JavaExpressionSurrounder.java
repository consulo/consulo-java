
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

package com.intellij.java.impl.codeInsight.generation.surroundWith;

import javax.annotation.Nonnull;

import consulo.language.editor.surroundWith.Surrounder;
import consulo.codeEditor.Editor;
import consulo.component.extension.ExtensionPointName;
import consulo.project.Project;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpression;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;

public abstract class JavaExpressionSurrounder implements Surrounder {
  public static ExtensionPointName<JavaExpressionSurrounder> EP_NAME = ExtensionPointName.create("consulo.java.expressionSurrounder");
  
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.surroundWith.SurroundExpressionHandler");

  @Override
  public boolean isApplicable(@Nonnull PsiElement[] elements) {
    LOG.assertTrue(elements.length == 1 && elements[0] instanceof PsiExpression);
    return isApplicable((PsiExpression)elements[0]);
  }

  public abstract boolean isApplicable(PsiExpression expr);

  @Override
  public TextRange surroundElements(@Nonnull Project project,
                                    @Nonnull Editor editor,
                                    @Nonnull PsiElement[] elements) throws IncorrectOperationException {
    return surroundExpression(project, editor, (PsiExpression)elements[0]);
  }

  public abstract TextRange surroundExpression(Project project, Editor editor, PsiExpression expr) throws IncorrectOperationException;
}