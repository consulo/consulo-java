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
package com.intellij.java.impl.ig.performance;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class MapReplaceableByEnumMapInspection extends BaseInspection {

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.mapReplaceableByEnumMapDisplayName();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.mapReplaceableByEnumMapProblemDescriptor().get();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SetReplaceableByEnumSetVisitor();
  }

  private static class SetReplaceableByEnumSetVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(@Nonnull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiType type = expression.getType();
      if (!(type instanceof PsiClassType)) {
        return;
      }
      PsiClassType classType = (PsiClassType)type;
      if (!classType.hasParameters()) {
        final PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, false);
        if (!(expectedType instanceof PsiClassType)) {
          return;
        }
        classType = (PsiClassType)expectedType;
      }
      final PsiType[] typeArguments = classType.getParameters();
      if (typeArguments.length != 2) {
        return;
      }
      final PsiType argumentType = typeArguments[0];
      if (!(argumentType instanceof PsiClassType)) {
        return;
      }
      if (!TypeUtils.expressionHasTypeOrSubtype(expression, CommonClassNames.JAVA_UTIL_MAP)) {
        return;
      }
      if (null != TypeUtils.expressionHasTypeOrSubtype(expression, "java.util.EnumMap", "java.util.concurrent.ConcurrentMap")) {
        return;
      }
      final PsiClassType argumentClassType = (PsiClassType)argumentType;
      final PsiClass argumentClass = argumentClassType.resolve();
      if (argumentClass == null || !argumentClass.isEnum()) {
        return;
      }
      final PsiClass aClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
      if (argumentClass.equals(aClass)) {
        final PsiMember member = PsiTreeUtil.getParentOfType(expression, PsiMember.class);
        if (member != null && !member.hasModifierProperty(PsiModifier.STATIC)) {
          return;
        }
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList != null) {
        final PsiExpression[] arguments = argumentList.getExpressions();
        if (arguments.length > 0 && TypeUtils.expressionHasTypeOrSubtype(arguments[0], CommonClassNames.JAVA_UTIL_COMPARATOR)) {
          return;
        }
      }
      registerNewExpressionError(expression);
    }
  }
}
