// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.codeInspection.dataFlow;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.*;
import com.intellij.java.analysis.impl.codeInspection.SetInspectionOptionFix;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.localize.LocalizeValue;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.annotation.component.ExtensionImpl;
import com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix.SimplifyBooleanExpressionFix;
import com.intellij.java.analysis.codeInspection.AbstractBaseJavaLocalInspectionTool;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import com.intellij.java.impl.codeInspection.RemoveAssignmentFix;
import com.intellij.java.impl.codeInspection.ReplaceWithTrivialLambdaFix;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.CommonDataflow.DataflowResult;
import com.intellij.java.impl.codeInspection.dataFlow.fix.FindDfaProblemCauseFix;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.fix.ReplaceWithConstantValueFix;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.fix.SimplifyToAssignmentFix;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.anchor.JavaDfaAnchor;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.anchor.JavaExpressionAnchor;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.anchor.JavaMethodReferenceReturnAnchor;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.anchor.JavaPolyadicPartAnchor;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfReferenceType;
import consulo.language.dataFlow.types.DfType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.java.language.psi.PsiKeyword;
import consulo.util.lang.ref.Ref;
import consulo.util.lang.StringUtil;
import com.intellij.java.language.JavaFeature;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiArrayAccessExpression;
import com.intellij.java.language.psi.PsiAssignmentExpression;
import com.intellij.java.language.psi.PsiBinaryExpression;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiCodeBlock;
import com.intellij.java.language.psi.PsiConditionalExpression;
import com.intellij.java.language.psi.PsiDoWhileStatement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiIfStatement;
import com.intellij.java.language.psi.PsiInstanceOfExpression;
import com.intellij.java.language.psi.PsiLambdaExpression;
import com.intellij.java.language.psi.PsiLiteralExpression;
import com.intellij.java.language.psi.PsiLocalVariable;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiMethodReferenceExpression;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiModifierList;
import com.intellij.java.language.psi.PsiParenthesizedExpression;
import com.intellij.java.language.psi.PsiPattern;
import com.intellij.java.language.psi.PsiPolyadicExpression;
import com.intellij.java.language.psi.PsiPrefixExpression;
import com.intellij.java.language.psi.PsiPrimitiveType;
import com.intellij.java.language.psi.PsiReferenceExpression;
import com.intellij.java.language.psi.PsiReturnStatement;
import com.intellij.java.language.psi.PsiStatement;
import com.intellij.java.language.psi.PsiSwitchExpression;
import com.intellij.java.language.psi.PsiSwitchLabelStatement;
import com.intellij.java.language.psi.PsiSwitchLabeledRuleStatement;
import com.intellij.java.language.psi.PsiSwitchStatement;
import com.intellij.java.language.psi.PsiSynchronizedStatement;
import com.intellij.java.language.psi.PsiThrowStatement;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiTypeCastExpression;
import com.intellij.java.language.psi.PsiTypeElement;
import com.intellij.java.language.psi.PsiTypeTestPattern;
import com.intellij.java.language.psi.PsiTypes;
import com.intellij.java.language.psi.PsiVariable;
import com.intellij.java.analysis.impl.psi.controlFlow.DefUseUtil;
import com.intellij.java.language.impl.psi.impl.source.PsiFieldImpl;
import consulo.language.ast.IElementType;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.SmartList;
import consulo.util.collection.ContainerUtil;
import com.siyeh.ig.bugs.EqualsWithItselfInspection;
import com.intellij.java.impl.ig.controlflow.PointlessBooleanExpressionInspection;
import com.siyeh.ig.fixes.EqualsToEqualityFix;
import com.intellij.java.impl.ig.numeric.ComparisonToNaNInspection;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.intellij.java.analysis.impl.codeInspection.ControlFlowUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static consulo.util.lang.ObjectUtil.tryCast;

@ExtensionImpl
public final class ConstantValueInspection extends AbstractBaseJavaLocalInspectionTool<ConstantValueInspectionState> {
  // the options are in ConstantValueInspectionState (JetBrains keeps them as fields of the inspection)

  @Override
  public InspectionToolState<ConstantValueInspectionState> createStateProvider() {
    return new ConstantValueInspectionState();
  }

  @Override
  public String getShortName() {
    return "ConstantValue";
  }

