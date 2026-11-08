// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.analysis.impl.codeInspection.dataFlow;

import consulo.logging.attachment.AttachmentFactory;

import consulo.language.dataFlow.interpreter.RunnerResult;
import consulo.language.dataFlow.interpreter.StandardDataFlowInterpreter;
import consulo.language.dataFlow.interpreter.StateQueue;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.ControlFlowAnalyzer;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.JvmDfaMemoryStateImpl;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.descriptors.AssertionDisabledDescriptor;
import consulo.language.dataFlow.interpreter.DfaListener;
import consulo.language.dataFlow.ir.ControlFlow;
import consulo.language.dataFlow.ir.DfaInstructionState;
import consulo.language.dataFlow.memory.DfaMemoryState;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes;
import consulo.language.dataFlow.value.DfaCondition;
import consulo.language.dataFlow.value.DfaValueFactory;
import consulo.language.dataFlow.value.DfaVariableValue;
import consulo.logging.attachment.Attachment;
import consulo.logging.Logger;
import consulo.component.ProcessCanceledException;
import consulo.project.Project;
import consulo.util.lang.ref.Ref;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiLambdaExpression;
import com.intellij.java.language.psi.PsiVariable;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.util.lang.ThreeState;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * A facade to run dataflow analysis on Java
 */
public class StandardDataFlowRunner {
  private static final Logger LOG = Logger.getInstance(StandardDataFlowRunner.class);

  private final DfaValueFactory myValueFactory;
  private final ThreeState myIgnoreAssertions;
  private boolean myInlining = true;
  private StandardDataFlowInterpreter myInterpreter;

  /**
   * @param project current project
   */
  public StandardDataFlowRunner(Project project) {
    this(project, ThreeState.NO);
  }

  /**
   * @param project current project
   * @param ignoreAssertions assertion status: YES (-da), NO (-ea), or UNSURE (either)
   */
  public StandardDataFlowRunner(Project project, ThreeState ignoreAssertions) {
    myValueFactory = new DfaValueFactory(project);
    myIgnoreAssertions = ignoreAssertions;
  }

  public DfaValueFactory getFactory() {
    return myValueFactory;
  }

  public final void cancel() {
    myInterpreter.cancel();
  }

  private @Nullable Collection<DfaMemoryState> createInitialStates(PsiElement psiBlock,
                                                                   DfaListener listener,
                                                                   boolean allowInlining) {
    PsiElement container = PsiTreeUtil.getParentOfType(psiBlock, PsiClass.class, PsiLambdaExpression.class);
    if (container != null && (!(container instanceof PsiClass) || PsiUtil.isLocalOrAnonymousClass((PsiClass)container))) {
      PsiElement block = DfaPsiUtil.getTopmostBlockInSameClass(container.getParent());
      if (block != null) {
        final RunnerResult result;
        try {
          myInlining = allowInlining;
          result = analyzeMethod(block, listener);
        }
        finally {
          myInlining = true;
        }
        if (result == RunnerResult.OK || result == RunnerResult.CANCELLED) {
          PsiElement topmostBlock = DfaPsiUtil.getTopmostBlockInSameClass(psiBlock);
          final Collection<DfaMemoryState> closureStates = topmostBlock == null ? List.of() : myInterpreter.getClosures().get(topmostBlock);
          if (allowInlining || !closureStates.isEmpty()) {
            return closureStates;
          }
        }
        return null;
      }
    }

    return Collections.singletonList(createMemoryState());
  }

  /**
   * Analyze this particular method (lambda, class initializer) without inlining this method into parent one.
   * E.g. if supplied method is a lambda within Stream API call chain, it still will be analyzed as separate method.
   * On the other hand, inlining will normally work inside the supplied method.
   *
   * @param psiBlock method/lambda/class initializer body
   * @param listener a listener to use
   * @return result status
   */
  public final RunnerResult analyzeMethod(PsiElement psiBlock, DfaListener listener) {
    Collection<DfaMemoryState> initialStates = createInitialStates(psiBlock, listener, false);
    return initialStates == null ? RunnerResult.NOT_APPLICABLE : analyzeMethod(psiBlock, listener, initialStates);
  }

  /**
   * Analyze this particular method (lambda, class initializer) trying to inline it into outer scope if possible.
   * Usually inlining works, e.g. for lambdas inside stream API calls.
   *
   * @param psiBlock method/lambda/class initializer body
   * @param listener a listener to use
   * @return result status
   */
  public final RunnerResult analyzeMethodWithInlining(PsiElement psiBlock, DfaListener listener) {
    Collection<DfaMemoryState> initialStates = createInitialStates(psiBlock, listener, true);
    if (initialStates == null) {
      return RunnerResult.NOT_APPLICABLE;
    }
    if (initialStates.isEmpty()) {
      return RunnerResult.OK;
    }
    return analyzeMethod(psiBlock, listener, initialStates);
  }

