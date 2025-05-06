/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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

import com.intellij.java.analysis.impl.codeInspection.concurrencyAnnotations.JCiPUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Contract;

import jakarta.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

public class ClassUtils {

  /**
   * @noinspection StaticCollection
   */
  private static final Set<String> immutableTypes = new HashSet<String>(19);

  /**
   * @noinspection StaticCollection
   */
  private static final Set<PsiType> primitiveNumericTypes = new HashSet<PsiType>(7);

  /**
   * @noinspection StaticCollection
   */
  private static final Set<PsiType> integralTypes = new HashSet<PsiType>(5);

  static {
    integralTypes.add(PsiType.LONG);
    integralTypes.add(PsiType.INT);
    integralTypes.add(PsiType.SHORT);
    integralTypes.add(PsiType.CHAR);
    integralTypes.add(PsiType.BYTE);

    primitiveNumericTypes.add(PsiType.BYTE);
    primitiveNumericTypes.add(PsiType.CHAR);
    primitiveNumericTypes.add(PsiType.SHORT);
    primitiveNumericTypes.add(PsiType.INT);
    primitiveNumericTypes.add(PsiType.LONG);
    primitiveNumericTypes.add(PsiType.FLOAT);
    primitiveNumericTypes.add(PsiType.DOUBLE);

    immutableTypes.add(JavaClassNames.JAVA_LANG_BOOLEAN);
    immutableTypes.add(JavaClassNames.JAVA_LANG_CHARACTER);
    immutableTypes.add(JavaClassNames.JAVA_LANG_SHORT);
    immutableTypes.add(JavaClassNames.JAVA_LANG_INTEGER);
    immutableTypes.add(JavaClassNames.JAVA_LANG_LONG);
    immutableTypes.add(JavaClassNames.JAVA_LANG_FLOAT);
    immutableTypes.add(JavaClassNames.JAVA_LANG_DOUBLE);
    immutableTypes.add(JavaClassNames.JAVA_LANG_BYTE);
    immutableTypes.add(JavaClassNames.JAVA_LANG_STRING);
    immutableTypes.add("java.awt.Font");
    immutableTypes.add("java.awt.Color");
    immutableTypes.add("java.math.BigDecimal");
    immutableTypes.add("java.math.BigInteger");
    immutableTypes.add("java.math.MathContext");
    immutableTypes.add("java.nio.channels.FileLock");
    immutableTypes.add("java.nio.charset.Charset");
    immutableTypes.add(JavaClassNames.JAVA_IO_FILE);
    immutableTypes.add("java.net.URI");
    immutableTypes.add("java.util.regex.Pattern");
  }

  private ClassUtils() {
  }

  @Nullable
  public static PsiClass findClass(String fqClassName, PsiElement context) {
    return JavaPsiFacade.getInstance(context.getProject()).findClass(fqClassName, context.getResolveScope());
  }

  @Nullable
  public static PsiClass findObjectClass(PsiElement context) {
    return findClass(JavaClassNames.JAVA_LANG_OBJECT, context);
  }

  public static boolean isPrimitive(PsiType type) {
    return TypeConversionUtil.isPrimitiveAndNotNull(type);
  }

  public static boolean isIntegral(PsiType type) {
    return integralTypes.contains(type);
  }

  /**
   * Checks whether given type represents a known immutable value (which visible state cannot be changed).
   * This call is equivalent to {@code isImmutable(type, true)}.
   *
   * @param type type to check
   * @return true if type is known to be immutable; false otherwise
   */
  @Contract("null -> false")
  public static boolean isImmutable(@Nullable PsiType type) {
    return isImmutable(type, true);
  }

  /**
   * Checks whether given type represents a known immutable value (which visible state cannot be changed).
   *
   * @param type            type to check
   * @param checkDocComment if true JavaDoc comment will be checked for {@code @Immutable} tag (which may cause AST loading).
   * @return true if type is known to be immutable; false otherwise
   */
  @Contract("null,_ -> false")
  public static boolean isImmutable(@Nullable PsiType type, boolean checkDocComment) {
    if (TypeConversionUtil.isPrimitiveAndNotNull(type)) {
      return true;
    }
    final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
    if (aClass == null) {
      return false;
    }
    if (isImmutableClass(aClass)) {
      return true;
    }
    return JCiPUtil.isImmutable(aClass, checkDocComment);
  }

