// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.refactoring;

import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.AddNewArrayExpressionFix;
import com.intellij.java.language.impl.psi.PsiDiamondTypeImpl;
import com.intellij.java.language.impl.psi.impl.PsiDiamondTypeUtil;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.psiutils.CommentTracker;
import consulo.language.psi.PsiElement;

import java.util.Objects;

public class IntroduceVariableUtil {
  /**
   * Ensure that diamond inside initializer is expanded, then replace variable type with var
   */
  public static PsiElement expandDiamondsAndReplaceExplicitTypeWithVar(PsiTypeElement typeElement, PsiElement context) {
    PsiElement parent = typeElement.getParent();
    if (parent instanceof PsiVariable) {
      PsiExpression copyVariableInitializer = ((PsiVariable)parent).getInitializer();
      if (copyVariableInitializer instanceof PsiNewExpression) {
        final PsiDiamondType.DiamondInferenceResult diamondResolveResult =
          PsiDiamondTypeImpl.resolveInferredTypesNoCheck((PsiNewExpression)copyVariableInitializer, copyVariableInitializer);
        if (!diamondResolveResult.getInferredTypes().isEmpty()) {
          PsiDiamondTypeUtil.expandTopLevelDiamondsInside(copyVariableInitializer);
        }
      }
      else if (copyVariableInitializer instanceof PsiArrayInitializerExpression initializer) {
        AddNewArrayExpressionFix.doFix(initializer);
      }
      else if (copyVariableInitializer instanceof PsiFunctionalExpression) {
        PsiTypeCastExpression castExpression =
          (PsiTypeCastExpression)JavaPsiFacade.getElementFactory(copyVariableInitializer.getProject())
                                              .createExpressionFromText("(" + typeElement.getText() + ")a", copyVariableInitializer);
        Objects.requireNonNull(castExpression.getOperand()).replace(copyVariableInitializer);
        copyVariableInitializer.replace(castExpression);
      }
    }

    return new CommentTracker().replaceAndRestoreComments(typeElement,
                                                          JavaPsiFacade.getElementFactory(context.getProject())
                                                                       .createTypeElementFromText("var", context));
  }
}
