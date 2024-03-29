// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.util;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.Contract;

import jakarta.annotation.Nonnull;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import static com.intellij.java.language.psi.CommonClassNames.JAVA_UTIL_OPTIONAL;

public class OptionalUtil {
  public static final String OPTIONAL_INT = "java.util.OptionalInt";
  public static final String OPTIONAL_LONG = "java.util.OptionalLong";
  public static final String OPTIONAL_DOUBLE = "java.util.OptionalDouble";
  public static final String GUAVA_OPTIONAL = "com.google.common.base.Optional";

  public static final CallMatcher GUAVA_OPTIONAL_FROM_NULLABLE = CallMatcher.staticCall(GUAVA_OPTIONAL, "fromNullable").parameterCount(1);
  public static final CallMatcher JDK_OPTIONAL_OF_NULLABLE = CallMatcher.staticCall(JAVA_UTIL_OPTIONAL, "ofNullable").parameterCount(1);
  public static final CallMatcher OPTIONAL_OF_NULLABLE = CallMatcher.anyOf(JDK_OPTIONAL_OF_NULLABLE, GUAVA_OPTIONAL_FROM_NULLABLE);

  public static final CallMatcher JDK_OPTIONAL_GET = CallMatcher.exactInstanceCall(JAVA_UTIL_OPTIONAL, "get").parameterCount(0);
  public static final CallMatcher JDK_OPTIONAL_INT_GET = CallMatcher.exactInstanceCall(OPTIONAL_INT, "getAsInt").parameterCount(0);
  public static final CallMatcher JDK_OPTIONAL_LONG_GET = CallMatcher.exactInstanceCall(OPTIONAL_LONG, "getAsLong").parameterCount(0);
  public static final CallMatcher JDK_OPTIONAL_DOUBLE_GET = CallMatcher.exactInstanceCall(OPTIONAL_DOUBLE, "getAsDouble").parameterCount(0);
  public static final CallMatcher GUAVA_OPTIONAL_GET = CallMatcher.instanceCall(GUAVA_OPTIONAL, "get").parameterCount(0);
  public static final CallMatcher OPTIONAL_GET = CallMatcher.anyOf(JDK_OPTIONAL_GET, JDK_OPTIONAL_INT_GET, JDK_OPTIONAL_LONG_GET,
      JDK_OPTIONAL_DOUBLE_GET, GUAVA_OPTIONAL_GET);

  public static final CallMatcher JDK_OPTIONAL_WRAP_METHOD =
      CallMatcher.staticCall(JAVA_UTIL_OPTIONAL, "of", "ofNullable").parameterCount(1);

  @Nonnull
  @Contract(pure = true)
  public static String getOptionalClass(String type) {
    switch (type) {
      case "int":
        return OPTIONAL_INT;
      case "long":
        return OPTIONAL_LONG;
      case "double":
        return OPTIONAL_DOUBLE;
      default:
        return JAVA_UTIL_OPTIONAL;
    }
  }

  public static boolean isJdkOptionalClassName(String className) {
    return JAVA_UTIL_OPTIONAL.equals(className) ||
        OPTIONAL_INT.equals(className) || OPTIONAL_LONG.equals(className) || OPTIONAL_DOUBLE.equals(className);
  }

  /**
   * Unwraps an {@link Optional}, {@link OptionalInt}, {@link OptionalLong} or {@link OptionalDouble}
   * returning its element type
   *
   * @param type a type representing optional (e.g. {@code Optional<String>} or {@code OptionalInt})
   * @return an element type (e.g. {@code String} or {@code int}). Returns {@code null} if the supplied type is not an optional type
   * or its a raw {@code java.util.Optional}.
   */
  @Contract("null -> null")
  public static PsiType getOptionalElementType(PsiType type) {
    PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
    if (aClass == null) {
      return null;
    }
    String className = aClass.getQualifiedName();
    if (className == null) {
      return null;
    }
    switch (className) {
      case OPTIONAL_INT:
        return PsiType.INT;
      case OPTIONAL_LONG:
        return PsiType.LONG;
      case OPTIONAL_DOUBLE:
        return PsiType.DOUBLE;
      case JAVA_UTIL_OPTIONAL:
      case GUAVA_OPTIONAL:
        PsiType[] parameters = ((PsiClassType) type).getParameters();
        if (parameters.length != 1) {
          return null;
        }
        PsiType streamType = parameters[0];
        if (streamType instanceof PsiCapturedWildcardType) {
          streamType = ((PsiCapturedWildcardType) streamType).getUpperBound();
        }
        return streamType;
      default:
        return null;
    }
  }

  @Contract("null -> false")
  public static boolean isOptionalEmptyCall(PsiExpression expression) {
    return expression instanceof PsiMethodCallExpression &&
        MethodCallUtils.isCallToStaticMethod((PsiMethodCallExpression) expression, JAVA_UTIL_OPTIONAL, "empty", 0);
  }
}
