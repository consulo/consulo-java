// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.psiutils;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.psi.PsiElement;
import consulo.language.util.ModuleUtilCore;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.ThreeState;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public final class JavaDeprecationUtils {
  @Nonnull
  @RequiredReadAction
  private static ThreeState isDeprecatedByAnnotation(@Nonnull PsiModifierListOwner owner, @Nullable PsiElement context) {
    PsiAnnotation annotation = AnnotationUtil.findAnnotation(owner, CommonClassNames.JAVA_LANG_DEPRECATED);
    if (annotation == null) return ThreeState.UNSURE;
    if (context == null) return ThreeState.YES;
    String since = null;
    PsiAnnotationMemberValue value = annotation.findAttributeValue("since");
    if (value instanceof PsiLiteralExpression) {
      since = ObjectUtil.tryCast(((PsiLiteralExpression)value).getValue(), String.class);
    }
    if (since == null || ModuleUtilCore.getSdk(owner, JavaModuleExtension.class) == null) return ThreeState.YES;
    LanguageLevel deprecationLevel = LanguageLevel.parse(since);
    return ThreeState.fromBoolean(deprecationLevel == null || PsiUtil.getLanguageLevel(context).isAtLeast(deprecationLevel));
  }


  /**
   * Checks if the given PSI element is deprecated with annotation or JavaDoc tag, taking the context into account.
   * <br>
   * It is suitable for elements other than {@link PsiDocCommentOwner}.
   * The deprecation of JDK members may depend on context. E.g., uses if a JDK method is deprecated since Java 19,
   * but current module has Java 17 target, than the method is not considered as deprecated.
   *
   * @param psiElement element to check whether it's deprecated
   * @param context    context in which the check should be performed
   */
  @RequiredReadAction
  public static boolean isDeprecated(@Nonnull PsiElement psiElement, @Nullable PsiElement context) {
    if (psiElement instanceof PsiModifierListOwner) {
      ThreeState byAnnotation = isDeprecatedByAnnotation((PsiModifierListOwner)psiElement, context);
      if (byAnnotation != ThreeState.UNSURE) {
        return byAnnotation.toBoolean();
      }
    }
    if (psiElement instanceof PsiDocCommentOwner) {
      return ((PsiDocCommentOwner)psiElement).isDeprecated();
    }
    if (psiElement instanceof PsiJavaDocumentedElement) {
      return PsiImplUtil.isDeprecatedByDocTag((PsiJavaDocumentedElement)psiElement);
    }
    return false;
  }
}
