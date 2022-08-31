/*
 * Copyright 2010-2011 Bas Leijdekkers
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
package com.intellij.java.impl.ig.junit;

import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.intellij.java.impl.ig.psiutils.ExceptionUtils;
import consulo.java.language.module.util.JavaClassNames;
import org.jetbrains.annotations.Nls;
import javax.annotation.Nonnull;

import java.util.Set;

public class ExpectedExceptionNeverThrownInspection
  extends BaseInspection {
  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "expected.exception.never.thrown.display.name");
  }

  @Nonnull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiMethod method = (PsiMethod)infos[0];
    return InspectionGadgetsBundle.message(
      "expected.exception.never.thrown.problem.descriptor",
      method.getName());
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ExpectedExceptionNeverThrownVisitor();
  }

  private static class ExpectedExceptionNeverThrownVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      final PsiAnnotation annotation =
        AnnotationUtil.findAnnotation(method, "org.junit.Test");
      if (annotation == null) {
        return;
      }
      final PsiAnnotationParameterList parameterList =
        annotation.getParameterList();
      final PsiNameValuePair[] attributes = parameterList.getAttributes();
      PsiAnnotationMemberValue value = null;
      for (PsiNameValuePair attribute : attributes) {
        if ("expected".equals(attribute.getName())) {
          value = attribute.getValue();
          break;
        }
      }
      if (!(value instanceof PsiClassObjectAccessExpression)) {
        return;
      }
      final PsiCodeBlock body = method.getBody();
      if (body == null) {
        return;
      }
      final PsiClassObjectAccessExpression classObjectAccessExpression =
        (PsiClassObjectAccessExpression)value;
      final PsiTypeElement operand =
        classObjectAccessExpression.getOperand();
      final PsiType type = operand.getType();
      if (!(type instanceof PsiClassType)) {
        return;
      }
      final PsiClassType classType = (PsiClassType)type;
      final PsiClass aClass = classType.resolve();
      if (InheritanceUtil.isInheritor(aClass,
                                      JavaClassNames.JAVA_LANG_RUNTIME_EXCEPTION)) {
        return;
      }
      final Set<PsiClassType> exceptionsThrown =
        ExceptionUtils.calculateExceptionsThrown(body);
      if (exceptionsThrown.contains(classType)) {
        return;
      }
      registerError(operand, method);
    }
  }
}
