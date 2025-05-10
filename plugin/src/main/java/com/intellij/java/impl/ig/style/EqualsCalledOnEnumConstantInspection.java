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
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Nls;

@ExtensionImpl
public class EqualsCalledOnEnumConstantInspection extends BaseInspection {

  @Override
  @Nls
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.equalsCalledOnEnumConstantDisplayName().get();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.equalsCalledOnEnumConstantProblemDescriptor().get();
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiElement element = (PsiElement)infos[0];
    final PsiElement parent = element.getParent();
    if (parent instanceof PsiExpressionStatement) {
      return null;
    }
    return new EqualsCalledOnEnumValueFix();
  }

  private static class EqualsCalledOnEnumValueFix
    extends InspectionGadgetsFix {

    @Nonnull
    public String getName() {
      return InspectionGadgetsLocalize.equalsCalledOnEnumConstantQuickfix().get();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      if (parent == null) {
        return;
      }
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression =
        (PsiMethodCallExpression)grandParent;
      final PsiExpressionList argumentList =
        methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length > 1) {
        return;
      }
      final PsiReferenceExpression methodExpression =
        methodCallExpression.getMethodExpression();
      final PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      final StringBuilder newExpression = new StringBuilder();
      final PsiElement greatGrandParent = grandParent.getParent();
      final boolean not;
      final PsiPrefixExpression prefixExpression;
      if (greatGrandParent instanceof PsiPrefixExpression) {
        prefixExpression = (PsiPrefixExpression)greatGrandParent;
        final IElementType tokenType =
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
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (!TypeUtils.expressionHasTypeOrSubtype(qualifier, CommonClassNames.JAVA_LANG_ENUM)) {
        return;
      }
      registerMethodCallError(expression, expression);
    }
  }
}
