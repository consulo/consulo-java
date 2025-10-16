/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.threading;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.java.analysis.impl.codeInspection.ControlFlowUtils;
import com.intellij.java.analysis.impl.codeInspection.EquivalenceChecker;
import com.intellij.java.analysis.impl.codeInspection.SideEffectChecker;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;

@ExtensionImpl
public class DoubleCheckedLockingInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean ignoreOnVolatileVariables = false;

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.doubleCheckedLockingDisplayName();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.doubleCheckedLockingProblemDescriptor().get();
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    LocalizeValue message = InspectionGadgetsLocalize.doubleCheckedLockingIgnoreOnVolatilesOption();
    return new SingleCheckboxOptionsPanel(message.get(), this, "ignoreOnVolatileVariables");
  }

  @Override
  protected InspectionGadgetsFix buildFix(final Object... infos) {
    final PsiField field = (PsiField)infos[0];
    if (field == null) {
      return null;
    }
    return new DoubleCheckedLockingFix(field);
  }

  private static class DoubleCheckedLockingFix extends InspectionGadgetsFix {

    private final PsiField field;

    private DoubleCheckedLockingFix(PsiField field) {
      this.field = field;
    }

    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.doubleCheckedLockingQuickfix(field.getName());
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiModifierList modifierList = field.getModifierList();
      if (modifierList == null) {
        return;
      }
      modifierList.setModifierProperty(PsiModifier.VOLATILE, true);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new DoubleCheckedLockingVisitor();
  }

  private class DoubleCheckedLockingVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitIfStatement(
      @Nonnull PsiIfStatement statement) {
      super.visitIfStatement(statement);
      final PsiExpression outerCondition = statement.getCondition();
      if (outerCondition == null) {
        return;
      }
      if (SideEffectChecker.mayHaveSideEffects(outerCondition)) {
        return;
      }
      PsiStatement thenBranch = statement.getThenBranch();
      thenBranch = ControlFlowUtils.stripBraces(thenBranch);
      if (!(thenBranch instanceof PsiSynchronizedStatement)) {
        return;
      }
      final PsiSynchronizedStatement synchronizedStatement =
        (PsiSynchronizedStatement)thenBranch;
      final PsiCodeBlock body = synchronizedStatement.getBody();
      if (body == null) {
        return;
      }
      final PsiStatement[] statements = body.getStatements();
      if (statements.length != 1) {
        return;
      }
      final PsiStatement firstStatement = statements[0];
      if (!(firstStatement instanceof PsiIfStatement)) {
        return;
      }
      final PsiIfStatement innerIf = (PsiIfStatement)firstStatement;
      final PsiExpression innerCondition = innerIf.getCondition();
      if (!EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(innerCondition,
                                                       outerCondition)) {
        return;
      }
      final PsiField field;
      if (ignoreOnVolatileVariables) {
        field = findCheckedField(innerCondition);
        if (field != null &&
            field.hasModifierProperty(PsiModifier.VOLATILE)) {
          return;
        }
      }
      else {
        field = null;
      }
      registerStatementError(statement, field);
    }

    @Nullable
    private PsiField findCheckedField(PsiExpression expression) {
      if (expression instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression =
          (PsiReferenceExpression)expression;
        final PsiElement target = referenceExpression.resolve();
        if (!(target instanceof PsiField)) {
          return null;
        }
        return (PsiField)target;
      }
      else if (expression instanceof PsiBinaryExpression) {
        final PsiBinaryExpression binaryExpression =
          (PsiBinaryExpression)expression;
        final IElementType tokenType =
          binaryExpression.getOperationTokenType();
        if (!JavaTokenType.EQEQ.equals(tokenType)
            && !JavaTokenType.NE.equals(tokenType)) {
          return null;
        }
        final PsiExpression lhs = binaryExpression.getLOperand();
        final PsiExpression rhs = binaryExpression.getROperand();
        final PsiField field = findCheckedField(lhs);
        if (field != null) {
          return field;
        }
        return findCheckedField(rhs);
      }
      else if (expression instanceof PsiPrefixExpression) {
        final PsiPrefixExpression prefixExpression =
          (PsiPrefixExpression)expression;
        final IElementType tokenType =
          prefixExpression.getOperationTokenType();
        if (!JavaTokenType.EXCL.equals(tokenType)) {
          return null;
        }
        return findCheckedField(prefixExpression.getOperand());
      }
      else {
        return null;
      }
    }
  }
}