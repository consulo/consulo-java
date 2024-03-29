// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection;

import com.intellij.java.language.impl.psi.impl.PsiDiamondTypeUtil;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.psiutils.CommentTracker;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;

public class RemoveRedundantTypeArgumentsUtil {
  private static final Logger LOG = Logger.getInstance(RemoveRedundantTypeArgumentsUtil.class);

  public static PsiElement replaceExplicitWithDiamond(PsiElement psiElement) {
    PsiElement replacement = PsiDiamondTypeUtil.createExplicitReplacement(psiElement);
    return replacement == null ? psiElement : psiElement.replace(replacement);
  }

  /**
   * Removes redundant type arguments which appear in any descendants of the supplied element.
   *
   * @param element element to start the replacement from
   */
  public static void removeRedundantTypeArguments(PsiElement element) {
    for (PsiNewExpression newExpression : PsiTreeUtil.collectElementsOfType(element, PsiNewExpression.class)) {
      PsiJavaCodeReferenceElement classReference = newExpression.getClassOrAnonymousClassReference();
      if (classReference != null && PsiDiamondTypeUtil.canCollapseToDiamond(newExpression, newExpression, null)) {
        replaceExplicitWithDiamond(classReference.getParameterList());
      }
    }
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());
    for (PsiMethodCallExpression call : PsiTreeUtil.collectElementsOfType(element, PsiMethodCallExpression.class)) {
      PsiType[] arguments = call.getTypeArguments();
      if (arguments.length == 0) {
        continue;
      }
      PsiMethod method = call.resolveMethod();
      if (method != null) {
        PsiTypeParameter[] parameters = method.getTypeParameters();
        if (arguments.length == parameters.length && PsiDiamondTypeUtil.areTypeArgumentsRedundant(arguments, call, false, method, parameters)) {
          PsiMethodCallExpression expr = (PsiMethodCallExpression) factory.createExpressionFromText("foo()", null);
          new CommentTracker().replaceAndRestoreComments(call.getTypeArgumentList(), expr.getTypeArgumentList());
        }
      }
    }
  }
}
