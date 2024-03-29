
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
package com.intellij.java.impl.codeInsight.generation.surroundWith;

import jakarta.annotation.Nonnull;

import consulo.language.psi.*;

import consulo.project.Project;
import consulo.codeEditor.Editor;

import consulo.document.util.TextRange;
import consulo.language.util.IncorrectOperationException;
import consulo.language.editor.surroundWith.Surrounder;

import jakarta.annotation.Nullable;

abstract class JavaStatementsSurrounder implements Surrounder {
  @Override
  public boolean isApplicable(@Nonnull PsiElement[] elements) {
    return true;
  }

  @Override
  @Nullable
  public TextRange surroundElements(@Nonnull Project project,
                                    @Nonnull Editor editor,
                                    @Nonnull PsiElement[] elements) throws IncorrectOperationException {
    PsiElement container = elements[0].getParent();
    if (container == null) return null;
    return surroundStatements (project, editor, container, elements);
  }

 @Nullable
 protected abstract TextRange surroundStatements(final Project project, final Editor editor, final PsiElement container, final PsiElement[] statements) throws IncorrectOperationException;
}