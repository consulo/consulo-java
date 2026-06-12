// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis;

import com.intellij.java.language.impl.psi.impl.light.LightJavaModule;
import com.intellij.java.language.psi.PsiJavaModule;
import com.intellij.java.language.psi.PsiPackageAccessibilityStatement;
import com.intellij.java.language.psi.util.JavaModuleGraphHelper;
import consulo.annotation.component.ServiceImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import jakarta.inject.Singleton;

import org.jspecify.annotations.Nullable;
import java.util.List;

@Singleton
@ServiceImpl
public final class JavaModuleGraphHelperImpl implements JavaModuleGraphHelper {
  @Override
  public List<PsiPackageAccessibilityStatement> getExportedPackages(PsiElement place, PsiJavaModule module) {
    return JavaModuleGraphUtil.getExportedPackages(place, module);
  }

  @Override
  public boolean isAccessible(String targetPackageName, @Nullable PsiFile targetFile, PsiElement place) {
    PsiJavaModule refModule = JavaModuleGraphUtil.findDescriptorByElement(place);
    if (refModule == null) return true;
    PsiJavaModule targetModule = JavaModuleGraphUtil.findDescriptorByElement(targetFile);
    if (targetModule == null || refModule.equals(targetModule)) return true;

    String requiredName = targetModule.getName();
    if (!(targetModule instanceof LightJavaModule || JavaModuleGraphUtil.exports(targetModule, targetPackageName, refModule))) {
      return false;
    }
    return PsiJavaModule.JAVA_BASE.equals(requiredName) || JavaModuleGraphUtil.reads(refModule, targetModule);
  }
}
