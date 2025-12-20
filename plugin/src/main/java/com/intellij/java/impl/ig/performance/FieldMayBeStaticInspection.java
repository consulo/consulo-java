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
package com.intellij.java.impl.ig.performance;

import com.intellij.java.impl.ig.fixes.ChangeModifierFix;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.intellij.java.analysis.impl.codeInspection.SideEffectChecker;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class FieldMayBeStaticInspection extends BaseInspection {

  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.fieldMayBeStaticDisplayName();
  }

  public BaseInspectionVisitor buildVisitor() {
    return new FieldMayBeStaticVisitor();
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.fieldMayBeStaticProblemDescriptor().get();
  }

  public InspectionGadgetsFix buildFix(Object... infos) {
    return new ChangeModifierFix(PsiModifier.STATIC);
  }

  private static class FieldMayBeStaticVisitor extends BaseInspectionVisitor {

    @Override
    public void visitField(@Nonnull PsiField field) {
      if (field.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      if (!field.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      PsiExpression initializer = field.getInitializer();
      if (initializer == null) {
        return;
      }
      if (SideEffectChecker.mayHaveSideEffects(initializer)) {
        return;
      }
      if (!canBeStatic(initializer)) {
        return;
      }
      PsiType type = field.getType();
      if (!ClassUtils.isImmutable(type)) {
        return;
      }
      PsiClass containingClass = field.getContainingClass();
      if (containingClass != null
          && !containingClass.hasModifierProperty(PsiModifier.STATIC)
          && containingClass.getContainingClass() != null
          && !PsiUtil.isCompileTimeConstant(field)) {
        // inner class cannot have static declarations
        return;
      }
      registerFieldError(field);
    }

    private static boolean canBeStatic(PsiExpression initializer) {
      CanBeStaticVisitor canBeStaticVisitor =
        new CanBeStaticVisitor();
      initializer.accept(canBeStaticVisitor);
      return canBeStaticVisitor.canBeStatic();
    }
  }
}