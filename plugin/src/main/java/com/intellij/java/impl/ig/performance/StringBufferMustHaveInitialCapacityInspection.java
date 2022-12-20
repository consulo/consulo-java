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

import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiExpressionList;
import com.intellij.java.language.psi.PsiNewExpression;
import com.intellij.java.language.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.language.module.util.JavaClassNames;

import javax.annotation.Nonnull;

@ExtensionImpl
public class StringBufferMustHaveInitialCapacityInspection
  extends BaseInspection {

  @Override
  @Nonnull
  public String getID() {
    return "StringBufferWithoutInitialCapacity";
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "string.buffer.must.have.initial.capacity.display.name");
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "string.buffer.must.have.initial.capacity.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringBufferInitialCapacityVisitor();
  }

  private static class StringBufferInitialCapacityVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(
      @Nonnull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiType type = expression.getType();

      if (!TypeUtils.typeEquals(JavaClassNames.JAVA_LANG_STRING_BUFFER,
                                type) &&
          !TypeUtils.typeEquals(JavaClassNames.JAVA_LANG_STRING_BUILDER, type)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] args = argumentList.getExpressions();
      if (args.length != 0) {
        return;
      }
      registerError(expression);
    }
  }
}