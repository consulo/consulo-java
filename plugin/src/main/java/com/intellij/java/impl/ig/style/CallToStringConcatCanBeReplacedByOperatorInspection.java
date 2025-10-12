/*
 * Copyright 2007-2010 Bas Leijdekkers
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
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class CallToStringConcatCanBeReplacedByOperatorInspection
  extends BaseInspection {

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.callToStringConcatCanBeReplacedByOperatorDisplayName();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.callToStringConcatCanBeReplacedByOperatorProblemDescriptor().get();
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new CallToStringConcatCanBeReplacedByOperatorFix();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CallToStringConcatCanBeReplacedByOperatorVisitor();
  }

  private static class CallToStringConcatCanBeReplacedByOperatorFix
    extends InspectionGadgetsFix {

    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.callToStringConcatCanBeReplacedByOperatorQuickfix();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)parent;
      final PsiExpression qualifier =
        referenceExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      final PsiElement grandParent = referenceExpression.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression =
        (PsiMethodCallExpression)grandParent;
      final PsiExpressionList argumentList =
        methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiExpression argument = arguments[0];
      @NonNls
      final String newExpression =
        qualifier.getText() + '+' + argument.getText();
      replaceExpression(methodCallExpression, newExpression);
    }
  }

  private static class CallToStringConcatCanBeReplacedByOperatorVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final Project project = expression.getProject();
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      final PsiClass stringClass = psiFacade.findClass(CommonClassNames.JAVA_LANG_STRING, expression.getResolveScope());
      if (stringClass == null) {
        return;
      }
      final PsiClassType stringType =
        psiFacade.getElementFactory().createType(stringClass);
      if (!MethodCallUtils.isCallToMethod(expression, CommonClassNames.JAVA_LANG_STRING, stringType, "concat", stringType)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiExpressionStatement) {
        return;
      }
      registerMethodCallError(expression);
    }
  }
}