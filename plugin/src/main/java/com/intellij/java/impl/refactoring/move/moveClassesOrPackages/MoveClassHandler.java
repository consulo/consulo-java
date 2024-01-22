/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import consulo.language.psi.PsiDirectory;
import consulo.language.util.IncorrectOperationException;
import consulo.usage.UsageInfo;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;

/**
 * @author Maxim.Medvedev
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface MoveClassHandler {
	ExtensionPointName<MoveClassHandler> EP_NAME = ExtensionPointName.create(MoveClassHandler.class);

  void prepareMove(@Nonnull PsiClass aClass);

  void finishMoveClass(@Nonnull PsiClass aClass);

  /**
   * @return null if it cannot move aClass
   */
  @Nullable
  PsiClass doMoveClass(@Nonnull PsiClass aClass, @Nonnull PsiDirectory moveDestination) throws IncorrectOperationException;

  /**
   * @param clazz psiClass
   * @return null, if this instance of FileNameForPsiProvider cannot provide name for clazz
   */
  String getName(PsiClass clazz);

  void preprocessUsages(Collection<UsageInfo> results);
}
