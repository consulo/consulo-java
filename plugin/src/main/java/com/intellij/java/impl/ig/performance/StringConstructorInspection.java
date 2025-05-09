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

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

@ExtensionImpl
public class StringConstructorInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean ignoreSubstringArguments = false;

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.stringConstructorDisplayName().get();
  }

  @Override
  @Nonnull
  public String getID() {
    return "RedundantStringConstructorCall";
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.stringConstructorProblemDescriptor().get();
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    LocalizeValue message = InspectionGadgetsLocalize.stringConstructorSubstringParameterOption();
    return new SingleCheckboxOptionsPanel(message.get(), this, "ignoreSubstringArguments");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringConstructorVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    final Boolean noArguments = (Boolean)infos[0];
    return new StringConstructorFix(noArguments.booleanValue());
  }

  private static class StringConstructorFix extends InspectionGadgetsFix {
    private final String m_name;

    private StringConstructorFix(boolean noArguments) {
      m_name = noArguments
        ? InspectionGadgetsLocalize.stringConstructorReplaceEmptyQuickfix().get()
        : InspectionGadgetsLocalize.stringConstructorReplaceArgQuickfix().get();
    }

    @Nonnull
    public String getName() {
      return m_name;
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiNewExpression expression =
        (PsiNewExpression)descriptor.getPsiElement();
      final PsiExpressionList argList = expression.getArgumentList();
      assert argList != null;
      final PsiExpression[] args = argList.getExpressions();
      final String argText;
      if (args.length == 1) {
        argText = args[0].getText();
      }
      else {
        argText = "\"\"";
      }
      replaceExpression(expression, argText);
    }
  }

  private class StringConstructorVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(
      @Nonnull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiType type = expression.getType();
      if (!TypeUtils.isJavaLangString(type)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length > 1) {
        return;
      }
      if (arguments.length == 1) {
        final PsiExpression argument = arguments[0];
        final PsiType parameterType = argument.getType();
        if (!TypeUtils.isJavaLangString(parameterType)) {
          return;
        }
        if (ignoreSubstringArguments &&
            hasSubstringArgument(argument)) {
          return;
        }
      }
      registerError(expression, Boolean.valueOf(arguments.length == 0));
    }

    private boolean hasSubstringArgument(PsiExpression argument) {
      if (!(argument instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiMethodCallExpression methodCallExpression =
        (PsiMethodCallExpression)argument;
      final PsiReferenceExpression methodExpression =
        methodCallExpression.getMethodExpression();
      final PsiElement element = methodExpression.resolve();
      if (!(element instanceof PsiMethod)) {
        return false;
      }
      final PsiMethod method = (PsiMethod)element;
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return true;
      }
      final String className = aClass.getQualifiedName();
      @NonNls final String methodName = method.getName();
      return CommonClassNames.JAVA_LANG_STRING.equals(className) && methodName.equals("substring");
    }
  }
}