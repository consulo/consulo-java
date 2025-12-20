/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.style;

import com.intellij.java.impl.ig.fixes.IntroduceVariableFix;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.MultipleCheckboxOptionsPanel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;

@ExtensionImpl
public class ChainedMethodCallInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean m_ignoreFieldInitializations = true;

  @SuppressWarnings("PublicField")
  public boolean m_ignoreThisSuperCalls = true;

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.chainedMethodCallDisplayName();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.chainedMethodCallProblemDescriptor().get();
  }

  @Override
  public JComponent createOptionsPanel() {
    MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionGadgetsLocalize.chainedMethodCallIgnoreOption().get(), "m_ignoreFieldInitializations");
    panel.addCheckbox(InspectionGadgetsLocalize.chainedMethodCallIgnoreThisSuperOption().get(), "m_ignoreThisSuperCalls");
    return panel;
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ChainedMethodCallVisitor();
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new IntroduceVariableFix(false) {
      @Nullable
      @Override
      public PsiExpression getExpressionToExtract(PsiElement element) {
        PsiElement parent = element.getParent();
        if (!(parent instanceof PsiReferenceExpression)) {
          return null;
        }
        PsiReferenceExpression methodExpression = (PsiReferenceExpression)parent;
        return methodExpression.getQualifierExpression();
      }
    };
  }

  private class ChainedMethodCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      PsiReferenceExpression reference = expression.getMethodExpression();
      PsiExpression qualifier = reference.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      if (!isCallExpression(qualifier)) {
        return;
      }
      if (m_ignoreFieldInitializations) {
        PsiElement field = PsiTreeUtil.getParentOfType(expression, PsiField.class);
        if (field != null) {
          return;
        }
      }
      if (m_ignoreThisSuperCalls) {
        PsiExpressionList expressionList = PsiTreeUtil.getParentOfType(expression, PsiExpressionList.class);
        if (expressionList != null) {
          PsiElement parent = expressionList.getParent();
          if (ExpressionUtils.isConstructorInvocation(parent)) {
            return;
          }
        }
      }
      registerMethodCallError(expression);
    }

    private boolean isCallExpression(PsiExpression expression) {
      expression = ParenthesesUtils.stripParentheses(expression);
      return expression instanceof PsiMethodCallExpression || expression instanceof PsiNewExpression;
    }
  }
}