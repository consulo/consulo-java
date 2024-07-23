/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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

import com.intellij.java.impl.ig.psiutils.VariableSearchUtils;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;

@ExtensionImpl
public class UnnecessaryThisInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreAssignments = false;

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.unnecessaryThisDisplayName().get();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.unnecessaryThisProblemDescriptor().get();
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    LocalizeValue message = InspectionGadgetsLocalize.unnecessaryThisIgnoreAssignmentsOption();
    return new SingleCheckboxOptionsPanel(message.get(), this, "ignoreAssignments");
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new UnnecessaryThisFix();
  }

  private static class UnnecessaryThisFix extends InspectionGadgetsFix {

    @Nonnull
    public String getName() {
      return InspectionGadgetsLocalize.unnecessaryThisRemoveQuickfix().get();
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement thisToken = descriptor.getPsiElement();
      final PsiReferenceExpression thisExpression =
        (PsiReferenceExpression)thisToken.getParent();
      assert thisExpression != null;
      final String newExpression = thisExpression.getReferenceName();
      if (newExpression == null) {
        return;
      }
      replaceExpression(thisExpression, newExpression);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryThisVisitor();
  }

  private class UnnecessaryThisVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReferenceExpression(@Nonnull PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      final PsiReferenceParameterList parameterList = expression.getParameterList();
      if (parameterList == null) {
        return;
      }
      if (parameterList.getTypeArguments().length > 0) {
        return;
      }
      final PsiExpression qualifierExpression = expression.getQualifierExpression();
      if (!(qualifierExpression instanceof PsiThisExpression)) {
        return;
      }
      final PsiThisExpression thisExpression = (PsiThisExpression)qualifierExpression;
      final PsiJavaCodeReferenceElement qualifier = thisExpression.getQualifier();
      final String referenceName = expression.getReferenceName();
      if (referenceName == null) {
        return;
      }
      if (ignoreAssignments && PsiUtil.isAccessedForWriting(expression)) {
        return;
      }
      final PsiElement parent = expression.getParent();
      if (qualifier == null) {
        if (parent instanceof PsiCallExpression) {
          // method calls are always in error
          registerError(qualifierExpression, ProblemHighlightType.LIKE_UNUSED_SYMBOL);
          return;
        }
        final PsiElement target = expression.resolve();
        if (!(target instanceof PsiVariable)) {
          return;
        }
        final PsiVariable variable = (PsiVariable)target;
        if (!VariableSearchUtils.variableNameResolvesToTarget(referenceName, variable, expression)) {
          return;
        }
        registerError(thisExpression, ProblemHighlightType.LIKE_UNUSED_SYMBOL);
      }
      else {
        final String qualifierName = qualifier.getReferenceName();
        if (qualifierName == null) {
          return;
        }
        if (parent instanceof PsiCallExpression) {
          final PsiCallExpression callExpression = (PsiCallExpression)parent;
          final PsiMethod calledMethod = callExpression.resolveMethod();
          if (calledMethod == null) {
            return;
          }
          final String methodName = calledMethod.getName();
          PsiClass parentClass = ClassUtils.getContainingClass(expression);
          final Project project = expression.getProject();
          final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
          final PsiResolveHelper resolveHelper = psiFacade.getResolveHelper();
          while (parentClass != null) {
            if (qualifierName.equals(parentClass.getName())) {
              registerError(thisExpression, ProblemHighlightType.LIKE_UNUSED_SYMBOL);
            }
            final PsiMethod[] methods = parentClass.findMethodsByName(methodName, true);
            for (PsiMethod method : methods) {
              final PsiClass containingClass = method.getContainingClass();
              if (resolveHelper.isAccessible(method, expression, containingClass)) {
                if (method.hasModifierProperty(PsiModifier.PRIVATE) && !PsiTreeUtil.isAncestor(containingClass, expression, true)) {
                  continue;
                }
                return;
              }
            }
            parentClass = ClassUtils.getContainingClass(parentClass);
          }
        }
        else {
          final PsiElement target = expression.resolve();
          if (!(target instanceof PsiVariable)) {
            return;
          }
          final PsiVariable variable = (PsiVariable)target;
          if (!VariableSearchUtils.variableNameResolvesToTarget(referenceName, variable, expression)) {
            return;
          }
          PsiClass parentClass = ClassUtils.getContainingClass(expression);
          while (parentClass != null) {
            if (qualifierName.equals(parentClass.getName())) {
              registerError(thisExpression, ProblemHighlightType.LIKE_UNUSED_SYMBOL);
            }
            final PsiField field = parentClass.findFieldByName(referenceName, true);
            if (field != null) {
              return;
            }
            parentClass = ClassUtils.getContainingClass(parentClass);
          }
        }
      }
    }
  }
}