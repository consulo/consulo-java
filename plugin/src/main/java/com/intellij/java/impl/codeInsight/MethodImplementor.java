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
package com.intellij.java.impl.codeInsight;

import com.intellij.java.language.impl.codeInsight.generation.GenerationInfo;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.language.util.IncorrectOperationException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * @author peter
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface MethodImplementor {
  @Nonnull
  PsiMethod[] getMethodsToImplement(PsiClass aClass);

  @Nonnull
  PsiMethod[] createImplementationPrototypes(final PsiClass inClass, PsiMethod method) throws IncorrectOperationException;

  @Nullable
  GenerationInfo createGenerationInfo(PsiMethod method, boolean mergeIfExists);

  @Nonnull
  Consumer<PsiMethod> createDecorator(PsiClass targetClass, PsiMethod baseMethod, boolean toCopyJavaDoc, boolean insertOverrideIfPossible);
}
