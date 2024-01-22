/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.move.moveClassesOrPackages;

import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.usage.NonCodeUsageInfo;
import consulo.usage.UsageInfo;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * @author Max Medvedev
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface MoveClassToInnerHandler {
  ExtensionPointName<MoveClassToInnerHandler> EP_NAME = ExtensionPointName.create(MoveClassToInnerHandler.class);

  @Nullable
  PsiClass moveClass(@Nonnull PsiClass aClass, @Nonnull PsiClass targetClass);

  /**
   * filters out import usages from results. Returns all found import usages
   */
  List<PsiElement> filterImports(@Nonnull List<UsageInfo> usageInfos, @Nonnull Project project);

  void retargetClassRefsInMoved(@Nonnull Map<PsiElement, PsiElement> mapping);

  void retargetNonCodeUsages(@Nonnull final Map<PsiElement, PsiElement> oldToNewElementMap, @Nonnull NonCodeUsageInfo[] myNonCodeUsages);

  void removeRedundantImports(PsiFile targetClassFile);
}
