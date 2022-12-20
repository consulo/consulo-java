/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.performance;

import javax.annotation.Nonnull;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import com.intellij.java.language.psi.*;
import consulo.project.Project;
import consulo.language.util.IncorrectOperationException;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import consulo.java.language.module.util.JavaClassNames;

@ExtensionImpl
public class StringToStringInspection extends BaseInspection {

  @Override
  @Nonnull
  public String getID() {
    return "RedundantStringToString";
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("string.to.string.display.name");
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "string.to.string.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringToStringVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new StringToStringFix();
  }

  private static class StringToStringFix extends InspectionGadgetsFix {

    @Nonnull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "constant.conditional.expression.simplify.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiMethodCallExpression call =
        (PsiMethodCallExpression)descriptor.getPsiElement();
      final PsiReferenceExpression expression =
        call.getMethodExpression();
      final PsiExpression qualifier = expression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      final String qualifierText = qualifier.getText();
      replaceExpression(call, qualifierText);
    }
  }

  private static class StringToStringVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @Nonnull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.TO_STRING.equals(methodName)) {
        return;
      }

      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      if (parameterList.getParametersCount() != 0) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      final String className = aClass.getQualifiedName();
      if (!JavaClassNames.JAVA_LANG_STRING.equals(className)) {
        return;
      }
      registerError(expression);
    }
  }
}
