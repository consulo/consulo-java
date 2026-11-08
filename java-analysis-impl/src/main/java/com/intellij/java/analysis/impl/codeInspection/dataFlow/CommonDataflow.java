// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow;

import consulo.language.dataFlow.interpreter.ReachabilityCountingInterpreter;
import consulo.language.dataFlow.interpreter.RunnerResult;
import consulo.language.dataFlow.interpreter.StandardDataFlowInterpreter;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.JavaDfaListener;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.anchor.JavaDfaAnchor;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.anchor.JavaExpressionAnchor;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.anchor.JavaMethodReferenceArgumentAnchor;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.anchor.JavaMethodReferenceReturnAnchor;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.anchor.JavaPolyadicPartAnchor;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.anchor.JavaSwitchDeconstructionLabelAnchor;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.descriptors.AssertionDisabledDescriptor;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.problems.ContractFailureProblem;
import consulo.language.dataFlow.ir.DfaAnchor;
import consulo.language.dataFlow.interpreter.DfaListener;
import consulo.language.dataFlow.interpreter.UnsatisfiedConditionProblem;
import consulo.language.dataFlow.ir.ControlFlow;
import consulo.language.dataFlow.ir.DataFlowIRProvider;
import consulo.language.dataFlow.memory.DfaMemoryState;
import consulo.language.dataFlow.rangeSet.LongRangeSet;
import consulo.language.dataFlow.types.DfIntegralType;
import consulo.language.dataFlow.types.DfType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes;
import consulo.language.dataFlow.value.DfaTypeValue;
import consulo.language.dataFlow.value.DfaValue;
import consulo.language.dataFlow.value.DfaVariableValue;
import consulo.document.util.TextRange;
import com.intellij.java.language.psi.PsiCallExpression;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiCodeBlock;
import com.intellij.java.language.psi.PsiConditionalExpression;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import consulo.application.util.CachedValueProvider;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.util.JavaPsiConstructorUtil;
import consulo.util.lang.ThreeState;
import consulo.application.util.ConcurrentFactoryMap;
import consulo.util.collection.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import static com.intellij.java.analysis.impl.codeInspection.dataFlow.DfaUtil.hasImplicitImpureSuperCall;

public final class CommonDataflow {
  private CommonDataflow() {}
  
  private static class DataflowPoint {
    DfType myDfType = DfType.BOTTOM;
    // empty = top; null = bottom
    @Nullable Set<Object> myPossibleValues = Collections.emptySet();
    boolean myMayFailByContract = false;

    DataflowPoint() {}

    DataflowPoint(DataflowPoint other) {
      myDfType = other.myDfType;
      myPossibleValues = other.myPossibleValues;
      myMayFailByContract = other.myMayFailByContract;
    }

    void addValue(DfaMemoryState memState, DfaValue value) {
      if (myPossibleValues == null) return;
      DfType dfType = memState.getDfType(value);
      Object newValue = dfType.getConstantOfType(Object.class);
      if (newValue == null && !dfType.equals(DfTypes.NULL)) {
        myPossibleValues = null;
        return;
      }
      if (myPossibleValues.contains(newValue)) return;
      if (myPossibleValues.isEmpty()) {
        myPossibleValues = Collections.singleton(newValue);
      }
      else {
        myPossibleValues = new HashSet<>(myPossibleValues);
        myPossibleValues.add(newValue);
      }
    }

    void addFacts(DfaMemoryState memState, DfaValue value) {
      if (myDfType == DfType.TOP) return;
      DfType newType = memState.getDfTypeIncludingDerived(value);
      myDfType = myDfType.join(newType);
    }
  }

  /**
   * Represents the result of dataflow applied to some code fragment (usually a method)
   */
  public static final class DataflowResult {
    private final Map<JavaDfaAnchor, DataflowPoint> myData = new HashMap<>();
    private final List<TextRange> myUnreachable = new ArrayList<>();
    private Map<JavaDfaAnchor, DataflowPoint> myDataAssertionsDisabled = myData;
    private final RunnerResult myResult;

