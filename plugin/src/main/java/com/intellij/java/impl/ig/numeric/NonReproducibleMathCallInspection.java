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
package com.intellij.java.impl.ig.numeric;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import java.util.HashSet;
import java.util.Set;

@ExtensionImpl
public class NonReproducibleMathCallInspection extends BaseInspection {

  @SuppressWarnings("StaticCollection")
  @NonNls private static final Set<String> nonReproducibleMethods =
    new HashSet<>(20);

  static {
    nonReproducibleMethods.add("acos");
    nonReproducibleMethods.add("asin");
    nonReproducibleMethods.add("atan");
    nonReproducibleMethods.add("atan2");
    nonReproducibleMethods.add("cbrt");
    nonReproducibleMethods.add("cos");
    nonReproducibleMethods.add("cosh");
    nonReproducibleMethods.add("exp");
    nonReproducibleMethods.add("expm1");
    nonReproducibleMethods.add("hypot");
    nonReproducibleMethods.add("log");
    nonReproducibleMethods.add("log10");
    nonReproducibleMethods.add("log1p");
    nonReproducibleMethods.add("pow");
    nonReproducibleMethods.add("sin");
    nonReproducibleMethods.add("sinh");
    nonReproducibleMethods.add("tan");
    nonReproducibleMethods.add("tanh");
  }


  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.nonReproducibleMathCallDisplayName().get();
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.nonReproducibleMathCallProblemDescriptor().get();
  }

  public InspectionGadgetsFix buildFix(Object... infos) {
    return new MakeStrictFix();
  }

  private static class MakeStrictFix extends InspectionGadgetsFix {

    @Nonnull
    public String getName() {
      return InspectionGadgetsLocalize.nonReproducibleMathCallReplaceQuickfix().get();
    }

    public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiIdentifier nameIdentifier = (PsiIdentifier)descriptor.getPsiElement();
      final PsiReferenceExpression reference = (PsiReferenceExpression)nameIdentifier.getParent();
      assert reference != null;
      final String name = reference.getReferenceName();
      replaceExpression(reference, "StrictMath." + name);
    }
  }

  public BaseInspectionVisitor buildVisitor() {
    return new BigDecimalEqualsVisitor();
  }

  private static class BigDecimalEqualsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @Nonnull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      if (!nonReproducibleMethods.contains(methodName)) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass referencedClass = method.getContainingClass();
      if (referencedClass == null) {
        return;
      }
      final String className = referencedClass.getQualifiedName();
      if (!CommonClassNames.JAVA_LANG_MATH.equals(className)) {
        return;
      }
      registerMethodCallError(expression);
    }
  }
}