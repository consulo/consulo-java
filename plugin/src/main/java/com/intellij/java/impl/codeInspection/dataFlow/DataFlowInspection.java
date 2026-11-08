// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.codeInspection.dataFlow;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.*;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.DataFlowInspectionStateBase;
import consulo.java.analysis.impl.localize.JavaInspectionsLocalize;
import consulo.localize.LocalizeValue;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.annotation.component.ExtensionImpl;
import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.UnwrapSwitchLabelFix;
import com.intellij.java.impl.codeInspection.AddAssertNonNullFromTestFrameworksFix;
import com.intellij.java.impl.codeInspection.AddAssertNonNullFromTestFrameworksFix.Variant;
import com.intellij.java.impl.codeInspection.AddAssertStatementFix;
import com.intellij.java.impl.codeInspection.IntroduceVariableAndAssertFix;
import com.intellij.java.impl.codeInspection.IntroduceVariableAndReplaceWithTernaryFix;
import com.intellij.java.impl.codeInspection.IntroduceVariableAndSurroundWithIfFix;
import consulo.language.editor.inspection.LocalQuickFix;
import com.intellij.java.impl.codeInspection.RemoveAssignmentFix;
import com.intellij.java.impl.codeInspection.ReplaceComputeWithComputeIfPresentFix;
import com.intellij.java.impl.codeInspection.ReplaceTypeInCastFix;
import com.intellij.java.impl.codeInspection.ReplaceWithTernaryOperatorFix;
import com.intellij.java.impl.codeInspection.StreamFilterNotNullFix;
import com.intellij.java.impl.codeInspection.SurroundWithIfFix;
import com.intellij.java.impl.codeInspection.WrapWithMutableCollectionFix;
import com.intellij.java.impl.codeInspection.dataFlow.fix.BoxPrimitiveInTernaryFix;
import com.intellij.java.impl.codeInspection.dataFlow.fix.DeleteSwitchLabelFix;
import com.intellij.java.impl.codeInspection.dataFlow.fix.FindDfaProblemCauseFix;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.fix.ReplaceWithBooleanEqualsFix;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.fix.SurroundWithRequireNonNullFix;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.JavaDfaValueFactory;
import consulo.language.dataFlow.value.DfaValueFactory;
import consulo.language.dataFlow.value.DfaVariableValue;
import com.intellij.java.impl.codeInspection.nullable.NullableStuffInspection;
import consulo.logging.Logger;
import com.intellij.java.language.JavaFeature;
import com.intellij.java.language.psi.PsiAssignmentExpression;
import com.intellij.java.language.psi.PsiCaseLabelElement;
import com.intellij.java.language.psi.PsiClassType;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiExpressionStatement;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiIntersectionType;
import com.intellij.java.language.psi.PsiMethodReferenceExpression;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiReferenceExpression;
import com.intellij.java.language.psi.PsiSwitchBlock;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiTypeCastExpression;
import com.intellij.java.language.psi.PsiTypeElement;
import com.intellij.java.language.psi.util.PsiPrecedenceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.util.lang.ObjectUtil;
import consulo.util.collection.SmartList;
import consulo.util.collection.ContainerUtil;
import com.intellij.java.impl.ig.dataflow.CreateNullBranchFix;
import com.intellij.java.impl.ig.fixes.IntroduceVariableFix;
import com.siyeh.ig.psiutils.CodeBlockSurrounder;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.intellij.java.analysis.impl.codeInspection.SideEffectChecker;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@SuppressWarnings("InspectionDescriptionNotFoundInspection")
@ExtensionImpl
public class DataFlowInspection extends DataFlowInspectionBase {
  @Override
  public InspectionToolState<? extends DataFlowInspectionStateBase> createStateProvider() {
    return new DataFlowInspectionState();
  }

