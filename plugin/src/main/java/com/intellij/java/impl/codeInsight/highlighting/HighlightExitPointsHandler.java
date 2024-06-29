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
package com.intellij.java.impl.codeInsight.highlighting;

import com.intellij.java.language.impl.psi.controlFlow.*;
import com.intellij.java.language.psi.*;
import consulo.codeEditor.Editor;
import consulo.externalService.statistic.FeatureUsageTracker;
import consulo.ide.impl.idea.codeInsight.highlighting.HighlightUsagesHandler;
import consulo.ide.impl.idea.featureStatistics.ProductivityFeatureNames;
import consulo.language.editor.highlight.usage.HighlightUsagesHandlerBase;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.util.collection.primitive.ints.IntLists;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

public class HighlightExitPointsHandler extends HighlightUsagesHandlerBase<PsiElement> {
  private final PsiElement myTarget;

  public HighlightExitPointsHandler(final Editor editor, final PsiFile file, final PsiElement target) {
    super(editor, file);
    myTarget = target;
  }

  @Override
  public List<PsiElement> getTargets() {
    return Collections.singletonList(myTarget);
  }

  @Override
  protected void selectTargets(final List<PsiElement> targets, final Consumer<List<PsiElement>> selectionConsumer) {
    selectionConsumer.accept(targets);
  }

  @Override
  public void computeUsages(final List<PsiElement> targets) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed(ProductivityFeatureNames.CODEASSISTS_HIGHLIGHT_RETURN);

    PsiElement parent = myTarget.getParent();
    if (!(parent instanceof PsiReturnStatement) && !(parent instanceof PsiThrowStatement)) {
      return;
    }

    PsiCodeBlock body = null;
    final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(myTarget, PsiLambdaExpression.class);
    if (lambdaExpression != null) {
      final PsiElement lambdaBody = lambdaExpression.getBody();
      if (lambdaBody instanceof PsiCodeBlock codeBlock) {
        body = codeBlock;
      }
    }

    if (body == null) {
      PsiMethod method = PsiTreeUtil.getParentOfType(myTarget, PsiMethod.class);
      body = method != null ? method.getBody() : null;
    }

    if (body == null) {
      return;
    }

    try {
      highlightExitPoints((PsiStatement) parent, body);
    } catch (AnalysisCanceledException e) {
      // ignore
    }
  }

  @Nullable
  private static PsiElement getExitTarget(PsiStatement exitStatement) {
    if (exitStatement instanceof PsiReturnStatement) {
      return PsiTreeUtil.getParentOfType(exitStatement, PsiMethod.class);
    } else if (exitStatement instanceof PsiBreakStatement breakStatement) {
      return breakStatement.findExitedStatement();
    } else if (exitStatement instanceof PsiContinueStatement continueStatement) {
      return continueStatement.findContinuedStatement();
    } else if (exitStatement instanceof PsiThrowStatement throwStatement) {
      final PsiExpression expr = throwStatement.getException();
      if (expr == null) {
        return null;
      }
      final PsiType exceptionType = expr.getType();
      if (!(exceptionType instanceof PsiClassType)) {
        return null;
      }

      PsiElement target = exitStatement;
      while (!(target instanceof PsiMethod || target == null || target instanceof PsiClass || target instanceof PsiFile)) {
        if (target instanceof PsiTryStatement tryStatement) {
          final PsiParameter[] params = tryStatement.getCatchBlockParameters();
          for (PsiParameter param : params) {
            if (param.getType().isAssignableFrom(exceptionType)) {
              break;
            }
          }

        }
        target = target.getParent();
      }
      if (target instanceof PsiMethod || target instanceof PsiTryStatement) {
        return target;
      }
      return null;
    }

    return null;
  }

  private void highlightExitPoints(final PsiStatement parent, final PsiCodeBlock body) throws AnalysisCanceledException {
    final Project project = myTarget.getProject();
    ControlFlow flow = ControlFlowFactory.getInstance(project).getControlFlow(body, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(),
        false);

    Collection<PsiStatement> exitStatements = ControlFlowUtil.findExitPointsAndStatements(flow, 0, flow.getSize(), IntLists.newArrayList(),
        PsiReturnStatement.class, PsiBreakStatement.class, PsiContinueStatement.class, PsiThrowStatement.class);
    if (!exitStatements.contains(parent)) {
      return;
    }

    PsiElement originalTarget = getExitTarget(parent);

    final Iterator<PsiStatement> it = exitStatements.iterator();
    while (it.hasNext()) {
      PsiStatement psiStatement = it.next();
      if (getExitTarget(psiStatement) != originalTarget) {
        it.remove();
      }
    }

    for (PsiElement e : exitStatements) {
      addOccurrence(e);
    }
    myStatusText =
      CodeInsightLocalize.statusBarExitPointsHighlightedMessage(exitStatements.size(), HighlightUsagesHandler.getShortcutText()).get();
  }
}
