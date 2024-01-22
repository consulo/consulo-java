
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
package com.intellij.java.analysis.codeInsight.guess;

import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiType;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.document.util.TextRange;
import consulo.ide.ServiceManager;
import consulo.project.Project;
import consulo.util.collection.MultiMap;

import jakarta.annotation.Nonnull;
import java.util.List;

@ServiceAPI(ComponentScope.PROJECT)
public abstract class GuessManager {
  public static GuessManager getInstance(Project project) {
    return ServiceManager.getService(project, GuessManager.class);
  }

  @Nonnull
  public abstract PsiType[] guessContainerElementType(PsiExpression containerExpr, TextRange rangeToIgnore);

  @jakarta.annotation.Nonnull
  public abstract PsiType[] guessTypeToCast(PsiExpression expr);

  @Nonnull
  public abstract MultiMap<PsiExpression, PsiType> getControlFlowExpressionTypes(@Nonnull PsiExpression forPlace, boolean honorAssignments);

  @Nonnull
  public List<PsiType> getControlFlowExpressionTypeConjuncts(@Nonnull PsiExpression expr) {
    return getControlFlowExpressionTypeConjuncts(expr, true);
  }

  @Nonnull
  public abstract List<PsiType> getControlFlowExpressionTypeConjuncts(@Nonnull PsiExpression expr, boolean honorAssignments);
}