  @Override
  public LocalizeValue getDisplayName() {
    return JavaInspectionsLocalize.inspectionDataFlowDisplayName();
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  /**
   * The id of the inspection before JetBrains split it into "DataFlowIssue" and "ConstantValue" (ConstantValueInspectionMerger)
   */
  @Override
  public String getAlternativeID() {
    return "ConstantConditions";
  }

  private static final Logger LOG = Logger.getInstance(DataFlowInspection.class);

  @Override
  protected LocalQuickFix createMutabilityViolationFix(PsiElement violation) {
    return WrapWithMutableCollectionFix.createFix(violation, true);
  }

  @Override
  protected LocalQuickFix createExplainFix(DataFlowInspectionStateBase state, PsiExpression anchor, TrackingRunner.DfaProblemType problemType) {
    return new FindDfaProblemCauseFix(state.IGNORE_ASSERT_STATEMENTS, anchor, problemType);
  }

  @Override
  protected LocalQuickFix createUnwrapSwitchLabelFix() {
    return new UnwrapSwitchLabelFix();
  }

  @Override
  protected LocalQuickFix createIntroduceVariableFix() {
    return new IntroduceVariableFix(true);
  }

  @Override
  protected LocalQuickFix createDeleteLabelFix(PsiCaseLabelElement label) {
    return new DeleteSwitchLabelFix(label, true);
  }

  private static boolean isVolatileFieldReference(PsiExpression qualifier) {
    return qualifier instanceof PsiReferenceExpression ref &&
           ref.resolve() instanceof PsiField field &&
           field.hasModifierProperty(PsiModifier.VOLATILE);
  }

  /**
   * Creates a fix that asserts {@code operand + suffix}.
   *
   * @param operand    expression the assertion is about
   * @param suffix     text to append to the operand to get the assertion condition (e.g., {@code " != null"})
   * @param precedence precedence the operand text should be parenthesized for
   * @return a fix that adds the assertion in-place if the operand may be checked as is;
   * otherwise a fix that extracts the operand into a local variable first; null if no assertion could be added
   * @see #canCheckAsIs(PsiExpression)
   */
  private static @Nullable LocalQuickFix createAssertFix(PsiExpression operand, String suffix, int precedence) {
    if (canCheckAsIs(operand)) {
      return new AddAssertStatementFix(ParenthesesUtils.getText(operand, precedence) + suffix);
    }
    return IntroduceVariableAndAssertFix.create(operand, suffix);
  }

  /**
   * Creates a fix that asserts that the qualifier is not null using the assertion method of the given test framework.
   *
   * @param qualifier expression the assertion is about
   * @param variant   test framework to take the assertion method from
   * @return a fix that adds the assertion call in-place if the qualifier may be checked as is;
   * otherwise a fix that extracts the qualifier into a local variable first; null if no assertion could be added
   * @see #canCheckAsIs(PsiExpression)
   */
  private static @Nullable LocalQuickFix createTestFrameworkAssertFix(PsiExpression qualifier, Variant variant) {
    if (canCheckAsIs(qualifier)) {
      return new AddAssertNonNullFromTestFrameworksFix(qualifier, variant);
    }
    return IntroduceVariableAndAssertFix.create(qualifier, variant);
  }

  /**
   * Creates a fix that surrounds the statement with an {@code if} that checks {@code operand + suffix}.
   *
   * @param operand expression the check is about
   * @param suffix  text to append to the operand to get the condition (e.g., {@code " != null"})
   * @return a fix that checks the operand in-place if it may be checked as is;
   * otherwise a fix that extracts the operand into a local variable first; null if no check could be added
   * @see #canCheckAsIs(PsiExpression)
   */
  private static @Nullable LocalQuickFix createSurroundWithIfFix(PsiExpression operand, String suffix) {
    if (canCheckAsIs(operand)) {
      return SurroundWithIfFix.isAvailable(operand) ? new SurroundWithIfFix(operand, suffix) : null;
    }
    return IntroduceVariableAndSurroundWithIfFix.create(operand, suffix);
  }

  /**
   * Creates a fix that replaces the dereference of the qualifier with a conditional expression checking it for null.
   *
   * @param qualifier  expression the check is about
   * @param expression expression that dereferences the qualifier
   * @return a fix that checks the qualifier in-place if it may be checked as is;
   * otherwise a fix that extracts the qualifier into a local variable first; null if no check could be added
   * @see #canCheckAsIs(PsiExpression)
   */
  private static @Nullable LocalQuickFix createTernaryFix(PsiExpression qualifier, PsiExpression expression) {
    if (!ReplaceWithTernaryOperatorFix.isAvailable(qualifier, expression)) return null;
    if (canCheckAsIs(qualifier)) {
      return new ReplaceWithTernaryOperatorFix(qualifier);
    }
    return IntroduceVariableAndReplaceWithTernaryFix.create(qualifier);
  }

  /**
   * A generated check mentions the expression one more time, which is only useful if re-evaluating it has no visible
   * effect and the analysis knows that both occurrences produce the same value. Otherwise, the expression should be
   * extracted into a local variable, which is evaluated exactly once and is checked instead of the expression.
   *
   * @return true if the expression may be checked in-place
   */
  private static boolean canCheckAsIs(PsiExpression expression) {
    return !SideEffectChecker.mayHaveSideEffects(expression) && isTrackedByDfa(expression);
  }

  /**
   * @return true if the dataflow analysis represents the expression as a variable, so an assertion about it
   * is remembered for the subsequent occurrences of the same expression. Note that it's kinda heuristical method,
   * which may not always work.
   */
  private static boolean isTrackedByDfa(PsiExpression expression) {
    // casts to a reference type are transparent for the analysis, see JavaDfaValueFactory#getQualifierOrThisValue
    PsiExpression stripped = PsiUtil.skipParenthesizedExprDown(expression);
    while (stripped instanceof PsiTypeCastExpression cast && cast.getType() instanceof PsiClassType) {
      stripped = PsiUtil.skipParenthesizedExprDown(cast.getOperand());
    }
    if (stripped == null) return false;
    DfaValueFactory factory = new DfaValueFactory(expression.getProject());
    return JavaDfaValueFactory.getExpressionDfaValue(factory, stripped) instanceof DfaVariableValue;
  }

  @Override
  protected List<LocalQuickFix> createMethodReferenceNPEFixes(PsiMethodReferenceExpression methodRef) {
    List<LocalQuickFix> fixes = new ArrayList<>();
    ContainerUtil.addIfNotNull(fixes, StreamFilterNotNullFix.makeFix(methodRef));
    fixes.add(new ReplaceWithTernaryOperatorFix.ReplaceMethodRefWithTernaryOperatorFix());
    return fixes;
  }

  @Override
  protected LocalQuickFix createRemoveAssignmentFix(PsiAssignmentExpression assignment) {
    if (assignment == null || assignment.getRExpression() == null || !(assignment.getParent() instanceof PsiExpressionStatement)) {
      return null;
    }
    return new RemoveAssignmentFix();
  }

  @Override
  protected List<LocalQuickFix> createCastFixes(PsiTypeCastExpression castExpression,
                                                                  PsiType realType,
                                                                  boolean alwaysFails) {
    List<LocalQuickFix> fixes = new ArrayList<>();
    PsiExpression operand = castExpression.getOperand();
    PsiTypeElement typeElement = castExpression.getCastType();
    if (typeElement != null && operand != null) {
      if (!alwaysFails && CodeBlockSurrounder.canSurround(castExpression)) {
        String suffix = " instanceof " + typeElement.getText();
        ContainerUtil.addIfNotNull(fixes, createAssertFix(operand, suffix, PsiPrecedenceUtil.RELATIONAL_PRECEDENCE));
        ContainerUtil.addIfNotNull(fixes, createSurroundWithIfFix(operand, suffix));
      }
      if (realType != null) {
        PsiType operandType = operand.getType();
        if (operandType != null) {
          PsiType type = typeElement.getType();
          PsiType[] types = realType instanceof PsiIntersectionType intersectionType ?
                            intersectionType.getConjuncts() : new PsiType[]{realType};
          for (PsiType psiType : types) {
            if (!psiType.isAssignableFrom(operandType)) {
              psiType = DfaPsiUtil.tryGenerify(operand, psiType);
              fixes.add(new ReplaceTypeInCastFix(type, psiType));
            }
          }
        }
      }
    }
    return fixes;
  }

  @Override
  protected List<LocalQuickFix> createNPEFixes(DataFlowInspectionStateBase state, @Nullable PsiExpression qualifier,
                                                                 PsiExpression expression,
                                                                 boolean alwaysNull) {
    qualifier = PsiUtil.deparenthesizeExpression(qualifier);

    final List<LocalQuickFix> fixes = new SmartList<>();
    if (qualifier == null || expression == null) return Collections.emptyList();

    try {
      ContainerUtil.addIfNotNull(fixes, StreamFilterNotNullFix.makeFix(qualifier));
      ContainerUtil.addIfNotNull(fixes, ReplaceComputeWithComputeIfPresentFix.makeFix(qualifier));
      if (isVolatileFieldReference(qualifier)) {
        ContainerUtil.addIfNotNull(fixes, createIntroduceVariableFix());
      }
      else if (!alwaysNull) {
        String suffix = " != null";

        Variant testFrameworkFixVariant = AddAssertNonNullFromTestFrameworksFix.isAvailable(expression);
        if (testFrameworkFixVariant != null) {
          ContainerUtil.addIfNotNull(fixes, createTestFrameworkAssertFix(qualifier, testFrameworkFixVariant));
        }
        else if (PsiUtil.isAvailable(JavaFeature.ASSERTIONS, qualifier) && CodeBlockSurrounder.canSurround(expression)) {
          ContainerUtil.addIfNotNull(fixes, createAssertFix(qualifier, suffix, ParenthesesUtils.EQUALITY_PRECEDENCE));
        }

        ContainerUtil.addIfNotNull(fixes, createSurroundWithIfFix(qualifier, suffix));
        ContainerUtil.addIfNotNull(fixes, createTernaryFix(qualifier, expression));
      }

      if (!alwaysNull && PsiUtil.isAvailable(JavaFeature.OBJECTS_CLASS, qualifier)) {
        fixes.add(new SurroundWithRequireNonNullFix(qualifier));
      }

      if (!ExpressionUtils.isNullLiteral(qualifier)) {
        ContainerUtil.addIfNotNull(fixes, createExplainFix(state, qualifier, new TrackingRunner.NullableDfaProblemType()));
      }

      ContainerUtil.addIfNotNull(fixes, DfaOptionalSupport.registerReplaceOptionalOfWithOfNullableFix(qualifier));

      addCreateNullBranchFix(qualifier, fixes);
    }

    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return fixes;
  }

  @Override
  protected List<LocalQuickFix> createUnboxingNullableFixes(PsiExpression qualifier, PsiElement anchor) {
    List<LocalQuickFix> result = new SmartList<>();
    if (TypeConversionUtil.isBooleanType(qualifier.getType())) {
      result.add(new ReplaceWithBooleanEqualsFix(qualifier));
    }
    ContainerUtil.addIfNotNull(result, BoxPrimitiveInTernaryFix.makeFix(ObjectUtil.tryCast(anchor, PsiExpression.class)));
    addCreateNullBranchFix(qualifier, result);
    return result;
  }

  private static void addCreateNullBranchFix(PsiExpression qualifier, List<? super LocalQuickFix> fixes) {
    if (!PsiUtil.isAvailable(JavaFeature.PATTERNS_IN_SWITCH, qualifier)) return;
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(qualifier.getParent());
    if (parent instanceof PsiSwitchBlock block && PsiUtil.skipParenthesizedExprDown(block.getExpression()) == qualifier) {
      fixes.add(new CreateNullBranchFix(block));
    }
  }

  @Override
  protected LocalQuickFix createNavigateToNullParameterUsagesFix(PsiParameter parameter) {
    // consulo keeps the fix nested in NullableStuffInspection
    return new NullableStuffInspection.NavigateToNullLiteralArguments(parameter);
  }
}
