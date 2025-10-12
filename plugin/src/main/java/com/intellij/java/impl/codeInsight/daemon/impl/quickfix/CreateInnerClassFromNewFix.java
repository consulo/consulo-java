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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;

/**
 * @author yole
 */
public class CreateInnerClassFromNewFix extends CreateClassFromNewFix {
  private static final Logger LOG = Logger.getInstance(CreateInnerClassFromNewFix.class);

  public CreateInnerClassFromNewFix(final PsiNewExpression expr) {
    super(expr);
  }

  @Override
  public LocalizeValue getText(String varName) {
    return JavaQuickFixLocalize.createInnerClassFromUsageText(StringUtil.capitalize(CreateClassKind.CLASS.getDescription()), varName);
  }

  @Override
  protected boolean isAllowOuterTargetClass() {
    return true;
  }

  @Override
  protected void invokeImpl(final PsiClass targetClass) {
    PsiNewExpression newExpression = getNewExpression();
    PsiJavaCodeReferenceElement ref = newExpression.getClassOrAnonymousClassReference();
    assert ref != null;
    String refName = ref.getReferenceName();
    LOG.assertTrue(refName != null);
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(newExpression.getProject()).getElementFactory();
    PsiClass created = elementFactory.createClass(refName);
    final PsiModifierList modifierList = created.getModifierList();
    LOG.assertTrue(modifierList != null);
    modifierList.setModifierProperty(PsiModifier.PRIVATE, true);
    if (PsiUtil.getEnclosingStaticElement(newExpression, targetClass) != null || isInThisOrSuperCall(newExpression)) {
      modifierList.setModifierProperty(PsiModifier.STATIC, true);
    }
    created = (PsiClass)targetClass.add(created);

    setupClassFromNewExpression(created, newExpression);

    setupGenericParameters(created, ref);
  }

  private static boolean isInThisOrSuperCall(PsiNewExpression newExpression) {
    boolean inFirstConstructorLine = false;
    final PsiExpressionStatement expressionStatement = PsiTreeUtil.getParentOfType(newExpression, PsiExpressionStatement.class);
    if (expressionStatement != null) {
      final PsiExpression expression = expressionStatement.getExpression();
      if (expression instanceof PsiMethodCallExpression) {
        final PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)expression).getMethodExpression();
        final PsiElement resolve = methodExpression.resolve();
        if (resolve instanceof PsiMethod && ((PsiMethod)resolve).isConstructor()) {
          final PsiElement referenceNameElement = methodExpression.getReferenceNameElement();
          if (referenceNameElement != null) {
            if (Comparing.strEqual(referenceNameElement.getText(), PsiKeyword.THIS) ||
                Comparing.strEqual(referenceNameElement.getText(), PsiKeyword.SUPER)) {
              inFirstConstructorLine = true;
            }
          }
        }
      }
    }
    return inFirstConstructorLine;
  }
}