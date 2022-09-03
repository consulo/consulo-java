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

/*
 * User: anna
 * Date: 21-Mar-2008
 */
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import java.util.Collection;

import javax.annotation.Nonnull;

import com.intellij.java.language.psi.*;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nullable;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.PsiElementBaseIntentionAction;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import com.intellij.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.ide.impl.idea.util.ArrayUtilRt;
import consulo.language.util.IncorrectOperationException;
import consulo.language.editor.TargetElementUtil;

public class SurroundWithArrayFix extends PsiElementBaseIntentionAction {
  private final PsiCall myMethodCall;
  @Nullable private final PsiExpression myExpression;

  public SurroundWithArrayFix(@Nullable PsiCall methodCall, @Nullable PsiExpression expression) {
    myMethodCall = methodCall;
    myExpression = expression;
  }

  @Override
  @Nonnull
  public String getText() {
    return "Surround with array initialization";
  }

  @Override
  @Nonnull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@Nonnull final Project project, final Editor editor, @Nonnull final PsiElement element) {
    return getExpression(element) != null;
  }

  @Nullable
  protected PsiExpression getExpression(PsiElement element) {
    if (myMethodCall == null || !myMethodCall.isValid()) {
      return myExpression == null || !myExpression.isValid() ? null : myExpression;
    }
    final PsiElement method = myMethodCall.resolveMethod();
    if (method != null) {
      final PsiMethod psiMethod = (PsiMethod)method;
      return checkMethod(element, psiMethod);
    } else if (myMethodCall instanceof PsiMethodCallExpression){
      final Collection<PsiElement> psiElements = TargetElementUtil.getTargetCandidates(((PsiMethodCallExpression) myMethodCall).getMethodExpression());
      for (PsiElement psiElement : psiElements) {
        if (psiElement instanceof PsiMethod) {
          final PsiExpression expression = checkMethod(element, (PsiMethod)psiElement);
          if (expression != null) return expression;
        }
      }
    }
    return null;
  }

  @Nullable
  private PsiExpression checkMethod(final PsiElement element, final PsiMethod psiMethod) {
    final PsiParameter[] psiParameters = psiMethod.getParameterList().getParameters();
    final PsiExpressionList argumentList = myMethodCall.getArgumentList();
    int idx = 0;
    for (PsiExpression expression : argumentList.getExpressions()) {
      if (element != null && PsiTreeUtil.isAncestor(expression, element, false)) {
        if (psiParameters.length > idx) {
          final PsiType paramType = psiParameters[idx].getType();
          if (paramType instanceof PsiArrayType) {
            final PsiType expressionType = expression.getType();
            if (expressionType != null) {
              final PsiType componentType = ((PsiArrayType)paramType).getComponentType();
              if (expressionType.isAssignableFrom(componentType)) {
                return expression;
              }
              final PsiClass psiClass = PsiUtil.resolveClassInType(componentType);
              if (ArrayUtilRt.find(psiMethod.getTypeParameters(), psiClass) != -1) {
                for (PsiClassType superType : psiClass.getSuperTypes()) {
                  if (TypeConversionUtil.isAssignable(superType, expressionType)) return expression;
                }
              }
            }
          }
        }
      }
      idx++;
    }
    return null;
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    final PsiExpression expression = getExpression(element);
    assert expression != null;
    final PsiExpression toReplace = elementFactory.createExpressionFromText(getArrayCreation(expression), element);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(expression.replace(toReplace));
  }

  @NonNls
  private static String getArrayCreation(@Nonnull PsiExpression expression) {
    final PsiType expressionType = expression.getType();
    assert expressionType != null;
    return "new " + expressionType.getCanonicalText() + "[]{" + expression.getText()+ "}";
  }
}