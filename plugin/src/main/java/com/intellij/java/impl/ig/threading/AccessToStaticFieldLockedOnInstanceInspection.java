/*
 * Copyright 2006-2012 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.threading;

import com.intellij.java.impl.ig.ui.UiUtils;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.util.LinkedHashSet;
import java.util.Set;

@ExtensionImpl
public class AccessToStaticFieldLockedOnInstanceInspection extends BaseInspection {

  @SuppressWarnings("PublicField") public Set<String> ignoredClasses = new LinkedHashSet<>();

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.accessToStaticFieldLockedOnInstanceDisplayName().get();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.accessToStaticFieldLockedOnInstanceProblemDescriptor().get();
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return UiUtils.createTreeClassChooserList(ignoredClasses, "Ignored Classes", "Choose class to ignore");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AccessToStaticFieldLockedOnInstanceVisitor();
  }

  private class AccessToStaticFieldLockedOnInstanceVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReferenceExpression(@Nonnull PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      boolean isLockedOnInstance = false;
      boolean isLockedOnClass = false;
      final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
      if (containingMethod != null && containingMethod.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
        if (containingMethod.hasModifierProperty(PsiModifier.STATIC)) {
          isLockedOnClass = true;
        }
        else {
          isLockedOnInstance = true;
        }
      }
      final PsiClass expressionClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
      if (expressionClass == null) {
        return;
      }
      PsiElement elementToCheck = expression;
      while (true) {
        final PsiSynchronizedStatement synchronizedStatement = PsiTreeUtil.getParentOfType(elementToCheck, PsiSynchronizedStatement.class);
        if (synchronizedStatement == null || !PsiTreeUtil.isAncestor(expressionClass, synchronizedStatement, true)) {
          break;
        }
        final PsiExpression lockExpression = synchronizedStatement.getLockExpression();
        if (lockExpression instanceof PsiReferenceExpression) {
          final PsiReferenceExpression reference = (PsiReferenceExpression)lockExpression;
          final PsiElement target = reference.resolve();
          if (target instanceof PsiField) {
            final PsiField lockField = (PsiField)target;
            if (lockField.hasModifierProperty(PsiModifier.STATIC)) {
              isLockedOnClass = true;
            }
            else {
              isLockedOnInstance = true;
            }
          }
        }
        else if (lockExpression instanceof PsiThisExpression) {
          isLockedOnInstance = true;
        }
        else if (lockExpression instanceof PsiClassObjectAccessExpression) {
          isLockedOnClass = true;
        }
        elementToCheck = synchronizedStatement;
      }
      if (!isLockedOnInstance || isLockedOnClass) {
        return;
      }
      final PsiElement target = expression.resolve();
      if (!(target instanceof PsiField)) {
        return;
      }
      final PsiField lockedField = (PsiField)target;
      if (!lockedField.hasModifierProperty(PsiModifier.STATIC) || ExpressionUtils.isConstant(lockedField)) {
        return;
      }
      final PsiClass containingClass = lockedField.getContainingClass();
      if (!PsiTreeUtil.isAncestor(containingClass, expression, false)) {
        return;
      }
      if (!ignoredClasses.isEmpty()) {
        final PsiType type = lockedField.getType();
        if (type instanceof PsiClassType) {
          final PsiClassType classType = (PsiClassType)type;
          final PsiClass aClass = classType.resolve();
          if (aClass != null && ignoredClasses.contains(aClass.getQualifiedName())) {
            return;
          }
        }
      }
      registerError(expression);
    }
  }
}