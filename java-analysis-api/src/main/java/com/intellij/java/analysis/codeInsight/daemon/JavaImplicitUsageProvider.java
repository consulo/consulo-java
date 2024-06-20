package com.intellij.java.analysis.codeInsight.daemon;

import consulo.language.editor.ImplicitUsageProvider;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;

public interface JavaImplicitUsageProvider extends ImplicitUsageProvider {
  /**
   * @return true if the given element is implicitly initialized to a non-null value
   */
  default boolean isImplicitlyNotNullInitialized(@Nonnull PsiElement element) {
    return false;
  }

  /**
   * @return true if given element is represents a class (or another data structure declaration depending on language)
   * which instances may have implicit initialization steps not directly available in the source code
   * (e.g. Java class initializer is processed via annotation processor and custom steps added)
   */
  default boolean isClassWithCustomizedInitialization(@Nonnull PsiElement element) {
    return false;
  }

  // is "unused import" warning can be shown in this file
  default boolean isUnusedImportEnabled(@Nonnull PsiElement element) {
    return false;
  }

  static boolean isClassWithCustomizedInitialization(ImplicitUsageProvider provider, PsiElement element) {
    return provider instanceof JavaImplicitUsageProvider && ((JavaImplicitUsageProvider)provider).isClassWithCustomizedInitialization(
      element);
  }
}
