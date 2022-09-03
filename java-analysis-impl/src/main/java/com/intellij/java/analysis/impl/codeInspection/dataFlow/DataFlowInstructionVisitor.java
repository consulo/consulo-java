// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.DataFlowInspectionBase.ConstantResult;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.instructions.*;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfConstantType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.*;
import com.intellij.java.analysis.impl.codeInspection.util.OptionalUtil;
import com.intellij.java.language.psi.*;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.logging.Logger;
import consulo.util.lang.Pair;
import consulo.document.util.TextRange;
import com.intellij.psi.*;
import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.language.psi.util.PsiTypesUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.util.lang.ThreeState;
import consulo.util.collection.ContainerUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.TypeUtils;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Stream;

import static consulo.ide.impl.idea.util.ObjectUtils.tryCast;

final class DataFlowInstructionVisitor extends StandardInstructionVisitor {
  private static final Logger LOG = Logger.getInstance(DataFlowInstructionVisitor.class);
  private final Map<NullabilityProblemKind.NullabilityProblem<?>, StateInfo> myStateInfos = new LinkedHashMap<>();
  private final Map<PsiTypeCastExpression, StateInfo> myClassCastProblems = new HashMap<>();
  private final Map<PsiTypeCastExpression, TypeConstraint> myRealOperandTypes = new HashMap<>();
  private final Map<PsiCallExpression, Boolean> myFailingCalls = new HashMap<>();
  private final Map<ExpressionChunk, ConstantResult> myConstantExpressions = new HashMap<>();
  private final Map<PsiElement, ThreeState> myOfNullableCalls = new HashMap<>();
  private final Map<PsiAssignmentExpression, Pair<PsiType, PsiType>> myArrayStoreProblems = new HashMap<>();
  private final Map<PsiMethodReferenceExpression, ConstantResult> myMethodReferenceResults = new HashMap<>();
  private final Map<PsiArrayAccessExpression, ThreeState> myOutOfBoundsArrayAccesses = new HashMap<>();
  private final Set<PsiElement> myReceiverMutabilityViolation = new HashSet<>();
  private final Set<PsiElement> myArgumentMutabilityViolation = new HashSet<>();
  private final Map<PsiExpression, Boolean> mySameValueAssigned = new HashMap<>();
  private final Map<PsiReferenceExpression, ArgResultEquality> mySameArguments = new HashMap<>();
  private final Map<PsiExpression, ThreeState> mySwitchLabelsReachability = new HashMap<>();
  private boolean myAlwaysReturnsNotNull = true;
  private final List<DfaMemoryState> myEndOfInitializerStates = new ArrayList<>();

