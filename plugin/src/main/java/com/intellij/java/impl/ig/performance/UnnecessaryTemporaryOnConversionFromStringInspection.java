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
package com.intellij.java.impl.ig.performance;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.HashMap;
import java.util.Map;

@ExtensionImpl
public class UnnecessaryTemporaryOnConversionFromStringInspection
  extends BaseInspection {

  /**
   * @noinspection StaticCollection
   */
  @NonNls private static final Map<String, String> s_conversionMap =
    new HashMap<String, String>(7);

  static {
    s_conversionMap.put(JavaClassNames.JAVA_LANG_BOOLEAN, "valueOf");
    s_conversionMap.put(JavaClassNames.JAVA_LANG_BYTE, "parseByte");
    s_conversionMap.put(JavaClassNames.JAVA_LANG_DOUBLE, "parseDouble");
    s_conversionMap.put(JavaClassNames.JAVA_LANG_FLOAT, "parseFloat");
    s_conversionMap.put(JavaClassNames.JAVA_LANG_INTEGER, "parseInt");
    s_conversionMap.put(JavaClassNames.JAVA_LANG_LONG, "parseLong");
    s_conversionMap.put(JavaClassNames.JAVA_LANG_SHORT, "parseShort");
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.unnecessaryTemporaryOnConversionFromStringDisplayName().get();
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    final String replacementString = calculateReplacementExpression((PsiMethodCallExpression)infos[0]);
    return InspectionGadgetsLocalize.unnecessaryTemporaryOnConversionFromStringProblemDescriptor(replacementString).get();
  }

  @Nullable
  @NonNls
  static String calculateReplacementExpression(
    PsiMethodCallExpression expression) {
    final PsiReferenceExpression methodExpression =
      expression.getMethodExpression();
    final PsiExpression qualifierExpression =
      methodExpression.getQualifierExpression();
    if (!(qualifierExpression instanceof PsiNewExpression)) {
      return null;
    }
    final PsiNewExpression qualifier =
      (PsiNewExpression)qualifierExpression;
    final PsiExpressionList argumentList =
      qualifier.getArgumentList();
    if (argumentList == null) {
      return null;
    }
    final PsiExpression arg = argumentList.getExpressions()[0];
    final PsiType type = qualifier.getType();
    if (type == null) {
      return null;
    }
    final String qualifierType = type.getPresentableText();
    final String canonicalType = type.getCanonicalText();
    final String conversionName = s_conversionMap.get(canonicalType);
    if (TypeUtils.typeEquals(JavaClassNames.JAVA_LANG_BOOLEAN, type)) {
      if (!PsiUtil.isLanguageLevel5OrHigher(expression)) {
        return qualifierType + '.' + conversionName + '(' +
               arg.getText() + ").booleanValue()";
      }
      else {
        return qualifierType + ".parseBoolean(" +
               arg.getText() + ')';
      }
    }
    else {
      return qualifierType + '.' + conversionName + '(' +
             arg.getText() + ')';
    }
  }

  @Override
  @Nullable
  public InspectionGadgetsFix buildFix(Object... infos) {
    final String replacementExpression =
      calculateReplacementExpression(
        (PsiMethodCallExpression)infos[0]);
    if (replacementExpression == null) {
      return null;
    }
    final LocalizeValue name = InspectionGadgetsLocalize.unnecessaryTemporaryOnConversionFromStringFixName(replacementExpression);
    return new UnnecessaryTemporaryObjectFix(name.get());
  }

  private static class UnnecessaryTemporaryObjectFix
    extends InspectionGadgetsFix {

    private final String m_name;

    private UnnecessaryTemporaryObjectFix(
      String name) {
      m_name = name;
    }

    @Nonnull
    public String getName() {
      return m_name;
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiMethodCallExpression expression =
        (PsiMethodCallExpression)descriptor.getPsiElement();
      final String newExpression =
        calculateReplacementExpression(expression);
      if (newExpression == null) {
        return;
      }
      replaceExpression(expression, newExpression);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryTemporaryObjectVisitor();
  }

  private static class UnnecessaryTemporaryObjectVisitor
    extends BaseInspectionVisitor {

    /**
     * @noinspection StaticCollection
     */
    @NonNls private static final Map<String, String> s_basicTypeMap =
      new HashMap<String, String>(7);

    static {
      s_basicTypeMap.put(JavaClassNames.JAVA_LANG_BOOLEAN, "booleanValue");
      s_basicTypeMap.put(JavaClassNames.JAVA_LANG_BYTE, "byteValue");
      s_basicTypeMap.put(JavaClassNames.JAVA_LANG_DOUBLE, "doubleValue");
      s_basicTypeMap.put(JavaClassNames.JAVA_LANG_FLOAT, "floatValue");
      s_basicTypeMap.put(JavaClassNames.JAVA_LANG_INTEGER, "intValue");
      s_basicTypeMap.put(JavaClassNames.JAVA_LANG_LONG, "longValue");
      s_basicTypeMap.put(JavaClassNames.JAVA_LANG_SHORT, "shortValue");
    }

    @Override
    public void visitMethodCallExpression(
      @Nonnull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      final Map<String, String> basicTypeMap = s_basicTypeMap;
      if (!basicTypeMap.containsValue(methodName)) {
        return;
      }
      final PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (!(qualifier instanceof PsiNewExpression)) {
        return;
      }
      final PsiNewExpression newExpression = (PsiNewExpression)qualifier;
      final PsiExpressionList argumentList =
        newExpression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiType argumentType = arguments[0].getType();
      if (!TypeUtils.isJavaLangString(argumentType)) {
        return;
      }
      final PsiType type = qualifier.getType();
      if (type == null) {
        return;
      }
      final String typeText = type.getCanonicalText();
      if (!basicTypeMap.containsKey(typeText)) {
        return;
      }
      final String mappingMethod = basicTypeMap.get(typeText);
      if (!mappingMethod.equals(methodName)) {
        return;
      }
      registerError(expression, expression);
    }
  }
}