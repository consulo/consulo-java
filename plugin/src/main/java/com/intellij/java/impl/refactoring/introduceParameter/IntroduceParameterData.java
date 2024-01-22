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
package com.intellij.java.impl.refactoring.introduceParameter;

import consulo.project.Project;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiType;
import consulo.util.collection.primitive.ints.IntList;
import org.intellij.lang.annotations.MagicConstant;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import static com.intellij.java.impl.refactoring.IntroduceParameterRefactoring.*;

public interface IntroduceParameterData {
  @Nonnull
  Project getProject();

  PsiMethod getMethodToReplaceIn();

  @jakarta.annotation.Nonnull
  PsiMethod getMethodToSearchFor();

  ExpressionWrapper getParameterInitializer();

  @Nonnull
  String getParameterName();

  @MagicConstant(intValues = {REPLACE_FIELDS_WITH_GETTERS_ALL, REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, REPLACE_FIELDS_WITH_GETTERS_NONE})
  int getReplaceFieldsWithGetters();

  boolean isDeclareFinal();

  boolean isGenerateDelegate();

  @Nonnull
  PsiType getForcedType();

  @Nonnull
  IntList getParametersToRemove();

  interface ExpressionWrapper<RealExpression extends PsiElement> {
    @Nonnull
    String getText();

    @Nullable
    PsiType getType();

    @jakarta.annotation.Nonnull
    RealExpression getExpression();

  }
}
