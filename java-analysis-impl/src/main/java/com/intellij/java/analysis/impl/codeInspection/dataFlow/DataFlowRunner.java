// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.java.analysis.impl.codeInspection.dataFlow;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.instructions.*;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaExpressionFactory;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaValue;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.java.language.codeInsight.Nullability;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.Application;
import consulo.application.progress.ProgressManager;
import consulo.application.util.registry.Registry;
import consulo.component.ProcessCanceledException;
import consulo.language.psi.PsiCodeFragment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.logging.attachment.Attachment;
import consulo.logging.attachment.AttachmentFactory;
import consulo.logging.attachment.RuntimeExceptionWithAttachments;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.ThreeState;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class DataFlowRunner {
    private static final Logger LOG = Logger.getInstance(DataFlowRunner.class);
    private static final int MERGING_BACK_BRANCHES_THRESHOLD = 50;
    // Maximum allowed attempts to process instruction. Fail as too complex to process if certain instruction
    // is executed more than this limit times.
    public static final int MAX_STATES_PER_BRANCH = 300;

    private Instruction[] myInstructions;
    private final
    @Nonnull
    MultiMap<PsiElement, DfaMemoryState> myNestedClosures = new MultiMap<>();
    private final
    @Nonnull
    DfaValueFactory myValueFactory;
    private final
    @Nonnull
    ThreeState myIgnoreAssertions;
    private boolean myInlining = true;
    private boolean myCancelled = false;
    private boolean myWasForciblyMerged = false;
    private final TimeStats myStats = createStatistics();

    public DataFlowRunner(@Nonnull Project project) {
        this(project, null);
    }

    public DataFlowRunner(@Nonnull Project project, @Nullable PsiElement context) {
        this(project, context, false, ThreeState.NO);
    }

    /**
     * @param project                   current project
     * @param context                   analysis context element (code block, class, expression, etc.); used to determine whether we can trust
     *                                  field initializers (e.g. we usually cannot if context is a constructor)
     * @param unknownMembersAreNullable if true every parameter or method return value without nullity annotation is assumed to be nullable
     * @param ignoreAssertions          if true, assertion statements will be ignored, as if JVM is started with -da.
     */
    public DataFlowRunner(
        @Nonnull Project project,
        @Nullable PsiElement context,
        boolean unknownMembersAreNullable,
        @Nonnull ThreeState ignoreAssertions
    ) {
        myValueFactory = new DfaValueFactory(project, context, unknownMembersAreNullable);
        myIgnoreAssertions = ignoreAssertions;
    }

    @Nonnull
    public DfaValueFactory getFactory() {
        return myValueFactory;
    }

    /**
     * Call this method from the visitor to cancel analysis (e.g. if wanted fact is already established and subsequent analysis
     * is useless). In this case {@link RunnerResult#CANCELLED} will be returned.
     */
    public final void cancel() {
        myCancelled = true;
    }

    @Nullable
    @RequiredReadAction
    private Collection<DfaMemoryState> createInitialStates(
        @Nonnull PsiElement psiBlock,
        @Nonnull InstructionVisitor visitor,
        boolean allowInlining
    ) {
        PsiElement container = PsiTreeUtil.getParentOfType(psiBlock, PsiClass.class, PsiLambdaExpression.class);
        if (container != null && !(container instanceof PsiClass psiClass && !PsiUtil.isLocalOrAnonymousClass(psiClass))) {
            PsiElement block = DfaPsiUtil.getTopmostBlockInSameClass(container.getParent());
            if (block != null) {
                final RunnerResult result;
                try {
                    myInlining = allowInlining;
                    result = analyzeMethod(block, visitor);
                }
                finally {
                    myInlining = true;
                }
                if (result == RunnerResult.OK) {
                    final Collection<DfaMemoryState> closureStates = myNestedClosures.get(DfaPsiUtil.getTopmostBlockInSameClass(psiBlock));
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
     * @param visitor  a visitor to use
     * @return result status
     */
    @Nonnull
    @RequiredReadAction
    public final RunnerResult analyzeMethod(@Nonnull PsiElement psiBlock, @Nonnull InstructionVisitor visitor) {
        Collection<DfaMemoryState> initialStates = createInitialStates(psiBlock, visitor, false);
        return initialStates == null ? RunnerResult.NOT_APPLICABLE : analyzeMethod(psiBlock, visitor, initialStates);
    }

    /**
     * Analyze this particular method (lambda, class initializer) trying to inline it into outer scope if possible.
     * Usually inlining works, e.g. for lambdas inside stream API calls.
     *
     * @param psiBlock method/lambda/class initializer body
     * @param visitor  a visitor to use
     * @return result status
     */
    @Nonnull
    @RequiredReadAction
    public final RunnerResult analyzeMethodWithInlining(@Nonnull PsiElement psiBlock, @Nonnull InstructionVisitor visitor) {
        Collection<DfaMemoryState> initialStates = createInitialStates(psiBlock, visitor, true);
        if (initialStates == null) {
            return RunnerResult.NOT_APPLICABLE;
        }
        if (initialStates.isEmpty()) {
            return RunnerResult.OK;
        }
        return analyzeMethod(psiBlock, visitor, initialStates);
    }

    /**
     * Analyze given code-block without analyzing any parent and children context
     *
     * @param block   block to analyze
     * @param visitor visitor to use
     * @return result status
     */
    @RequiredReadAction
    public final RunnerResult analyzeCodeBlock(@Nonnull PsiCodeBlock block, @Nonnull InstructionVisitor visitor) {
        return analyzeMethod(block, visitor, Collections.singleton(createMemoryState()));
    }

    @Nonnull
    @RequiredReadAction
    final RunnerResult analyzeMethod(
        @Nonnull PsiElement psiBlock,
        @Nonnull InstructionVisitor visitor,
        @Nonnull Collection<? extends DfaMemoryState> initialStates
    ) {
        ControlFlow flow = buildFlow(psiBlock);
        if (flow == null) {
            return RunnerResult.NOT_APPLICABLE;
        }
        List<DfaInstructionState> startingStates = createInitialInstructionStates(psiBlock, initialStates, flow);
        if (startingStates.isEmpty()) {
            return RunnerResult.ABORTED;
        }

        return interpret(psiBlock, visitor, flow, startingStates);
    }

    @Nullable
    @RequiredReadAction
    protected final ControlFlow buildFlow(@Nonnull PsiElement psiBlock) {
        ControlFlow flow = null;
        try {
            myStats.reset();
            flow = new ControlFlowAnalyzer(myValueFactory, psiBlock, myInlining).buildControlFlow();
            myStats.endFlow();

            if (flow != null) {
                new LiveVariablesAnalyzer(flow, myValueFactory).flushDeadVariablesOnStatementFinish();
            }
            myStats.endLVA();
        }
        catch (ProcessCanceledException ex) {
            throw ex;
        }
        catch (RuntimeException | AssertionError e) {
            reportDfaProblem(psiBlock, flow, null, e);
        }
        return flow;
    }

    @Nonnull
    @RequiredReadAction
    protected final RunnerResult interpret(
        @Nonnull PsiElement psiBlock,
        @Nonnull InstructionVisitor visitor,
        @Nonnull ControlFlow flow,
        @Nonnull List<DfaInstructionState> startingStates
    ) {
        int endOffset = flow.getInstructionCount();
        myInstructions = flow.getInstructions();
        DfaInstructionState lastInstructionState = null;
        myNestedClosures.clear();
        myWasForciblyMerged = false;

        final StateQueue queue = new StateQueue();
        for (DfaInstructionState state : startingStates) {
            queue.offer(state);
        }

        MultiMap<BranchingInstruction, DfaMemoryState> processedStates = MultiMap.createSet();
        MultiMap<BranchingInstruction, DfaMemoryState> incomingStates = MultiMap.createSet();
        try {
            Set<Instruction> joinInstructions = getJoinInstructions();
            int[] loopNumber = flow.getLoopNumbers();

            int stateLimit = Registry.intValue("ide.dfa.state.limit", 50000);
            int count = 0;
            while (!queue.isEmpty()) {
                myStats.startMerge();
                List<DfaInstructionState> states = queue.getNextInstructionStates(joinInstructions);
                myStats.endMerge();
                if (states.size() > MAX_STATES_PER_BRANCH) {
                    LOG.trace("Too complex because too many different possible states");
                    return RunnerResult.TOO_COMPLEX;
                }
                assert !states.isEmpty();
                Instruction instruction = states.get(0).getInstruction();
                beforeInstruction(instruction);
                for (DfaInstructionState instructionState : states) {
                    lastInstructionState = instructionState;
                    if (count++ > stateLimit) {
                        LOG.trace("Too complex data flow: too many instruction states processed");
                        return RunnerResult.TOO_COMPLEX;
                    }
                    ProgressManager.checkCanceled();

                    if (LOG.isTraceEnabled()) {
                        LOG.trace(instructionState.toString());
                    }

                    if (instruction instanceof BranchingInstruction branching) {
                        Collection<DfaMemoryState> processed = processedStates.get(branching);
                        if (containsState(processed, instructionState)) {
                            continue;
                        }
                        if (processed.size() > MERGING_BACK_BRANCHES_THRESHOLD) {
                            myStats.startMerge();
                            instructionState = mergeBackBranches(instructionState, processed);
                            myStats.endMerge();
                            if (containsState(processed, instructionState)) {
                                continue;
                            }
                        }
                        if (processed.size() > MAX_STATES_PER_BRANCH) {
                            LOG.trace("Too complex because too many different possible states");
                            return RunnerResult.TOO_COMPLEX;
                        }
                        if (loopNumber[branching.getIndex()] != 0) {
                            processedStates.putValue(branching, instructionState.getMemoryState().createCopy());
                        }
                    }

                    DfaInstructionState[] after = acceptInstruction(visitor, instructionState);
                    if (LOG.isDebugEnabled() && instruction instanceof ControlTransferInstruction && after.length == 0) {
                        DfaMemoryState memoryState = instructionState.getMemoryState();
                        if (!memoryState.isEmptyStack()) {
                            // can pop safely as this memory state is unnecessary anymore (after is empty)
                            DfaValue topValue = memoryState.pop();
                            if (!(topValue instanceof DfaControlTransferValue || psiBlock instanceof PsiCodeFragment && memoryState.isEmptyStack())) {
                                // push back so error report includes this entry
                                memoryState.push(topValue);
                                reportDfaProblem(psiBlock, flow, instructionState, new RuntimeException("Stack is corrupted"));
                            }
                        }
                    }
                    for (DfaInstructionState state : after) {
                        Instruction nextInstruction = state.getInstruction();
                        if (nextInstruction.getIndex() >= endOffset) {
                            continue;
                        }
                        handleStepOutOfLoop(
                            instruction,
                            nextInstruction,
                            loopNumber,
                            processedStates,
                            incomingStates,
                            states,
                            after,
                            queue
                        );
                        if (nextInstruction instanceof BranchingInstruction branching) {
                            if (containsState(processedStates.get(branching), state)
                                || containsState(incomingStates.get(branching), state)) {
                                continue;
                            }
                            if (loopNumber[branching.getIndex()] != 0) {
                                incomingStates.putValue(branching, state.getMemoryState().createCopy());
                            }
                        }
                        queue.offer(state);
                    }
                }
                afterInstruction(instruction);
                if (myCancelled) {
                    return RunnerResult.CANCELLED;
                }
            }

            myWasForciblyMerged |= queue.wasForciblyMerged();
            myStats.endProcess();
            if (myStats.isTooSlow()) {
                String message = "Too slow DFA\nIf you report this problem, please consider including the attachments\n" + myStats +
                    "\nControl flow size: " + flow.getInstructionCount();
                reportDfaProblem(psiBlock, flow, null, new RuntimeException(message));
            }
            return RunnerResult.OK;
        }
        catch (ProcessCanceledException ex) {
            throw ex;
        }
        catch (RuntimeException | AssertionError e) {
            reportDfaProblem(psiBlock, flow, lastInstructionState, e);
            return RunnerResult.ABORTED;
        }
    }

    @Nonnull
    protected List<DfaInstructionState> createInitialInstructionStates(
        @Nonnull PsiElement psiBlock,
        @Nonnull Collection<? extends DfaMemoryState> memStates,
        @Nonnull ControlFlow flow
    ) {
        initializeVariables(psiBlock, memStates, flow);
        return ContainerUtil.map(memStates, s -> new DfaInstructionState(flow.getInstruction(0), s));
    }

    protected void beforeInstruction(Instruction instruction) {
    }

    protected void afterInstruction(Instruction instruction) {
    }

    @Nonnull
    private DfaInstructionState mergeBackBranches(DfaInstructionState instructionState, Collection<DfaMemoryState> processed) {
        DfaMemoryStateImpl curState = (DfaMemoryStateImpl)instructionState.getMemoryState();
        Object key = curState.getMergeabilityKey();
        DfaMemoryStateImpl mergedState = StreamEx.of(processed)
            .select(DfaMemoryStateImpl.class)
            .filterBy(DfaMemoryStateImpl::getMergeabilityKey, key)
            .foldLeft(
                curState,
                (s1, s2) -> {
                    s1.merge(s2);
                    return s1;
                }
            );
        instructionState = new DfaInstructionState(instructionState.getInstruction(), mergedState);
        myWasForciblyMerged = true;
        return instructionState;
    }

    boolean wasForciblyMerged() {
        return myWasForciblyMerged;
    }

    @Nonnull
    private Set<Instruction> getJoinInstructions() {
        Set<Instruction> joinInstructions = new HashSet<>();
        for (int index = 0; index < myInstructions.length; index++) {
            Instruction instruction = myInstructions[index];
            if (instruction instanceof GotoInstruction gotoInsn) {
                joinInstructions.add(myInstructions[gotoInsn.getOffset()]);
            }
            else if (instruction instanceof ConditionalGotoInstruction condGotoInsn) {
                joinInstructions.add(myInstructions[condGotoInsn.getOffset()]);
            }
            else if (instruction instanceof ControlTransferInstruction controlTransferInsn) {
                IntStreamEx.of(controlTransferInsn.getPossibleTargetIndices()).elements(myInstructions).into(joinInstructions);
            }
            else if (instruction instanceof MethodCallInstruction methodCallInsn && !methodCallInsn.getContracts().isEmpty()) {
                joinInstructions.add(myInstructions[index + 1]);
            }
            else if (instruction instanceof FinishElementInstruction finishElementInsn && !finishElementInsn.getVarsToFlush().isEmpty()) {
                // Good chances to squash something after some vars are flushed
                joinInstructions.add(myInstructions[index + 1]);
            }
        }
        return joinInstructions;
    }

    @RequiredReadAction
    private static void reportDfaProblem(
        @Nonnull PsiElement psiBlock,
        ControlFlow flow,
        DfaInstructionState lastInstructionState, Throwable e
    ) {
        Attachment[] attachments = {AttachmentFactory.get().create("method_body.txt", psiBlock.getText())};
        if (flow != null) {
            String flowText = flow.toString();
            if (lastInstructionState != null) {
                int index = lastInstructionState.getInstruction().getIndex();
                flowText = flowText.replaceAll("(?m)^", "  ");
                flowText = flowText.replaceFirst("(?m)^ {2}" + index + ": ", "* " + index + ": ");
            }
            attachments = ArrayUtil.append(attachments, AttachmentFactory.get().create("flow.txt", flowText));
            if (lastInstructionState != null) {
                DfaMemoryState memoryState = lastInstructionState.getMemoryState();
                String memStateText = null;
                try {
                    memStateText = memoryState.toString();
                }
                catch (RuntimeException second) {
                    e.addSuppressed(second);
                }
                if (memStateText != null) {
                    attachments = ArrayUtil.append(attachments, AttachmentFactory.get().create("memory_state.txt", memStateText));
                }
            }
        }
        if (e instanceof RuntimeExceptionWithAttachments runtimeExceptionWithAttachments) {
            attachments = ArrayUtil.mergeArrays(attachments, runtimeExceptionWithAttachments.getAttachments());
        }
        LOG.error(new RuntimeExceptionWithAttachments(e, attachments));
    }

    @Nonnull
    @RequiredReadAction
    public RunnerResult analyzeMethodRecursively(@Nonnull PsiElement block, @Nonnull StandardInstructionVisitor visitor) {
        Collection<DfaMemoryState> states = createInitialStates(block, visitor, false);
        if (states == null) {
            return RunnerResult.NOT_APPLICABLE;
        }
        return analyzeBlockRecursively(block, states, visitor);
    }

    @Nonnull
    @RequiredReadAction
    public RunnerResult analyzeBlockRecursively(
        @Nonnull PsiElement block,
        @Nonnull Collection<? extends DfaMemoryState> states,
        @Nonnull StandardInstructionVisitor visitor
    ) {
        RunnerResult result = analyzeMethod(block, visitor, states);
        if (result != RunnerResult.OK) {
            return result;
        }

        SimpleReference<RunnerResult> ref = SimpleReference.create(RunnerResult.OK);
        forNestedClosures((closure, nestedStates) -> {
            RunnerResult res = analyzeBlockRecursively(closure, nestedStates, visitor);
            if (res != RunnerResult.OK) {
                ref.set(res);
            }
        });
        return ref.get();
    }

    private void initializeVariables(
        @Nonnull PsiElement psiBlock,
        @Nonnull Collection<? extends DfaMemoryState> initialStates,
        @Nonnull ControlFlow flow
    ) {
        List<DfaVariableValue> vars = flow.accessedVariables().collect(Collectors.toList());
        DfaVariableValue assertionStatus = myValueFactory.getAssertionDisabled();
        if (assertionStatus != null && myIgnoreAssertions != ThreeState.UNSURE) {
            for (DfaMemoryState state : initialStates) {
                state.applyCondition(assertionStatus.eq(myValueFactory.getBoolean(myIgnoreAssertions.toBoolean())));
            }
        }
        if (psiBlock instanceof PsiClass psiClass) {
            DfaVariableValue thisValue = getFactory().getVarFactory().createThisValue(psiClass);
            // In class initializer this variable is local until escaped
            for (DfaMemoryState state : initialStates) {
                state.meetDfType(thisValue, DfTypes.LOCAL_OBJECT);
            }
            return;
        }
        if (psiBlock.getParent() instanceof PsiMethod method && !method.isConstructor()) {
            Map<DfaVariableValue, DfaValue> initialValues = StreamEx.of(vars)
                .mapToEntry(var -> makeInitialValue(var, method))
                .nonNullValues()
                .toMap();
            for (DfaMemoryState state : initialStates) {
                initialValues.forEach(state::setVarValue);
            }
        }
    }

    @Nullable
    private static DfaValue makeInitialValue(DfaVariableValue var, @Nonnull PsiMethod method) {
        DfaValueFactory factory = var.getFactory();
        if (var.getDescriptor() instanceof DfaExpressionFactory.ThisDescriptor thisDescriptor && var.getType() != null) {
            PsiClass aClass = thisDescriptor.getPsiElement();
            if (method.getContainingClass() == aClass && MutationSignature.fromMethod(method).preservesThis()) {
                // Unmodifiable view, because we cannot call mutating methods, but it's not guaranteed that all fields are stable
                // as fields may not contribute to the visible state
                DfType dfType = DfTypes.typedObject(var.getType(), Nullability.NOT_NULL).meet(Mutability.UNMODIFIABLE_VIEW.asDfType());
                return factory.fromDfType(dfType);
            }
            return null;
        }
        if (!DfaUtil.isEffectivelyUnqualified(var)) {
            return null;
        }
        PsiField field = ObjectUtil.tryCast(var.getPsiVariable(), PsiField.class);
        if (field == null || DfaUtil.ignoreInitializer(field) || DfaUtil.hasInitializationHacks(field)) {
            return null;
        }
        return DfaUtil.getPossiblyNonInitializedValue(factory, field, method);
    }

    private static boolean containsState(
        Collection<DfaMemoryState> processed,
        DfaInstructionState instructionState
    ) {
        if (processed.contains(instructionState.getMemoryState())) {
            return true;
        }
        for (DfaMemoryState state : processed) {
            if (((DfaMemoryStateImpl)state).isSuperStateOf((DfaMemoryStateImpl)instructionState.getMemoryState())) {
                return true;
            }
        }
        return false;
    }

    private void handleStepOutOfLoop(
        @Nonnull Instruction prevInstruction,
        @Nonnull Instruction nextInstruction,
        @Nonnull int[] loopNumber,
        @Nonnull MultiMap<BranchingInstruction, DfaMemoryState> processedStates,
        @Nonnull MultiMap<BranchingInstruction, DfaMemoryState> incomingStates,
        @Nonnull List<DfaInstructionState> inFlightStates,
        @Nonnull DfaInstructionState[] afterStates,
        @Nonnull StateQueue queue
    ) {
        if (loopNumber[prevInstruction.getIndex()] == 0 || inSameLoop(prevInstruction, nextInstruction, loopNumber)) {
            return;
        }
        // stepped out of loop. destroy all memory states from the loop, we don't need them anymore

        // but do not touch yet states being handled right now
        for (DfaInstructionState state : inFlightStates) {
            Instruction instruction = state.getInstruction();
            if (inSameLoop(prevInstruction, instruction, loopNumber)) {
                return;
            }
        }
        for (DfaInstructionState state : afterStates) {
            Instruction instruction = state.getInstruction();
            if (inSameLoop(prevInstruction, instruction, loopNumber)) {
                return;
            }
        }
        // and still in queue
        if (!queue.processAll(state -> {
            Instruction instruction = state.getInstruction();
            return !inSameLoop(prevInstruction, instruction, loopNumber);
        })) {
            return;
        }

        // now remove obsolete memory states
        final Set<BranchingInstruction> mayRemoveStatesFor = new HashSet<>();
        for (Instruction instruction : myInstructions) {
            if (inSameLoop(prevInstruction, instruction, loopNumber) && instruction instanceof BranchingInstruction branchingInsn) {
                mayRemoveStatesFor.add(branchingInsn);
            }
        }

        for (BranchingInstruction instruction : mayRemoveStatesFor) {
            processedStates.remove(instruction);
            incomingStates.remove(instruction);
        }
    }

    private static boolean inSameLoop(
        @Nonnull Instruction prevInstruction,
        @Nonnull Instruction nextInstruction,
        @Nonnull int[] loopNumber
    ) {
        return loopNumber[nextInstruction.getIndex()] == loopNumber[prevInstruction.getIndex()];
    }

    @Nonnull
    protected DfaInstructionState[] acceptInstruction(@Nonnull InstructionVisitor visitor, @Nonnull DfaInstructionState instructionState) {
        Instruction instruction = instructionState.getInstruction();
        DfaInstructionState[] states = instruction.accept(this, instructionState.getMemoryState(), visitor);

        if (instruction instanceof ClosureInstruction closureInsn) {
            PsiElement closure = closureInsn.getClosureElement();
            if (closure instanceof PsiClass psiClass) {
                registerNestedClosures(instructionState, psiClass);
            }
            else if (closure instanceof PsiLambdaExpression lambda) {
                registerNestedClosures(instructionState, lambda);
            }
        }

        return states;
    }

    private void registerNestedClosures(@Nonnull DfaInstructionState instructionState, @Nonnull PsiClass nestedClass) {
        DfaMemoryState state = instructionState.getMemoryState();
        for (PsiMethod method : nestedClass.getMethods()) {
            PsiCodeBlock body = method.getBody();
            if (body != null && (method.isPhysical() || !nestedClass.isPhysical())) {
                // Skip analysis of non-physical methods of physical class (possibly autogenerated by some plugin like Lombok)
                createClosureState(body, state);
            }
        }
        for (PsiClassInitializer initializer : nestedClass.getInitializers()) {
            createClosureState(initializer.getBody(), state);
        }
        for (PsiField field : nestedClass.getFields()) {
            createClosureState(field, state);
        }
    }

    private void registerNestedClosures(@Nonnull DfaInstructionState instructionState, @Nonnull PsiLambdaExpression expr) {
        DfaMemoryState state = instructionState.getMemoryState();
        PsiElement body = expr.getBody();
        if (body != null) {
            createClosureState(body, state);
        }
    }

    private void createClosureState(PsiElement anchor, DfaMemoryState state) {
        myNestedClosures.putValue(anchor, state.createClosureState());
    }

    @Nonnull
    protected TimeStats createStatistics() {
        return new TimeStats();
    }

    @Nonnull
    protected DfaMemoryState createMemoryState() {
        return new DfaMemoryStateImpl(myValueFactory);
    }

    @Nonnull
    public Instruction[] getInstructions() {
        return myInstructions;
    }

    @Nonnull
    public Instruction getInstruction(int index) {
        return myInstructions[index];
    }

    @RequiredReadAction
    public void forNestedClosures(
        @RequiredReadAction BiConsumer<? super PsiElement, ? super Collection<? extends DfaMemoryState>> consumer
    ) {
        // Copy to avoid concurrent modifications
        MultiMap<PsiElement, DfaMemoryState> closures = new MultiMap<>(myNestedClosures);
        for (PsiElement closure : closures.keySet()) {
            List<DfaVariableValue> unusedVars = StreamEx.of(getFactory().getValues())
                .select(DfaVariableValue.class)
                .filter(var -> var.getQualifier() == null)
                .filter(
                    var -> var.getPsiVariable() instanceof PsiVariable variable
                        && !VariableAccessUtils.variableIsUsed(variable, closure)
                )
                .toList();
            Collection<? extends DfaMemoryState> states = closures.get(closure);
            if (!unusedVars.isEmpty()) {
                List<DfaMemoryStateImpl> stateList = StreamEx.of(states)
                    .peek(state -> unusedVars.forEach(state::flushVariable))
                    .map(state -> (DfaMemoryStateImpl)state).distinct().toList();
                states = StateQueue.mergeGroup(stateList);
            }
            consumer.accept(closure, states);
        }
    }

    protected static class TimeStats {
        private static final long DFA_EXECUTION_TIME_TO_REPORT_NANOS = TimeUnit.SECONDS.toNanos(30);
        @Nullable
        private final ThreadMXBean myMxBean;
        private long myStart;
        private long myMergeStart, myFlowTime, myLVATime, myMergeTime, myProcessTime;

        TimeStats() {
            this(Application.get().isInternal());
        }

        public TimeStats(boolean record) {
            myMxBean = record ? ManagementFactory.getThreadMXBean() : null;
            reset();
        }

        void reset() {
            if (myMxBean == null) {
                myStart = 0;
            }
            else {
                myStart = myMxBean.getCurrentThreadCpuTime();
            }
            myMergeStart = myFlowTime = myLVATime = myMergeTime = myProcessTime = 0;
        }

        void endFlow() {
            if (myMxBean != null) {
                myFlowTime = myMxBean.getCurrentThreadCpuTime() - myStart;
            }
        }

        void endLVA() {
            if (myMxBean != null) {
                myLVATime = myMxBean.getCurrentThreadCpuTime() - myStart - myFlowTime;
            }
        }

        void startMerge() {
            if (myMxBean != null) {
                myMergeStart = System.nanoTime();
            }
        }

        void endMerge() {
            if (myMxBean != null) {
                myMergeTime += System.nanoTime() - myMergeStart;
            }
        }

        void endProcess() {
            if (myMxBean != null) {
                myProcessTime = myMxBean.getCurrentThreadCpuTime() - myStart;
            }
        }

        boolean isTooSlow() {
            return myProcessTime > DFA_EXECUTION_TIME_TO_REPORT_NANOS;
        }

        @Override
        public String toString() {
            double flowTime = myFlowTime / 1e9;
            double lvaTime = myLVATime / 1e9;
            double mergeTime = myMergeTime / 1e9;
            double interpretTime = (myProcessTime - myFlowTime - myLVATime - myMergeTime) / 1e9;
            double totalTime = myProcessTime / 1e9;
            String format =
                "Building ControlFlow: %.2fs\nLiveVariableAnalyzer: %.2fs\nMerging states: %.2fs\nInterpreting: %.2fs\nTotal: %.2fs";
            return String.format(Locale.ENGLISH, format, flowTime, lvaTime, mergeTime, interpretTime, totalTime);
        }
    }
}
