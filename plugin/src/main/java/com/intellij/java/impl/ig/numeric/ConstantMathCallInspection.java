/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
import com.intellij.java.language.psi.util.ConstantExpressionUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.HashSet;
import java.util.Set;

@ExtensionImpl
public class ConstantMathCallInspection extends BaseInspection {

  @SuppressWarnings("StaticCollection")
  @NonNls static final Set<String> constantMathCall =
    new HashSet<String>(23);

  static {
    constantMathCall.add("abs");
    constantMathCall.add("acos");
    constantMathCall.add("asin");
    constantMathCall.add("atan");
    constantMathCall.add("cbrt");
    constantMathCall.add("ceil");
    constantMathCall.add("cos");
    constantMathCall.add("cosh");
    constantMathCall.add("exp");
    constantMathCall.add("expm1");
    constantMathCall.add("floor");
    constantMathCall.add("log");
    constantMathCall.add("log10");
    constantMathCall.add("log1p");
    constantMathCall.add("rint");
    constantMathCall.add("round");
    constantMathCall.add("sin");
    constantMathCall.add("sinh");
    constantMathCall.add("sqrt");
    constantMathCall.add("tan");
    constantMathCall.add("tanh");
    constantMathCall.add("toDegrees");
    constantMathCall.add("toRadians");
  }

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.constantMathCallDisplayName();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.constantMathCallProblemDescriptor().get();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new MakeStrictFix();
  }

  private static class MakeStrictFix extends InspectionGadgetsFix {

    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.constantConditionalExpressionSimplifyQuickfix();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      PsiIdentifier nameIdentifier = (PsiIdentifier)descriptor.getPsiElement();
      PsiReferenceExpression reference = (PsiReferenceExpression)nameIdentifier.getParent();
      assert reference != null;
      PsiMethodCallExpression call = (PsiMethodCallExpression)reference.getParent();
      assert call != null;
      PsiExpressionList argumentList = call.getArgumentList();
      PsiExpression[] arguments = argumentList.getExpressions();
      String methodName = reference.getReferenceName();
      PsiExpression argument = arguments[0];
      PsiMethod method = call.resolveMethod();
      if (method == null) {
        return;
      }
      PsiParameterList parameterList = method.getParameterList();
      PsiParameter[] parameters = parameterList.getParameters();
      if (parameters.length != 1) {
        return;
      }
      PsiType type = parameters[0].getType();
      Object argumentValue =
        ConstantExpressionUtil.computeCastTo(argument, type);
      String newExpression;
      if (argumentValue instanceof Float ||
          argumentValue instanceof Double) {
        Number number = (Number)argumentValue;
        newExpression = createValueString(methodName, number.doubleValue());
      }
      else {
        Number number = (Number)argumentValue;
        newExpression = createValueString(methodName, number.longValue());
      }
      if (newExpression == null) {
        return;
      }
      if (PsiType.LONG.equals(type)) {
        replaceExpressionAndShorten(call, newExpression + 'L');
      }
      else {
        replaceExpressionAndShorten(call, newExpression);
      }
    }
  }

  @SuppressWarnings({"NestedMethodCall", "FloatingPointEquality"})
  @Nullable
  @NonNls
  static String createValueString(@NonNls String name, double value) {
    if ("abs".equals(name)) {
      return Double.toString(Math.abs(value));
    }
    else if ("floor".equals(name)) {
      return Double.toString(Math.floor(value));
    }
    else if ("ceil".equals(name)) {
      return Double.toString(Math.ceil(value));
    }
    else if ("toDegrees".equals(name)) {
      return Double.toString(Math.toDegrees(value));
    }
    else if ("toRadians".equals(name)) {
      return Double.toString(Math.toRadians(value));
    }
    else if ("sqrt".equals(name)) {
      return Double.toString(Math.sqrt(value));
    }
    else if ("cbrt".equals(name)) {
      return Double.toString(Math.pow(value, 1.0 / 3.0));
    }
    else if ("round".equals(name)) {
      return Long.toString(Math.round(value));
    }
    else if ("rint".equals(name)) {
      return Double.toString(Math.rint(value));
    }
    else if ("log".equals(name)) {
      if (value == 1.0) {
        return "0.0";
      }
      else {
        return null;
      }
    }
    else if ("log10".equals(name)) {
      if (value == 1.0) {
        return "0.0";
      }
      else {
        return null;
      }
    }
    else if ("log1p".equals(name)) {
      if (value == 0.0) {
        return "0.0";
      }
      else {
        return null;
      }
    }
    else if ("exp".equals(name)) {
      if (value == 0.0) {
        return "1.0";
      }
      else if (value == 1.0) {
        return "Math.E";
      }
      else {
        return null;
      }
    }
    else if ("expm1".equals(name)) {
      if (value == 0.0) {
        return "0.0";
      }
      else {
        return null;
      }
    }
    else if ("cos".equals(name) || "cosh".equals(name)) {
      if (value == 0.0) {
        return "1.0";
      }
      else {
        return null;
      }
    }
    else if ("acos".equals(name)) {
      if (value == 1.0) {
        return "0.0";
      }
      else if (value == 0.0) {
        return "(Math.PI/2.0)";
      }
      else {
        return null;
      }
    }
    else if ("acosh".equals(name)) {
      if (value == 1.0) {
        return "0.0";
      }
      else {
        return null;
      }
    }
    else if ("sin".equals(name) || "sinh".equals(name)) {
      if (value == 0.0) {
        return "0.0";
      }
      else {
        return null;
      }
    }
    else if ("asin".equals(name)) {
      if (value == 0.0) {
        return "0.0";
      }
      else if (value == 1.0) {
        return "(Math.PI/2.0)";
      }
      else {
        return null;
      }
    }
    else if ("asinh".equals(name)) {
      if (value == 0.0) {
        return "0.0";
      }
      else {
        return null;
      }
    }
    else if ("tan".equals(name) || "tanh".equals(name)) {
      if (value == 0.0) {
        return "0.0";
      }
      else {
        return null;
      }
    }
    else if ("atan".equals(name)) {
      if (value == 0.0) {
        return "0.0";
      }
      else if (value == 1.0) {
        return "(Math.PI/4.0)";
      }
      else {
        return null;
      }
    }
    else if ("atanh".equals(name)) {
      if (value == 0.0) {
        return "0.0";
      }
      else {
        return null;
      }
    }
    return null;
  }

  @Nullable
  @NonNls
  static String createValueString(@NonNls String name, long value) {
    if ("abs".equals(name)) {
      return Long.toString(Math.abs(value));
    }
    return null;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ConstantMathCallVisitor();
  }

  private static class ConstantMathCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @Nonnull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      String methodName = methodExpression.getReferenceName();
      if (!constantMathCall.contains(methodName)) {
        return;
      }
      PsiExpressionList argumentList = expression.getArgumentList();
      PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        return;
      }
      PsiExpression argument = arguments[0];
      Object argumentValue =
        ConstantExpressionUtil.computeCastTo(argument, PsiType.DOUBLE);
      if (!(argumentValue instanceof Double)) {
        return;
      }
      double doubleValue = ((Double)argumentValue).doubleValue();
      String valueString = createValueString(methodName,
                                                   doubleValue);
      if (valueString == null) {
        return;
      }
      PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      PsiClass referencedClass = method.getContainingClass();
      if (referencedClass == null) {
        return;
      }
      String className = referencedClass.getQualifiedName();
      if (!CommonClassNames.JAVA_LANG_MATH.equals(className)
          && !CommonClassNames.JAVA_LANG_STRICT_MATH.equals(className)) {
        return;
      }
      registerMethodCallError(expression);
    }
  }
}
