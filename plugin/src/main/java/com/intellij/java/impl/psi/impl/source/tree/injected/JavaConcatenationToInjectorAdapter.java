// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.psi.impl.source.tree.injected;

import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.inject.BaseConcatenation2InjectorAdapter;
import consulo.language.inject.MultiHostInjector;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.util.lang.Pair;
import jakarta.inject.Inject;

import javax.annotation.Nonnull;

@ExtensionImpl(order = "last")
public class JavaConcatenationToInjectorAdapter extends BaseConcatenation2InjectorAdapter implements MultiHostInjector {
  @Inject
  public JavaConcatenationToInjectorAdapter(@Nonnull Project project) {
    super(project);
  }

  @Override
  public Pair<PsiElement, PsiElement[]> computeAnchorAndOperands(@Nonnull PsiElement context) {
    PsiElement element = context;
    PsiElement parent = context.getParent();
    while (parent instanceof PsiPolyadicExpression && ((PsiPolyadicExpression)parent).getOperationTokenType() == JavaTokenType.PLUS
      || parent instanceof PsiAssignmentExpression && ((PsiAssignmentExpression)parent).getOperationTokenType() == JavaTokenType.PLUSEQ
      || parent instanceof PsiConditionalExpression && ((PsiConditionalExpression)parent).getCondition() != element
      || parent instanceof PsiTypeCastExpression
      || parent instanceof PsiParenthesizedExpression) {
      element = parent;
      parent = parent.getParent();
    }

    PsiElement[] operands;
    PsiElement anchor;
    if (element instanceof PsiPolyadicExpression) {
      operands = ((PsiPolyadicExpression)element).getOperands();
      anchor = element;
    }
    else if (element instanceof PsiAssignmentExpression) {
      PsiExpression rExpression = ((PsiAssignmentExpression)element).getRExpression();
      operands = new PsiElement[]{rExpression == null ? element : rExpression};
      anchor = element;
    }
    else {
      operands = new PsiElement[]{context};
      anchor = context;
    }

    return Pair.create(anchor, operands);
  }

  @Nonnull
  @Override
  public Class<? extends PsiElement> getElementClass() {
    return PsiLiteralExpression.class;
  }
}
