/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.template.postfix.templates;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiType;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.impl.refactoring.introduceField.IntroduceFieldHandler;
import jakarta.annotation.Nonnull;

public class IntroduceFieldPostfixTemplate extends ExpressionPostfixTemplateWithChooser {
  public IntroduceFieldPostfixTemplate() {
    super("field", "myField = expr;");
  }

  @Override
  protected void doIt(@Nonnull Editor editor, @Nonnull PsiExpression expression) {
    IntroduceFieldHandler handler = ApplicationManager.getApplication().isUnitTestMode() ? getMockHandler(expression) : new IntroduceFieldHandler();
    handler.invoke(expression.getProject(), new PsiElement[]{expression}, null);
  }

  @Nonnull
  private static IntroduceFieldHandler getMockHandler(@Nonnull final PsiExpression expression) {
    final PsiClass containingClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
    assert containingClass != null;

    return new IntroduceFieldHandler() {
      // mock default settings
      @Override
      protected Settings showRefactoringDialog(Project project, Editor editor, PsiClass parentClass,
                                               PsiExpression expr, PsiType type, PsiExpression[] occurrences,
                                               PsiElement anchorElement, PsiElement anchorElementIfAll) {
        return new Settings(
          "foo", expression, PsiExpression.EMPTY_ARRAY, false, false, false,
          InitializationPlace.IN_CURRENT_METHOD, PsiModifier.PRIVATE, null,
          null, false, containingClass, false, false);
      }
    };
  }
}