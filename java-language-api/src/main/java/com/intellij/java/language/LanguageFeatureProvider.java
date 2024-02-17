// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.language.psi.PsiFile;
import consulo.util.lang.ThreeState;
import jakarta.annotation.Nonnull;

/**
 * This can be used to modify Java language features availability depending on context (e.g. due to specific runtime implementation).
 *
 * @see JavaFeature
 */
@ExtensionAPI(ComponentScope.PROJECT)
public interface LanguageFeatureProvider {
  /**
   * @return {@link ThreeState#YES} or {@link ThreeState#NO} to alternate default ({@link LanguageLevel}-based) availability,
   * or {@link ThreeState#UNSURE} otherwise.
   */
  @Nonnull
  ThreeState isFeatureSupported(@Nonnull JavaFeature feature, @Nonnull PsiFile file);
}