  final RunnerResult analyzeMethod(PsiElement psiBlock,
                                            DfaListener listener,
                                            Collection<? extends DfaMemoryState> initialStates) {
    ControlFlow flow = buildFlow(psiBlock);
    if (flow == null) return RunnerResult.NOT_APPLICABLE;
    return analyzeFlow(psiBlock, listener, initialStates, flow);
  }

  RunnerResult analyzeFlow(PsiElement psiBlock,
                                    DfaListener listener,
                                    Collection<? extends DfaMemoryState> initialStates,
                                    ControlFlow flow) {
    List<DfaInstructionState> startingStates = createInitialInstructionStates(psiBlock, initialStates, flow);
    if (startingStates.isEmpty()) {
      return RunnerResult.ABORTED;
    }

    return interpret(listener, flow, startingStates);
  }

  protected final @Nullable ControlFlow buildFlow(PsiElement psiBlock) {
    try {
      return ControlFlowAnalyzer.buildFlow(psiBlock, myValueFactory, myInlining);
    }
    catch (ProcessCanceledException ex) {
      throw ex;
    }
    catch (RuntimeException | AssertionError e) {
      LOG.error("Error building control flow", e, AttachmentFactory.get().create("method_body.txt", psiBlock.getText()));
    }
    return null;
  }

  protected final RunnerResult interpret(DfaListener listener,
                                                  ControlFlow flow,
                                                  List<DfaInstructionState> startingStates) {
    myInterpreter = createInterpreter(listener, flow);
    RunnerResult result = myInterpreter.interpret(startingStates);
    afterInterpretation(flow, myInterpreter, result);
    return result;
  }

  protected void afterInterpretation(ControlFlow flow, StandardDataFlowInterpreter interpreter, RunnerResult result) {
    
  }

  protected StandardDataFlowInterpreter createInterpreter(DfaListener listener, ControlFlow flow) {
    return new StandardDataFlowInterpreter(flow, listener);
  }

  protected List<DfaInstructionState> createInitialInstructionStates(PsiElement psiBlock,
                                                                                            Collection<? extends DfaMemoryState> memStates,
                                                                                            ControlFlow flow) {
    DfaVariableValue assertionStatus = AssertionDisabledDescriptor.getAssertionsDisabledVar(myValueFactory);
    if (assertionStatus != null && myIgnoreAssertions != ThreeState.UNSURE) {
      DfaCondition condition = assertionStatus.eq(DfTypes.booleanValue(myIgnoreAssertions.toBoolean()));
      for (DfaMemoryState state : memStates) {
        state.applyCondition(condition);
      }
    }
    return ContainerUtil.map(memStates, s -> new DfaInstructionState(flow.getInstruction(0), s));
  }

  public boolean wasForciblyMerged() {
    return myInterpreter.wasForciblyMerged();
  }

  public RunnerResult analyzeMethodRecursively(PsiElement block, DfaListener listener) {
    Collection<DfaMemoryState> states = createInitialStates(block, listener, false);
    if (states == null) return RunnerResult.NOT_APPLICABLE;
    return analyzeBlockRecursively(block, states, listener);
  }

  public RunnerResult analyzeBlockRecursively(PsiElement block,
                                                       Collection<? extends DfaMemoryState> states,
                                                       DfaListener listener) {
    RunnerResult result = analyzeMethod(block, listener, states);
    if (result != RunnerResult.OK) return result;

    Ref<RunnerResult> ref = Ref.create(RunnerResult.OK);
    forNestedClosures((closure, nestedStates) -> {
      RunnerResult res = analyzeBlockRecursively(closure, nestedStates, listener);
      if (res != RunnerResult.OK) {
        ref.set(res);
      }
    });
    return ref.get();
  }

  protected DfaMemoryState createMemoryState() {
    return new JvmDfaMemoryStateImpl(myValueFactory);
  }

  public void forNestedClosures(BiConsumer<? super PsiElement, ? super Collection<? extends DfaMemoryState>> consumer) {
    MultiMap<PsiElement, DfaMemoryState> closures = myInterpreter.getClosures();
    for (PsiElement closure : closures.keySet()) {
      List<DfaVariableValue> unusedVars = StreamEx.of(getFactory().getValues())
        .select(DfaVariableValue.class)
        .filter(var -> var.getQualifier() == null)
        .filter(var -> var.getPsiVariable() instanceof PsiVariable &&
                       !VariableAccessUtils.variableIsUsed((PsiVariable)var.getPsiVariable(), closure))
        .toList();
      Collection<DfaMemoryState> states = closures.get(closure);
      if (!unusedVars.isEmpty()) {
        List<DfaMemoryState> stateList = StreamEx.of(states)
          .peek(state -> state.forgetVariables(unusedVars::contains))
          .distinct().toList();
        states = StateQueue.squash(stateList);
      }
      consumer.accept(closure, states);
    }
  }
}
