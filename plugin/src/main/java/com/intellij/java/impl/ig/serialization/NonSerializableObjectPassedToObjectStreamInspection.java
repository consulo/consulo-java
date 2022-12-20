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

import javax.annotation.Nonnull;

import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiExpressionList;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.intellij.java.impl.ig.psiutils.SerializationUtils;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.language.module.util.JavaClassNames;

@ExtensionImpl
public class NonSerializableObjectPassedToObjectStreamInspection
  extends BaseInspection {

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "non.serializable.object.passed.to.object.stream.display.name");
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "non.serializable.object.passed.to.object.stream.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NonSerializableObjectPassedToObjectStreamVisitor();
  }

  private static class NonSerializableObjectPassedToObjectStreamVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      PsiMethodCallExpression methodCallExpression) {
      super.visitMethodCallExpression(methodCallExpression);

      if (!MethodCallUtils.isSimpleCallToMethod(methodCallExpression,
                                                "java.io.ObjectOutputStream", PsiType.VOID, "writeObject",
                                                JavaClassNames.JAVA_LANG_OBJECT)) {
        return;
      }
      final PsiExpressionList argumentList =
        methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiExpression argument = arguments[0];
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