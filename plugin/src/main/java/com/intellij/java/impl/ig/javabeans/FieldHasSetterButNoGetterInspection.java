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
package com.intellij.java.impl.ig.javabeans;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.util.PropertyUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class FieldHasSetterButNoGetterInspection extends BaseInspection {

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.fieldHasSetterButNoGetterDisplayName().get();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.fieldHasSetterButNoGetterProblemDescriptor().get();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new FieldHasSetterButNoGetterVisitor();
  }

  private static class FieldHasSetterButNoGetterVisitor extends BaseInspectionVisitor {
    @Override
    public void visitField(@Nonnull PsiField field) {
      final String propertyName = PropertyUtil.suggestPropertyName(field);
      final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
      final PsiClass containingClass = field.getContainingClass();
      final PsiMethod setter = PropertyUtil.findPropertySetter(containingClass, propertyName, isStatic, false);
      if (setter == null) {
        return;
      }
      final PsiMethod getter = PropertyUtil.findPropertyGetter(containingClass, propertyName, isStatic, false);
      if (getter != null) {
        return;
      }
      registerFieldError(field);
    }
  }
}