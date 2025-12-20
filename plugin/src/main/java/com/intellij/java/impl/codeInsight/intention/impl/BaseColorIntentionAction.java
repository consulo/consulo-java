/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.intention.impl;

import consulo.language.editor.intention.HighPriorityAction;
import consulo.language.editor.intention.PsiElementBaseIntentionAction;
import com.intellij.java.language.psi.*;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import static consulo.language.pattern.PlatformPatterns.psiElement;

/**
 * @author Danila Ponomarenko
 */
public abstract class BaseColorIntentionAction extends PsiElementBaseIntentionAction implements HighPriorityAction {
  protected static final String JAVA_AWT_COLOR = "java.awt.Color";

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) {
    if (!psiElement().inside(psiElement(PsiNewExpression.class)).accepts(element)) {
      return false;
    }

    PsiNewExpression expression = PsiTreeUtil.getParentOfType(element, PsiNewExpression.class, false);
    if (expression == null) {
      return false;
    }

    return isJavaAwtColor(expression.getClassOrAnonymousClassReference()) && isValueArguments(expression.getArgumentList());
  }

  private static boolean isJavaAwtColor(@Nullable PsiJavaCodeReferenceElement ref) {
    if (ref == null) {
      return false;
    }

    PsiReference reference = ref.getReference();
    if (reference == null) {
      return false;
    }

    PsiElement psiElement = reference.resolve();
    if (psiElement instanceof PsiClass && JAVA_AWT_COLOR.equals(((PsiClass)psiElement).getQualifiedName())) {
      return true;
    }

    return false;
  }

  private static boolean isValueArguments(@Nullable PsiExpressionList arguments) {
    if (arguments == null) {
      return false;
    }

    for (PsiExpression argument : arguments.getExpressions()) {
      if (argument instanceof PsiReferenceExpression) {
        return false;
      }
    }

    return true;
  }
}
