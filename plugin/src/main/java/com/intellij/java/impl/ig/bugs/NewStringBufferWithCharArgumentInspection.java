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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import consulo.language.editor.inspection.ProblemDescriptor;
import com.intellij.java.language.psi.*;
import consulo.project.Project;
import consulo.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.language.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import consulo.java.language.module.util.JavaClassNames;
import org.jetbrains.annotations.Nls;

public class NewStringBufferWithCharArgumentInspection extends BaseInspection {

  @Override
  @Nls
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "new.string.buffer.with.char.argument.display.name");
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "new.string.buffer.with.char.argument.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiExpression argument = (PsiExpression)infos[0];
    if (!(argument instanceof PsiLiteralExpression)) {
      return null;
    }
    return new NewStringBufferWithCharArgumentFix();
  }

  private static class NewStringBufferWithCharArgumentFix
    extends InspectionGadgetsFix {

    @Nonnull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "new.string.buffer.with.char.argument.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      final PsiNewExpression newExpression =
        (PsiNewExpression)element.getParent();
      final PsiExpressionList argumentList =
        newExpression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiExpression argument = arguments[0];
      final String text = argument.getText();
      final String newArgument =
        '"' + text.substring(1, text.length() - 1) + '"';
      replaceExpression(argument, newArgument);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringBufferWithCharArgumentVisitor();
  }

  private static class StringBufferWithCharArgumentVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiExpression argument = arguments[0];
      final PsiType type = argument.getType();
      if (!PsiType.CHAR.equals(type)) {
        return;
      }
      final PsiMethod constructor = expression.resolveConstructor();
      if (constructor == null) {
        return;
      }
      final PsiClass aClass = constructor.getContainingClass();
      if (!InheritanceUtil.isInheritor(aClass,
                                       JavaClassNames.JAVA_LANG_ABSTRACT_STRING_BUILDER)) {
        return;
      }
      registerNewExpressionError(expression, argument);
    }
  }
}