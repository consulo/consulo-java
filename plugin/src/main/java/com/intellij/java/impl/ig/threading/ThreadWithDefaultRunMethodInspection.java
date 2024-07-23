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

import com.intellij.java.language.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class ThreadWithDefaultRunMethodInspection extends BaseInspection {

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.threadWithDefaultRunMethodDisplayName().get();
  }

  @Nonnull
  public String getID() {
    return "InstantiatingAThreadWithDefaultRunMethod";
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.threadWithDefaultRunMethodProblemDescriptor().get();
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ThreadWithDefaultRunMethodVisitor();
  }

  private static class ThreadWithDefaultRunMethodVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(@Nonnull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiAnonymousClass anonymousClass =
        expression.getAnonymousClass();

      if (anonymousClass != null) {
        final PsiJavaCodeReferenceElement baseClassReference =
          anonymousClass.getBaseClassReference();
        final PsiElement referent = baseClassReference.resolve();
        if (referent == null) {
          return;
        }
        final PsiClass referencedClass = (PsiClass)referent;
        final String referencedClassName =
          referencedClass.getQualifiedName();
        if (!"java.lang.Thread".equals(referencedClassName)) {
          return;
        }
        if (definesRun(anonymousClass)) {
          return;
        }
        final PsiExpressionList argumentList =
          expression.getArgumentList();
        if (argumentList == null) {
          return;
        }
        final PsiExpression[] arguments = argumentList.getExpressions();
        for (PsiExpression argument : arguments) {
          if (TypeUtils.expressionHasTypeOrSubtype(argument,
                                                   "java.lang.Runnable")) {
            return;
          }
        }
        registerNewExpressionError(expression);
      }
      else {
        final PsiJavaCodeReferenceElement classReference =
          expression.getClassReference();
        if (classReference == null) {
          return;
        }
        final PsiElement referent = classReference.resolve();
        if (referent == null) {
          return;
        }
        final PsiClass referencedClass = (PsiClass)referent;
        final String referencedClassName =
          referencedClass.getQualifiedName();
        if (!"java.lang.Thread".equals(referencedClassName)) {
          return;
        }
        final PsiExpressionList argumentList =
          expression.getArgumentList();
        if (argumentList == null) {
          return;
        }
        final PsiExpression[] arguments = argumentList.getExpressions();
        for (PsiExpression argument : arguments) {
          if (TypeUtils.expressionHasTypeOrSubtype(argument,
                                                   "java.lang.Runnable")) {
            return;
          }
        }
        registerNewExpressionError(expression);
      }
    }

    private static boolean definesRun(PsiAnonymousClass aClass) {
      final PsiMethod[] methods = aClass.getMethods();
      for (final PsiMethod method : methods) {
        final String methodName = method.getName();
        if (HardcodedMethodConstants.RUN.equals(methodName)) {
          final PsiParameterList parameterList =
            method.getParameterList();
          if (parameterList.getParametersCount() == 0) {
            return true;
          }
        }
      }
      return false;
    }
  }
}