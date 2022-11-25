/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.intellij.java.language.psi.*;
import consulo.language.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.java.impl.ig.fixes.MakeFieldFinalFix;
import com.siyeh.ig.psiutils.MethodUtils;

public class HashCodeUsesNonFinalVariableInspection
  extends BaseInspection {

  @Nonnull
  public String getID() {
    return "NonFinalFieldReferencedInHashCode";
  }

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "non.final.field.in.hashcode.display.name");
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "non.final.field.in.hashcode.problem.descriptor");
  }

  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiField field = (PsiField)infos[0];
    return MakeFieldFinalFix.buildFix(field);
  }

  public BaseInspectionVisitor buildVisitor() {
    return new HashCodeUsesNonFinalVariableVisitor();
  }

  private static class HashCodeUsesNonFinalVariableVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      final boolean isHashCode = MethodUtils.isHashCode(method);
      if (isHashCode) {
        method.accept(new JavaRecursiveElementVisitor() {

          @Override
          public void visitClass(PsiClass aClass) {
            // Do not recurse into.
          }

          @Override
          public void visitReferenceExpression(
            @Nonnull PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            final PsiElement element = expression.resolve();
            if (!(element instanceof PsiField)) {
              return;
            }
            final PsiField field = (PsiField)element;
            if (field.hasModifierProperty(PsiModifier.FINAL)) {
              return;
            }
            registerError(expression, field);
          }
        });
      }
    }
  }
}