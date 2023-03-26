/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.assignment;

import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.*;
import consulo.language.ast.IElementType;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CollectionUtils;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import javax.swing.*;

@ExtensionImpl
public class AssignmentToCollectionFieldFromParameterInspection
  extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean ignorePrivateMethods = true;

  @Nonnull
  public String getID() {
    return "AssignmentToCollectionOrArrayFieldFromParameter";
  }

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "assignment.collection.array.field.from.parameter.display.name");
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    final PsiExpression rhs = (PsiExpression)infos[0];
    final PsiField field = (PsiField)infos[1];
    final PsiType type = field.getType();
    if (type instanceof PsiArrayType) {
      return InspectionGadgetsBundle.message(
        "assignment.collection.array.field.from.parameter.problem.descriptor.array",
        rhs.getText());
    }
    else {
      return InspectionGadgetsBundle.message(
        "assignment.collection.array.field.from.parameter.problem.descriptor.collection",
        rhs.getText());
    }
  }

  @Nullable
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(
      InspectionGadgetsBundle.message(
        "assignment.collection.array.field.option"), this,
      "ignorePrivateMethods");
  }

  public BaseInspectionVisitor buildVisitor() {
    return new AssignmentToCollectionFieldFromParameterVisitor();
  }

  private class AssignmentToCollectionFieldFromParameterVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitAssignmentExpression(@Nonnull
                                          PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      final PsiExpression rhs = expression.getRExpression();
      if (!(rhs instanceof PsiReferenceExpression)) {
        return;
      }
      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.EQ)) {
        return;
      }
      final PsiExpression lhs = expression.getLExpression();
      if (!(lhs instanceof PsiReferenceExpression)) {
        return;
      }
      if (ignorePrivateMethods) {
        final PsiMethod containingMethod =
          PsiTreeUtil.getParentOfType(expression,
                                      PsiMethod.class);
        if (containingMethod == null ||
            containingMethod.hasModifierProperty(
              PsiModifier.PRIVATE)) {
          return;
        }
      }
      final PsiElement element = ((PsiReference)rhs).resolve();
      if (!(element instanceof PsiParameter)) {
        return;
      }
      if (!(element.getParent() instanceof PsiParameterList)) {
        return;
      }
      final PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)lhs;
      final PsiElement referent = referenceExpression.resolve();
      if (!(referent instanceof PsiField)) {
        return;
      }
      final PsiField field = (PsiField)referent;
      if (!CollectionUtils.isArrayOrCollectionField(field)) {
        return;
      }
      registerError(lhs, rhs, field);
    }
  }
}