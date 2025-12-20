/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.intention.impl;

import com.intellij.java.language.impl.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Danila Ponomarenko
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ConvertColorRepresentationIntentionAction", categories = {"Java", "Declaration"}, fileExtensions = "java")
public class ConvertColorRepresentationIntentionAction extends BaseColorIntentionAction {
  public ConvertColorRepresentationIntentionAction() {
    setText(CodeInsightLocalize.intentionConvertColorRepresentationFamily());
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) {
    if (!super.isAvailable(project, editor, element)) {
      return false;
    }

    PsiNewExpression expression = PsiTreeUtil.getParentOfType(element, PsiNewExpression.class, false);
    if (expression == null) {
      return false;
    }

    PsiExpressionList arguments = expression.getArgumentList();
    if (arguments == null) {
      return false;
    }

    PsiMethod constructor = expression.resolveConstructor();
    if (constructor == null) {
      return false;
    }

    PsiExpressionList newArguments = createNewArguments(
      JavaPsiFacade.getElementFactory(project),
      constructor.getParameterList().getParameters(),
      arguments.getExpressions()
    );

    if (newArguments == null) {
      return false;
    }

    setText(CodeInsightLocalize.intentionConvertColorRepresentationText(newArguments.getText()));

    return true;
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;

    PsiNewExpression expression = PsiTreeUtil.getParentOfType(element, PsiNewExpression.class, false);
    if (expression == null) {
      return;
    }

    PsiExpressionList arguments = expression.getArgumentList();
    if (arguments == null) {
      return;
    }

    PsiMethod constructor = expression.resolveConstructor();
    if (constructor == null) {
      return;
    }

    PsiExpressionList newArguments = createNewArguments(
      JavaPsiFacade.getElementFactory(project),
      constructor.getParameterList().getParameters(),
      arguments.getExpressions()
    );

    if (newArguments == null) {
      return;
    }

    arguments.replace(newArguments);
  }

  @Nullable
  private static PsiExpressionList createNewArguments(
    @Nonnull PsiElementFactory factory,
    @Nonnull PsiParameter[] parameters,
    @Nonnull PsiExpression[] arguments
  ) {
    String[] newValues = createArguments(parameters, arguments);
    if (newValues == null) {
      return null;
    }

    PsiExpressionList result = ((PsiNewExpression)factory.createExpressionFromText("new Object()", parameters[0])).getArgumentList();
    if (result == null) {
      return null;
    }
    for (String value : newValues) {
      result.add(factory.createExpressionFromText(value, parameters[0]));
    }
    return result;
  }

  @Nullable
  private static String[] createArguments(@Nonnull PsiParameter[] parameters,
                                          @Nonnull PsiExpression[] arguments) {
    if (parameters.length != arguments.length) {
      return null;
    }

    switch (parameters.length) {
      default:
        return null;
      case 1:
        return createArguments(arguments[0]);
      case 2:
        return createArguments(arguments[0], arguments[1]);
      case 3:
        return createArguments(arguments[0], arguments[1], arguments[2]);
      case 4:
        return createArguments(arguments[0], arguments[1], arguments[2], arguments[3]);
    }
  }

  @Nullable
  private static String[] createArguments(@Nonnull PsiExpression rgbExpression) {
    return createArguments(rgbExpression, 3);
  }

  @Nullable
  private static String[] createArguments(@Nonnull PsiExpression rgbExpression,
                                          @Nonnull PsiExpression hasAlphaExpression) {
    Boolean hasAlpha = computeBoolean(hasAlphaExpression);
    if (hasAlpha == null) {
      return null;
    }
    return hasAlpha ? createArguments(rgbExpression, 4) : createArguments(rgbExpression);
  }

  @Nullable
  private static String[] createArguments(@Nonnull PsiExpression rExpression,
                                          @Nonnull PsiExpression gExpression,
                                          @Nonnull PsiExpression bExpression) {
    Integer value = createInt(computeInteger(rExpression), computeInteger(gExpression), computeInteger(bExpression));
    return value != null ? new String[]{"0x" + Integer.toHexString(value)} : null;
  }

  @Nullable
  private static String[] createArguments(@Nonnull PsiExpression rExpression,
                                          @Nonnull PsiExpression gExpression,
                                          @Nonnull PsiExpression bExpression,
                                          @Nonnull PsiExpression aExpression) {
    Integer value = createInt(computeInteger(rExpression), computeInteger(gExpression), computeInteger(bExpression), computeInteger(aExpression));
    if (value == null) {
      return null;
    }

    return new String[]{
      "0x" + Integer.toHexString(value),
      "true",
    };
  }

  @Nullable
  private static String[] createArguments(@Nonnull PsiExpression rgbExpression,
                                          int parts) {
    Integer rgb = computeInteger(rgbExpression);
    if (rgb == null) {
      return null;
    }

    String[] result = new String[parts];
    for (int i = 0; i < result.length; i++) {
      result[result.length - i - 1] = String.valueOf(rgb >> (i * Byte.SIZE) & 0xFF);
    }
    return result;
  }

  @Nullable
  private static Integer createInt(Integer... ints) {
    int result = 0;
    for (Integer i : ints) {
      if (i == null) {
        return null;
      }
      result = result << Byte.SIZE | (i & 0xFF);
    }
    return result;
  }

  @Nullable
  public static Integer computeInteger(@Nonnull PsiExpression expr) {
    Object result = compute(expr);
    return result instanceof Integer i ? i : null;
  }

  @Nullable
  public static Boolean computeBoolean(@Nonnull PsiExpression expr) {
    Object result = compute(expr);
    return result instanceof Boolean b ? b : null;
  }

  @Nullable
  private static Object compute(@Nonnull PsiExpression expr) {
    return JavaConstantExpressionEvaluator.computeConstantExpression(expr, true);
  }
}
