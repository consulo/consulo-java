// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.psi.scope;

import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiModifierListOwner;
import consulo.language.psi.resolve.PsiScopeProcessor;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public final class JavaScopeProcessorEvent implements PsiScopeProcessor.Event {
  private JavaScopeProcessorEvent() {
  }

  public static final JavaScopeProcessorEvent START_STATIC = new JavaScopeProcessorEvent();

  /**
   * An event issued by {@link consulo.language.psi.resolve.PsiScopesUtilCore#treeWalkUp}
   * after {@link consulo.language.psi.PsiElement#processDeclarations} was called,
   * for each element in the hierarchy defined by a chain of {@link consulo.language.psi.PsiElement#getContext()} calls.
   * The associated object is the {@link consulo.language.psi.PsiElement} whose declarations have been processed.
   */
  public static final JavaScopeProcessorEvent EXIT_LEVEL = new JavaScopeProcessorEvent();

  public static final JavaScopeProcessorEvent CHANGE_LEVEL = new JavaScopeProcessorEvent();
  public static final JavaScopeProcessorEvent SET_CURRENT_FILE_CONTEXT = new JavaScopeProcessorEvent();

  public static boolean isEnteringStaticScope(@Nonnull PsiScopeProcessor.Event event, @Nullable Object associated) {
    if (event == START_STATIC) return true;

    return event == EXIT_LEVEL &&
      associated instanceof PsiModifierListOwner &&
      ((PsiModifierListOwner)associated).hasModifierProperty(PsiModifier.STATIC);
  }
}
