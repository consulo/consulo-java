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

import com.intellij.java.language.JavaLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.surroundWith.SurroundDescriptor;
import consulo.language.editor.surroundWith.Surrounder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import com.intellij.java.impl.codeInsight.CodeInsightUtil;
import consulo.externalService.statistic.FeatureUsageTracker;
import jakarta.annotation.Nonnull;

/**
 * @author ven
 */
@ExtensionImpl
public class JavaStatementsSurroundDescriptor implements SurroundDescriptor {
  private static final Surrounder[] SURROUNDERS = {
    new JavaWithIfSurrounder(),
    new JavaWithIfElseSurrounder(),
    new JavaWithWhileSurrounder(),
    new JavaWithDoWhileSurrounder(),
    new JavaWithForSurrounder(),

    new JavaWithTryCatchSurrounder(),
    new JavaWithTryFinallySurrounder(),
    new JavaWithTryCatchFinallySurrounder(),
    new JavaWithSynchronizedSurrounder(),
    new JavaWithRunnableSurrounder(),

    new JavaWithBlockSurrounder()
  };

  @Override
  @Nonnull
  public Surrounder[] getSurrounders() {
    return SURROUNDERS;
  }

  @Override
  public boolean isExclusive() {
    return false;
  }

  @Override
  @Nonnull
  public PsiElement[] getElementsToSurround(PsiFile file, int startOffset, int endOffset) {
    PsiElement[] statements = CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset);
    if (statements.length == 0) return PsiElement.EMPTY_ARRAY;
    FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.surroundwith.statement");
    return statements;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
