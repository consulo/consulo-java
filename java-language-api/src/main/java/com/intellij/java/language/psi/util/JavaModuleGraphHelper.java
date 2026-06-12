// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.psi.util;

import com.intellij.java.language.psi.PsiJavaModule;
import com.intellij.java.language.psi.PsiPackageAccessibilityStatement;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.application.Application;
import consulo.language.psi.PsiElement;

import java.util.List;

/**
 * Bridges the module dependency graph (computed in a higher module) to lower-level PSI consumers.
 */
@ServiceAPI(ComponentScope.APPLICATION)
public interface JavaModuleGraphHelper {
  static JavaModuleGraphHelper getInstance() {
    return Application.get().getInstance(JavaModuleGraphHelper.class);
  }

  /**
   * Retrieves a list of package accessibility statements for a given Java module that
   * are accessible to the specified place.
   *
   * @param place  the place from which accessibility is being checked.
   * @param module the module whose exported packages are to be retrieved.
   * @return a list of {@link PsiPackageAccessibilityStatement} elements that represent the exported packages accessible
   * to the specified place.
   */
  List<PsiPackageAccessibilityStatement> getExportedPackages(PsiElement place, PsiJavaModule module);
}
