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
package com.intellij.testFramework.fixtures;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NonNls;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.java.language.impl.psi.impl.JavaPsiFacadeEx;

/**
 * @author yole
 */
public interface JavaCodeInsightTestFixture extends CodeInsightTestFixture {
  JavaPsiFacadeEx getJavaFacade();

  PsiClass addClass(@Nonnull @NonNls final String classText);

  @Nonnull
  PsiClass findClass(@Nonnull @NonNls String name);

  @Nonnull
  PsiJavaPackage findPackage(@Nonnull @NonNls String name);
}
