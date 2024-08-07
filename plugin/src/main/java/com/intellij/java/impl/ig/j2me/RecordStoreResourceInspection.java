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
package com.intellij.java.impl.ig.j2me;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class RecordStoreResourceInspection extends BaseInspection {

  @Nonnull
  public String getID() {
    return "RecordStoreOpenedButNotSafelyClosed";
  }

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.recordstoreOpenedNotSafelyClosedDisplayName().get();
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    final PsiExpression expression = (PsiExpression)infos[0];
    final PsiType type = expression.getType();
    assert type != null;
    final String text = type.getPresentableText();
    return InspectionGadgetsLocalize.resourceOpenedNotClosedProblemDescriptor(text).get();
  }

  public BaseInspectionVisitor buildVisitor() {
    return new RecordStoreResourceVisitor();
  }

  private static class RecordStoreResourceVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @Nonnull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!isRecordStoreFactoryMethod(expression)) {
        return;
      }
      final PsiElement parent = expression.getParent();
      if (!(parent instanceof PsiAssignmentExpression)) {
        registerError(expression, expression);
        return;
      }
      final PsiAssignmentExpression assignment =
        (PsiAssignmentExpression)parent;
      final PsiExpression lhs = assignment.getLExpression();
      if (!(lhs instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiElement referent =
        ((PsiReference)lhs).resolve();
      if (!(referent instanceof PsiVariable)) {
        return;
      }
      final PsiVariable boundVariable = (PsiVariable)referent;
      PsiElement currentContext = expression;
      while (true) {
        final PsiTryStatement tryStatement =
          PsiTreeUtil.getParentOfType(currentContext,
                                      PsiTryStatement.class);
        if (tryStatement == null) {
          registerError(expression, expression);
          return;
        }
        if (resourceIsOpenedInTryAndClosedInFinally(tryStatement,
                                                    expression,
                                                    boundVariable)) {
          return;
        }
        currentContext = tryStatement;
      }
    }

    private static boolean resourceIsOpenedInTryAndClosedInFinally(
      PsiTryStatement tryStatement, PsiExpression lhs,
      PsiVariable boundVariable) {
      final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
      if (finallyBlock == null) {
        return false;
      }
      final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
      if (tryBlock == null) {
        return false;
      }
      if (!PsiTreeUtil.isAncestor(tryBlock, lhs, true)) {
        return false;
      }
      return containsResourceClose(finallyBlock, boundVariable);
    }

    private static boolean containsResourceClose(PsiCodeBlock finallyBlock,
                                                 PsiVariable boundVariable) {
      final CloseVisitor visitor =
        new CloseVisitor(boundVariable);
      finallyBlock.accept(visitor);
      return visitor.containsStreamClose();
    }

    private static boolean isRecordStoreFactoryMethod(
      @Nonnull PsiMethodCallExpression expression) {
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      @NonNls final String openStore = "openRecordStore";
      if (!openStore.equals(methodName)) {
        return false;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return false;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return false;
      }
      final String className = containingClass.getQualifiedName();
      @NonNls final String recordStore =
        "javax.microedition.rms.RecordStore";
      return recordStore.equals(className);
    }
  }

  private static class CloseVisitor extends JavaRecursiveElementVisitor {

    private boolean containsClose = false;
    private final PsiVariable objectToClose;

    private CloseVisitor(PsiVariable objectToClose) {
      super();
      this.objectToClose = objectToClose;
    }

    @Override
    public void visitElement(@Nonnull PsiElement element) {
      if (!containsClose) {
        super.visitElement(element);
      }
    }

    @Override
    public void visitMethodCallExpression(
      @Nonnull PsiMethodCallExpression call) {
      if (containsClose) {
        return;
      }
      super.visitMethodCallExpression(call);
      final PsiReferenceExpression methodExpression =
        call.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      @NonNls final String closeStore = "closeRecordStore";
      if (!closeStore.equals(methodName)) {
        return;
      }
      final PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (!(qualifier instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiElement referent =
        ((PsiReference)qualifier).resolve();
      if (referent == null) {
        return;
      }
      if (referent.equals(objectToClose)) {
        containsClose = true;
      }
    }

    public boolean containsStreamClose() {
      return containsClose;
    }
  }
}