    public DataflowResult(RunnerResult result) {
      myResult = result;
    }

    DataflowResult copy() {
      DataflowResult copy = new DataflowResult(myResult);
      myData.forEach((anchor, point) -> copy.myData.put(anchor, new DataflowPoint(point)));
      copy.myUnreachable.addAll(myUnreachable);
      return copy;
    }

    void add(JavaDfaAnchor anchor, DfaMemoryState memState, DfaValue value) {
      DfaVariableValue assertionDisabled = AssertionDisabledDescriptor.getAssertionsDisabledVar(value.getFactory());
      if (assertionDisabled == null) {
        assert myData == myDataAssertionsDisabled;
        updateDataPoint(myData, anchor, memState, value);
      } else {
        DfType type = memState.getDfType(assertionDisabled);
        if (type == DfTypes.TRUE || type == DfTypes.FALSE) {
          if (myData == myDataAssertionsDisabled) {
            myDataAssertionsDisabled = new HashMap<>(myData);
          }
          updateDataPoint(type == DfTypes.TRUE ? myDataAssertionsDisabled : myData, anchor, memState, value);
        } else {
          updateDataPoint(myData, anchor, memState, value);
          if (myData != myDataAssertionsDisabled) {
            updateDataPoint(myDataAssertionsDisabled, anchor, memState, value);
          }
        }
      }
    }

    private void updateDataPoint(Map<JavaDfaAnchor, DataflowPoint> data,
                                 JavaDfaAnchor anchor,
                                 DfaMemoryState memState,
                                 DfaValue value) {
      DataflowPoint point = data.computeIfAbsent(anchor, e -> new DataflowPoint());
      if (DfaTypeValue.isContractFail(value)) {
        point.myMayFailByContract = true;
        return;
      }
      if (point.myDfType != DfType.TOP && anchor instanceof JavaExpressionAnchor) {
        PsiExpression expression = ((JavaExpressionAnchor)anchor).getExpression();
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
        if (parent instanceof PsiConditionalExpression &&
            !PsiTreeUtil.isAncestor(((PsiConditionalExpression)parent).getCondition(), expression, false)) {
          add(new JavaExpressionAnchor((PsiExpression)parent), memState, value);
        }
      }
      point.addFacts(memState, value);
      point.addValue(memState, value);
    }

    /**
     * @param anchor anchor to check
     * @return true if a given anchor appeared during the analysis
     */
    public boolean anchorWasAnalyzed(JavaDfaAnchor anchor) {
      return myData.containsKey(anchor);
    }

    /**
     * Returns true if given call cannot fail according to its contracts
     * (e.g. {@code Optional.get()} executed under {@code Optional.isPresent()}).
     *
     * @param call call to check
     * @return true if it cannot fail by contract; false if unknown or can fail
     */
    public boolean cannotFailByContract(@Nullable PsiCallExpression call) {
      if (call == null) return false;
      DataflowPoint point = myData.get(new JavaExpressionAnchor(call));
      return point != null && !point.myMayFailByContract;
    }
    
    public Collection<TextRange> getUnreachableRanges() {
      return myResult != RunnerResult.OK ? Collections.emptyList() : myUnreachable;
    }

    /**
     * Returns a set of expression values if known. If non-empty set is returned, then given expression
     * is guaranteed to have one of returned values.
     *
     * @param expression an expression to get its value
     * @return a set of possible values or empty set if not known
     */
    public Set<Object> getExpressionValues(@Nullable PsiExpression expression) {
      if (expression == null) return Collections.emptySet();
      DataflowPoint point = myData.get(new JavaExpressionAnchor(expression));
      if (point == null) return Collections.emptySet();
      Set<Object> values = point.myPossibleValues;
      return values == null ? Collections.emptySet() : Collections.unmodifiableSet(values);
    }

