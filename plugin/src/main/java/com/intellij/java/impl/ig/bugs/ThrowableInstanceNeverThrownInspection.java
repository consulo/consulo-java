/*
 * Copyright 2007-2011 Bas Leijdekkers
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
package com.intellij.java.impl.ig.bugs;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.query.Query;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class ThrowableInstanceNeverThrownInspection extends BaseInspection {

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.throwableInstanceNeverThrownDisplayName();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    PsiExpression expression = (PsiExpression)infos[0];
    String type = TypeUtils.expressionHasTypeOrSubtype(
      expression,
      CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION,
      CommonClassNames.JAVA_LANG_EXCEPTION,
      CommonClassNames.JAVA_LANG_ERROR
    );
    if (CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION.equals(type)) {
      return InspectionGadgetsLocalize.throwableInstanceNeverThrownRuntimeExceptionProblemDescriptor().get();
    }
    else if (CommonClassNames.JAVA_LANG_EXCEPTION.equals(type)) {
      return InspectionGadgetsLocalize.throwableInstanceNeverThrownCheckedExceptionProblemDescriptor().get();
    }
    else if (CommonClassNames.JAVA_LANG_ERROR.equals(type)) {
      return InspectionGadgetsLocalize.throwableInstanceNeverThrownErrorProblemDescriptor().get();
    }
    else {
      return InspectionGadgetsLocalize.throwableInstanceNeverThrownProblemDescriptor().get();
    }
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ExceptionInstanceNeverThrownVisitor();
  }

  private static class ExceptionInstanceNeverThrownVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(PsiNewExpression expression) {
      super.visitNewExpression(expression);
      if (!TypeUtils.expressionHasTypeOrSubtype(expression,
                                                CommonClassNames.JAVA_LANG_THROWABLE)) {
        return;
      }
      PsiElement parent = getParent(expression.getParent());
      if (parent instanceof PsiThrowStatement ||
          parent instanceof PsiReturnStatement) {
        return;
      }
      if (PsiTreeUtil.getParentOfType(parent, PsiCallExpression.class) !=
          null) {
        return;
      }
      PsiElement typedParent =
        PsiTreeUtil.getParentOfType(expression,
                                    PsiAssignmentExpression.class,
                                    PsiVariable.class);
      PsiLocalVariable variable;
      if (typedParent instanceof PsiAssignmentExpression) {
        PsiAssignmentExpression assignmentExpression =
          (PsiAssignmentExpression)typedParent;
        PsiExpression rhs = assignmentExpression.getRExpression();
        if (!PsiTreeUtil.isAncestor(rhs, expression, false)) {
          return;
        }
        PsiExpression lhs = assignmentExpression.getLExpression();
        if (!(lhs instanceof PsiReferenceExpression)) {
          return;
        }
        PsiReferenceExpression referenceExpression =
          (PsiReferenceExpression)lhs;
        PsiElement target = referenceExpression.resolve();
        if (!(target instanceof PsiLocalVariable)) {
          return;
        }
        variable = (PsiLocalVariable)target;
      }
      else if (typedParent instanceof PsiVariable) {
        if (!(typedParent instanceof PsiLocalVariable)) {
          return;
        }
        variable = (PsiLocalVariable)typedParent;
      }
      else {
        variable = null;
      }
      if (variable != null) {
        Query<PsiReference> query =
          ReferencesSearch.search(variable,
                                  variable.getUseScope());
        for (PsiReference reference : query) {
          PsiElement usage = reference.getElement();
          PsiElement usageParent = usage.getParent();
          while (usageParent instanceof PsiParenthesizedExpression) {
            usageParent = usageParent.getParent();
          }
          if (usageParent instanceof PsiThrowStatement ||
              usageParent instanceof PsiReturnStatement) {
            return;
          }
          if (PsiTreeUtil.getParentOfType(usageParent,
                                          PsiCallExpression.class) != null) {
            return;
          }
        }
      }
      registerError(expression, expression);
    }

    public static PsiElement getParent(PsiElement element) {
      PsiElement parent = element;
      while (parent instanceof PsiParenthesizedExpression ||
             parent instanceof PsiConditionalExpression ||
             parent instanceof PsiTypeCastExpression) {
        parent = parent.getParent();
      }
      PsiElement skipped = skipInitCause(parent);
      if (skipped != null) {
        return getParent(skipped);
      }
      return parent;
    }

    private static PsiElement skipInitCause(PsiElement parent) {
      if (!(parent instanceof PsiReferenceExpression)) {
        return null;
      }
      PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        return null;
      }
      PsiMethodCallExpression methodCallExpression =
        (PsiMethodCallExpression)grandParent;
      PsiReferenceExpression methodExpression =
        methodCallExpression.getMethodExpression();
      @NonNls String methodName =
        methodExpression.getReferenceName();
      if (!"initCause".equals(methodName)) {
        return null;
      }
      PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) {
        return null;
      }
      PsiParameterList parameterList = method.getParameterList();
      if (parameterList.getParametersCount() != 1) {
        return null;
      }
      PsiParameter[] parameters = parameterList.getParameters();
      PsiType type = parameters[0].getType();
      if (!type.equalsToText(CommonClassNames.JAVA_LANG_THROWABLE)) {
        return null;
      }
      return getParent(methodCallExpression.getParent());
    }
  }
}