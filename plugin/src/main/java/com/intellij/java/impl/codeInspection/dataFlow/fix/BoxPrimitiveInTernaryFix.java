// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.codeInspection.dataFlow.fix;

import consulo.localize.LocalizeValue;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.java.impl.JavaBundle;
import consulo.project.Project;
import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiConditionalExpression;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiPrimitiveType;
import com.intellij.java.language.psi.PsiType;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.impl.ig.jdk.AutoBoxingInspection;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import org.jspecify.annotations.Nullable;

import java.util.Objects;


public class BoxPrimitiveInTernaryFix implements LocalQuickFix {
  private final SmartPsiElementPointer<PsiExpression> myPointer;

  private BoxPrimitiveInTernaryFix(PsiExpression expression) {
    myPointer = SmartPointerManager.createPointer(expression);
  }

  public static @Nullable BoxPrimitiveInTernaryFix makeFix(@Nullable PsiExpression npeExpression) {
    if (npeExpression == null) return null;
    PsiConditionalExpression parentConditional = getParentConditional(npeExpression);
    if (parentConditional == null) return null;
    PsiType type = Objects.requireNonNull(parentConditional.getType());
    PsiType expectedType = ExpectedTypeUtils.findExpectedType(parentConditional, false);
    if (expectedType instanceof PsiClassType && expectedType.isAssignableFrom(type)) {
      return new BoxPrimitiveInTernaryFix(npeExpression);
    }
    return null;
  }

  private static PsiConditionalExpression getParentConditional(PsiExpression expression) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (!(parent instanceof PsiConditionalExpression conditional)) return null;
    PsiType type = conditional.getType();
    if (type == null) return null;
    PsiExpression thenExpression = conditional.getThenExpression();
    if (thenExpression == null) return null;
    PsiType thenType = thenExpression.getType();
    if (thenType == null) return null;
    PsiExpression elseExpression = conditional.getElseExpression();
    if (elseExpression == null) return null;
    PsiType elseType = elseExpression.getType();
    if (elseType == null) return null;
    if (elseType instanceof PsiPrimitiveType && thenType instanceof PsiClassType) {
      if (PsiTreeUtil.isAncestor(thenExpression, expression, false) && thenType.isAssignableFrom(elseType)) return conditional;
    }
    else if (elseType instanceof PsiClassType && thenType instanceof PsiPrimitiveType) {
      if (PsiTreeUtil.isAncestor(elseExpression, expression, false) && elseType.isAssignableFrom(thenType)) return conditional;
    }
    return null;
  }

  @Override
  public LocalizeValue getName() {
    return LocalizeValue.of(JavaBundle.message("intention.family.name.box.primitive.in.conditional.branch"));
  }

  @Override
  public void applyFix(Project project, ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getStartElement();
    PsiExpression expression = myPointer.getElement();
    if (expression == null) return;
        PsiConditionalExpression conditional = getParentConditional(expression);
    if (conditional == null) return;
    PsiExpression thenExpression = conditional.getThenExpression();
    if (thenExpression != null) {
      AutoBoxingInspection.replaceWithBoxing(thenExpression);
    }
    PsiExpression elseExpression = conditional.getElseExpression();
    if (elseExpression != null) {
      AutoBoxingInspection.replaceWithBoxing(elseExpression);
    }
  }
}
