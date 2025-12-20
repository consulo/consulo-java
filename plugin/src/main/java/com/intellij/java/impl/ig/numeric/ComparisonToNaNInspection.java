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
package com.intellij.java.impl.ig.numeric;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class ComparisonToNaNInspection extends BaseInspection {

  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.comparisonToNanDisplayName();
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    PsiBinaryExpression comparison = (PsiBinaryExpression)infos[0];
    return JavaTokenType.EQEQ.equals(comparison.getOperationTokenType())
      ? InspectionGadgetsLocalize.comparisonToNanProblemDescriptor1().get()
      : InspectionGadgetsLocalize.comparisonToNanProblemDescriptor2().get();
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ComparisonToNaNVisitor();
  }

  public InspectionGadgetsFix buildFix(Object... infos) {
    return new ComparisonToNaNFix();
  }

  private static class ComparisonToNaNFix extends InspectionGadgetsFix {

    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.comparisonToNanReplaceQuickfix();
    }

    public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      PsiReferenceExpression nanExpression = (PsiReferenceExpression)descriptor.getPsiElement();
      PsiElement target = nanExpression.resolve();
      if (!(target instanceof PsiField)) {
        return;
      }
      PsiField field = (PsiField)target;
      PsiClass containingClass = field.getContainingClass();
      if (containingClass == null) {
        return;
      }
      String typeText = containingClass.getQualifiedName();
      PsiBinaryExpression comparison = (PsiBinaryExpression)nanExpression.getParent();
      PsiExpression lhs = comparison.getLOperand();
      PsiExpression rhs = comparison.getROperand();
      PsiExpression operand;
      if (nanExpression.equals(lhs)) {
        operand = rhs;
      }
      else {
        operand = lhs;
      }
      assert operand != null;
      String operandText = operand.getText();
      IElementType tokenType = comparison.getOperationTokenType();
      String negationText;
      if (tokenType.equals(JavaTokenType.EQEQ)) {
        negationText = "";
      }
      else {
        negationText = "!";
      }
      @NonNls String newExpressionText = negationText + typeText + ".isNaN(" + operandText + ')';
      replaceExpression(comparison, newExpressionText);
    }
  }

  private static class ComparisonToNaNVisitor extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(@Nonnull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      if (!ComparisonUtils.isEqualityComparison(expression)) {
        return;
      }
      PsiExpression lhs = expression.getLOperand();
      PsiExpression rhs = expression.getROperand();
      if (rhs == null || !TypeUtils.hasFloatingPointType(lhs) && !TypeUtils.hasFloatingPointType(rhs)) {
        return;
      }
      if (isNaN(lhs)) {
        registerError(lhs, expression);
      }
      else if (isNaN(rhs)) {
        registerError(rhs, expression);
      }
    }

    private static boolean isNaN(PsiExpression expression) {
      if (!(expression instanceof PsiReferenceExpression)) {
        return false;
      }
      PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
      @NonNls String referenceName = referenceExpression.getReferenceName();
      if (!"NaN".equals(referenceName)) {
        return false;
      }
      PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiField)) {
        return false;
      }
      PsiField field = (PsiField)target;
      PsiClass containingClass = field.getContainingClass();
      if (containingClass == null) {
        return false;
      }
      String qualifiedName = containingClass.getQualifiedName();
      return CommonClassNames.JAVA_LANG_DOUBLE.equals(qualifiedName) || CommonClassNames.JAVA_LANG_FLOAT.equals(qualifiedName);
    }
  }
}