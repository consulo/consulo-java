// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis;

import com.intellij.java.language.psi.PsiJavaModule;
import com.intellij.java.language.psi.PsiPackageAccessibilityStatement;
import com.intellij.java.language.psi.util.JavaModuleGraphHelper;
import consulo.annotation.component.ServiceImpl;
import consulo.language.psi.PsiElement;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
@ServiceImpl
public final class JavaModuleGraphHelperImpl implements JavaModuleGraphHelper {
  @Override
  public List<PsiPackageAccessibilityStatement> getExportedPackages(PsiElement place, PsiJavaModule module) {
    return JavaModuleGraphUtil.getExportedPackages(place, module);
  }
}
