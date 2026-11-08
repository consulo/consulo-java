// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.fix;

import com.intellij.java.analysis.impl.codeInspection.SideEffectChecker;
import com.intellij.java.analysis.impl.refactoring.extractMethod.ExtractMethodUtil;
import com.intellij.java.language.impl.codeInsight.BlockUtils;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.psiutils.CodeBlockSurrounder;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.StatementExtractor;
import consulo.language.editor.inspection.LocalQuickFixOnPsiElement;
import consulo.language.editor.localize.CommonQuickFixLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.lang.ObjectUtil;

import java.util.List;
import java.util.Objects;

/**
 * JetBrains' fix is a ModCommand that lets the user choose between extracting and deleting possible side effects;
 * as a quick fix it extracts them.
 */
public class ReplaceWithConstantValueFix extends LocalQuickFixOnPsiElement {
  private final String myPresentableName;
  private final String myReplacementText;

  public ReplaceWithConstantValueFix(PsiExpression expression, String presentableName, String replacementText) {
    super(expression);
    myPresentableName = presentableName;
    myReplacementText = replacementText;
  }

  @Override
  public LocalizeValue getText() {
    return CommonQuickFixLocalize.fixReplaceWithX(myPresentableName);
  }

  @Override
  public void invoke(Project project, PsiFile file, PsiElement startElement, PsiElement endElement) {
    if (!(startElement instanceof PsiExpression expression)) return;
    List<PsiExpression> sideEffects = SideEffectChecker.extractSideEffectExpressions(expression);
    CodeBlockSurrounder surrounder = CodeBlockSurrounder.forExpression(expression);
    if (surrounder == null) {
      sideEffects = List.of();
    }
    PsiStatement[] statements = sideEffects.isEmpty() ? PsiStatement.EMPTY_ARRAY : StatementExtractor.generateStatements(sideEffects, expression);
    applyFix(expression, statements);
  }

  private void applyFix(PsiExpression expression, PsiStatement[] sideEffects) {
    if (sideEffects.length > 0) {
      CodeBlockSurrounder surrounder = Objects.requireNonNull(CodeBlockSurrounder.forExpression(expression));
      CodeBlockSurrounder.SurroundResult result = surrounder.surround();
      expression = result.getExpression();
      BlockUtils.addBefore(result.getAnchor(), sideEffects);
    }
    PsiMethodCallExpression call = expression.getParent() instanceof PsiExpressionList list &&
                                   list.getParent() instanceof PsiMethodCallExpression grandParent ? grandParent : null;
    PsiMethod targetMethod = null;
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    if (call != null) {
      JavaResolveResult result = call.resolveMethodGenerics();
      substitutor = result.getSubstitutor();
      targetMethod = ObjectUtil.tryCast(result.getElement(), PsiMethod.class);
    }

    new CommentTracker().replaceAndRestoreComments(expression, myReplacementText);

    if (targetMethod != null) {
      ExtractMethodUtil.addCastsToEnsureResolveTarget(targetMethod, substitutor, call);
    }
  }
}
