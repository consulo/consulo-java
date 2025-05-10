/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.MultipleCheckboxOptionsPanel;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.util.collection.primitive.objects.ObjectIntMap;
import consulo.util.collection.primitive.objects.ObjectMaps;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

@ExtensionImpl
public class ImplicitNumericConversionInspection extends BaseInspection {

  /**
   * @noinspection StaticCollection
   */
  private static final ObjectIntMap<PsiType> typePrecisions = ObjectMaps.newObjectIntHashMap(7);

  static {
    typePrecisions.putInt(PsiType.BYTE, 1);
    typePrecisions.putInt(PsiType.CHAR, 2);
    typePrecisions.putInt(PsiType.SHORT, 2);
    typePrecisions.putInt(PsiType.INT, 3);
    typePrecisions.putInt(PsiType.LONG, 4);
    typePrecisions.putInt(PsiType.FLOAT, 5);
    typePrecisions.putInt(PsiType.DOUBLE, 6);
  }

  @SuppressWarnings({"PublicField"})
  public boolean ignoreWideningConversions = false;

  @SuppressWarnings({"PublicField"})
  public boolean ignoreCharConversions = false;

  @SuppressWarnings({"PublicField"})
  public boolean ignoreConstantConversions = false;

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.implicitNumericConversionDisplayName().get();
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(
      InspectionGadgetsLocalize.implicitNumericConversionIgnoreWideningConversionOption().get(),
      "ignoreWideningConversions"
    );
    optionsPanel.addCheckbox(
      InspectionGadgetsLocalize.implicitNumericConversionIgnoreCharConversionOption().get(),
      "ignoreCharConversions");
    optionsPanel.addCheckbox(
      InspectionGadgetsLocalize.implicitNumericConversionIgnoreConstantConversionOption().get(),
      "ignoreConstantConversions"
    );
    return optionsPanel;
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    final PsiType type = (PsiType)infos[1];
    final PsiType expectedType = (PsiType)infos[2];
    return InspectionGadgetsLocalize.implicitNumericConversionProblemDescriptor(
        type.getPresentableText(),
        expectedType.getPresentableText()
    ).get();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ImplicitNumericConversionVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new ImplicitNumericConversionFix((PsiExpression)infos[0], (PsiType)infos[2]);
  }

  private static class ImplicitNumericConversionFix extends InspectionGadgetsFix {

    private final String m_name;

    ImplicitNumericConversionFix(PsiExpression expression, PsiType expectedType) {
      m_name = isConvertible(expression, expectedType)
        ? InspectionGadgetsLocalize.implicitNumericConversionConvertQuickfix(expectedType.getCanonicalText()).get()
        : InspectionGadgetsLocalize.implicitNumericConversionMakeExplicitQuickfix().get();
    }

    @Nonnull
    public String getName() {
      return m_name;
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiExpression expression = (PsiExpression)descriptor.getPsiElement();
      final PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, true);
      if (expectedType == null) {
        return;
      }
      if (isConvertible(expression, expectedType)) {
        final String newExpression = convertExpression(expression, expectedType);
        if (newExpression == null) {
          return;
        }
        replaceExpression(expression, newExpression);
      }
      else {
        final String newExpression;
        if (ParenthesesUtils.getPrecedence(expression) <= ParenthesesUtils.TYPE_CAST_PRECEDENCE) {
          newExpression = '(' + expectedType.getCanonicalText() + ')' + expression.getText();
        }
        else {
          newExpression = '(' + expectedType.getCanonicalText() + ")(" + expression.getText() + ')';
        }
        replaceExpression(expression, newExpression);
      }
    }

    @Nullable
    @NonNls
    private static String convertExpression(PsiExpression expression, PsiType expectedType) {
      final PsiType expressionType = expression.getType();
      if (expressionType == null) {
        return null;
      }
      if (expressionType.equals(PsiType.INT) && expectedType.equals(PsiType.LONG)) {
        return expression.getText() + 'L';
      }
      if (expressionType.equals(PsiType.INT) && expectedType.equals(PsiType.FLOAT)) {
        return expression.getText() + ".0F";
      }
      if (expressionType.equals(PsiType.INT) && expectedType.equals(PsiType.DOUBLE)) {
        return expression.getText() + ".0";
      }
      if (expressionType.equals(PsiType.LONG) && expectedType.equals(PsiType.FLOAT)) {
        final String text = expression.getText();
        final int length = text.length();
        return text.substring(0, length - 1) + ".0F";
      }
      if (expressionType.equals(PsiType.LONG) && expectedType.equals(PsiType.DOUBLE)) {
        final String text = expression.getText();
        final int length = text.length();
        return text.substring(0, length - 1) + ".0";
      }
      if (expressionType.equals(PsiType.DOUBLE) && expectedType.equals(PsiType.FLOAT)) {
        final String text = expression.getText();
        final int length = text.length();
        if (text.charAt(length - 1) == 'd' || text.charAt(length - 1) == 'D') {
          return text.substring(0, length - 1) + 'F';
        }
        else {
          return text + 'F';
        }
      }
      if (expressionType.equals(PsiType.FLOAT) && expectedType.equals(PsiType.DOUBLE)) {
        final String text = expression.getText();
        final int length = text.length();
        return text.substring(0, length - 1);
      }
      return null;   //can't happen
    }

    private static boolean isConvertible(PsiExpression expression, PsiType expectedType) {
      if (!(expression instanceof PsiLiteralExpression) && !isNegatedLiteral(expression)) {
        return false;
      }
      final PsiType expressionType = expression.getType();
      if (expressionType == null) {
        return false;
      }
      if (hasLowerPrecision(expectedType, expressionType)) {
        return false;
      }
      if (isIntegral(expressionType) && isIntegral(expectedType)) {
        return true;
      }
      if (isIntegral(expressionType) && isFloatingPoint(expectedType)) {
        return true;
      }
      return isFloatingPoint(expressionType) && isFloatingPoint(expectedType);
    }

    private static boolean isNegatedLiteral(PsiExpression expression) {
      if (!(expression instanceof PsiPrefixExpression)) {
        return false;
      }
      final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)expression;
      final IElementType tokenType = prefixExpression.getOperationTokenType();
      if (!JavaTokenType.MINUS.equals(tokenType)) {
        return false;
      }
      final PsiExpression operand = prefixExpression.getOperand();
      return operand instanceof PsiLiteralExpression;
    }

    private static boolean isIntegral(@Nullable PsiType expressionType) {
      return PsiType.INT.equals(expressionType) || PsiType.LONG.equals(expressionType);
    }

    private static boolean isFloatingPoint(@Nullable PsiType expressionType) {
      return PsiType.FLOAT.equals(expressionType) || PsiType.DOUBLE.equals(expressionType);
    }
  }

  private class ImplicitNumericConversionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitConditionalExpression(PsiConditionalExpression expression) {
      super.visitConditionalExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitLiteralExpression(PsiLiteralExpression expression) {
      super.visitLiteralExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitPostfixExpression(PsiPostfixExpression expression) {
      super.visitPostfixExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitPrefixExpression(PsiPrefixExpression expression) {
      super.visitPrefixExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitTypeCastExpression(PsiTypeCastExpression expression) {
      super.visitTypeCastExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitAssignmentExpression(PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitParenthesizedExpression(PsiParenthesizedExpression expression) {
      super.visitParenthesizedExpression(expression);
      checkExpression(expression);
    }

    private void checkExpression(PsiExpression expression) {
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiParenthesizedExpression) {
        return;
      }
      if (ignoreConstantConversions) {
        PsiExpression rootExpression = expression;
        while (rootExpression instanceof PsiParenthesizedExpression) {
          final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)rootExpression;
          rootExpression = parenthesizedExpression.getExpression();
        }
        if (rootExpression instanceof PsiLiteralExpression || PsiUtil.isConstantExpression(rootExpression)) {
          return;
        }
      }
      final PsiType expressionType = expression.getType();
      if (expressionType == null || !ClassUtils.isPrimitiveNumericType(expressionType)) {
        return;
      }
      if (PsiType.CHAR.equals(expressionType) && (ignoreCharConversions || isArgumentOfStringIndexOf(parent))) {
        return;
      }
      final PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, true);
      if (!ClassUtils.isPrimitiveNumericType(expectedType)) {
        return;
      }
      if (expressionType.equals(expectedType)) {
        return;
      }
      if (ignoreWideningConversions && hasLowerPrecision(expressionType, expectedType)) {
        return;
      }
      if (ignoreCharConversions && PsiType.CHAR.equals(expectedType)) {
        return;
      }
      registerError(expression, expression, expressionType, expectedType);
    }

    private boolean isArgumentOfStringIndexOf(PsiElement parent) {
      if (!(parent instanceof PsiExpressionList)) {
        return false;
      }
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.INDEX_OF.equals(methodName) && !HardcodedMethodConstants.LAST_INDEX_OF.equals(methodName)) {
        return false;
      }
      final PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) {
        return false;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return false;
      }
      final String className = aClass.getQualifiedName();
      return CommonClassNames.JAVA_LANG_STRING.equals(className);
    }
  }

  static boolean hasLowerPrecision(PsiType expressionType, PsiType expectedType) {
    final int operandPrecision = typePrecisions.getInt(expressionType);
    final int castPrecision = typePrecisions.getInt(expectedType);
    return operandPrecision <= castPrecision;
  }
}
