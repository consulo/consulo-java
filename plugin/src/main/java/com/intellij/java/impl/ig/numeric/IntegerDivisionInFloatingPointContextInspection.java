/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.numeric;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import java.util.HashSet;
import java.util.Set;

@ExtensionImpl
public class IntegerDivisionInFloatingPointContextInspection
  extends BaseInspection {

  /**
   * @noinspection StaticCollection
   */
  @NonNls
  private static final Set<String> s_integralTypes = new HashSet<String>(10);

  static {
    s_integralTypes.add("int");
    s_integralTypes.add("long");
    s_integralTypes.add("short");
    s_integralTypes.add("byte");
    s_integralTypes.add("char");
    s_integralTypes.add(CommonClassNames.JAVA_LANG_INTEGER);
    s_integralTypes.add(CommonClassNames.JAVA_LANG_LONG);
    s_integralTypes.add(CommonClassNames.JAVA_LANG_SHORT);
    s_integralTypes.add(CommonClassNames.JAVA_LANG_BYTE);
    s_integralTypes.add(CommonClassNames.JAVA_LANG_CHARACTER);
  }

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.integerDivisionInFloatingPointContextDisplayName();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.integerDivisionInFloatingPointContextProblemDescriptor().get();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new IntegerDivisionInFloatingPointContextVisitor();
  }

  private static class IntegerDivisionInFloatingPointContextVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(
      @Nonnull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.DIV)) {
        return;
      }
      final PsiExpression lhs = expression.getLOperand();
      final PsiType lhsType = lhs.getType();
      if (!isIntegral(lhsType)) {
        return;
      }
      final PsiExpression rhs = expression.getROperand();
      if (rhs == null) {
        return;
      }
      final PsiType rhsType = rhs.getType();
      if (!isIntegral(rhsType)) {
        return;
      }
      final PsiExpression context = getContainingExpression(expression);
      if (context == null) {
        return;
      }
      final PsiType contextType =
        ExpectedTypeUtils.findExpectedType(context, true);
      if (contextType == null) {
        return;
      }
      if (!(contextType.equals(PsiType.FLOAT)
            || contextType.equals(PsiType.DOUBLE))) {
        return;
      }
      registerError(expression);
    }

    private static boolean isIntegral(PsiType type) {
      if (type == null) {
        return false;
      }
      final String text = type.getCanonicalText();
      return text != null && s_integralTypes.contains(text);
    }

    private static PsiExpression getContainingExpression(
      PsiExpression expression) {
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiBinaryExpression ||
          parent instanceof PsiParenthesizedExpression ||
          parent instanceof PsiPrefixExpression ||
          parent instanceof PsiConditionalExpression) {
        return getContainingExpression((PsiExpression)parent);
      }
      return expression;
    }
  }
}