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
package com.intellij.java.impl.ig.portability;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class UseOfSunClassesInspection extends BaseInspection {

  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.useSunClassesDisplayName();
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.useSunClassesProblemDescriptor().get();
  }

  public BaseInspectionVisitor buildVisitor() {
    return new UseOfSunClassesVisitor();
  }

  private static class UseOfSunClassesVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitVariable(@Nonnull PsiVariable variable) {
      super.visitVariable(variable);
      PsiType type = variable.getType();
      PsiType deepComponentType = type.getDeepComponentType();
      if (!(deepComponentType instanceof PsiClassType)) {
        return;
      }
      PsiClassType classType = (PsiClassType)deepComponentType;
      @NonNls String className = classType.getCanonicalText();
      if (className == null || !className.startsWith("sun.")) {
        return;
      }
      PsiTypeElement typeElement = variable.getTypeElement();
      if (typeElement == null) {
        return;
      }
      registerError(typeElement);
    }

    @Override
    public void visitNewExpression(
      @Nonnull PsiNewExpression newExpression) {
      super.visitNewExpression(newExpression);
      PsiType type = newExpression.getType();
      if (type == null) {
        return;
      }
      if (!(type instanceof PsiClassType)) {
        return;
      }
      PsiClassType classType = (PsiClassType)type;
      @NonNls String className = classType.getCanonicalText();
      if (className == null || !className.startsWith("sun.")) {
        return;
      }
      registerNewExpressionError(newExpression);
    }
  }
}