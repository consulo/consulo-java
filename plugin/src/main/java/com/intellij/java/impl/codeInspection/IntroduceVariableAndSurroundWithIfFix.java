// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.codeInspection;

import consulo.localize.LocalizeValue;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.LocalQuickFix;
import com.intellij.java.impl.codeInsight.generation.surroundWith.JavaWithIfSurrounder;
import consulo.java.impl.JavaBundle;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiIfStatement;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import com.intellij.java.impl.ipp.trivialif.MergeIfAndIntention;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Extracts an expression into a new local variable, uses the variable in the place of the original expression,
 * and surrounds the statement with an {@code if} that checks the variable.
 * <p>
 * Unlike {@link SurroundWithIfFix}, this fix is applicable to expressions that the dataflow analysis cannot represent
 * as a stable value (e.g., {@code arr[i]} or {@code map.get(key)}), as well as to expressions with side effects:
 * the generated condition tests the variable, so the original expression is neither re-evaluated nor left unchecked.
 */
public final class IntroduceVariableAndSurroundWithIfFix implements LocalQuickFix {
  private final SmartPsiElementPointer<PsiExpression> myExpressionPointer;
  private final String mySuffix;
  private final String myVariableName;

  private IntroduceVariableAndSurroundWithIfFix(PsiExpression expression,
                                                String suffix,
                                                String variableName) {
    myExpressionPointer = SmartPointerManager.getInstance(expression.getProject()).createSmartPsiElementPointer(expression);
    mySuffix = suffix;
    myVariableName = variableName;
  }

  /**
   * @param expression expression to extract into a local variable
   * @param suffix     text to append to the variable name to get the condition of the generated {@code if}
   *                   (e.g., {@code " != null"})
   * @return a new fix, or null if the expression cannot be extracted into a local variable
   */
  public static @Nullable IntroduceVariableAndSurroundWithIfFix create(PsiExpression expression, String suffix) {
    List<String> names = ExtractedVariableInfo.suggestNames(expression);
    if (names.isEmpty()) return null;
    return new IntroduceVariableAndSurroundWithIfFix(expression, suffix, names.getFirst());
  }

  @Override
  public LocalizeValue getName() {
    return LocalizeValue.of(JavaBundle.message("inspection.introduce.variable.and.surround.if.quickfix", myVariableName, mySuffix));
  }


  @Override
  public void applyFix(Project project, ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getStartElement();
    PsiExpression expression = myExpressionPointer.getElement();
    if (expression == null) return;
    ExtractedVariableInfo extracted = ExtractedVariableInfo.extract(expression);
    if (extracted == null) return;

    String condition = extracted.variable().getName() + mySuffix;
    PsiIfStatement ifStatement = new JavaWithIfSurrounder()
      .surroundStatements(project, extracted.statement().getParent(), extracted.statementWithSuppression(), condition);
    if (ifStatement == null) return;
    MergeIfAndIntention.mergeWithNestedIf(ifStatement);

    // JetBrains starts a rename template of the new variable here (ModTemplateBuilder); a quick fix has no updater for it
  }
}
