/*
 * Copyright 2008-2012 Bas Leijdekkers
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
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Nls;

@ExtensionImpl
public class SynchronizationOnLocalVariableOrMethodParameterInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean reportLocalVariables = true;
  @SuppressWarnings({"PublicField"})
  public boolean reportMethodParameters = true;

  @Nls
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.synchronizationOnLocalVariableOrMethodParameterDisplayName().get();
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    final Boolean localVariable = (Boolean)infos[0];
    return localVariable
      ? InspectionGadgetsLocalize.synchronizationOnLocalVariableProblemDescriptor().get()
      : InspectionGadgetsLocalize.synchronizationOnMethodParameterProblemDescriptor().get();
  }

  public BaseInspectionVisitor buildVisitor() {
    return new SynchronizationOnLocalVariableVisitor();
  }

  private class SynchronizationOnLocalVariableVisitor extends BaseInspectionVisitor {

    public void visitSynchronizedStatement(PsiSynchronizedStatement statement) {
      super.visitSynchronizedStatement(statement);
      if (!reportLocalVariables && !reportMethodParameters) {
        return;
      }
      final PsiExpression lockExpression = statement.getLockExpression();
      if (!(lockExpression instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)lockExpression;
      if (referenceExpression.isQualified()) {
        return;
      }
      boolean localVariable = false;
      final PsiElement target = referenceExpression.resolve();
      if (target instanceof PsiLocalVariable) {
        if (!reportLocalVariables) {
          return;
        }
        localVariable = true;
      }
      else if (target instanceof PsiParameter) {
        final PsiParameter parameter = (PsiParameter)target;
        final PsiElement scope = parameter.getDeclarationScope();
        if (scope instanceof PsiMethod) {
          if (!reportMethodParameters) {
            return;
          }
        }
        else {
          if (!reportLocalVariables) {
            return;
          }
          localVariable = true;
        }
      }
      else {
        return;
      }
      final PsiElement statementScope = getScope(statement);
      final PsiElement targetScope = getScope(target);
      if (statementScope != targetScope) {
        return;
      }
      registerError(referenceExpression, Boolean.valueOf(localVariable));
    }

    private PsiElement getScope(PsiElement element) {
      return PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiLambdaExpression.class, PsiClassInitializer.class);
    }
  }
}