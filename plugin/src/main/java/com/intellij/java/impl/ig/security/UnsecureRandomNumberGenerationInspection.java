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
package com.intellij.java.impl.ig.security;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class UnsecureRandomNumberGenerationInspection
  extends BaseInspection {

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.unsecureRandomNumberGenerationDisplayName().get();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    @NonNls final String text = ((PsiElement)infos[0]).getText();
    if ("random".equals(text)) {
      return InspectionGadgetsLocalize.unsecureRandomNumberGenerationProblemDescriptor1().get();
    }
    else if ("Random".equals(text)) {
      return InspectionGadgetsLocalize.unsecureRandomNumberGenerationProblemDescriptor2().get();
    }
    else {
      return InspectionGadgetsLocalize.unsecureRandomNumberGenerationProblemDescriptor3().get();
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new InsecureRandomNumberGenerationVisitor();
  }

  private static class InsecureRandomNumberGenerationVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(
      @Nonnull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiJavaCodeReferenceElement reference =
        expression.getClassReference();
      if (reference == null) {
        return;
      }
      final PsiElement element = reference.resolve();
      if (!(element instanceof PsiClass)) {
        return;
      }
      final PsiClass aClass = (PsiClass)element;
      if (!InheritanceUtil.isInheritor(aClass, "java.util.Random")) {
        return;
      }
      final String qualifiedName = aClass.getQualifiedName();
      if ("java.security.SecureRandom".equals(qualifiedName)) {
        return;
      }
      registerError(reference, reference);
    }

    @Override
    public void visitMethodCallExpression(
      @Nonnull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      @NonNls final String methodName =
        methodExpression.getReferenceName();
      if (!"random".equals(methodName)) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final String className = containingClass.getQualifiedName();
      if (!CommonClassNames.JAVA_LANG_MATH.equals(className)) {
        return;
      }
      registerMethodCallError(expression, expression);
    }
  }
}