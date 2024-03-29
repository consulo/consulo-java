/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package consulo.java.impl.intelliLang.util;

import com.intellij.java.language.psi.*;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.util.collection.SmartList;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.Ref;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class that can compute the prefix and suffix of an expression inside a binary (usually additive) expression
 * that computes the values not only for compile-time constants, but also for elements annotated with a substitution
 * annotation.
 *
 * @see SubstitutedExpressionEvaluationHelper
 */
public class ContextComputationProcessor {

  private final SubstitutedExpressionEvaluationHelper myEvaluationHelper;

  private ContextComputationProcessor(final Project project) {
    myEvaluationHelper = new SubstitutedExpressionEvaluationHelper(project);
  }

  @Nonnull
  public static List<Object> collectOperands(@Nonnull final String prefix, final String suffix, final Ref<Boolean> unparsable, final PsiElement[] operands) {
    final ArrayList<Object> result = new ArrayList<Object>();
    final ContextComputationProcessor processor = new ContextComputationProcessor(operands[0].getProject());
    addStringFragment(prefix, result);
    for (PsiElement operand : operands) {
      processor.collectOperands(operand, result, unparsable);
    }
    addStringFragment(suffix, result);
    return result;
  }

  private static void addStringFragment(final String string, final List<Object> result) {
    if (StringUtil.isEmpty(string)) return;
    final int size = result.size();
    final Object last = size > 0? result.get(size -1) : null;
    if (last instanceof String) {
      result.set(size - 1, last + string);
    }
    else {
      result.add(string);
    }
  }

  public void collectOperands(final PsiElement expression, final List<Object> result, final Ref<Boolean> unparsable) {
    if (expression instanceof PsiParenthesizedExpression) {
      collectOperands(((PsiParenthesizedExpression)expression).getExpression(), result, unparsable);
    }
    else if (expression instanceof PsiTypeCastExpression) {
      collectOperands(((PsiTypeCastExpression)expression).getOperand(), result, unparsable);
    }
    else if (expression instanceof PsiConditionalExpression) {
      unparsable.set(Boolean.TRUE);
      collectOperands(((PsiConditionalExpression)expression).getThenExpression(), result, unparsable);
      addStringFragment(" ", result); // do not glue branches together
      collectOperands(((PsiConditionalExpression)expression).getElseExpression(), result, unparsable);
    }
    else if (expression instanceof PsiPolyadicExpression &&
             ((PsiPolyadicExpression)expression).getOperationTokenType() == JavaTokenType.PLUS) {
      final PsiPolyadicExpression binaryExpression = (PsiPolyadicExpression)expression;
      for (PsiExpression operand : binaryExpression.getOperands()) {
        collectOperands(operand, result, unparsable);
      }
    }
    else if (expression instanceof PsiAssignmentExpression &&
             ((PsiAssignmentExpression)expression).getOperationTokenType() == JavaTokenType.PLUSEQ) {
      unparsable.set(Boolean.TRUE);
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression;
      collectOperands(assignmentExpression.getLExpression(), result, unparsable);
      collectOperands(assignmentExpression.getRExpression(), result, unparsable);
    }
    else if (PsiUtilEx.isStringOrCharacterLiteral(expression)) {
      result.add(expression);
    }
    else if (expression instanceof PsiExpression) {
      final SmartList<PsiExpression> uncomputables = new SmartList<PsiExpression>();
      final Object o = myEvaluationHelper.computeExpression((PsiExpression)expression, uncomputables);
      // in many languages 'null' is a reserved word
      addStringFragment(o == null? "missingValue" : String.valueOf(o), result);
      if (uncomputables.size() > 0) {
        unparsable.set(Boolean.TRUE);
      }
    }
    else {
      unparsable.set(Boolean.TRUE);
      result.add(expression);
    }
  }

  @Nonnull
  public static PsiElement getTopLevelInjectionTarget(@Nonnull final PsiElement host) {
    PsiElement target = host;
    PsiElement parent = target.getParent();
    for (; parent != null; target = parent, parent = target.getParent()) {
      if (parent instanceof PsiPolyadicExpression) continue;
      if (parent instanceof PsiParenthesizedExpression) continue;
      if (parent instanceof PsiConditionalExpression && ((PsiConditionalExpression)parent).getCondition() != target) continue;
      if (parent instanceof PsiArrayInitializerMemberValue) continue;
      if (parent instanceof PsiArrayInitializerExpression) {
        parent = parent.getParent(); continue;
      }
      break;
    }
    return target;
  }
}
