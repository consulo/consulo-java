// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis;

import com.intellij.java.codeserver.core.JavaPsiModuleUtil;
import com.intellij.java.codeserver.core.JpmsModuleAccessInfo;
import com.intellij.java.codeserver.core.JpmsModuleInfo;
import com.intellij.java.language.psi.PsiJavaModule;
import com.intellij.java.language.psi.PsiPackageAccessibilityStatement;
import com.intellij.java.language.psi.util.JavaModuleGraphHelper;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ServiceImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.util.collection.ContainerUtil;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Helper service to support resolve in java-language-impl
 */
@Singleton
@ServiceImpl
public final class JavaModuleGraphHelperImpl implements JavaModuleGraphHelper {
  @Override
  @RequiredReadAction
  public List<PsiPackageAccessibilityStatement> getExportedPackages(PsiElement place, PsiJavaModule module) {
    return JavaPsiModuleUtil.getExportedPackages(place, module);
  }

  @Override
  @RequiredReadAction
  public boolean isAccessible(String targetPackageName, @Nullable PsiFile targetFile, PsiElement place) {
    PsiFile useFile = place.getContainingFile() != null ? place.getContainingFile().getOriginalFile() : null;
    if (useFile == null) return true;
    List<JpmsModuleInfo.TargetModuleInfo> infos = JpmsModuleInfo.findTargetModuleInfos(targetPackageName, targetFile, useFile);
    if (infos == null) return true;
    return !infos.isEmpty() && ContainerUtil.exists(
      infos, info -> info.accessAt(useFile).checkAccess(useFile, JpmsModuleAccessInfo.JpmsModuleAccessMode.EXPORT) == null);
  }

  @Override
  @RequiredReadAction
  public boolean isAccessible(PsiJavaModule targetModule, PsiElement place) {
    PsiFile useFile = place.getContainingFile() != null ? place.getContainingFile().getOriginalFile() : null;
    if (useFile == null) return true;
    return new JpmsModuleInfo.TargetModuleInfoByJavaModule(targetModule, "").accessAt(useFile).checkModuleAccess(place) == null;
  }
}
