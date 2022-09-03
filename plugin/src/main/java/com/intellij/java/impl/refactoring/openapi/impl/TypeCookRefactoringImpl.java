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
package com.intellij.java.impl.refactoring.openapi.impl;

import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.editor.refactoring.RefactoringImpl;
import com.intellij.java.impl.refactoring.TypeCookRefactoring;
import com.intellij.java.impl.refactoring.typeCook.Settings;
import com.intellij.java.impl.refactoring.typeCook.TypeCookProcessor;

import java.util.List;

/**
 * @author dsl
 */
public class TypeCookRefactoringImpl extends RefactoringImpl<TypeCookProcessor> implements TypeCookRefactoring {
  TypeCookRefactoringImpl(Project project,
                          PsiElement[] elements,
                          final boolean dropObsoleteCasts,
                          final boolean leaveObjectsRaw,
                          final boolean preserveRawArrays,
                          final boolean exhaustiveSearch,
                          final boolean cookObjects,
                          final boolean cookToWildcards) {
    super(new TypeCookProcessor(project, elements, new Settings() {
      public boolean dropObsoleteCasts() {
        return dropObsoleteCasts;
      }

      public boolean leaveObjectParameterizedTypesRaw() {
        return leaveObjectsRaw;
      }

      public boolean exhaustive() {
        return exhaustiveSearch;
      }

      public boolean cookObjects() {
        return cookObjects;
      }

      public boolean cookToWildcards() {
        return cookToWildcards;
      }

      public boolean preserveRawArrays() {
        return preserveRawArrays;
      }
    }));
  }

  public List<PsiElement> getElements() {
    return myProcessor.getElements();
  }
}
