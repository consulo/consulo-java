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
package com.intellij.java.impl.ig.abstraction;

import com.intellij.java.language.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;

import javax.annotation.Nonnull;

@ExtensionImpl
public class InstanceofThisInspection extends BaseInspection {

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("instanceof.check.for.this.display.name");
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("instanceof.check.for.this.problem.descriptor");
  }

  public BaseInspectionVisitor buildVisitor() {
    return new InstanceofThisVisitor();
  }

  private static class InstanceofThisVisitor extends BaseInspectionVisitor {

    @Override
    public void visitThisExpression(@Nonnull PsiThisExpression thisValue) {
      super.visitThisExpression(thisValue);
      if (thisValue.getQualifier() != null) {
        return;
      }
      final PsiElement parent =
        PsiTreeUtil.skipParentsOfType(thisValue, PsiParenthesizedExpression.class,
                                      PsiConditionalExpression.class, PsiTypeCastExpression.class);
      if (!(parent instanceof PsiInstanceOfExpression)) {
        return;
      }
      registerError(thisValue);
    }
  }
}