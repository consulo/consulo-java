/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Nls;

/**
 * @author Bas Leijdekkers
 */
@ExtensionImpl
public class SuspiciousArrayCastInspection extends BaseInspection {

  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsLocalize.suspiciousArrayCastDisplayName().get();
  }

  @Nonnull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.suspiciousArrayCastProblemDescriptor().get();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SuspiciousArrayCastVisitor();
  }

  private static class SuspiciousArrayCastVisitor extends BaseInspectionVisitor {

    @Override
    public void visitTypeCastExpression(PsiTypeCastExpression expression) {
      super.visitTypeCastExpression(expression);
      final PsiTypeElement typeElement = expression.getCastType();
      if (typeElement == null) {
        return;
      }
      final PsiType castType = typeElement.getType();
      if (!(castType instanceof PsiArrayType)) {
        return;
      }
      final PsiExpression operand = expression.getOperand();
      if (operand == null) {
        return;
      }
      final PsiType type = operand.getType();
      if (!(type instanceof PsiArrayType)) {
        return;
      }
      final PsiType castComponentType = castType.getDeepComponentType();
      if (!(castComponentType instanceof PsiClassType)) {
        return;
      }
      final PsiClassType castClassType = (PsiClassType)castComponentType;
      final PsiClass castClass = castClassType.resolve();
      if (castClass == null) {
        return;
      }
      final PsiType componentType = type.getDeepComponentType();
      if (!(componentType instanceof PsiClassType)) {
        return;
      }
      final PsiClassType classType = (PsiClassType)componentType;
      final PsiClass aClass = classType.resolve();
      if (aClass == null) {
        return;
      }
      if (!castClass.isInheritor(aClass, true)) {
        return;
      }
      registerError(typeElement);
    }
  }
}
