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
package com.intellij.java.impl.ig.threading;

import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiSynchronizedStatement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class NestedSynchronizedStatementInspection extends BaseInspection {

  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.nestedSynchronizedStatementDisplayName();
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.nestedSynchronizedStatementProblemDescriptor().get();
  }

  public BaseInspectionVisitor buildVisitor() {
    return new NestedSynchronizedStatementVisitor();
  }

  private static class NestedSynchronizedStatementVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitSynchronizedStatement(
      @Nonnull PsiSynchronizedStatement statement) {
      super.visitSynchronizedStatement(statement);
      final PsiElement containingSynchronizedStatement =
        PsiTreeUtil.getParentOfType(statement,
                                    PsiSynchronizedStatement.class);
      if (containingSynchronizedStatement == null) {
        return;
      }
      final PsiMethod containingMethod =
        PsiTreeUtil.getParentOfType(statement,
                                    PsiMethod.class);
      final PsiMethod containingContainingMethod =
        PsiTreeUtil.getParentOfType(containingSynchronizedStatement,
                                    PsiMethod.class);
      if (containingMethod == null ||
          containingContainingMethod == null ||
          !containingMethod.equals(containingContainingMethod)) {
        return;
      }
      registerStatementError(statement);
    }
  }
}