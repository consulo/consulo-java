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
package com.intellij.java.impl.ig.methodmetrics;

import javax.annotation.Nonnull;

import com.intellij.java.language.psi.PsiMethod;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;

public class NonCommentSourceStatementsInspection
  extends MethodMetricInspection {

  private static final int DEFAULT_LIMIT = 30;

  @Nonnull
  public String getID() {
    return "OverlyLongMethod";
  }

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "non.comment.source.statements.display.name");
  }

  protected int getDefaultLimit() {
    return DEFAULT_LIMIT;
  }

  protected String getConfigurationLabel() {
    return InspectionGadgetsBundle.message(
      "non.comment.source.statements.limit.option");
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    final Integer statementCount = (Integer)infos[0];
    return InspectionGadgetsBundle.message(
      "non.comment.source.statements.problem.descriptor",
      statementCount);
  }

  public BaseInspectionVisitor buildVisitor() {
    return new NonCommentSourceStatementsMethodVisitor();
  }

  private class NonCommentSourceStatementsMethodVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      // note: no call to super
      if (method.getNameIdentifier() == null) {
        return;
      }
      final NCSSVisitor visitor = new NCSSVisitor();
      method.accept(visitor);
      final int count = visitor.getStatementCount();
      if (count <= getLimit()) {
        return;
      }
      registerMethodError(method, Integer.valueOf(count));
    }
  }
}