    /**
     * @param expression an expression to infer the DfType, must be deparenthesized.
     * @return DfType for that expression, assuming assertions are disabled.
     * May return {@link DfType#TOP} if no information from dataflow is known about this expression
     * @see #getDfTypeNoAssertions(PsiExpression)
     */
    public DfType getDfType(PsiExpression expression) {
      if (expression == null) return DfType.TOP;
      DataflowPoint point = myData.get(new JavaExpressionAnchor(expression));
      return point == null ? DfType.TOP : point.myDfType;
    }

    public DfType getDfType(JavaDfaAnchor anchor) {
      DataflowPoint point = myData.get(anchor);
      return point == null ? DfType.TOP : point.myDfType;
    }

    public DfType getDfTypeNoAssertions(JavaDfaAnchor anchor) {
      DataflowPoint point = myDataAssertionsDisabled.get(anchor);
      return point == null ? DfType.TOP : point.myDfType;
    }

    /**
     * @param expression an expression to infer the DfType, must be deparenthesized.
     * @return DfType for that expression, assuming assertions are disabled.
     * May return {@link DfType#TOP} if no information from dataflow is known about this expression
     * @see #getDfType(PsiExpression)
     */
    public DfType getDfTypeNoAssertions(PsiExpression expression) {
      if (expression == null) return DfType.TOP;
      DataflowPoint point = myDataAssertionsDisabled.get(new JavaExpressionAnchor(expression));
      return point == null ? DfType.TOP : point.myDfType;
    }
  }

  private static DataflowResult runDFA(@Nullable PsiElement block) {
    if (block == null) return new DataflowResult(RunnerResult.NOT_APPLICABLE);
    var listener = new CommonDataflowListener();
    var runner = new StandardDataFlowRunner(block.getProject(), ThreeState.UNSURE) {
      @Override
      protected StandardDataFlowInterpreter createInterpreter(DfaListener listener, ControlFlow flow) {
        return new ReachabilityCountingInterpreter(flow, listener, false, true, 0);
      }

      @Override
      protected void afterInterpretation(ControlFlow flow,
                                         StandardDataFlowInterpreter interpreter,
                                         RunnerResult result) {
        if (result == RunnerResult.OK) {
          Set<PsiElement> unreachable = ((ReachabilityCountingInterpreter)interpreter).getUnreachable();
          listener.myResult.myUnreachable.addAll(DataFlowIRProvider.computeUnreachableSegments(block, unreachable));
        }
        super.afterInterpretation(flow, interpreter, result);
      }
    };
    RunnerResult result = runner.analyzeMethodRecursively(block, listener);
    if (result != RunnerResult.OK) return new DataflowResult(result);
    if (!(block instanceof PsiClass psiClass)) return listener.myResult;
    DataflowResult dfr = listener.myResult.copy();
    List<DfaMemoryState> states = listener.myEndOfInitializerStates;
    for (PsiMethod method : psiClass.getConstructors()) {
      List<DfaMemoryState> initialStates;
      PsiCodeBlock body = method.getBody();
      if (body == null) continue;
      PsiMethodCallExpression call = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(method);
      if (JavaPsiConstructorUtil.isChainedConstructorCall(call) || (call == null && hasImplicitImpureSuperCall(psiClass, method))) {
        initialStates = Collections.singletonList(runner.createMemoryState());
      } else {
        initialStates = ContainerUtil.map(states, DfaMemoryState::createCopy);
      }
      if (runner.analyzeBlockRecursively(body, initialStates, listener) == RunnerResult.OK) {
        dfr = listener.myResult.copy();
      } else {
        listener.myResult = dfr;
      }
    }
    return dfr;
  }

  /**
   * Returns the dataflow result for code fragment which contains given context
   * @param context a context to get the dataflow result
   * @return the dataflow result or null if dataflow cannot be launched for this context (e.g. we are inside too complex method)
   */
  public static @Nullable DataflowResult getDataflowResult(PsiElement context) {
    PsiElement body = DfaUtil.getDataflowContext(context);
    if (body == null) return null;

    ConcurrentMap<PsiElement, DataflowResult> fileMap =
      LanguageCachedValueUtil.getCachedValue(body.getContainingFile(), () ->
        CachedValueProvider.Result.create(ConcurrentFactoryMap.createMap(e -> {
          DataflowResult result = runDFA(e);
          return result.myResult != RunnerResult.OK ? null : result;
        }), PsiModificationTracker.MODIFICATION_COUNT));
    return fileMap.get(body);
  }

