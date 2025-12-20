/*
 * Copyright 2010-2013 Bas Leijdekkers
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
package com.intellij.java.impl.ig.psiutils;

import com.intellij.java.language.psi.*;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.HashSet;
import java.util.Set;

public class FormatUtils {

  /**
   * @noinspection StaticCollection
   */
  @NonNls
  public static final Set<String> formatMethodNames = new HashSet<String>(2);
  /**
   * @noinspection StaticCollection
   */
  public static final Set<String> formatClassNames = new HashSet<String>(4);

  static {
    formatMethodNames.add("format");
    formatMethodNames.add("printf");

    formatClassNames.add(CommonClassNames.JAVA_IO_PRINT_WRITER);
    formatClassNames.add(CommonClassNames.JAVA_IO_PRINT_STREAM);
    formatClassNames.add("java.util.Formatter");
    formatClassNames.add(CommonClassNames.JAVA_LANG_STRING);
  }

  private FormatUtils() {}

  public static boolean isFormatCall(PsiMethodCallExpression expression) {
    PsiReferenceExpression methodExpression = expression.getMethodExpression();
    String name = methodExpression.getReferenceName();
    if (!formatMethodNames.contains(name)) {
      return false;
    }
    PsiMethod method = expression.resolveMethod();
    if (method == null) {
      return false;
    }
    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) {
      return false;
    }
    String className = containingClass.getQualifiedName();
    return formatClassNames.contains(className);
  }

  public static boolean isFormatCallArgument(PsiElement element) {
    PsiExpressionList expressionList =
      PsiTreeUtil.getParentOfType(element, PsiExpressionList.class, true, PsiCodeBlock.class, PsiStatement.class, PsiClass.class);
    if (expressionList == null) {
      return false;
    }
    PsiElement parent = expressionList.getParent();
    return parent instanceof PsiMethodCallExpression && isFormatCall((PsiMethodCallExpression)parent);
  }

  @Nullable
  public static PsiExpression getFormatArgument(PsiExpressionList argumentList) {
    PsiExpression[] arguments = argumentList.getExpressions();
    if (arguments.length == 0) {
      return null;
    }
    PsiExpression firstArgument = arguments[0];
    PsiType type = firstArgument.getType();
    if (type == null) {
      return null;
    }
    int formatArgumentIndex;
    if ("java.util.Locale".equals(type.getCanonicalText()) && arguments.length > 1) {
      formatArgumentIndex = 1;
    }
    else {
      formatArgumentIndex = 0;
    }
    return arguments[formatArgumentIndex];
  }
}