  private static final CallMatcher USELESS_SAME_ARGUMENTS = CallMatcher.anyOf(
      CallMatcher.staticCall(CommonClassNames.JAVA_LANG_MATH, "min", "max").parameterCount(2),
      CallMatcher.staticCall(CommonClassNames.JAVA_LANG_INTEGER, "min", "max").parameterCount(2),
      CallMatcher.staticCall(CommonClassNames.JAVA_LANG_LONG, "min", "max").parameterCount(2),
      CallMatcher.staticCall(CommonClassNames.JAVA_LANG_FLOAT, "min", "max").parameterCount(2),
      CallMatcher.staticCall(CommonClassNames.JAVA_LANG_DOUBLE, "min", "max").parameterCount(2),
      CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_STRING, "replace").parameterCount(2)
  );

  @Override
  public DfaInstructionState[] visitAssign(AssignInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    PsiExpression left = instruction.getLExpression();
    if (left != null && !Boolean.FALSE.equals(mySameValueAssigned.get(left))) {
      if (!left.isPhysical()) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Non-physical element in assignment instruction: " + left.getParent().getText(), new Throwable());
        }
      } else {
        DfaValue value = memState.peek();
        DfaValue target = memState.getStackValue(1);
        DfType dfType = memState.getDfType(value);
        if (target != null && memState.areEqual(value, target) &&
            !(dfType instanceof DfConstantType && isFloatingZero(((DfConstantType<?>) dfType).getValue())) &&
            // Reporting strings is skipped because string reassignment might be intentionally used to deduplicate the heap objects
            // (we compare strings by contents)
            !(TypeUtils.isJavaLangString(left.getType()) && !memState.isNull(value)) &&
            !isAssignmentToDefaultValueInConstructor(instruction, runner, target)) {
          mySameValueAssigned.merge(left, Boolean.TRUE, Boolean::logicalAnd);
        } else {
          mySameValueAssigned.put(left, Boolean.FALSE);
        }
      }
    }
    return super.visitAssign(instruction, runner, memState);
  }

  @Override
  protected void beforeConditionalJump(ConditionalGotoInstruction instruction, boolean isTrueBranch) {
    PsiExpression anchor = instruction.getPsiAnchor();
    if (anchor != null && PsiImplUtil.getSwitchLabel(anchor) != null) {
      mySwitchLabelsReachability.merge(anchor, ThreeState.fromBoolean(isTrueBranch), ThreeState::merge);
    }
  }

  private static boolean isAssignmentToDefaultValueInConstructor(AssignInstruction instruction, DataFlowRunner runner, DfaValue target) {
    if (!(target instanceof DfaVariableValue)) {
      return false;
    }
    DfaVariableValue var = (DfaVariableValue) target;
    if (!(var.getPsiVariable() instanceof PsiField) || var.getQualifier() == null ||
        !(var.getQualifier().getDescriptor() instanceof DfaExpressionFactory.ThisDescriptor)) {
      return false;
    }

    // chained assignment like this.a = this.b = 0; is also supported
    PsiExpression rExpression = instruction.getRExpression();
    while (rExpression instanceof PsiAssignmentExpression &&
        ((PsiAssignmentExpression) rExpression).getOperationTokenType().equals(JavaTokenType.EQ)) {
      rExpression = ((PsiAssignmentExpression) rExpression).getRExpression();
    }
    DfaValue dest = runner.getFactory().createValue(rExpression);
    if (dest == null) {
      return false;
    }
    DfType dfType = dest.getDfType();

    PsiType type = var.getType();
    boolean isDefaultValue = DfConstantType.isConst(dfType, PsiTypesUtil.getDefaultValue(type)) ||
        DfConstantType.isConst(dfType, 0) && TypeConversionUtil.isIntegralNumberType(type);
    if (!isDefaultValue) {
      return false;
    }
    PsiMethod method = PsiTreeUtil.getParentOfType(rExpression, PsiMethod.class);
    return method != null && method.isConstructor();
  }

  // Reporting of floating zero is skipped, because this produces false-positives on the code like
  // if(x == -0.0) x = 0.0;
  private static boolean isFloatingZero(Object value) {
    if (value instanceof Double) {
      return ((Double) value).doubleValue() == 0.0;
    }
    if (value instanceof Float) {
      return ((Float) value).floatValue() == 0.0f;
    }
    return false;
  }

  StreamEx<PsiExpression> sameValueAssignments() {
    return StreamEx.ofKeys(mySameValueAssigned, Boolean::booleanValue);
  }

  EntryStream<PsiReferenceExpression, ArgResultEquality> pointlessSameArguments() {
    return EntryStream.of(mySameArguments).filterValues(ArgResultEquality::hasEquality);
  }

  @Override
  protected void onTypeCast(PsiTypeCastExpression castExpression, DfaMemoryState state, boolean castPossible) {
    myClassCastProblems.computeIfAbsent(castExpression, e -> new StateInfo()).update(state, castPossible);
  }

  StreamEx<NullabilityProblemKind.NullabilityProblem<?>> problems() {
    return StreamEx.ofKeys(myStateInfos, StateInfo::shouldReport);
  }

  public Map<PsiAssignmentExpression, Pair<PsiType, PsiType>> getArrayStoreProblems() {
    return myArrayStoreProblems;
  }

  Map<PsiElement, ThreeState> getOfNullableCalls() {
    return myOfNullableCalls;
  }

  Map<PsiExpression, ConstantResult> getConstantExpressions() {
    return EntryStream.of(myConstantExpressions).filterKeys(chunk -> chunk.myRange == null)
        .mapKeys(chunk -> chunk.myExpression).toMap();
  }

  Map<ExpressionChunk, ConstantResult> getConstantExpressionChunks() {
    return myConstantExpressions;
  }

  Map<PsiMethodReferenceExpression, ConstantResult> getMethodReferenceResults() {
    return myMethodReferenceResults;
  }

  Map<PsiExpression, ThreeState> getSwitchLabelsReachability() {
    return mySwitchLabelsReachability;
  }

  EntryStream<PsiTypeCastExpression, Pair<Boolean, PsiType>> getFailingCastExpressions() {
    return EntryStream.of(myClassCastProblems).filterValues(StateInfo::shouldReport).mapToValue(
        (cast, info) -> Pair.create(info.alwaysFails(), myRealOperandTypes.getOrDefault(cast, TypeConstraints.TOP).getPsiType(cast.getProject())));
  }

  Set<PsiElement> getMutabilityViolations(boolean receiver) {
    return receiver ? myReceiverMutabilityViolation : myArgumentMutabilityViolation;
  }

  public List<DfaMemoryState> getEndOfInitializerStates() {
    return myEndOfInitializerStates;
  }

  Stream<PsiArrayAccessExpression> outOfBoundsArrayAccesses() {
    return StreamEx.ofKeys(myOutOfBoundsArrayAccesses, ThreeState.YES::equals);
  }

  StreamEx<PsiCallExpression> alwaysFailingCalls() {
    return StreamEx.ofKeys(myFailingCalls, v -> v);
  }

  boolean isAlwaysReturnsNotNull(Instruction[] instructions) {
    return myAlwaysReturnsNotNull &&
        ContainerUtil.exists(instructions, i -> i instanceof ReturnInstruction && ((ReturnInstruction) i).getAnchor() instanceof PsiReturnStatement);
  }

  public boolean isInstanceofRedundant(InstanceofInstruction instruction) {
    PsiExpression expression = instruction.getExpression();
    if (expression == null || myUsefulInstanceofs.contains(instruction) || !myReachable.contains(instruction)) {
      return false;
    }
    ConstantResult result = expression instanceof PsiMethodReferenceExpression ?
        myMethodReferenceResults.get(expression) : myConstantExpressions.get(new ExpressionChunk(expression, null));
    return result != ConstantResult.TRUE && result != ConstantResult.FALSE;
  }

  @Override
  protected void beforeExpressionPush(@Nonnull DfaValue value,
                                      @Nonnull PsiExpression expression,
                                      @Nullable TextRange range,
                                      @Nonnull DfaMemoryState memState) {
    if (!expression.isPhysical()) {
      Application application = ApplicationManager.getApplication();
      if (application.isEAP() || application.isInternal() || application.isUnitTestMode()) {
        throw new IllegalStateException("Non-physical expression is passed");
      }
    }
    expression.accept(new ExpressionVisitor(value, memState));
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (parent instanceof PsiTypeCastExpression) {
      TypeConstraint fact = TypeConstraint.fromDfType(memState.getDfType(value));
      myRealOperandTypes.merge((PsiTypeCastExpression) parent, fact, TypeConstraint::join);
    }
    reportConstantExpressionValue(value, memState, expression, range);
  }

  @Override
  protected void onMethodCall(@Nonnull DfaValue result,
                              @Nonnull PsiExpression expression,
                              @Nonnull DfaCallArguments arguments,
                              @Nonnull DfaMemoryState memState) {
    PsiReferenceExpression reference = USELESS_SAME_ARGUMENTS.getReferenceIfMatched(expression);
    if (reference != null) {
      ArgResultEquality equality = new ArgResultEquality(
          memState.areEqual(arguments.myArguments[0], arguments.myArguments[1]),
          memState.areEqual(result, arguments.myArguments[0]),
          memState.areEqual(result, arguments.myArguments[1]));
      mySameArguments.merge(reference, equality, ArgResultEquality::merge);
    }
  }

  @Override
  protected void beforeMethodReferenceResultPush(@Nonnull DfaValue value,
                                                 @Nonnull PsiMethodReferenceExpression methodRef,
                                                 @Nonnull DfaMemoryState state) {
    if (OptionalUtil.OPTIONAL_OF_NULLABLE.methodReferenceMatches(methodRef)) {
      processOfNullableResult(value, state, methodRef.getReferenceNameElement());
    }
    PsiMethod method = tryCast(methodRef.resolve(), PsiMethod.class);
    if (method != null && JavaMethodContractUtil.isPure(method)) {
      List<StandardMethodContract> contracts = JavaMethodContractUtil.getMethodContracts(method);
      if (contracts.isEmpty() || !contracts.get(0).isTrivial()) {
        myMethodReferenceResults.compute(methodRef, (mr, curState) -> ConstantResult.mergeValue(curState, state, value));
      }
    }
  }

  private void processOfNullableResult(@Nonnull DfaValue value, @Nonnull DfaMemoryState memState, PsiElement anchor) {
    DfaValueFactory factory = value.getFactory();
    DfaValue optionalValue = SpecialField.OPTIONAL_VALUE.createValue(factory, value);
    ThreeState present;
    if (memState.isNull(optionalValue)) {
      present = ThreeState.NO;
    } else if (memState.isNotNull(optionalValue)) {
      present = ThreeState.YES;
    } else {
      present = ThreeState.UNSURE;
    }
    myOfNullableCalls.merge(anchor, present, ThreeState::merge);
  }

  @Override
  protected void processArrayAccess(PsiArrayAccessExpression expression, boolean alwaysOutOfBounds) {
    myOutOfBoundsArrayAccesses.merge(expression, ThreeState.fromBoolean(alwaysOutOfBounds), ThreeState::merge);
  }

  @Override
  protected void processArrayStoreTypeMismatch(PsiAssignmentExpression assignmentExpression, PsiType fromType, PsiType toType) {
    if (assignmentExpression != null) {
      myArrayStoreProblems.put(assignmentExpression, Pair.create(fromType, toType));
    }
  }

  @Override
  public DfaInstructionState[] visitEndOfInitializer(EndOfInitializerInstruction instruction, DataFlowRunner runner, DfaMemoryState state) {
    if (!instruction.isStatic()) {
      myEndOfInitializerStates.add(state.createCopy());
    }
    return super.visitEndOfInitializer(instruction, runner, state);
  }

  private static boolean hasNonTrivialFailingContracts(PsiCallExpression call) {
    List<? extends MethodContract> contracts = JavaMethodContractUtil.getMethodCallContracts(call);
    return !contracts.isEmpty() &&
        contracts.stream().anyMatch(contract -> contract.getReturnValue().isFail() && !contract.isTrivial());
  }

  private void reportConstantExpressionValue(DfaValue value, DfaMemoryState memState, PsiExpression expression, TextRange range) {
    if (expression instanceof PsiLiteralExpression) {
      return;
    }
    ExpressionChunk chunk = new ExpressionChunk(expression, range);
    myConstantExpressions.compute(chunk, (c, curState) -> ConstantResult.mergeValue(curState, memState, value));
  }

  @Override
  protected boolean checkNotNullable(DfaMemoryState state, @Nonnull DfaValue value, @Nullable NullabilityProblemKind.NullabilityProblem<?> problem) {
    if (problem != null && problem.getKind() == NullabilityProblemKind.nullableReturn && !state.isNotNull(value)) {
      myAlwaysReturnsNotNull = false;
    }

    boolean ok = super.checkNotNullable(state, value, problem);
    if (problem == null) {
      return ok;
    }
    StateInfo info = myStateInfos.computeIfAbsent(problem, k -> new StateInfo());
    info.update(state, ok);
    return ok;
  }

  @Override
  protected void reportMutabilityViolation(boolean receiver, @Nonnull PsiElement anchor) {
    if (receiver) {
      if (anchor instanceof PsiMethodReferenceExpression) {
        anchor = ((PsiMethodReferenceExpression) anchor).getReferenceNameElement();
      } else if (anchor instanceof PsiMethodCallExpression) {
        anchor = ((PsiMethodCallExpression) anchor).getMethodExpression().getReferenceNameElement();
      }
      if (anchor != null) {
        myReceiverMutabilityViolation.add(anchor);
      }
    } else {
      myArgumentMutabilityViolation.add(anchor);
    }
  }

  private static class StateInfo {
    boolean ephemeralException;
    boolean normalException;
    boolean normalOk;

    void update(DfaMemoryState state, boolean ok) {
      if (state.isEphemeral()) {
        if (!ok) {
          ephemeralException = true;
        }
      } else {
        if (ok) {
          normalOk = true;
        } else {
          normalException = true;
        }
      }
    }

    boolean shouldReport() {
      // non-ephemeral exceptions should be reported
      // ephemeral exceptions should also be reported if only ephemeral states have reached a particular problematic instruction
      //  (e.g. if it's inside "if (var == null)" check after contract method invocation
      return normalException || ephemeralException && !normalOk;
    }

    boolean alwaysFails() {
      return (normalException || ephemeralException) && !normalOk;
    }
  }

  private class ExpressionVisitor extends JavaElementVisitor {
    private final DfaValue myValue;
    private final DfaMemoryState myMemState;

    ExpressionVisitor(DfaValue value, DfaMemoryState memState) {
      myValue = value;
      myMemState = memState;
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression call) {
      super.visitMethodCallExpression(call);
      if (OptionalUtil.OPTIONAL_OF_NULLABLE.test(call)) {
        processOfNullableResult(myValue, myMemState, call.getArgumentList().getExpressions()[0]);
      }
    }

    @Override
    public void visitCallExpression(PsiCallExpression call) {
      super.visitCallExpression(call);
      Boolean isFailing = myFailingCalls.get(call);
      if (isFailing != null || hasNonTrivialFailingContracts(call)) {
        myFailingCalls.put(call, DfaTypeValue.isContractFail(myValue) && !Boolean.FALSE.equals(isFailing));
      }
    }
  }

  static class ExpressionChunk {
    final
    @Nonnull
    PsiExpression myExpression;
    final
    @Nullable
    TextRange myRange;

    ExpressionChunk(@Nonnull PsiExpression expression, @Nullable TextRange range) {
      myExpression = expression;
      myRange = range;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      ExpressionChunk chunk = (ExpressionChunk) o;
      return myExpression.equals(chunk.myExpression) &&
          Objects.equals(myRange, chunk.myRange);
    }

    @Override
    public int hashCode() {
      return 31 * myExpression.hashCode() + Objects.hashCode(myRange);
    }

    @Override
    public String toString() {
      String text = myExpression.getText();
      return myRange == null ? text : myRange.substring(text);
    }
  }

  static class ArgResultEquality {
    boolean argsEqual;
    boolean firstArgEqualToResult;
    boolean secondArgEqualToResult;

    ArgResultEquality(boolean argsEqual, boolean firstArgEqualToResult, boolean secondArgEqualToResult) {
      this.argsEqual = argsEqual;
      this.firstArgEqualToResult = firstArgEqualToResult;
      this.secondArgEqualToResult = secondArgEqualToResult;
    }

    ArgResultEquality merge(ArgResultEquality other) {
      return new ArgResultEquality(argsEqual && other.argsEqual, firstArgEqualToResult && other.firstArgEqualToResult,
          secondArgEqualToResult && other.secondArgEqualToResult);
    }

    boolean hasEquality() {
      return argsEqual || firstArgEqualToResult || secondArgEqualToResult;
    }
  }
}
