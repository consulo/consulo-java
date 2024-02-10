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

import jakarta.annotation.Nonnull;

import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import consulo.annotation.component.ExtensionImpl;

@ExtensionImpl
public class VolatileLongOrDoubleFieldInspection extends BaseInspection {

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "volatile.long.or.double.field.display.name");
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    final PsiType type = (PsiType)infos[0];
    final String typeString = type.getPresentableText();
    return InspectionGadgetsBundle.message(
      "volatile.field.problem.descriptor", typeString);
  }

  public BaseInspectionVisitor buildVisitor() {
    return new VolatileLongOrDoubleFieldVisitor();
  }

  private static class VolatileLongOrDoubleFieldVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitField(@Nonnull PsiField field) {
      super.visitField(field);
      if (!field.hasModifierProperty(PsiModifier.VOLATILE)) {
        return;
      }
      final PsiType type = field.getType();
      if (PsiType.LONG.equals(type) || PsiType.DOUBLE.equals(type)) {
        registerFieldError(field, type);
      }
    }
  }
}