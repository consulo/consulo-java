/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInspection.concurrencyAnnotations;

import com.intellij.java.analysis.impl.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.java.analysis.impl.codeInspection.concurrencyAnnotations.JCiPUtil;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
public class FieldAccessNotGuardedInspection extends BaseJavaLocalInspectionTool {

  @Override
  @Nonnull
  public LocalizeValue getGroupDisplayName() {
    return InspectionLocalize.groupNamesConcurrencyAnnotationIssues();
  }

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return LocalizeValue.localizeTODO("Unguarded field access");
  }

  @Override
  @Nonnull
  public String getShortName() {
    return "FieldAccessNotGuarded";
  }

  @Override
  @Nonnull
  public PsiElementVisitor buildVisitorImpl(@Nonnull ProblemsHolder holder,
                                            boolean isOnTheFly,
                                            LocalInspectionToolSession session,
                                            Object state) {
    return new Visitor(holder);
  }


  private static class Visitor extends JavaElementVisitor {
    private final ProblemsHolder myHolder;

    public Visitor(ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      PsiElement referent = expression.resolve();
      if (referent == null || !(referent instanceof PsiField)) {
        return;
      }
      PsiField field = (PsiField)referent;
      String guard = JCiPUtil.findGuardForMember(field);
      if (guard == null) {
        return;
      }
      PsiMethod containingMethod = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
      if (containingMethod != null && JCiPUtil.isGuardedBy(containingMethod, guard)) {
        return;
      }
      if (containingMethod != null && containingMethod.isConstructor()) {
        return;
      }
      if ("this".equals(guard)) {
        if (containingMethod != null && containingMethod.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
          return;
        }
      }

      if (findLockTryStatement(expression, guard) != null) {
        PsiElement lockExpr = expression;
        while (lockExpr != null) {
          PsiElement child = lockExpr;
          while (child != null) {
            if (isLockGuardStatement(guard, child, "lock")) return;
            PsiElement childParent = child.getParent();
            if (child instanceof PsiMethodCallExpression &&
                isCallOnGuard(guard, "tryLock", (PsiMethodCallExpression)child) &&
                childParent instanceof PsiIfStatement &&
                ((PsiIfStatement)childParent).getCondition() == child) {
              return;
            }
            child = child.getPrevSibling();
          }
          lockExpr = lockExpr.getParent();
        }
      }

      PsiElement check = expression;
      while (true) {
        PsiSynchronizedStatement syncStatement = PsiTreeUtil.getParentOfType(check, PsiSynchronizedStatement.class);
        if (syncStatement == null) {
          break;
        }
        PsiExpression lockExpression = syncStatement.getLockExpression();
        if (lockExpression != null && lockExpression.getText().equals(guard))    //TODO: this isn't quite right,
        {
          return;
        }
        check = syncStatement;
      }
      myHolder.registerProblem(expression, "Access to field <code>#ref</code> outside of declared guards #loc");
    }

    @Nullable
    private static PsiTryStatement findLockTryStatement(PsiReferenceExpression expression, String guard) {
      PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(expression, PsiTryStatement.class);
      while (tryStatement != null) {
        PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
        if (finallyBlock != null) {
          for (PsiStatement psiStatement : finallyBlock.getStatements()) {
            if (isLockGuardStatement(guard, psiStatement, "unlock")) {
              return tryStatement;
            }
          }
        }
        tryStatement = PsiTreeUtil.getParentOfType(tryStatement, PsiTryStatement.class);
      }
      return tryStatement;
    }

    private static boolean isLockGuardStatement(String guard, PsiElement element, String lockMethodStart) {
      if (element instanceof PsiExpressionStatement) {
        PsiExpression psiExpression = ((PsiExpressionStatement)element).getExpression();
        if (psiExpression instanceof PsiMethodCallExpression) {
          return isCallOnGuard(guard, lockMethodStart, (PsiMethodCallExpression)psiExpression);
        }
      }
      return false;
    }
  }

  private static boolean isCallOnGuard(String guard, String lockMethodStart, PsiMethodCallExpression psiExpression) {
    PsiReferenceExpression methodExpression = psiExpression.getMethodExpression();
    PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
    if (qualifierExpression != null) {
      if (isCallOnGuard(guard, lockMethodStart, methodExpression, qualifierExpression)) {
        return true;
      } else if (qualifierExpression instanceof PsiReferenceExpression) {
        PsiElement resolve = ((PsiReferenceExpression)qualifierExpression).resolve();
        if (resolve instanceof PsiField && ((PsiField)resolve).hasModifierProperty(PsiModifier.FINAL)) {
          PsiExpression initializer = ((PsiField)resolve).getInitializer();
          return initializer != null && isCallOnGuard(guard, lockMethodStart, methodExpression, initializer);
        }
      }
    }
    return false;
  }

  private static boolean isCallOnGuard(String guard,
                                       String lockMethodStart,
                                       PsiReferenceExpression methodExpression,
                                       PsiExpression qualifier) {
    String qualifierText = qualifier.getText();
    if (qualifierText.startsWith(guard + ".") || qualifierText.equals(guard)) {
      PsiElement resolve = methodExpression.resolve();
      if (resolve instanceof PsiMethod) {
        String methodName = ((PsiMethod)resolve).getName();
        if (methodName.startsWith(lockMethodStart)) {
          return true;
        }
      }
    }
    return false;
  }
}