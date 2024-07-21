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
package com.intellij.java.impl.ig.j2me;

import com.intellij.java.impl.ig.fixes.IntroduceVariableFix;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElementVisitor;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
public class MethodCallInLoopConditionInspection extends BaseInspection {

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.methodCallInLoopConditionDisplayName().get();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.methodCallInLoopConditionProblemDescriptor().get();
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new IntroduceVariableFix(true);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MethodCallInLoopConditionVisitor();
  }

  private static class MethodCallInLoopConditionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitForStatement(@Nonnull PsiForStatement statement) {
      super.visitForStatement(statement);
      final PsiExpression condition = statement.getCondition();
      if (condition == null) {
        return;
      }
      checkForMethodCalls(condition);
    }

    @Override
    public void visitWhileStatement(@Nonnull PsiWhileStatement statement) {
      super.visitWhileStatement(statement);
      final PsiExpression condition = statement.getCondition();
      if (condition == null) {
        return;
      }
      checkForMethodCalls(condition);
    }

    @Override
    public void visitDoWhileStatement(@Nonnull PsiDoWhileStatement statement) {
      super.visitDoWhileStatement(statement);
      final PsiExpression condition = statement.getCondition();
      if (condition == null) {
        return;
      }
      checkForMethodCalls(condition);
    }

    private void checkForMethodCalls(PsiExpression condition) {
      final PsiElementVisitor visitor = new JavaRecursiveElementVisitor() {

          @Override
          public void visitMethodCallExpression(
            @Nonnull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            registerMethodCallError(expression);
          }
        };
      condition.accept(visitor);
    }
  }
}