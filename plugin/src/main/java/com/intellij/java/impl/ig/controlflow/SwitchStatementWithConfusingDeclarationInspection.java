/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.controlflow;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;

import java.util.HashSet;
import java.util.Set;

@ExtensionImpl
public class SwitchStatementWithConfusingDeclarationInspection extends BaseInspection {

  @Nonnull
  public String getID() {
    return "LocalVariableUsedAndDeclaredInDifferentSwitchBranches";
  }

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.switchStatementWithConfusingDeclarationDisplayName().get();
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.switchStatementWithConfusingDeclarationProblemDescriptor().get();
  }

  public BaseInspectionVisitor buildVisitor() {
    return new SwitchStatementWithConfusingDeclarationVisitor();
  }

  private static class SwitchStatementWithConfusingDeclarationVisitor extends BaseInspectionVisitor {

    @Override
    public void visitSwitchStatement(@Nonnull PsiSwitchStatement statement) {
      final PsiCodeBlock body = statement.getBody();
      if (body == null) {
        return;
      }
      final Set<PsiLocalVariable> variablesInPreviousBranches = new HashSet<PsiLocalVariable>(5);
      final Set<PsiLocalVariable> variablesInCurrentBranch = new HashSet<PsiLocalVariable>(5);
      final PsiStatement[] statements = body.getStatements();
      final LocalVariableAccessVisitor visitor = new LocalVariableAccessVisitor(variablesInPreviousBranches);
      for (final PsiStatement child : statements) {
        if (child instanceof PsiDeclarationStatement) {
          final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)child;
          final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
          for (final PsiElement declaredElement : declaredElements) {
            if (declaredElement instanceof PsiLocalVariable) {
              final PsiLocalVariable localVariable = (PsiLocalVariable)declaredElement;
              variablesInCurrentBranch.add(localVariable);
            }
          }
        }
        else if (child instanceof PsiBreakStatement) {
          variablesInPreviousBranches.addAll(variablesInCurrentBranch);
          variablesInCurrentBranch.clear();
        }
        child.accept(visitor);
      }
    }

    class LocalVariableAccessVisitor extends JavaRecursiveElementVisitor {

      private final Set<PsiLocalVariable> myVariablesInPreviousBranches;

      public LocalVariableAccessVisitor(Set<PsiLocalVariable> variablesInPreviousBranches) {
        myVariablesInPreviousBranches = variablesInPreviousBranches;
      }

      @Override
      public void visitReferenceExpression(@Nonnull PsiReferenceExpression referenceExpression) {
        super.visitReferenceExpression(referenceExpression);
        final PsiExpression qualifier = referenceExpression.getQualifierExpression();
        if (qualifier != null) {
          return;
        }
        final PsiElement element = referenceExpression.resolve();
        if (!(element instanceof PsiLocalVariable)) {
          return;
        }
        final PsiLocalVariable accessedVariable = (PsiLocalVariable)element;
        if (myVariablesInPreviousBranches.contains(accessedVariable)) {
          myVariablesInPreviousBranches.remove(accessedVariable);
          registerVariableError(accessedVariable);
        }
      }
    }
  }
}