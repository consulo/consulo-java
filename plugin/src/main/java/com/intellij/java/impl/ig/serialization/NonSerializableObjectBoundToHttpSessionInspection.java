/*
 * Copyright 2006-2010 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.serialization;

import com.intellij.java.impl.ig.psiutils.SerializationUtils;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class NonSerializableObjectBoundToHttpSessionInspection
  extends BaseInspection {

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.nonSerializableObjectBoundToHttpSessionDisplayName().get();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.nonSerializableObjectBoundToHttpSessionProblemDescriptor().get();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NonSerializableObjectBoundToHttpSessionVisitor();
  }

  private static class NonSerializableObjectBoundToHttpSessionVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      PsiMethodCallExpression methodCallExpression) {
      super.visitMethodCallExpression(methodCallExpression);
      if (!MethodCallUtils.isSimpleCallToMethod(
        methodCallExpression,
        "javax.servlet.http.HttpSession",
        PsiType.VOID,
        "putValue",
        CommonClassNames.JAVA_LANG_STRING,
        CommonClassNames.JAVA_LANG_OBJECT
      )
        && !MethodCallUtils.isSimpleCallToMethod(
          methodCallExpression,
          "javax.servlet.http.HttpSession",
          PsiType.VOID,
          "setAttribute",
          CommonClassNames.JAVA_LANG_STRING,
          CommonClassNames.JAVA_LANG_OBJECT
        )) {
        return;
      }
      final PsiExpressionList argumentList =
        methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 2) {
        return;
      }
      final PsiExpression argument = arguments[1];
      final PsiType argumentType = argument.getType();
      if (argumentType == null) {
        return;
      }
      if (SerializationUtils.isProbablySerializable(argumentType)) {
        return;
      }
      registerError(argument);
    }
  }
}