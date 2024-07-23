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
package com.intellij.java.impl.ig.threading;

import com.intellij.java.impl.ig.psiutils.SynchronizationUtil;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class PublicFieldAccessedInSynchronizedContextInspection extends BaseInspection {

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.publicFieldAccessedInSynchronizedContextDisplayName().get();
  }

  @Nonnull
  public String getID() {
    return "NonPrivateFieldAccessedInSynchronizedContext";
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.publicFieldAccessedInSynchronizedContextProblemDescriptor().get();
  }

  public BaseInspectionVisitor buildVisitor() {
    return new PublicFieldAccessedInSynchronizedContextVisitor();
  }

  private static class PublicFieldAccessedInSynchronizedContextVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReferenceExpression(@Nonnull PsiReferenceExpression expression) {
      final PsiExpression qualifier = expression.getQualifierExpression();
      if (qualifier != null && !(qualifier instanceof PsiThisExpression) && !(qualifier instanceof PsiSuperExpression)) {
        return;
      }
      final PsiElement element = expression.resolve();
      if (!(element instanceof PsiField)) {
        return;
      }
      final PsiField field = (PsiField)element;
      if (field.hasModifierProperty(PsiModifier.PRIVATE) || field.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      if (!SynchronizationUtil.isInSynchronizedContext(expression)) {
        return;
      }
      final PsiClass containingClass = field.getContainingClass();
      if (containingClass == null || containingClass.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      registerError(expression);
    }
  }
}