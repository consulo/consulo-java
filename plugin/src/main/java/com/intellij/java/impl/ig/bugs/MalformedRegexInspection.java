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
package com.intellij.java.impl.ig.bugs;

import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiExpressionList;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.util.ConstantExpressionUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import consulo.annotation.component.ExtensionImpl;

import jakarta.annotation.Nonnull;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@ExtensionImpl
public class MalformedRegexInspection extends BaseInspection {

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("malformed.regular.expression.display.name");
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    if (infos.length == 0) {
      return InspectionGadgetsBundle.message("malformed.regular.expression.problem.descriptor1");
    }
    else {
      return InspectionGadgetsBundle.message("malformed.regular.expression.problem.descriptor2", infos[0]);
    }
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MalformedRegexVisitor();
  }

  private static class MalformedRegexVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@jakarta.annotation.Nonnull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        return;
      }
      final PsiExpression argument = arguments[0];
      if (!ExpressionUtils.hasStringType(argument)) {
        return;
      }
      if (!PsiUtil.isConstantExpression(argument)) {
        return;
      }
      final PsiType regexType = argument.getType();
      final String value = (String)ConstantExpressionUtil.computeCastTo(argument, regexType);
      if (value == null) {
        return;
      }
      if (!MethodCallUtils.isCallToRegexMethod(expression)) {
        return;
      }
      //noinspection UnusedCatchParameter,ProhibitedExceptionCaught
      try {
        Pattern.compile(value);
      }
      catch (PatternSyntaxException e) {
        registerError(argument, e.getDescription());
      }
      catch (NullPointerException e) {
        registerError(argument); // due to a bug in the sun regex code
      }
    }
  }
}