/*
 * Copyright 2006-2012 Bas Leijdekkers
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
package com.intellij.java.impl.ig.style;

import com.intellij.java.impl.ig.fixes.AddThisQualifierFix;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class UnqualifiedMethodAccessInspection extends BaseInspection {

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.unqualifiedMethodAccessDisplayName().get();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnqualifiedMethodAccessVisitor();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.unqualifiedMethodAccessProblemDescriptor().get();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new AddThisQualifierFix();
  }

  private static class UnqualifiedMethodAccessVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReferenceExpression(@Nonnull PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      final PsiExpression qualifierExpression = expression.getQualifierExpression();
      if (qualifierExpression != null) {
        return;
      }
      final PsiReferenceParameterList parameterList = expression.getParameterList();
      if (parameterList == null) {
        return;
      }
      final PsiElement element = expression.resolve();
      if (!(element instanceof PsiMethod)) {
        return;
      }
      final PsiMethod method = (PsiMethod)element;
      if (method.isConstructor() || method.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass instanceof PsiAnonymousClass) {
        return;
      }
      registerError(expression);
    }
  }
}