  /**
   * @param expression an expression to infer the DfType
   * @return DfType for that expression. May return {@link DfType#TOP} if no information from dataflow is known about this expression
   */
  public static DfType getDfType(PsiExpression expression) {
    return getDfType(expression, false);
  }

  /**
   * @param expression an expression to infer the DfType
   * @param ignoreAssertions whether to ignore assertion statement during the analysis
   * @return DfType for that expression. May return {@link DfType#TOP} if no information from dataflow is known about this expression
   */
  public static DfType getDfType(PsiExpression expression, boolean ignoreAssertions) {
    DataflowResult result = getDataflowResult(expression);
    if (result == null) return DfType.TOP;
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    return ignoreAssertions ? result.getDfTypeNoAssertions(expression) : result.getDfType(expression);
  }

  /**
   * Returns long range set for expression or null if range is unknown.
   * This method first tries to compute expression using {@link com.intellij.java.language.impl.psi.impl.ConstantExpressionEvaluator}
   * and only then calls {@link #getDfType(PsiExpression)}.
   *
   * @param expression expression to get its range
   * @return long range set
   */
  public static @Nullable LongRangeSet getExpressionRange(@Nullable PsiExpression expression) {
    if (expression == null) return null;
    Object value = ExpressionUtils.computeConstantExpression(expression);
    LongRangeSet rangeSet = LongRangeSet.fromConstant(value);
    if (rangeSet != null) return rangeSet;
    DfType dfType = getDfType(expression);
    return dfType instanceof DfIntegralType ? ((DfIntegralType)dfType).getRange() : null;
  }

  /**
   * Returns the value of given expression calculated via dataflow; or null if value is null or unknown.
   *
   * @param expression expression to analyze
   * @return expression value if known
   */
  public static Object computeValue(@Nullable PsiExpression expression) {
    PsiExpression expressionToAnalyze = PsiUtil.skipParenthesizedExprDown(expression);
    if (expressionToAnalyze == null) return null;
    Object computed = ExpressionUtils.computeConstantExpression(expressionToAnalyze);
    if (computed != null) return computed;
    return getDfType(expressionToAnalyze).getConstantOfType(Object.class);
  }

  private static class CommonDataflowListener implements JavaDfaListener {
    private DataflowResult myResult = new DataflowResult(RunnerResult.OK);
    private final List<DfaMemoryState> myEndOfInitializerStates = new ArrayList<>();

    @Override
    public void beforeInstanceInitializerEnd(DfaMemoryState state) {
      myEndOfInitializerStates.add(state.createCopy());
    }

    @Override
    public void beforeExpressionPush(DfaValue value,
                                     PsiExpression expression,
                                     DfaMemoryState state) {
      myResult.add(new JavaExpressionAnchor(expression), state, value);
    }

    @Override
    public void beforePush(DfaValue [] args,
                           DfaValue value,
                           DfaAnchor anchor,
                           DfaMemoryState state) {
      JavaDfaListener.super.beforePush(args, value, anchor, state);
      if (anchor instanceof JavaMethodReferenceArgumentAnchor ||
          anchor instanceof JavaPolyadicPartAnchor ||
          anchor instanceof JavaMethodReferenceReturnAnchor ||
          anchor instanceof JavaSwitchDeconstructionLabelAnchor) {
        myResult.add((JavaDfaAnchor)anchor, state, value);
      }
    }

    @Override
    public void onCondition(UnsatisfiedConditionProblem problem,
                            DfaValue value,
                            ThreeState failed,
                            DfaMemoryState state) {
      if (problem instanceof ContractFailureProblem && failed != ThreeState.NO) {
        myResult.add(new JavaExpressionAnchor(((ContractFailureProblem)problem).getAnchor()), state,
                     value.getFactory().fromDfType(DfType.FAIL));
      }
    }
  }
}
