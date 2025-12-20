/*
 * Copyright 2008 Bas Leijdekkers
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

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.MethodCallUtils;
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
import org.jetbrains.annotations.Nls;

@ExtensionImpl
public class EqualsCalledOnEnumConstantInspection extends BaseInspection {

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.equalsCalledOnEnumConstantDisplayName();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.equalsCalledOnEnumConstantProblemDescriptor().get();
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    PsiElement element = (PsiElement)infos[0];
    PsiElement parent = element.getParent();
    if (parent instanceof PsiExpressionStatement) {
      return null;
    }
    return new EqualsCalledOnEnumValueFix();
  }

  private static class EqualsCalledOnEnumValueFix
    extends InspectionGadgetsFix {

    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.equalsCalledOnEnumConstantQuickfix();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      PsiElement element = descriptor.getPsiElement();
      PsiElement parent = element.getParent();
      if (parent == null) {
        return;
      }
      PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        return;
      }
      PsiMethodCallExpression methodCallExpression =
        (PsiMethodCallExpression)grandParent;
      PsiExpressionList argumentList =
        methodCallExpression.getArgumentList();
      PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length > 1) {
        return;
      }
      PsiReferenceExpression methodExpression =
        methodCallExpression.getMethodExpression();
      PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      StringBuilder newExpression = new StringBuilder();
      PsiElement greatGrandParent = grandParent.getParent();
      boolean not;
      PsiPrefixExpression prefixExpression;
      if (greatGrandParent instanceof PsiPrefixExpression) {
        prefixExpression = (PsiPrefixExpression)greatGrandParent;
        IElementType tokenType =
          prefixExpression.getOperationTokenType();
        not = JavaTokenType.EXCL == tokenType;
      }
      else {
        prefixExpression = null;
        not = false;
      }
      newExpression.append(qualifier.getText());
      if (not) {
        newExpression.append("!=");
      }
      else {
        newExpression.append("==");
      }
      if (arguments.length == 1) {
        newExpression.append(arguments[0].getText());
      }
      if (not) {
        replaceExpression(prefixExpression, newExpression.toString());
      }
      else {
        replaceExpression(methodCallExpression,
                          newExpression.toString());
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new EqualsCalledOnEnumValueVisitor();
  }

  private static class EqualsCalledOnEnumValueVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @Nonnull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!MethodCallUtils.isEqualsCall(expression)) {
        return;
      }
      PsiReferenceExpression methodExpression = expression.getMethodExpression();
      PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (!TypeUtils.expressionHasTypeOrSubtype(qualifier, CommonClassNames.JAVA_LANG_ENUM)) {
        return;
      }
      registerMethodCallError(expression, expression);
    }
  }
}
