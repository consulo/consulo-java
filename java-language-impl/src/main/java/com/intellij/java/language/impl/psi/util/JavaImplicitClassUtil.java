// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.util;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiImplicitClass;
import com.intellij.java.language.psi.PsiJavaFile;
import consulo.language.psi.PsiElement;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public final class JavaImplicitClassUtil {
  public static boolean isFileWithImplicitClass(@Nonnull PsiElement file) {
    return getImplicitClassFor(file) != null;
  }

  /**
   * Retrieves the implicitly declared class PSI element from the given PsiFile.
   *
   * @param file the PsiFile from which to retrieve the implicitly declared class
   * @return the implicitly declared class if found, null otherwise
   */
  @Nullable
  public static PsiImplicitClass getImplicitClassFor(@Nonnull PsiElement file) {
    if (file instanceof PsiJavaFile) {
      final PsiJavaFile javaFile = (PsiJavaFile)file;
      PsiClass[] classes = javaFile.getClasses();
      if (classes.length == 1 && classes[0] instanceof PsiImplicitClass) {
        return (PsiImplicitClass)classes[0];
      }
    }
    return null;
  }

  /**
   * @param name the name of the implicitly declared class (might include the ".java" extension)
   * @return the JVM name of the implicitly declared class
   */
  public static String getJvmName(@Nonnull String name) {
    return StringUtil.trimEnd(name, ".java", true);
  }
}
