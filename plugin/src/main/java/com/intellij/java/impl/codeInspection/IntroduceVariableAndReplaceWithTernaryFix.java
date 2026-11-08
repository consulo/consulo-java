// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.codeInspection;

import com.intellij.java.language.psi.util.PsiTypesUtil;
import consulo.localize.LocalizeValue;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.java.impl.JavaBundle;
import consulo.project.Project;
import com.intellij.java.language.psi.PsiArrayAccessExpression;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiNewExpression;
import com.intellij.java.language.psi.PsiReferenceExpression;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import com.intellij.java.language.psi.util.PsiUtil;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Extracts an expression into a new local variable and replaces the dereference of that variable with a conditional
 * expression that yields a default value when the variable is null.
 * <p>
 * Unlike {@link ReplaceWithTernaryOperatorFix}, this fix is applicable to expressions that the dataflow analysis cannot
 * represent as a stable value (e.g., {@code arr[i]} or {@code map.get(key)}), as well as to expressions with side effects:
 * the generated condition tests the variable, so the original expression is neither re-evaluated nor left unchecked.
 */
public final class IntroduceVariableAndReplaceWithTernaryFix implements LocalQuickFix {
  private final SmartPsiElementPointer<PsiExpression> myExpressionPointer;
  private final String myVariableName;

  private IntroduceVariableAndReplaceWithTernaryFix(PsiExpression expression, String variableName) {
    myExpressionPointer = SmartPointerManager.getInstance(expression.getProject()).createSmartPsiElementPointer(expression);
    myVariableName = variableName;
  }

  /**
   * @param qualifier expression to extract into a local variable and to check for null
   * @return a new fix, or null if the expression cannot be extracted into a local variable
   */
  public static @Nullable IntroduceVariableAndReplaceWithTernaryFix create(PsiExpression qualifier) {
    // if nothing is dereferenced, the conditional expression would simply yield the variable back
    if (getOutermostDereference(qualifier) == qualifier) return null;
    List<String> names = ExtractedVariableInfo.suggestNames(qualifier);
    if (names.isEmpty()) return null;
    return new IntroduceVariableAndReplaceWithTernaryFix(qualifier, names.getFirst());
  }

  @Override
  public LocalizeValue getName() {
    return LocalizeValue.of(JavaBundle.message("inspection.introduce.variable.and.replace.ternary.quickfix", myVariableName));
  }


  @Override
  public void applyFix(Project project, ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getStartElement();
    PsiExpression qualifier = myExpressionPointer.getElement();
    if (qualifier == null) return;
    ExtractedVariableInfo extracted = ExtractedVariableInfo.extract(qualifier);
    if (extracted == null) return;

    PsiExpression dereference = getOutermostDereference(extracted.reference());
    // JetBrains' helper also starts a template on the default value; consulo's helper only replaces
    ReplaceWithTernaryOperatorFix.replaceWithConditionalExpression(
      project, extracted.variable().getName() + "!=null", dereference, PsiTypesUtil.getDefaultValueOfType(dereference.getType()));

    // JetBrains starts a rename template of the new variable here (ModTemplateBuilder); a quick fix has no updater for it
  }

  /**
   * @param expression expression that may be dereferenced
   * @return the outermost expression that dereferences the given one, and thus produces a value only if the given
   * expression is not null; the expression itself if it's not dereferenced
   */
  private static PsiExpression getOutermostDereference(PsiExpression expression) {
    PsiExpression result = expression;
    while (true) {
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(result.getParent());
      if (parent instanceof PsiReferenceExpression reference && PsiUtil.skipParenthesizedExprDown(reference.getQualifierExpression()) == result ||
          parent instanceof PsiMethodCallExpression call && call.getMethodExpression() == result ||
          parent instanceof PsiArrayAccessExpression access && PsiUtil.skipParenthesizedExprDown(access.getArrayExpression()) == result ||
          parent instanceof PsiNewExpression creation && PsiUtil.skipParenthesizedExprDown(creation.getQualifier()) == result) {
        result = (PsiExpression)parent;
      }
      else {
        return result;
      }
    }
  }
}