  public static boolean isImmutableClass(@Nonnull PsiClass aClass) {
    String qualifiedName = aClass.getQualifiedName();
    return qualifiedName != null && (immutableTypes.contains(qualifiedName) ||
        qualifiedName.startsWith("com.google.common.collect.Immutable"));
  }

  public static boolean inSamePackage(@Nullable PsiElement element1,
                                      @Nullable PsiElement element2) {
    if (element1 == null || element2 == null) {
      return false;
    }
    final PsiFile containingFile1 = element1.getContainingFile();
    if (!(containingFile1 instanceof PsiClassOwner)) {
      return false;
    }
    final PsiClassOwner containingJavaFile1 =
        (PsiClassOwner) containingFile1;
    final String packageName1 = containingJavaFile1.getPackageName();
    final PsiFile containingFile2 = element2.getContainingFile();
    if (!(containingFile2 instanceof PsiClassOwner)) {
      return false;
    }
    final PsiClassOwner containingJavaFile2 =
        (PsiClassOwner) containingFile2;
    final String packageName2 = containingJavaFile2.getPackageName();
    return packageName1.equals(packageName2);
  }

  public static boolean isFieldVisible(PsiField field, PsiClass fromClass) {
    final PsiClass fieldClass = field.getContainingClass();
    if (fieldClass == null) {
      return false;
    }
    if (fieldClass.equals(fromClass)) {
      return true;
    }
    if (field.hasModifierProperty(PsiModifier.PRIVATE)) {
      return false;
    }
    if (field.hasModifierProperty(PsiModifier.PUBLIC) ||
        field.hasModifierProperty(PsiModifier.PROTECTED)) {
      return true;
    }
    return inSamePackage(fieldClass, fromClass);
  }

  public static boolean isPrimitiveNumericType(PsiType type) {
    return primitiveNumericTypes.contains(type);
  }

  public static boolean isInnerClass(PsiClass aClass) {
    final PsiClass parentClass = getContainingClass(aClass);
    return parentClass != null;
  }

  @Nullable
  public static PsiClass getContainingClass(PsiElement element) {
    return PsiTreeUtil.getParentOfType(element, PsiClass.class);
  }

  public static PsiClass getOutermostContainingClass(PsiClass aClass) {
    PsiClass outerClass = aClass;
    while (true) {
      final PsiClass containingClass = getContainingClass(outerClass);
      if (containingClass != null) {
        outerClass = containingClass;
      } else {
        return outerClass;
      }
    }
  }

  @Nullable
  public static PsiClass getContainingStaticClass(PsiElement element) {
    PsiClass aClass = PsiTreeUtil.getParentOfType(element, PsiClass.class, false, PsiFile.class);
    while (isNonStaticClass(aClass)) {
      aClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class, true, PsiFile.class);
    }
    return aClass;
  }

  public static boolean isNonStaticClass(@Nullable PsiClass aClass) {
    if (aClass == null) {
      return false;
    }
    if (aClass.hasModifierProperty(PsiModifier.STATIC) || aClass.isInterface() || aClass.isEnum()) {
      return false;
    }
    if (aClass instanceof PsiAnonymousClass) {
      return true;
    }
    final PsiElement parent = aClass.getParent();
    if (parent == null || parent instanceof PsiFile) {
      return false;
    }
    if (!(parent instanceof PsiClass)) {
      return true;
    }
    final PsiClass parentClass = (PsiClass) parent;
    return !parentClass.isInterface();
  }

  public static boolean isClassVisibleFromClass(PsiClass baseClass,
                                                PsiClass referencedClass) {
    if (referencedClass.hasModifierProperty(PsiModifier.PUBLIC)) {
      return true;
    } else if (referencedClass.hasModifierProperty(PsiModifier.PROTECTED)) {
      return inSamePackage(baseClass, referencedClass);
    } else if (referencedClass.hasModifierProperty(PsiModifier.PRIVATE)) {
      return PsiTreeUtil.findCommonParent(baseClass, referencedClass) !=
          null;
    } else {
      return inSamePackage(baseClass, referencedClass);
    }
  }
}