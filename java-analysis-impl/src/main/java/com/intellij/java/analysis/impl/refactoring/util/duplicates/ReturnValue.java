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
package com.intellij.java.analysis.impl.refactoring.util.duplicates;

import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiStatement;
import consulo.language.util.IncorrectOperationException;
import jakarta.annotation.Nullable;

/**
 * @author dsl
 */
public interface ReturnValue {
  boolean isEquivalent(ReturnValue other);

  @Nullable
  PsiStatement createReplacement(final PsiMethod extractedMethod, PsiMethodCallExpression methodCallExpression) throws IncorrectOperationException;
}
