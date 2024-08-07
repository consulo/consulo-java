/*
 * Copyright 2009 Bas Leijdekkers
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

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Nls;

@ExtensionImpl
public class AssertWithSideEffectsInspection extends BaseInspection {

  @Override
  @Nls
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.assertWithSideEffectsDisplayName().get();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.assertWithSideEffectsProblemDescriptor().get();
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AssertWithSideEffectsVisitor();
  }

  private static class AssertWithSideEffectsVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitAssertStatement(PsiAssertStatement statement) {
      super.visitAssertStatement(statement);
      final PsiExpression condition = statement.getAssertCondition();
      if (condition == null) {
        return;
      }
      final SideEffectVistor visitor = new SideEffectVistor();
      condition.accept(visitor);
      if (!visitor.hasSideEffects()) {
        return;
      }
      registerStatementError(statement);
    }
  }

  private static class SideEffectVistor extends JavaRecursiveElementVisitor {

    private boolean hasSideEffects = false;

    public boolean hasSideEffects() {
      return hasSideEffects;
    }

    @Override
    public void visitAssignmentExpression(
      PsiAssignmentExpression expression) {
      hasSideEffects = true;
    }

    @Override
    public void visitElement(PsiElement element) {
      if (hasSideEffects) {
        return;
      }
      super.visitElement(element);
    }

    @Override
    public void visitMethodCallExpression(
      PsiMethodCallExpression expression) {
      if (hasSideEffects) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      if (methodHasSideEffects(method)) {
        hasSideEffects = true;
      }
      else {
        super.visitMethodCallExpression(expression);
      }
    }

    @Override
    public void visitPrefixExpression(PsiPrefixExpression expression) {
      final IElementType tokenType = expression.getOperationTokenType();
      if (JavaTokenType.PLUSPLUS.equals(tokenType) ||
          JavaTokenType.MINUSMINUS.equals(tokenType)) {
        hasSideEffects = true;
      }
    }

    @Override
    public void visitPostfixExpression(PsiPostfixExpression expression) {
      final IElementType tokenType = expression.getOperationTokenType();
      if (JavaTokenType.PLUSPLUS.equals(tokenType) ||
          JavaTokenType.MINUSMINUS.equals(tokenType)) {
        hasSideEffects = true;
      }
    }
  }

  private static boolean methodHasSideEffects(PsiMethod method) {
    final PsiCodeBlock body = method.getBody();
    if (body == null) {
      return false;
    }
    final MethodSideEffectVisitor visitor = new MethodSideEffectVisitor();
    body.accept(visitor);
    return visitor.hasSideEffects();
  }

  private static class MethodSideEffectVisitor
    extends JavaRecursiveElementVisitor {

    private boolean hasSideEffects = false;

    @Override
    public void visitAssignmentExpression(
      PsiAssignmentExpression expression) {
      if (hasSideEffects) {
        return;
      }
      checkExpression(expression.getLExpression());
      super.visitAssignmentExpression(expression);
    }

    @Override
    public void visitPrefixExpression(PsiPrefixExpression expression) {
      if (hasSideEffects) {
        return;
      }
      final IElementType tokenType = expression.getOperationTokenType();
      if (JavaTokenType.PLUSPLUS.equals(tokenType) ||
          JavaTokenType.MINUSMINUS.equals(tokenType)) {
        checkExpression(expression.getOperand());
      }
      super.visitPrefixExpression(expression);
    }

    @Override
    public void visitPostfixExpression(PsiPostfixExpression expression) {
      if (hasSideEffects) {
        return;
      }
      final IElementType tokenType = expression.getOperationTokenType();
      if (JavaTokenType.PLUSPLUS.equals(tokenType) ||
          JavaTokenType.MINUSMINUS.equals(tokenType)) {
        checkExpression(expression.getOperand());
      }
      super.visitPostfixExpression(expression);
    }

    private void checkExpression(PsiExpression operand) {
      if (!(operand instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)operand;
      final PsiElement target = referenceExpression.resolve();
      if (target instanceof PsiField) {
        hasSideEffects = true;
      }
    }

    public boolean hasSideEffects() {
      return hasSideEffects;
    }
  }
}