  /**
   * The id of the inspection before JetBrains split it from "DataFlowIssue" (ConstantValueInspectionMerger)
   */
  @Override
  public String getAlternativeID() {
    return "ConstantConditions";
  }

  @Override
  public LocalizeValue getDisplayName() {
    return LocalizeValue.localizeTODO("Constant values");
  }

  @Override
  public LocalizeValue getGroupDisplayName() {
    return InspectionLocalize.groupNamesProbableBugs();
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }

  @Override
  public PsiElementVisitor buildVisitorImpl(ProblemsHolder holder,
                                            boolean isOnTheFly,
                                            LocalInspectionToolSession session,
                                            ConstantValueInspectionState state) {
    return new JavaElementVisitor() {
      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        visitExpression(expression);
      }

      @Override
      public void visitExpression(PsiExpression expression) {
        if (expression instanceof PsiLiteralExpression) return;
        DataflowResult dfr = CommonDataflow.getDataflowResult(expression);
        if (dfr == null) return;
        JavaDfaAnchor anchor = expression instanceof PsiMethodReferenceExpression methodRef ?
                               new JavaMethodReferenceReturnAnchor(methodRef) :
                               new JavaExpressionAnchor(expression);
        processAnchor(state, dfr, anchor, holder);
        if (expression instanceof PsiPolyadicExpression polyadic) {
          PsiExpression[] operands = polyadic.getOperands();
          for (int i = 1; i < operands.length - 1; i++) {
            processAnchor(state, dfr, new JavaPolyadicPartAnchor(polyadic, i), holder);
          }
        }
      }

      @Override
      public void visitSwitchLabeledRuleStatement(PsiSwitchLabeledRuleStatement statement) {
        checkSwitchCaseGuard(PsiUtil.skipParenthesizedExprDown(statement.getGuardExpression()));
      }

      @Override
      public void visitSwitchLabelStatement(PsiSwitchLabelStatement statement) {
        checkSwitchCaseGuard(PsiUtil.skipParenthesizedExprDown(statement.getGuardExpression()));
      }

      private void checkSwitchCaseGuard(@Nullable PsiExpression guard) {
        if (guard == null) return;
        if (BoolUtils.isTrue(guard)) {
          LocalQuickFix fix = createSimplifyBooleanExpressionFix(guard, guard.textMatches(PsiKeyword.TRUE));
          holder.registerProblem(guard, JavaAnalysisBundle
            .message("dataflow.message.constant.no.ref", guard.textMatches(PsiKeyword.TRUE) ? 1 : 0), LocalQuickFix.notNullElements(fix));
        }
      }

      @Override
      public void visitIfStatement(PsiIfStatement statement) {
        PsiExpression condition = PsiUtil.skipParenthesizedExprDown(statement.getCondition());
        if (BoolUtils.isBooleanLiteral(condition)) {
          LocalQuickFix fix = createSimplifyBooleanExpressionFix(condition, condition.textMatches(PsiKeyword.TRUE));
          holder.registerProblem(condition, JavaAnalysisBundle
            .message("dataflow.message.constant.no.ref", condition.textMatches(PsiKeyword.TRUE) ? 1 : 0), LocalQuickFix.notNullElements(fix));
        }
      }

      @Override
      public void visitDoWhileStatement(PsiDoWhileStatement statement) {
        PsiExpression condition = PsiUtil.skipParenthesizedExprDown(statement.getCondition());
        if (condition != null && condition.textMatches(PsiKeyword.FALSE)) {
          LocalQuickFix fix = createSimplifyBooleanExpressionFix(condition, false);
          if (fix != null) {
            holder.registerProblem(condition, JavaAnalysisBundle.message("dataflow.message.constant.no.ref", 0),
                                   LocalQuickFix.notNullElements(fix));
          }
        }
      }
    };
  }

  private void processAnchor(ConstantValueInspectionState state, DataflowResult dfr,
                             JavaDfaAnchor anchor,
                             ProblemsHolder holder) {
    DfType dfType;
    if (state.IGNORE_ASSERT_STATEMENTS) {
      dfType = dfr.getDfTypeNoAssertions(anchor);
    } else {
      dfType = dfr.getDfType(anchor);
    }
    processAnchor(state, dfType, anchor, holder);
  }

  private void processAnchor(ConstantValueInspectionState state, DfType dfType, JavaDfaAnchor anchor, ProblemsHolder reporter) {
    ConstantResult result = ConstantResult.fromDfType(dfType);
    if (result == ConstantResult.UNKNOWN && dfType instanceof DfReferenceType refType && refType.getSpecialField() == SpecialField.UNBOX &&
        refType.getNullability() == DfaNullability.NOT_NULL) {
      result = ConstantResult.fromDfType(refType.getSpecialFieldType());
    }
    if (result == ConstantResult.UNKNOWN) return;
    Object value = result.value();
    if (anchor instanceof JavaPolyadicPartAnchor polyadicPartAnchor) {
      if (value instanceof Boolean booleanValue) {
        // report rare cases like a == b == c where "a == b" part is constant
        String message = JavaAnalysisBundle.message("dataflow.message.constant.condition",
                                                    booleanValue.booleanValue() ? 1 : 0);
        reporter.registerProblem(polyadicPartAnchor.getExpression(),
                                 polyadicPartAnchor.getTextRange(), message);
        // do not add to reported anchors if only part of expression was reported
      }
    }
    else if (anchor instanceof JavaExpressionAnchor expressionAnchor) {
      PsiExpression expression = expressionAnchor.getExpression();
      if (isCondition(expression)) {
        if (value instanceof Boolean booleanValue) {
          reportConstantBoolean(state, reporter, expression, booleanValue);
        }
      }
      else {
        reportConstantReferenceValue(state, reporter, expression, result);
      }
    }
    else if (anchor instanceof JavaMethodReferenceReturnAnchor methodRefAnchor) {
      PsiMethodReferenceExpression methodRef = methodRefAnchor.getMethodReferenceExpression();
      PsiMethod method = tryCast(methodRef.resolve(), PsiMethod.class);
      if (method != null && JavaMethodContractUtil.isPure(method)) {
        List<StandardMethodContract> contracts = JavaMethodContractUtil.getMethodContracts(method);
        if (contracts.isEmpty() || !contracts.getFirst().isTrivial()) {
          reporter.registerProblem(methodRef, JavaAnalysisBundle.message("dataflow.message.constant.method.reference", value),
                                   new ReplaceWithTrivialLambdaFix(value));
        }
      }
    }
  }

  private static boolean shouldReportZero(PsiExpression ref) {
    if (ref instanceof PsiPolyadicExpression polyadic) {
      if (PsiUtil.isConstantExpression(ref)) return false;
      IElementType tokenType = polyadic.getOperationTokenType();
      if (tokenType.equals(JavaTokenType.ASTERISK)) {
        PsiMethod method = PsiTreeUtil.getParentOfType(ref, PsiMethod.class, true, PsiLambdaExpression.class, PsiClass.class);
        if (MethodUtils.isHashCode(method)) {
          // Standard hashCode template generates int result = 0; result = result * 31 + ...;
          // so annoying warnings might be produced there
          return false;
        }
      }
    }
    else if (ref instanceof PsiMethodCallExpression call) {
      PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      if (PsiUtil.isConstantExpression(qualifier) &&
          ContainerUtil.and(call.getArgumentList().getExpressions(), PsiUtil::isConstantExpression)) {
        return false;
      }
    }
    else if (ref instanceof PsiTypeCastExpression) {
      PsiExpression operand = ((PsiTypeCastExpression)ref).getOperand();
      return operand != null && TypeConversionUtil.isFloatOrDoubleType(operand.getType());
    }
    else {
      return false;
    }
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(ref.getParent());
    PsiBinaryExpression binOp = tryCast(parent, PsiBinaryExpression.class);
    return binOp == null || !ComparisonUtils.isEqualityComparison(binOp) ||
           (!ExpressionUtils.isZero(binOp.getLOperand()) && !ExpressionUtils.isZero(binOp.getROperand()));
  }

  private void reportConstantReferenceValue(ConstantValueInspectionState state, ProblemsHolder reporter, PsiExpression ref, ConstantResult constant) {
    if (!state.REPORT_CONSTANT_REFERENCE_VALUES && ref instanceof PsiReferenceExpression) return;
    if (shouldBeSuppressed(ref) || constant == ConstantResult.UNKNOWN) return;
    String presentableName = constant.toString();
    if (Integer.valueOf(0).equals(constant.value()) && !shouldReportZero(ref)) return;
    List<LocalQuickFix> fixes = new SmartList<>();
    if (constant.value() instanceof Boolean) {
      fixes.add(createSimplifyBooleanExpressionFix(ref, (Boolean)constant.value()));
    } else {
      LocalQuickFix action = new ReplaceWithConstantValueFix(ref, presentableName, presentableName);
      fixes.add(action);
    }
    Object value = constant.value();
    boolean isAssertion = isAssertionEffectively(ref, constant);
    if (isAssertion && state.DONT_REPORT_TRUE_ASSERT_STATEMENTS) return;
    if (value instanceof Boolean) {
      ContainerUtil.addIfNotNull(fixes, createReplaceWithNullCheckFix(ref, (Boolean)value));
    }
    if (reporter.isOnTheFly()) {
      if (ref instanceof PsiReferenceExpression) {
        fixes.add(new SetInspectionOptionFix<>(this, (i, v) -> i.REPORT_CONSTANT_REFERENCE_VALUES = v,
                                            LocalizeValue.of(JavaAnalysisBundle.message("inspection.data.flow.turn.off.constant.references.quickfix")),
                                            false));
      }
      if (isAssertion) {
        fixes.add(new SetInspectionOptionFix<>(this, (i, v) -> i.DONT_REPORT_TRUE_ASSERT_STATEMENTS = v,
                                            LocalizeValue.of(JavaAnalysisBundle.message("inspection.data.flow.turn.off.true.asserts.quickfix")),
                                            true));
      }
    }
    ContainerUtil.addIfNotNull(fixes, new FindDfaProblemCauseFix(state.IGNORE_ASSERT_STATEMENTS, ref, new TrackingRunner.ValueDfaProblemType(value)));

    ProblemHighlightType type;
    String message;
    if (ref instanceof PsiMethodCallExpression || ref instanceof PsiPolyadicExpression || ref instanceof PsiTypeCastExpression) {
      type = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
      message = JavaAnalysisBundle.message("dataflow.message.constant.expression", presentableName);
    }
    else {
      type = ProblemHighlightType.WEAK_WARNING;
      message = JavaAnalysisBundle.message("dataflow.message.constant.value", presentableName);
    }
    reporter.registerProblem(ref, message, type, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
  }

  private static boolean isCoveredBySurroundingFix(PsiElement anchor, boolean evaluatesToTrue) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(anchor.getParent());
    if (parent instanceof PsiPolyadicExpression polyadic) {
      IElementType tokenType = polyadic.getOperationTokenType();
      return tokenType.equals(JavaTokenType.ANDAND) && !evaluatesToTrue ||
             tokenType.equals(JavaTokenType.OROR) && evaluatesToTrue;
    }
    return parent instanceof PsiExpression && BoolUtils.isNegation((PsiExpression)parent);
  }

  private static boolean isCondition(PsiExpression expression) {
    PsiType type = expression.getType();
    if (type == null || !PsiTypes.booleanType().isAssignableFrom(type)) return false;
    if (!(expression instanceof PsiMethodCallExpression) && !(expression instanceof PsiReferenceExpression)) return true;
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (parent instanceof PsiStatement) return !(parent instanceof PsiReturnStatement);
    if (parent instanceof PsiPolyadicExpression polyadic) {
      IElementType tokenType = polyadic.getOperationTokenType();
      return tokenType.equals(JavaTokenType.ANDAND) || tokenType.equals(JavaTokenType.OROR) ||
             tokenType.equals(JavaTokenType.AND) || tokenType.equals(JavaTokenType.OR);
    }
    if (parent instanceof PsiConditionalExpression conditional) {
      return PsiTreeUtil.isAncestor(conditional.getCondition(), expression, false);
    }
    return PsiUtil.isAccessedForWriting(expression);
  }

  private static boolean shouldBeSuppressed(PsiElement anchor) {
    if (!(anchor instanceof PsiExpression expression)) return false;
    // Don't report System.out.println(b = false) or doSomething((Type)null)
    if (anchor instanceof PsiAssignmentExpression ||
        (anchor instanceof PsiTypeCastExpression cast && !(cast.getType() instanceof PsiPrimitiveType))) {
      return true;
    }
    // For conditional the root cause (constant condition or both branches constant) should be already reported for branches
    if (anchor instanceof PsiConditionalExpression) return true;
    if (expression instanceof PsiReferenceExpression ref &&
        ("TRUE".equals(ref.getReferenceName()) || "FALSE".equals(ref.getReferenceName())) &&
        ref.resolve() instanceof PsiField field) {
      PsiClass containingClass = field.getContainingClass();
      if (containingClass != null && CommonClassNames.JAVA_LANG_BOOLEAN.equals(containingClass.getQualifiedName())) return true;
    }
    if (expression instanceof PsiBinaryExpression binOp) {
      PsiExpression lOperand = binOp.getLOperand();
      PsiExpression rOperand = binOp.getROperand();
      IElementType tokenType = binOp.getOperationTokenType();
      // Suppress on type mismatch compilation errors
      if (rOperand == null) return true;
      PsiType lType = lOperand.getType();
      PsiType rType = rOperand.getType();
      if (lType == null || rType == null) return true;
      if (!TypeConversionUtil.isBinaryOperatorApplicable(tokenType, lType, rType, false)) return true;
    }
    if (expression instanceof PsiInstanceOfExpression instanceOf) {
      PsiType type = instanceOf.getOperand().getType();
      LanguageLevel languageLevel = PsiUtil.getLanguageLevel(instanceOf);
      if (type == null ||
          (!TypeConstraints.instanceOf(type).isResolved() &&
           (!JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.isSufficient(languageLevel) ||
            !(type instanceof PsiPrimitiveType)))) {
        return true;
      }
      PsiPattern pattern = instanceOf.getPattern();
      if (pattern instanceof PsiTypeTestPattern typeTestPattern && typeTestPattern.getPatternVariable() != null) {
        PsiTypeElement checkType = typeTestPattern.getCheckType();
        if (checkType != null && checkType.getType().isAssignableFrom(type) &&
            !JavaFeature.PATTERN_GUARDS_AND_RECORD_PATTERNS.isSufficient(languageLevel)) {
          // Reported as compilation error
          return true;
        }
      }
    }

    //it can be done deliberately, because it is expected to throw exception
    if (expression instanceof PsiSwitchExpression switchExpression && containsImmediateThrowStatement(switchExpression)) {
      return true;
    }
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    // Don't report "x" in "x == null" as will be anyway reported as "always true"
    if (parent instanceof PsiBinaryExpression binOp && ExpressionUtils.getValueComparedWithNull(binOp) != null) return true;
    // Parent is negation: parent will be reported
    if (parent instanceof PsiPrefixExpression prefix && prefix.getOperationTokenType().equals(JavaTokenType.EXCL)) return true;
    // Dereference of null will be covered by other warning
    if (ExpressionUtils.isVoidContext(expression) || isDereferenceContext(expression)) return true;
    // We assume all Void variables as null because you cannot instantiate it without dirty hacks
    // However reporting them as "always null" looks redundant (dereferences or comparisons will be reported though).
    if (TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_VOID, expression.getType())) return true;
    if (isFlagCheck(anchor)) return true;
    if (!isCondition(expression) && expression instanceof PsiMethodCallExpression call) {
      List<? extends MethodContract> contracts = JavaMethodContractUtil.getMethodCallContracts(call);
      ContractReturnValue value = JavaMethodContractUtil.getNonFailingReturnValue(contracts);
      if (value != null) return true;
      if (!(parent instanceof PsiAssignmentExpression) && !(parent instanceof PsiVariable) &&
          !(parent instanceof PsiReturnStatement)) {
        PsiMethod method = call.resolveMethod();
        if (method == null || !JavaMethodContractUtil.isPure(method)) return true;
      }
    }
    if (new PointlessBooleanExpressionInspection().getExpressionKind(expression) !=
        PointlessBooleanExpressionInspection.BooleanExpressionKind.UNKNOWN) {
      // avoid double reporting
      return true;
    }
    while (expression != null && BoolUtils.isNegation(expression)) {
      expression = BoolUtils.getNegated(expression);
    }
    if (expression == null) return false;
    if (!isCondition(expression) && expression instanceof PsiReferenceExpression ref) {
      PsiVariable variable = tryCast(ref.resolve(), PsiVariable.class);
      if (variable instanceof PsiField &&
          variable.hasModifierProperty(PsiModifier.STATIC) &&
          ExpressionUtils.isNullLiteral(PsiFieldImpl.getDetachedInitializer(variable))) {
        return true;
      }
      if (variable instanceof PsiLocalVariable && variable.hasInitializer()) {
        boolean effectivelyFinal = variable.hasModifierProperty(PsiModifier.FINAL) ||
                                   !VariableAccessUtils.variableIsAssigned(variable, PsiUtil.getVariableCodeBlock(variable, null));
        return effectivelyFinal && PsiUtil.isConstantExpression(variable.getInitializer());
      }
      return false;
    }
    // Avoid double reporting
    return expression instanceof PsiMethodCallExpression call && EqualsWithItselfInspection.isEqualsWithItself(call) ||
           expression instanceof PsiBinaryExpression binOp && ComparisonToNaNInspection.extractNaNFromComparison(binOp) != null;
  }

  private static boolean containsImmediateThrowStatement(PsiSwitchExpression expression) {
    Ref<Boolean> ref = Ref.create();
    ref.set(false);
    ControlFlowUtils.processElementsInCurrentScope(expression, element -> {
      if (element instanceof PsiThrowStatement) {
        ref.set(true);
        return false;
      }
      if (element instanceof PsiMethodCallExpression methodCallExpression) {
        List<? extends MethodContract> contracts = JavaMethodContractUtil.getMethodCallContracts(methodCallExpression);
        if (ContainerUtil.exists(contracts, contract -> contract.isTrivial() && contract.getReturnValue().isFail())) {
          ref.set(true);
          return false;
        }
      }
      return true;
    });
    return ref.get();
  }

  private static boolean isDereferenceContext(PsiExpression ref) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(ref.getParent());
    return parent instanceof PsiReferenceExpression || parent instanceof PsiArrayAccessExpression
           || parent instanceof PsiSwitchStatement || parent instanceof PsiSynchronizedStatement;
  }

  static boolean isFlagCheck(PsiElement element) {
    PsiElement scope = PsiTreeUtil.getParentOfType(element, PsiStatement.class, PsiVariable.class);
    PsiExpression topExpression = scope instanceof PsiIfStatement ifStatement ? ifStatement.getCondition() :
                                  scope instanceof PsiVariable variable ? variable.getInitializer() :
                                  null;
    if (!PsiTreeUtil.isAncestor(topExpression, element, false)) return false;

    return StreamEx.<PsiElement>ofTree(topExpression, e -> StreamEx.of(e.getChildren()))
      .anyMatch(ConstantValueInspection::isCompileTimeFlagCheck);
  }

  private static boolean isCompileTimeFlagCheck(PsiElement element) {
    if (element instanceof PsiBinaryExpression binOp) {
      if(ComparisonUtils.isComparisonOperation(binOp.getOperationTokenType())) {
        PsiExpression comparedWith = null;
        if(ExpressionUtils.isLiteral(binOp.getROperand())) {
          comparedWith = binOp.getLOperand();
        } else if(ExpressionUtils.isLiteral(binOp.getLOperand())) {
          comparedWith = binOp.getROperand();
        }
        comparedWith = PsiUtil.skipParenthesizedExprDown(comparedWith);
        if (isConstantOfType(comparedWith, PsiTypes.intType(), PsiTypes.longType())) {
          // like "if(DEBUG_LEVEL > 2)"
          return true;
        }
        if(comparedWith instanceof PsiBinaryExpression subOp) {
          if(subOp.getOperationTokenType().equals(JavaTokenType.AND)) {
            PsiExpression left = PsiUtil.skipParenthesizedExprDown(subOp.getLOperand());
            PsiExpression right = PsiUtil.skipParenthesizedExprDown(subOp.getROperand());
            if(isConstantOfType(left, PsiTypes.intType(), PsiTypes.longType()) ||
               isConstantOfType(right, PsiTypes.intType(), PsiTypes.longType())) {
              // like "if((FLAGS & SOME_FLAG) != 0)"
              return true;
            }
          }
        }
      }
    }
    // like "if(DEBUG)"
    return isConstantOfType(element, PsiTypes.booleanType());
  }

  private static boolean isConstantOfType(PsiElement element, PsiPrimitiveType... types) {
    if (!(element instanceof PsiReferenceExpression ref) || !(ref.resolve() instanceof PsiField field)) return false;
    PsiModifierList modifierList = field.getModifierList();
    if (modifierList == null ||
        !modifierList.hasModifierProperty(PsiModifier.STATIC) ||
        !modifierList.hasModifierProperty(PsiModifier.FINAL)) {
      return false;
    }
    if (!ArrayUtil.contains(field.getType(), types)) return false;
    return field.hasInitializer() && PsiUtil.isConstantExpression(field.getInitializer());
  }

  private static boolean isAtRHSOfBooleanAnd(PsiElement expr) {
    PsiElement cur = expr;

    while (cur != null && !(cur instanceof PsiMember)) {
      PsiElement parent = cur.getParent();
      if (parent instanceof PsiBinaryExpression binOp && cur == binOp.getROperand()) return true;
      cur = parent;
    }

    return false;
  }

  private void reportConstantBoolean(ConstantValueInspectionState state, ProblemsHolder reporter, PsiElement psiAnchor, boolean evaluatesToTrue) {
    while (psiAnchor instanceof PsiParenthesizedExpression parenthesized) {
      psiAnchor = parenthesized.getExpression();
    }
    if (psiAnchor == null || shouldBeSuppressed(psiAnchor)) return;
    boolean isAssertion = psiAnchor instanceof PsiExpression expr && DfaPsiUtil.isAssertionEffectively(expr, evaluatesToTrue);
    if (state.DONT_REPORT_TRUE_ASSERT_STATEMENTS && isAssertion) return;

    if (PsiUtil.skipParenthesizedExprUp(psiAnchor.getParent()) instanceof PsiAssignmentExpression assignment &&
        PsiTreeUtil.isAncestor(assignment.getLExpression(), psiAnchor, false)) {
      IElementType tokenType = assignment.getOperationTokenType();
      if (tokenType.equals(JavaTokenType.ANDEQ) || tokenType.equals(JavaTokenType.OREQ)) {
        if (isFlagSetChain(assignment.getLExpression(), tokenType.equals(JavaTokenType.ANDEQ))) return;
      }
      reporter.registerProblem(
        psiAnchor,
        JavaAnalysisBundle.message("dataflow.message.pointless.assignment.expression", Boolean.toString(evaluatesToTrue)),
        createConditionalAssignmentFixes(evaluatesToTrue, assignment, reporter.isOnTheFly())
      );
      return;
    }

    List<LocalQuickFix> fixes = new ArrayList<>();
    if (!isCoveredBySurroundingFix(psiAnchor, evaluatesToTrue)) {
      ContainerUtil.addIfNotNull(fixes, createSimplifyBooleanExpressionFix(psiAnchor, evaluatesToTrue));
      if (isAssertion && reporter.isOnTheFly()) {
        fixes.add(new SetInspectionOptionFix<>(this, (i, v) -> i.DONT_REPORT_TRUE_ASSERT_STATEMENTS = v,
                                            LocalizeValue.of(JavaAnalysisBundle.message("inspection.data.flow.turn.off.true.asserts.quickfix")),
                                            true));
      }
      ContainerUtil.addIfNotNull(fixes, createReplaceWithNullCheckFix(psiAnchor, evaluatesToTrue));
    }
    if (psiAnchor instanceof PsiExpression expression) {
      ContainerUtil.addIfNotNull(fixes, new FindDfaProblemCauseFix(state.IGNORE_ASSERT_STATEMENTS,
                                                                   expression, new TrackingRunner.ValueDfaProblemType(evaluatesToTrue)));
    }
    String message = JavaAnalysisBundle.message(isAtRHSOfBooleanAnd(psiAnchor) ?
                                                "dataflow.message.constant.condition.when.reached" :
                                                "dataflow.message.constant.condition", evaluatesToTrue ? 1 : 0);
    reporter.registerProblem(psiAnchor, message, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
  }

  private static boolean isFlagSetChain(PsiExpression expression, boolean isAnd) {
    PsiLocalVariable local = ExpressionUtils.resolveLocalVariable(expression);
    if (local == null) return false;
    PsiExpression initializer = local.getInitializer();
    if (!ExpressionUtils.isLiteral(initializer, isAnd)) return false;
    if (!(PsiUtil.getVariableCodeBlock(local, null) instanceof PsiCodeBlock block)) return false;
    PsiElement[] defs = DefUseUtil.getDefs(block, local, expression.getParent());
    // boolean x = false; x|=something;
    return defs.length == 1 && defs[0] == local && 
           VariableAccessUtils.getVariableReferences(local).stream().filter(PsiUtil::isAccessedForWriting).limit(2).count() > 1;
  }

  private static @Nullable LocalQuickFix createSimplifyBooleanExpressionFix(PsiElement element, final boolean value) {
    return createSimplifyBooleanFix(element, value);
  }

  private static LocalQuickFix createReplaceWithNullCheckFix(PsiElement psiAnchor, boolean evaluatesToTrue) {
    if (!(psiAnchor instanceof PsiExpression expr)) return null;
    boolean negated = false;
    if (BoolUtils.isNegation(expr)) {
      expr = BoolUtils.getNegated(expr);
      negated = true;
    }
    if (evaluatesToTrue != negated) return null;
    if (!(expr instanceof final PsiMethodCallExpression call)) return null;
    if (!MethodCallUtils.isEqualsCall(call)) return null;
    PsiExpression arg = ArrayUtil.getFirstElement(call.getArgumentList().getExpressions());
    if (!ExpressionUtils.isNullLiteral(arg)) return null;
    return EqualsToEqualityFix.buildFix(call, negated);
  }

  private static LocalQuickFix [] createConditionalAssignmentFixes(boolean evaluatesToTrue,
                                                                  PsiAssignmentExpression assignment,
                                                                  final boolean onTheFly) {
    IElementType op = assignment.getOperationTokenType();
    boolean toRemove = op == JavaTokenType.ANDEQ && !evaluatesToTrue || op == JavaTokenType.OREQ && evaluatesToTrue;
    if (toRemove && !onTheFly) {
      return LocalQuickFix.EMPTY_ARRAY;
    }
    return new LocalQuickFix[]{toRemove ? new RemoveAssignmentFix() : new SimplifyToAssignmentFix()};
  }

  private static SimplifyBooleanExpressionFix createSimplifyBooleanFix(PsiElement element, boolean value) {
    if (!(element instanceof PsiExpression expression)) return null;
    if (PsiTreeUtil.findChildOfType(element, PsiAssignmentExpression.class) != null) return null;

    while (element.getParent() instanceof PsiExpression) {
      element = element.getParent();
    }
    final SimplifyBooleanExpressionFix fix = new SimplifyBooleanExpressionFix(expression, value);
    // simplify intention already active
    if (!fix.isAvailable() ||
        (SimplifyBooleanExpressionFix.canBeSimplified(expression) && expression instanceof PsiLiteralExpression)) {
      return null;
    }
    return fix;
  }

  /**
   * @param anchor expression
   * @param result the expected value of expression
   * @return true if this expression is effectively an assertion (code throws if its value is not equal to expectedValue)
   */
  private static boolean isAssertionEffectively(PsiExpression anchor, ConstantResult result) {
    Object value = result.value();
    if (value instanceof Boolean booleanValue) {
      return DfaPsiUtil.isAssertionEffectively(anchor, booleanValue);
    }
    if (value != null) return false;
    return DfaPsiUtil.isAssertCallArgument(anchor, ContractValue.nullValue());
  }

  /**
   * Values that especially tracked by data flow inspection
   */
  private enum ConstantResult {
    TRUE, FALSE, NULL, ZERO, UNKNOWN;

    @Override
    public String toString() {
      return this == ZERO ? "0" : StringUtil.toLowerCase(name());
    }

    public Object value() {
      return switch (this) {
        case TRUE -> Boolean.TRUE;
        case FALSE -> Boolean.FALSE;
        case ZERO -> 0;
        case NULL -> null;
        default -> throw new UnsupportedOperationException();
      };
    }

    public static ConstantResult fromDfType(DfType dfType) {
      if (dfType == DfTypes.NULL) return NULL;
      if (dfType == DfTypes.TRUE) return TRUE;
      if (dfType == DfTypes.FALSE) return FALSE;
      if (dfType.isConst(0) || dfType.isConst(0L)) return ZERO;
      return UNKNOWN;
    }
  }
}
