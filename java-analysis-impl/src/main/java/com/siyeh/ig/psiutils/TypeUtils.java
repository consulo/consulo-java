/*
 * Copyright 2003-2016 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;

import java.util.HashMap;
import java.util.Map;

public class TypeUtils {

  private static final Map<PsiType, Integer> typePrecisions = new HashMap<>(7);

  static {
    typePrecisions.put(PsiType.BYTE, 1);
    typePrecisions.put(PsiType.CHAR, 2);
    typePrecisions.put(PsiType.SHORT, 2);
    typePrecisions.put(PsiType.INT, 3);
    typePrecisions.put(PsiType.LONG, 4);
    typePrecisions.put(PsiType.FLOAT, 5);
    typePrecisions.put(PsiType.DOUBLE, 6);
  }

  private TypeUtils() {
  }

  @Contract("_, null -> false")
  public static boolean typeEquals(@NonNls @Nonnull String typeName, @Nullable PsiType targetType) {
    return targetType != null && targetType.equalsToText(typeName);
  }

  public static PsiClassType getType(@Nonnull String fqName, @Nonnull PsiElement context) {
    final Project project = context.getProject();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    final GlobalSearchScope scope = context.getResolveScope();
    return factory.createTypeByFQClassName(fqName, scope);
  }

  public static PsiClassType getType(@Nonnull PsiClass aClass) {
    return JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createType(aClass);
  }

  public static PsiClassType getObjectType(@Nonnull PsiElement context) {
    return getType(CommonClassNames.JAVA_LANG_OBJECT, context);
  }

  public static PsiClassType getStringType(@Nonnull PsiElement context) {
    return getType(CommonClassNames.JAVA_LANG_STRING, context);
  }

  /**
   * JLS 5.1.3. Narrowing Primitive Conversion
   */
  public static boolean isNarrowingConversion(@Nullable PsiType sourceType, @Nullable PsiType targetType) {
    final Integer sourcePrecision = typePrecisions.get(sourceType);
    final Integer targetPrecision = typePrecisions.get(targetType);
    return sourcePrecision != null && targetPrecision != null && targetPrecision.intValue() < sourcePrecision.intValue();
  }

  @Contract("null -> false")
  public static boolean isJavaLangObject(@Nullable PsiType targetType) {
    return typeEquals(CommonClassNames.JAVA_LANG_OBJECT, targetType);
  }

  @Contract("null -> false")
  public static boolean isJavaLangString(@Nullable PsiType targetType) {
    return typeEquals(CommonClassNames.JAVA_LANG_STRING, targetType);
  }

  public static boolean isOptional(@Nullable PsiType type) {
    return isOptional(PsiUtil.resolveClassInClassTypeOnly(type));
  }

  @Contract("null -> false")
  public static boolean isOptional(PsiClass aClass) {
    if (aClass == null) {
      return false;
    }
    final String qualifiedName = aClass.getQualifiedName();
    return CommonClassNames.JAVA_UTIL_OPTIONAL.equals(qualifiedName) || "java.util.OptionalDouble".equals(qualifiedName) || "java.util.OptionalInt".equals(qualifiedName) || ("java.util" +
        ".OptionalLong").equals(qualifiedName) || "com.google.common.base.Optional".equals(qualifiedName);
  }

  public static boolean isExpressionTypeAssignableWith(@Nonnull PsiExpression expression, @Nonnull Iterable<String> rhsTypeTexts) {
    final PsiType type = expression.getType();
    if (type == null) {
      return false;
    }
    final PsiElementFactory factory = JavaPsiFacade.getInstance(expression.getProject()).getElementFactory();
    for (String rhsTypeText : rhsTypeTexts) {
      final PsiClassType rhsType = factory.createTypeByFQClassName(rhsTypeText, expression.getResolveScope());
      if (type.isAssignableFrom(rhsType)) {
        return true;
      }
    }
    return false;
  }

  public static boolean expressionHasTypeOrSubtype(@Nullable PsiExpression expression, @NonNls @Nonnull String typeName) {
    return expressionHasTypeOrSubtype(expression, new String[]{typeName}) != null;
  }

  //getTypeIfOneOfOrSubtype
  public static String expressionHasTypeOrSubtype(@Nullable PsiExpression expression, @NonNls @Nonnull String... typeNames) {
    if (expression == null) {
      return null;
    }
    PsiType type = expression instanceof PsiFunctionalExpression ? ((PsiFunctionalExpression) expression).getFunctionalInterfaceType() : expression.getType();
    if (type == null) {
      return null;
    }
    if (!(type instanceof PsiClassType)) {
      return null;
    }
    final PsiClassType classType = (PsiClassType) type;
    final PsiClass aClass = classType.resolve();
    if (aClass == null) {
      return null;
    }
    for (String typeName : typeNames) {
      if (InheritanceUtil.isInheritor(aClass, typeName)) {
        return typeName;
      }
    }
    return null;
  }

  public static boolean expressionHasTypeOrSubtype(@Nullable PsiExpression expression, @NonNls @Nonnull Iterable<String> typeNames) {
    if (expression == null) {
      return false;
    }
    final PsiType type = expression.getType();
    if (type == null) {
      return false;
    }
    if (!(type instanceof PsiClassType)) {
      return false;
    }
    final PsiClassType classType = (PsiClassType) type;
    final PsiClass aClass = classType.resolve();
    if (aClass == null) {
      return false;
    }
    for (String typeName : typeNames) {
      if (InheritanceUtil.isInheritor(aClass, typeName)) {
        return true;
      }
    }
    return false;
  }

  public static boolean variableHasTypeOrSubtype(@Nullable PsiVariable variable, @NonNls @Nonnull String... typeNames) {
    if (variable == null) {
      return false;
    }
    final PsiType type = variable.getType();
    if (!(type instanceof PsiClassType)) {
      return false;
    }
    final PsiClassType classType = (PsiClassType) type;
    final PsiClass aClass = classType.resolve();
    if (aClass == null) {
      return false;
    }
    for (String typeName : typeNames) {
      if (InheritanceUtil.isInheritor(aClass, typeName)) {
        return true;
      }
    }
    return false;
  }

  public static boolean hasFloatingPointType(@Nullable PsiExpression expression) {
    if (expression == null) {
      return false;
    }
    final PsiType type = expression.getType();
    return type != null && (PsiType.FLOAT.equals(type) || PsiType.DOUBLE.equals(type));
  }

  public static boolean areConvertible(PsiType type1, PsiType type2) {
    if (TypeConversionUtil.areTypesConvertible(type1, type2)) {
      return true;
    }
    final PsiType comparedTypeErasure = TypeConversionUtil.erasure(type1);
    final PsiType comparisonTypeErasure = TypeConversionUtil.erasure(type2);
    if (comparedTypeErasure == null || comparisonTypeErasure == null ||
        TypeConversionUtil.areTypesConvertible(comparedTypeErasure, comparisonTypeErasure)) {
      if (type1 instanceof PsiClassType && type2 instanceof PsiClassType) {
        final PsiClassType classType1 = (PsiClassType) type1;
        final PsiClassType classType2 = (PsiClassType) type2;
        final PsiType[] parameters1 = classType1.getParameters();
        final PsiType[] parameters2 = classType2.getParameters();
        if (parameters1.length != parameters2.length) {
          return ((PsiClassType) type1).isRaw() || ((PsiClassType) type2).isRaw();
        }
        for (int i = 0; i < parameters1.length; i++) {
          if (!areConvertible(parameters1[i], parameters2[i])) {
            return false;
          }
        }
      }
      return true;
    }
    return false;
  }

  public static boolean isTypeParameter(PsiType type) {
    if (!(type instanceof PsiClassType)) {
      return false;
    }
    final PsiClassType classType = (PsiClassType) type;
    final PsiClass aClass = classType.resolve();
    return aClass != null && aClass instanceof PsiTypeParameter;
  }

  /**
   * JLS 5.6.1 Unary Numeric Promotion
   */
  public static PsiType unaryNumericPromotion(PsiType type) {
    if (type == null) {
      return null;
    }
    if (type.equalsToText(CommonClassNames.JAVA_LANG_BYTE)
        || type.equalsToText(CommonClassNames.JAVA_LANG_SHORT)
        || type.equalsToText(CommonClassNames.JAVA_LANG_CHARACTER)
        || type.equalsToText(CommonClassNames.JAVA_LANG_INTEGER)
        || type.equals(PsiType.BYTE)
        || type.equals(PsiType.SHORT)
        || type.equals(PsiType.CHAR)) {
      return PsiType.INT;
    } else if (type.equalsToText(CommonClassNames.JAVA_LANG_LONG)) {
      return PsiType.LONG;
    } else if (type.equalsToText(CommonClassNames.JAVA_LANG_FLOAT)) {
      return PsiType.FLOAT;
    } else if (type.equalsToText(CommonClassNames.JAVA_LANG_DOUBLE)) {
      return PsiType.DOUBLE;
    }
    return type;
  }

  /**
   * Returns a textual representation of default value representable by given type
   *
   * @param type type to get the default value for
   * @return the textual representation of default value
   */
  @NonNls
  public static String getDefaultValue(PsiType type) {
    if (PsiType.INT.equals(type)) {
      return "0";
    } else if (PsiType.LONG.equals(type)) {
      return "0L";
    } else if (PsiType.DOUBLE.equals(type)) {
      return "0.0";
    } else if (PsiType.FLOAT.equals(type)) {
      return "0.0F";
    } else if (PsiType.SHORT.equals(type)) {
      return "(short)0";
    } else if (PsiType.BYTE.equals(type)) {
      return "(byte)0";
    } else if (PsiType.BOOLEAN.equals(type)) {
      return PsiKeyword.FALSE;
    } else if (PsiType.CHAR.equals(type)) {
      return "'\0'";
    }
    return PsiKeyword.NULL;
  }
}