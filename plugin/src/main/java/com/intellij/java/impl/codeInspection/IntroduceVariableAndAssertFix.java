// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.codeInspection;

import consulo.localize.LocalizeValue;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.LocalQuickFix;
import com.intellij.java.impl.codeInspection.AddAssertNonNullFromTestFrameworksFix.Variant;
import consulo.java.impl.JavaBundle;
import consulo.project.Project;
import com.intellij.java.language.psi.JavaPsiFacade;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiStatement;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * Extracts an expression into a new local variable, asserts something about that variable, and uses the variable
 * in the place of the original expression.
 * <p>
 * Unlike {@link AddAssertStatementFix}, this fix is applicable to expressions that the dataflow analysis cannot represent
 * as a stable value (e.g., {@code arr[i]} or {@code map.get(key)}): asserting such an expression in-place does not
 * remove the warning, as the next evaluation of the same expression may produce another value.
 */
public final class IntroduceVariableAndAssertFix implements LocalQuickFix {
  private final SmartPsiElementPointer<PsiExpression> myExpressionPointer;
  private final String myAssertionPrefix;
  private final String myAssertionSuffix;
  private final String myName;

  private IntroduceVariableAndAssertFix(PsiExpression expression,
                                        String assertionPrefix,
                                        String assertionSuffix,
                                        String name) {
    myExpressionPointer = SmartPointerManager.getInstance(expression.getProject()).createSmartPsiElementPointer(expression);
    myAssertionPrefix = assertionPrefix;
    myAssertionSuffix = assertionSuffix;
    myName = name;
  }

  /**
   * @param expression expression to extract into a local variable
   * @param suffix     text to append to the variable name to get the assertion condition (e.g., {@code " != null"})
   * @return a new fix that adds an {@code assert} statement; null if the expression cannot be extracted into a local variable
   */
  public static @Nullable IntroduceVariableAndAssertFix create(PsiExpression expression, String suffix) {
    return create(expression, "assert ", suffix,
                  name -> JavaBundle.message("inspection.introduce.variable.and.assert.quickfix", name + suffix));
  }

  /**
   * @param expression expression to extract into a local variable
   * @param variant    test framework whose {@code assertNotNull} method should be called
   * @return a new fix that adds an {@code assertNotNull} call; null if the expression cannot be extracted into a local variable
   */
  public static @Nullable IntroduceVariableAndAssertFix create(PsiExpression expression, Variant variant) {
    return create(expression, variant.methodReference + "(", ")",
                  name -> JavaBundle.message("inspection.introduce.variable.and.testframework.assert.quickfix",
                                             variant.name, variant.replacement + "(" + name + ")"));
  }

  private static @Nullable IntroduceVariableAndAssertFix create(PsiExpression expression,
                                                                String assertionPrefix,
                                                                String assertionSuffix,
                                                                Function<String, String> presentation) {
    List<String> names = ExtractedVariableInfo.suggestNames(expression);
    if (names.isEmpty()) return null;
    return new IntroduceVariableAndAssertFix(expression, assertionPrefix, assertionSuffix, presentation.apply(names.getFirst()));
  }

  @Override
  public LocalizeValue getName() {
    return LocalizeValue.of(myName);
  }


  @Override
  public void applyFix(Project project, ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getStartElement();
    PsiExpression expression = myExpressionPointer.getElement();
    if (expression == null) return;
    ExtractedVariableInfo extracted = ExtractedVariableInfo.extract(expression);
    if (extracted == null) return;

    String assertionText = myAssertionPrefix + extracted.variable().getName() + myAssertionSuffix + ";";
    PsiStatement assertion = JavaPsiFacade.getElementFactory(project).createStatementFromText(assertionText, extracted.statement());
    PsiElement anchor = extracted.anchor();
    PsiElement addedAssertion = anchor.getParent().addBefore(assertion, anchor);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(addedAssertion);

    // JetBrains starts a rename template of the new variable here (ModTemplateBuilder); a quick fix has no updater for it
  }
}
