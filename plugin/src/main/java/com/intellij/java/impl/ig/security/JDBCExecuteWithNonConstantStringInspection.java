/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.security;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.ConstantExpressionUtil;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import java.util.HashSet;
import java.util.Set;

@ExtensionImpl
public class JDBCExecuteWithNonConstantStringInspection
  extends BaseInspection {

  /**
   * @noinspection StaticCollection
   */
  @NonNls private static final Set<String> s_execMethodNames =
    new HashSet<String>(4);

  static {
    s_execMethodNames.add("execute");
    s_execMethodNames.add("executeQuery");
    s_execMethodNames.add("executeUpdate");
    s_execMethodNames.add("addBatch");
  }


  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.jdbcExecuteWithNonConstantStringDisplayName();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.jdbcExecuteWithNonConstantStringProblemDescriptor().get();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new RuntimeExecVisitor();
  }

  private static class RuntimeExecVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @Nonnull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      PsiReferenceExpression methodExpression = expression
        .getMethodExpression();
      String methodName = methodExpression.getReferenceName();
      if (!s_execMethodNames.contains(methodName)) {
        return;
      }
      PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      if (!InheritanceUtil.isInheritor(aClass, "java.sql.Statement")) {
        return;
      }
      PsiExpressionList argumentList = expression.getArgumentList();
      PsiExpression[] args = argumentList.getExpressions();
      if (args.length == 0) {
        return;
      }
      PsiExpression arg = args[0];
      PsiType type = arg.getType();
      if (type == null) {
        return;
      }
      String typeText = type.getCanonicalText();
      if (!CommonClassNames.JAVA_LANG_STRING.equals(typeText)) {
        return;
      }
      String stringValue =
        (String)ConstantExpressionUtil.computeCastTo(arg, type);
      if (stringValue != null) {
        return;
      }
      registerMethodCallError(expression);
    }
  }
}
