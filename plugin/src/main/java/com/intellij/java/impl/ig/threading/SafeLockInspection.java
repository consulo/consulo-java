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
package com.intellij.java.impl.ig.threading;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class SafeLockInspection extends BaseInspection { // todo extend ResourceInspection?

  @Override
  @Nonnull
  public String getID() {
    return "LockAcquiredButNotSafelyReleased";
  }

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.safeLockDisplayName();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    PsiExpression expression = (PsiExpression)infos[0];
    PsiType type = expression.getType();
    assert type != null;
    String text = type.getPresentableText();
    return InspectionGadgetsLocalize.safeLockProblemDescriptor(text).get();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SafeLockVisitor();
  }

  private static class SafeLockVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @Nonnull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!isLockAcquireMethod(expression)) {
        return;
      }
      PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      PsiExpression qualifierExpression =
        methodExpression.getQualifierExpression();
      PsiVariable boundVariable;
      PsiReferenceExpression referenceExpression;
      LockType type;
      if (qualifierExpression instanceof PsiReferenceExpression) {
        referenceExpression = (PsiReferenceExpression)qualifierExpression;
        PsiElement target = referenceExpression.resolve();
        if (!(target instanceof PsiVariable)) {
          return;
        }
        boundVariable = (PsiVariable)target;
        type = LockType.REGULAR;
      }
      else if (qualifierExpression instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression methodCallExpression =
          (PsiMethodCallExpression)qualifierExpression;
        PsiReferenceExpression methodExpression1 =
          methodCallExpression.getMethodExpression();
        @NonNls String methodName =
          methodExpression1.getReferenceName();
        if ("readLock".equals(methodName)) {
          type = LockType.READ;
        }
        else if ("writeLock".equals(methodName)) {
          type = LockType.WRITE;
        }
        else {
          return;
        }
        PsiExpression qualifierExpression1 =
          methodExpression1.getQualifierExpression();
        if (!(qualifierExpression1 instanceof PsiReferenceExpression)) {
          return;
        }
        referenceExpression =
          (PsiReferenceExpression)qualifierExpression1;
        PsiElement target = referenceExpression.resolve();
        if (!(target instanceof PsiVariable)) {
          return;
        }
        boundVariable = (PsiVariable)target;
      }
      else {
        return;
      }
      PsiStatement statement =
        PsiTreeUtil.getParentOfType(expression, PsiStatement.class);
      if (statement == null) {
        return;
      }
      PsiStatement nextStatement =
        PsiTreeUtil.getNextSiblingOfType(statement,
                                         PsiStatement.class);
      if (!(nextStatement instanceof PsiTryStatement)) {
        registerError(expression, referenceExpression);
        return;
      }
      PsiTryStatement tryStatement =
        (PsiTryStatement)nextStatement;
      if (lockIsUnlockedInFinally(tryStatement, boundVariable, type)) {
        return;
      }
      registerError(expression, referenceExpression);
    }

    private static boolean lockIsUnlockedInFinally(
      PsiTryStatement tryStatement, PsiVariable boundVariable,
      LockType type) {
      PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
      if (finallyBlock == null) {
        return false;
      }
      PsiCodeBlock tryBlock = tryStatement.getTryBlock();
      if (tryBlock == null) {
        return false;
      }
      UnlockVisitor visitor =
        new UnlockVisitor(boundVariable, type);
      finallyBlock.accept(visitor);
      return visitor.containsUnlock();
    }

    private static boolean isLockAcquireMethod(
      PsiMethodCallExpression expression) {
      PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      @NonNls String methodName =
        methodExpression.getReferenceName();
      if (!"lock".equals(methodName) &&
          !"lockInterruptibly".equals(methodName)) {
        return false;
      }
      PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return false;
      }
      return TypeUtils.expressionHasTypeOrSubtype(qualifier,
                                                  "java.util.concurrent.locks.Lock");
    }
  }

  private static class UnlockVisitor extends JavaRecursiveElementVisitor {

    private boolean containsUnlock = false;
    private final PsiVariable variable;
    private final LockType type;

    private UnlockVisitor(@Nonnull PsiVariable variable,
                          @Nonnull LockType type) {
      this.variable = variable;
      this.type = type;
    }

    @Override
    public void visitElement(@Nonnull PsiElement element) {
      if (!containsUnlock) {
        super.visitElement(element);
      }
    }

    @Override
    public void visitMethodCallExpression(
      @Nonnull PsiMethodCallExpression call) {
      if (containsUnlock) {
        return;
      }
      super.visitMethodCallExpression(call);
      PsiReferenceExpression methodExpression =
        call.getMethodExpression();
      @NonNls String methodName =
        methodExpression.getReferenceName();
      if (!"unlock".equals(methodName)) {
        return;
      }
      PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (qualifier instanceof PsiReferenceExpression) {
        if (type != LockType.REGULAR) {
          return;
        }
        PsiReference reference = (PsiReference)qualifier;
        PsiElement target = reference.resolve();
        if (variable.equals(target)) {
          containsUnlock = true;
        }
      }
      else if (qualifier instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression methodCallExpression =
          (PsiMethodCallExpression)qualifier;
        PsiReferenceExpression methodExpression1 =
          methodCallExpression.getMethodExpression();
        @NonNls String methodName1 =
          methodExpression1.getReferenceName();
        if (type == LockType.READ && "readLock".equals(methodName1) ||
            type == LockType.WRITE &&
            "writeLock".equals(methodName1)) {
          PsiExpression qualifierExpression =
            methodExpression1.getQualifierExpression();
          if (!(qualifierExpression instanceof PsiReferenceExpression)) {
            return;
          }
          PsiReferenceExpression referenceExpression =
            (PsiReferenceExpression)qualifierExpression;
          PsiElement target = referenceExpression.resolve();
          if (variable.equals(target)) {
            containsUnlock = true;
          }
        }
      }
    }

    public boolean containsUnlock() {
      return containsUnlock;
    }
  }

  enum LockType {
    READ, WRITE, REGULAR
  }
}
