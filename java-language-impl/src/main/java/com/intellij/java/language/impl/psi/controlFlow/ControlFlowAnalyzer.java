// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.controlFlow;

import com.intellij.java.language.JavaPsiBundle;
import com.intellij.java.language.impl.codeInsight.ExceptionUtil;
import com.intellij.java.language.impl.psi.util.JavaPsiRecordUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.progress.ProgressManager;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiErrorElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.Stack;
import consulo.util.collection.primitive.ints.IntList;
import consulo.util.collection.primitive.ints.IntLists;
import consulo.util.lang.Comparing;
import consulo.util.lang.ObjectUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

class ControlFlowAnalyzer extends JavaElementVisitor {
    private static final Logger LOG = Logger.getInstance(ControlFlowAnalyzer.class);

    private final PsiElement myCodeFragment;
    private final ControlFlowPolicy myPolicy;

    private ControlFlowImpl myCurrentFlow;
    private final Stack<PsiParameter> myCatchParameters = new Stack<>();// stack of PsiParameter for 'catch'
    private final Stack<PsiElement> myCatchBlocks = new Stack<>();

    private final Stack<FinallyBlockSubroutine> myFinallyBlocks = new Stack<>();
    private final Stack<PsiElement> myUnhandledExceptionCatchBlocks = new Stack<>();

    // element to jump to from inner (sub)expression in "jump to begin" situation.
    // E.g. we should jump to "then" branch if condition expression evaluated to true inside if statement
    private final StatementStack myStartStatementStack = new StatementStack();
    // element to jump to from inner (sub)expression in "jump to end" situation.
    // E.g. we should jump to "else" branch if condition expression evaluated to false inside if statement
    private final StatementStack myEndStatementStack = new StatementStack();

    private final Stack<BranchingInstruction.Role> myStartJumpRoles = new Stack<>();
    private final Stack<BranchingInstruction.Role> myEndJumpRoles = new Stack<>();

    private final
    @Nonnull
    ControlFlowOptions myOptions;
    private final boolean myAssignmentTargetsAreElements;

    private final Stack<IntList> intArrayPool = new Stack<>();
    // map: PsiElement element -> TIntArrayList instructionOffsetsToPatch with getStartOffset(element)
    private final Map<PsiElement, IntList> offsetsAddElementStart = new HashMap<>();
    // map: PsiElement element -> TIntArrayList instructionOffsetsToPatch with getEndOffset(element)
    private final Map<PsiElement, IntList> offsetsAddElementEnd = new HashMap<>();
    private final ControlFlowFactory myControlFlowFactory;
    private final List<SubRangeInfo> mySubRanges = new ArrayList<>();
    private final PsiConstantEvaluationHelper myConstantEvaluationHelper;
    private final Map<PsiField, PsiParameter> myImplicitCompactConstructorAssignments;

    ControlFlowAnalyzer(
        @Nonnull PsiElement codeFragment,
        @Nonnull ControlFlowPolicy policy,
        @Nonnull ControlFlowOptions options
    ) {
        this(codeFragment, policy, options, false);
    }

    private ControlFlowAnalyzer(
        @Nonnull PsiElement codeFragment,
        @Nonnull ControlFlowPolicy policy,
        @Nonnull ControlFlowOptions options,
        boolean assignmentTargetsAreElements
    ) {
        myCodeFragment = codeFragment;
        myPolicy = policy;
        myOptions = options;
        myAssignmentTargetsAreElements = assignmentTargetsAreElements;
        Project project = codeFragment.getProject();
        myControlFlowFactory = ControlFlowFactory.getInstance(project);
        myConstantEvaluationHelper = JavaPsiFacade.getInstance(project).getConstantEvaluationHelper();
        myImplicitCompactConstructorAssignments = getImplicitCompactConstructorAssignmentsMap();
    }

    private Map<PsiField, PsiParameter> getImplicitCompactConstructorAssignmentsMap() {
        PsiMethod ctor = ObjectUtil.tryCast(myCodeFragment.getParent(), PsiMethod.class);
        if (ctor == null || !JavaPsiRecordUtil.isCompactConstructor(ctor)) {
            return Collections.emptyMap();
        }
        PsiClass containingClass = ctor.getContainingClass();
        if (containingClass == null) {
            return Collections.emptyMap();
        }
        PsiParameter[] parameters = ctor.getParameterList().getParameters();
        PsiRecordComponent[] components = containingClass.getRecordComponents();
        Map<PsiField, PsiParameter> map = new HashMap<>();
        for (int i = 0; i < Math.min(components.length, parameters.length); i++) {
            PsiRecordComponent component = components[i];
            PsiField field = JavaPsiRecordUtil.getFieldForComponent(component);
            PsiParameter parameter = parameters[i];
            map.put(field, parameter);
        }
        return map;
    }

    @Nonnull
    ControlFlow buildControlFlow() throws AnalysisCanceledException {
        // push guard outer statement offsets in case when nested expression is incorrect
        myStartJumpRoles.push(BranchingInstruction.Role.END);
        myEndJumpRoles.push(BranchingInstruction.Role.END);

        myCurrentFlow = new ControlFlowImpl();

        // guard elements
        myStartStatementStack.pushStatement(myCodeFragment, false);
        myEndStatementStack.pushStatement(myCodeFragment, false);

        try {
            myCodeFragment.accept(this);
            return cleanup();
        }
        catch (AnalysisCanceledSoftException e) {
            throw new AnalysisCanceledException(e.getErrorElement());
        }
    }

    private void generateCompactConstructorAssignments() {
        myImplicitCompactConstructorAssignments.values()
            .stream()
            .filter(myPolicy::isParameterAccepted)
            .forEach(this::generateReadInstruction);
    }

    private static class StatementStack {
        private final Stack<PsiElement> myStatements = new Stack<>();
        private final IntList myAtStart = IntLists.newArrayList();

        private void popStatement() {
            myAtStart.removeByIndex(myAtStart.size() - 1);
            myStatements.pop();
        }

        @Nonnull
        private PsiElement peekElement() {
            return myStatements.peek();
        }

        private boolean peekAtStart() {
            return myAtStart.get(myAtStart.size() - 1) == 1;
        }

        private void pushStatement(@Nonnull PsiElement statement, boolean atStart) {
            myStatements.push(statement);
            myAtStart.add(atStart ? 1 : 0);
        }
    }

    @Nonnull
    private IntList getEmptyIntArray() {
        if (intArrayPool.isEmpty()) {
            return IntLists.newArrayList(1);
        }
        IntList list = intArrayPool.pop();
        list.clear();
        return list;
    }

    private void poolIntArray(@Nonnull IntList list) {
        intArrayPool.add(list);
    }

    // patch instruction currently added to control flow so that its jump offset corrected on getStartOffset(element) or getEndOffset(element)
    //  when corresponding element offset become available
    private void addElementOffsetLater(@Nonnull PsiElement element, boolean atStart) {
        Map<PsiElement, IntList> offsetsAddElement = atStart ? offsetsAddElementStart : offsetsAddElementEnd;
        IntList offsets = offsetsAddElement.get(element);
        if (offsets == null) {
            offsets = getEmptyIntArray();
            offsetsAddElement.put(element, offsets);
        }
        int offset = myCurrentFlow.getSize() - 1;
        offsets.add(offset);
        if (myCurrentFlow.getEndOffset(element) != -1) {
            patchInstructionOffsets(element);
        }
    }


    private void patchInstructionOffsets(@Nonnull PsiElement element) {
        patchInstructionOffsets(offsetsAddElementStart.get(element), myCurrentFlow.getStartOffset(element));
        offsetsAddElementStart.put(element, null);
        patchInstructionOffsets(offsetsAddElementEnd.get(element), myCurrentFlow.getEndOffset(element));
        offsetsAddElementEnd.put(element, null);
    }

    private void patchInstructionOffsets(@Nullable IntList offsets, int add) {
        if (offsets == null) {
            return;
        }
        for (int i = 0; i < offsets.size(); i++) {
            int offset = offsets.get(i);
            BranchingInstruction instruction = (BranchingInstruction)myCurrentFlow.getInstructions().get(offset);
            instruction.offset += add;
            LOG.assertTrue(instruction.offset >= 0);
        }
        poolIntArray(offsets);
    }

    private ControlFlow cleanup() {
        // make all non patched goto instructions jump to the end of control flow
        for (IntList offsets : offsetsAddElementStart.values()) {
            patchInstructionOffsets(offsets, myCurrentFlow.getEndOffset(myCodeFragment));
        }
        for (IntList offsets : offsetsAddElementEnd.values()) {
            patchInstructionOffsets(offsets, myCurrentFlow.getEndOffset(myCodeFragment));
        }

        ControlFlow result = myCurrentFlow.immutableCopy();

        // register all sub ranges
        for (SubRangeInfo info : mySubRanges) {
            ProgressManager.checkCanceled();
            myControlFlowFactory.registerSubRange(
                info.myElement,
                new ControlFlowSubRange(result, info.myStart, info.myEnd),
                myOptions,
                myPolicy
            );
        }
        return result;
    }

    @RequiredReadAction
    private void startElement(@Nonnull PsiElement element) {
        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            ProgressManager.checkCanceled();
            if (child instanceof PsiErrorElement errorElem
                && !Comparing.strEqual(errorElem.getErrorDescription(), JavaPsiBundle.message("expected.semicolon"))) {
                // do not perform control flow analysis for incomplete code
                throw new AnalysisCanceledSoftException(element);
            }
        }
        ProgressManager.checkCanceled();
        myCurrentFlow.startElement(element);

        generateUncheckedExceptionJumpsIfNeeded(element, true);
    }

    @RequiredReadAction
    private void generateUncheckedExceptionJumpsIfNeeded(@Nonnull PsiElement element, boolean atStart) {
        if (!myOptions.isExceptionAfterAssignment() && !atStart) {
            if (element instanceof PsiExpression) {
                if (PsiUtil.skipParenthesizedExprUp(element.getParent()) instanceof PsiAssignmentExpression assignment
                    && assignment.getParent() instanceof PsiExpressionStatement) {
                    generateUncheckedExceptionJumps(element, false);
                    return;
                }
            }
            if (element instanceof PsiCodeBlock
                || element instanceof PsiExpressionStatement expressionStmt
                && expressionStmt.getExpression() instanceof PsiAssignmentExpression) {
                return;
            }
        }
        // optimization: reduce number of instructions
        boolean isGeneratingStatement = element instanceof PsiStatement && !(element instanceof PsiSwitchLabelStatement);
        boolean isGeneratingCodeBlock = element instanceof PsiCodeBlock && !(element.getParent() instanceof PsiSwitchStatement);
        if (isGeneratingStatement || isGeneratingCodeBlock) {
            generateUncheckedExceptionJumps(element, atStart);
        }
    }

    @RequiredReadAction
    private void finishElement(@Nonnull PsiElement element) {
        generateUncheckedExceptionJumpsIfNeeded(element, false);

        myCurrentFlow.finishElement(element);
        patchInstructionOffsets(element);
    }

    @RequiredReadAction
    private void generateUncheckedExceptionJumps(@Nonnull PsiElement element, boolean atStart) {
        // optimization: if we just generated all necessary jumps, do not generate it once again
        if (atStart
            && element instanceof PsiStatement
            && element.getParent() instanceof PsiCodeBlock && element.getPrevSibling() != null) {
            return;
        }

        for (int i = myUnhandledExceptionCatchBlocks.size() - 1; i >= 0; i--) {
            ProgressManager.checkCanceled();
            PsiElement block = myUnhandledExceptionCatchBlocks.get(i);
            // cannot jump to outer catch blocks (belonging to outer try stmt) if current try{} has 'finally' block
            if (block == null) {
                if (!myFinallyBlocks.isEmpty()) {
                    break;
                }
                else {
                    continue;
                }
            }
            ConditionalThrowToInstruction throwToInstruction = new ConditionalThrowToInstruction(-1); // -1 for init parameter
            myCurrentFlow.addInstruction(throwToInstruction);
            if (!patchUncheckedThrowInstructionIfInsideFinally(throwToInstruction, element, block)) {
                addElementOffsetLater(block, true);
            }
        }

        // generate a jump to the top 'finally' block
        if (!myFinallyBlocks.isEmpty()) {
            PsiElement finallyBlock = myFinallyBlocks.peek().getElement();
            ConditionalThrowToInstruction throwToInstruction = new ConditionalThrowToInstruction(-2);
            myCurrentFlow.addInstruction(throwToInstruction);
            if (!patchUncheckedThrowInstructionIfInsideFinally(throwToInstruction, element, finallyBlock)) {
                addElementOffsetLater(finallyBlock, true);
            }
        }
    }

    @RequiredReadAction
    private void generateCheckedExceptionJumps(@Nonnull PsiElement element) {
        //generate jumps to all handled exception handlers
        generateExceptionJumps(element, ExceptionUtil.collectUnhandledExceptions(element, element.getParent()));
    }

    private void generateExceptionJumps(@Nonnull PsiElement element, Collection<? extends PsiClassType> unhandledExceptions) {
        for (PsiClassType unhandledException : unhandledExceptions) {
            ProgressManager.checkCanceled();
            generateThrow(unhandledException, element);
        }
    }

    private void generateThrow(@Nonnull PsiClassType unhandledException, @Nonnull PsiElement throwingElement) {
        List<PsiElement> catchBlocks = findThrowToBlocks(unhandledException);
        for (PsiElement block : catchBlocks) {
            ProgressManager.checkCanceled();
            ConditionalThrowToInstruction instruction = new ConditionalThrowToInstruction(0);
            myCurrentFlow.addInstruction(instruction);
            if (!patchCheckedThrowInstructionIfInsideFinally(instruction, throwingElement, block)) {
                if (block == null) {
                    addElementOffsetLater(myCodeFragment, false);
                }
                else {
                    instruction.offset--; // -1 for catch block param init
                    addElementOffsetLater(block, true);
                }
            }
        }
    }

    private final Map<PsiElement, List<PsiElement>> finallyBlockToUnhandledExceptions = new HashMap<>();

    private boolean patchCheckedThrowInstructionIfInsideFinally(
        @Nonnull ConditionalThrowToInstruction instruction,
        @Nonnull PsiElement throwingElement,
        PsiElement elementToJumpTo
    ) {
        PsiElement finallyBlock = findEnclosingFinallyBlockElement(throwingElement, elementToJumpTo);
        if (finallyBlock == null) {
            return false;
        }

        List<PsiElement> unhandledExceptionCatchBlocks =
            finallyBlockToUnhandledExceptions.computeIfAbsent(finallyBlock, k -> new ArrayList<>());
        int index = unhandledExceptionCatchBlocks.indexOf(elementToJumpTo);
        if (index == -1) {
            index = unhandledExceptionCatchBlocks.size();
            unhandledExceptionCatchBlocks.add(elementToJumpTo);
        }
        // first three return instructions are for normal completion, return statement call completion and unchecked exception throwing completion resp.
        instruction.offset = 3 + index;
        addElementOffsetLater(finallyBlock, false);

        return true;
    }

    private boolean patchUncheckedThrowInstructionIfInsideFinally(
        @Nonnull ConditionalThrowToInstruction instruction,
        @Nonnull PsiElement throwingElement,
        @Nonnull PsiElement elementToJumpTo
    ) {
        PsiElement finallyBlock = findEnclosingFinallyBlockElement(throwingElement, elementToJumpTo);
        if (finallyBlock == null) {
            return false;
        }

        // first three return instructions are for normal completion, return statement call completion and unchecked exception throwing completion resp.
        instruction.offset = 2;
        addElementOffsetLater(finallyBlock, false);

        return true;
    }

    @Override
    @RequiredReadAction
    public void visitCodeFragment(@Nonnull JavaCodeFragment codeFragment) {
        startElement(codeFragment);
        int prevOffset = myCurrentFlow.getSize();
        PsiElement[] children = codeFragment.getChildren();
        for (PsiElement child : children) {
            ProgressManager.checkCanceled();
            child.accept(this);
        }

        finishElement(codeFragment);
        registerSubRange(codeFragment, prevOffset);
    }

    private void registerSubRange(@Nonnull PsiElement codeFragment, int startOffset) {
        mySubRanges.add(new SubRangeInfo(codeFragment, startOffset, myCurrentFlow.getSize()));
    }

    @Override
    @RequiredReadAction
    public void visitCodeBlock(@Nonnull PsiCodeBlock block) {
        startElement(block);
        int prevOffset = myCurrentFlow.getSize();
        PsiStatement[] statements = block.getStatements();
        for (PsiStatement statement : statements) {
            ProgressManager.checkCanceled();
            statement.accept(this);
        }

        //each statement should contain at least one instruction in order to getElement(offset) work
        int nextOffset = myCurrentFlow.getSize();
        if (!(block.getParent() instanceof PsiSwitchStatement) && prevOffset == nextOffset) {
            emitEmptyInstruction();
        }
        if (block == myCodeFragment) {
            generateCompactConstructorAssignments();
        }

        finishElement(block);
        if (prevOffset != 0) {
            registerSubRange(block, prevOffset);
        }
    }

    private void emitEmptyInstruction() {
        myCurrentFlow.addInstruction(EmptyInstruction.INSTANCE);
    }

    @Override
    @RequiredReadAction
    public void visitFile(@Nonnull PsiFile file) {
        visitChildren(file);
    }

    @Override
    @RequiredReadAction
    public void visitBlockStatement(@Nonnull PsiBlockStatement statement) {
        startElement(statement);
        PsiCodeBlock codeBlock = statement.getCodeBlock();
        codeBlock.accept(this);
        finishElement(statement);
    }

    @Override
    @RequiredReadAction
    public void visitBreakStatement(@Nonnull PsiBreakStatement statement) {
        generateYieldInstructions(statement, null, statement.findExitedStatement());
    }

    @Override
    @RequiredReadAction
    public void visitYieldStatement(@Nonnull PsiYieldStatement statement) {
        generateYieldInstructions(statement, statement.getExpression(), statement.findEnclosingExpression());
    }

    @RequiredReadAction
    private void generateYieldInstructions(PsiStatement statement, PsiExpression valueExpression, PsiElement exitedStatement) {
        startElement(statement);
        generateExpressionInstructions(valueExpression);

        if (exitedStatement != null) {
            callFinallyBlocksOnExit(exitedStatement);

            Instruction instruction;
            PsiElement finallyBlock = findEnclosingFinallyBlockElement(statement, exitedStatement);
            int finallyStartOffset = finallyBlock == null ? -1 : myCurrentFlow.getStartOffset(finallyBlock);
            if (finallyBlock != null && finallyStartOffset != -1) {
                // go out of finally, use return
                CallInstruction callInstruction = (CallInstruction)myCurrentFlow.getInstructions().get(finallyStartOffset - 2);
                instruction = new ReturnInstruction(0, callInstruction);
            }
            else {
                instruction =
                    new GoToInstruction(0, BranchingInstruction.Role.END, PsiTreeUtil.isAncestor(exitedStatement, myCodeFragment, true));
            }
            myCurrentFlow.addInstruction(instruction);
            // exited statement might be out of control flow analyzed
            addElementOffsetLater(exitedStatement, false);
        }
        finishElement(statement);
    }

    private void callFinallyBlocksOnExit(PsiElement exitedStatement) {
        for (ListIterator<FinallyBlockSubroutine> it = myFinallyBlocks.listIterator(myFinallyBlocks.size()); it.hasPrevious(); ) {
            FinallyBlockSubroutine finallyBlockSubroutine = it.previous();
            PsiElement finallyBlock = finallyBlockSubroutine.getElement();
            PsiElement enclosingTryStatement = finallyBlock.getParent();
            if (enclosingTryStatement == null || !PsiTreeUtil.isAncestor(exitedStatement, enclosingTryStatement, false)) {
                break;
            }
            CallInstruction instruction = new CallInstruction(0, 0);
            finallyBlockSubroutine.addCall(instruction);
            myCurrentFlow.addInstruction(instruction);
            addElementOffsetLater(finallyBlock, true);
        }
    }

    private PsiElement findEnclosingFinallyBlockElement(@Nonnull PsiElement sourceElement, @Nullable PsiElement jumpElement) {
        PsiElement element = sourceElement;
        while (element != null && !(element instanceof PsiFile)) {
            if (element instanceof PsiCodeBlock
                && element.getParent() instanceof PsiTryStatement
                && ((PsiTryStatement)element.getParent()).getFinallyBlock() == element) {
                // element maybe out of scope to be analyzed
                if (myCurrentFlow.getStartOffset(element.getParent()) == -1) {
                    return null;
                }
                if (jumpElement == null || !PsiTreeUtil.isAncestor(element, jumpElement, false)) {
                    return element;
                }
            }
            element = element.getParent();
        }
        return null;
    }

    @Override
    @RequiredReadAction
    public void visitContinueStatement(@Nonnull PsiContinueStatement statement) {
        startElement(statement);
        PsiStatement continuedStatement = statement.findContinuedStatement();
        if (continuedStatement != null) {
            PsiElement body = null;
            if (continuedStatement instanceof PsiLoopStatement) {
                body = ((PsiLoopStatement)continuedStatement).getBody();
            }
            if (body == null) {
                body = myCodeFragment;
            }
            callFinallyBlocksOnExit(continuedStatement);

            Instruction instruction;
            PsiElement finallyBlock = findEnclosingFinallyBlockElement(statement, continuedStatement);
            int finallyStartOffset = finallyBlock == null ? -1 : myCurrentFlow.getStartOffset(finallyBlock);
            if (finallyBlock != null && finallyStartOffset != -1) {
                // go out of finally, use return
                CallInstruction callInstruction = (CallInstruction)myCurrentFlow.getInstructions().get(finallyStartOffset - 2);
                instruction = new ReturnInstruction(0, callInstruction);
            }
            else {
                instruction = new GoToInstruction(0, BranchingInstruction.Role.END, PsiTreeUtil.isAncestor(body, myCodeFragment, true));
            }
            myCurrentFlow.addInstruction(instruction);
            addElementOffsetLater(body, false);
        }
        finishElement(statement);
    }

    @Override
    @RequiredReadAction
    public void visitDeclarationStatement(@Nonnull PsiDeclarationStatement statement) {
        startElement(statement);
        int pc = myCurrentFlow.getSize();
        PsiElement[] elements = statement.getDeclaredElements();
        for (PsiElement element : elements) {
            ProgressManager.checkCanceled();
            if (element instanceof PsiClass) {
                element.accept(this);
            }
            else if (element instanceof PsiVariable) {
                processVariable((PsiVariable)element);
            }
        }
        if (pc == myCurrentFlow.getSize()) {
            // generate at least one instruction for declaration
            emitEmptyInstruction();
        }
        finishElement(statement);
    }

    @RequiredReadAction
    private void processVariable(@Nonnull PsiVariable element) {
        PsiExpression initializer = element.getInitializer();
        generateExpressionInstructions(initializer);

        if (element instanceof PsiLocalVariable && initializer != null ||
            element instanceof PsiField) {
            if (element instanceof PsiLocalVariable && !myPolicy.isLocalVariableAccepted((PsiLocalVariable)element)) {
                return;
            }

            if (myAssignmentTargetsAreElements) {
                startElement(element);
            }

            generateWriteInstruction(element);

            if (myAssignmentTargetsAreElements) {
                finishElement(element);
            }
        }
    }

    @Override
    @RequiredReadAction
    public void visitDoWhileStatement(@Nonnull PsiDoWhileStatement statement) {
        startElement(statement);
        PsiStatement body = statement.getBody();
        myStartStatementStack.pushStatement(body == null ? statement : body, true);
        myEndStatementStack.pushStatement(statement, false);

        if (body != null) {
            body.accept(this);
        }

        PsiExpression condition = statement.getCondition();
        if (condition != null) {
            condition.accept(this);
        }

        int offset = myCurrentFlow.getStartOffset(statement);

        Object loopCondition = myConstantEvaluationHelper.computeConstantExpression(statement.getCondition());
        if (loopCondition instanceof Boolean value) {
            if (value) {
                myCurrentFlow.addInstruction(new GoToInstruction(offset));
            }
            else {
                emitEmptyInstruction();
            }
        }
        else {
            Instruction instruction = new ConditionalGoToInstruction(offset, statement.getCondition());
            myCurrentFlow.addInstruction(instruction);
        }

        myStartStatementStack.popStatement();
        myEndStatementStack.popStatement();
        finishElement(statement);
    }

    @Override
    @RequiredReadAction
    public void visitEmptyStatement(@Nonnull PsiEmptyStatement statement) {
        startElement(statement);
        emitEmptyInstruction();

        finishElement(statement);
    }

    @Override
    @RequiredReadAction
    public void visitExpressionStatement(@Nonnull PsiExpressionStatement statement) {
        startElement(statement);
        PsiExpression expression = statement.getExpression();
        expression.accept(this);

        for (PsiParameter catchParameter : myCatchParameters) {
            ProgressManager.checkCanceled();
            if (myUnhandledExceptionCatchBlocks.contains(((PsiCatchSection)catchParameter.getDeclarationScope()).getCatchBlock())) {
                continue;
            }
            PsiType type = catchParameter.getType();
            List<PsiType> types =
                type instanceof PsiDisjunctionType ? ((PsiDisjunctionType)type).getDisjunctions() : Collections.singletonList(type);
            for (PsiType subType : types) {
                if (subType instanceof PsiClassType) {
                    generateThrow((PsiClassType)subType, statement);
                }
            }
        }
        finishElement(statement);
    }

    @Override
    @RequiredReadAction
    public void visitExpressionListStatement(@Nonnull PsiExpressionListStatement statement) {
        startElement(statement);
        PsiExpression[] expressions = statement.getExpressionList().getExpressions();
        for (PsiExpression expr : expressions) {
            ProgressManager.checkCanceled();
            expr.accept(this);
        }
        finishElement(statement);
    }

    @Override
    @RequiredReadAction
    public void visitField(PsiField field) {
        PsiExpression initializer = field.getInitializer();
        if (initializer != null) {
            startElement(field);
            initializer.accept(this);
            finishElement(field);
        }
    }

    @Override
    @RequiredReadAction
    public void visitForStatement(@Nonnull PsiForStatement statement) {
        startElement(statement);
        PsiStatement body = statement.getBody();
        myStartStatementStack.pushStatement(body == null ? statement : body, false);
        myEndStatementStack.pushStatement(statement, false);

        PsiStatement initialization = statement.getInitialization();
        if (initialization != null) {
            initialization.accept(this);
        }

        PsiExpression condition = statement.getCondition();
        if (condition != null) {
            condition.accept(this);
        }

        Object loopCondition = myConstantEvaluationHelper.computeConstantExpression(condition);
        if (loopCondition instanceof Boolean || condition == null) {
            boolean value = condition == null || (Boolean)loopCondition;
            if (value) {
                emitEmptyInstruction();
            }
            else {
                myCurrentFlow.addInstruction(new GoToInstruction(0));
                addElementOffsetLater(statement, false);
            }
        }
        else {
            Instruction instruction = new ConditionalGoToInstruction(0, statement.getCondition());
            myCurrentFlow.addInstruction(instruction);
            addElementOffsetLater(statement, false);
        }

        if (body != null) {
            body.accept(this);
        }

        PsiStatement update = statement.getUpdate();
        if (update != null) {
            update.accept(this);
        }

        int offset = initialization != null
            ? myCurrentFlow.getEndOffset(initialization)
            : myCurrentFlow.getStartOffset(statement);
        Instruction instruction = new GoToInstruction(offset);
        myCurrentFlow.addInstruction(instruction);

        myStartStatementStack.popStatement();
        myEndStatementStack.popStatement();
        finishElement(statement);
    }

    @Override
    @RequiredReadAction
    public void visitForeachStatement(@Nonnull PsiForeachStatement statement) {
        startElement(statement);
        PsiStatement body = statement.getBody();
        myStartStatementStack.pushStatement(body == null ? statement : body, false);
        myEndStatementStack.pushStatement(statement, false);
        PsiExpression iteratedValue = statement.getIteratedValue();
        if (iteratedValue != null) {
            iteratedValue.accept(this);
        }

        int gotoTarget = myCurrentFlow.getSize();
        Instruction instruction = new ConditionalGoToInstruction(0, statement.getIteratedValue());
        myCurrentFlow.addInstruction(instruction);
        addElementOffsetLater(statement, false);

        PsiParameter iterationParameter = statement.getIterationParameter();
        if (myPolicy.isParameterAccepted(iterationParameter)) {
            generateWriteInstruction(iterationParameter);
        }
        if (body != null) {
            body.accept(this);
        }

        GoToInstruction gotoInstruction = new GoToInstruction(gotoTarget);
        myCurrentFlow.addInstruction(gotoInstruction);
        myStartStatementStack.popStatement();
        myEndStatementStack.popStatement();
        finishElement(statement);
    }

    @Override
    @RequiredReadAction
    public void visitIfStatement(@Nonnull PsiIfStatement statement) {
        startElement(statement);

        PsiStatement elseBranch = statement.getElseBranch();
        PsiStatement thenBranch = statement.getThenBranch();
        PsiExpression conditionExpression = statement.getCondition();

        generateConditionalStatementInstructions(statement, conditionExpression, thenBranch, elseBranch);

        finishElement(statement);
    }

    private void generateConditionalStatementInstructions(
        @Nonnull PsiElement statement,
        @Nullable PsiExpression conditionExpression,
        PsiElement thenBranch,
        PsiElement elseBranch
    ) {
        if (thenBranch == null) {
            myStartStatementStack.pushStatement(statement, false);
        }
        else {
            myStartStatementStack.pushStatement(thenBranch, true);
        }
        if (elseBranch == null) {
            myEndStatementStack.pushStatement(statement, false);
        }
        else {
            myEndStatementStack.pushStatement(elseBranch, true);
        }

        myEndJumpRoles.push(elseBranch == null ? BranchingInstruction.Role.END : BranchingInstruction.Role.ELSE);
        myStartJumpRoles.push(thenBranch == null ? BranchingInstruction.Role.END : BranchingInstruction.Role.THEN);

        if (conditionExpression != null) {
            conditionExpression.accept(this);
        }

        boolean thenReachable = true;
        boolean generateConditionalJump = true;
        /*
         * if() statement generated instructions outline:
         *  'if (C) { A } [ else { B } ]' :
         *     generate (C)
         *     cond_goto else
         *     generate (A)
         *     [ goto end ]
         * :else
         *     [ generate (B) ]
         * :end
         */
        if (myOptions.shouldEvaluateConstantIfCondition()) {
            if (myConstantEvaluationHelper.computeConstantExpression(conditionExpression) instanceof Boolean value) {
                thenReachable = value;
                generateConditionalJump = false;
                myCurrentFlow.setConstantConditionOccurred(true);
            }
        }
        if (generateConditionalJump || !thenReachable) {
            BranchingInstruction.Role role = elseBranch == null ? BranchingInstruction.Role.END : BranchingInstruction.Role.ELSE;
            Instruction instruction = generateConditionalJump
                ? new ConditionalGoToInstruction(0, role, conditionExpression)
                : new GoToInstruction(0, role);
            myCurrentFlow.addInstruction(instruction);
            if (elseBranch == null) {
                addElementOffsetLater(statement, false);
            }
            else {
                addElementOffsetLater(elseBranch, true);
            }
        }
        if (thenBranch != null) {
            thenBranch.accept(this);
        }
        if (elseBranch != null) {
            Instruction instruction = new GoToInstruction(0);
            myCurrentFlow.addInstruction(instruction);
            addElementOffsetLater(statement, false);
            elseBranch.accept(this);
        }

        myStartJumpRoles.pop();
        myEndJumpRoles.pop();

        myStartStatementStack.popStatement();
        myEndStatementStack.popStatement();
    }

    @Override
    @RequiredReadAction
    public void visitLabeledStatement(@Nonnull PsiLabeledStatement statement) {
        startElement(statement);
        PsiStatement innerStatement = statement.getStatement();
        if (innerStatement != null) {
            innerStatement.accept(this);
        }
        finishElement(statement);
    }

    @Override
    @RequiredReadAction
    public void visitReturnStatement(@Nonnull PsiReturnStatement statement) {
        startElement(statement);
        PsiExpression returnValue = statement.getReturnValue();

        if (returnValue != null) {
            myStartStatementStack.pushStatement(returnValue, false);
            myEndStatementStack.pushStatement(returnValue, false);
            returnValue.accept(this);
        }
        addReturnInstruction(statement);
        if (returnValue != null) {
            myStartStatementStack.popStatement();
            myEndStatementStack.popStatement();
        }

        finishElement(statement);
    }

    private void addReturnInstruction(@Nonnull PsiElement statement) {
        BranchingInstruction instruction;
        PsiElement finallyBlock = findEnclosingFinallyBlockElement(statement, null);
        int finallyStartOffset = finallyBlock == null ? -1 : myCurrentFlow.getStartOffset(finallyBlock);
        if (finallyBlock != null && finallyStartOffset != -1) {
            // go out of finally, go to 2nd return after finally block
            // second return is for return statement called completion
            instruction = new GoToInstruction(1, BranchingInstruction.Role.END, true);
            myCurrentFlow.addInstruction(instruction);
            addElementOffsetLater(finallyBlock, false);
        }
        else {
            instruction = new GoToInstruction(0, BranchingInstruction.Role.END, true);
            myCurrentFlow.addInstruction(instruction);
            if (myFinallyBlocks.isEmpty()) {
                addElementOffsetLater(myCodeFragment, false);
            }
            else {
                instruction.offset = -4; // -4 for return
                addElementOffsetLater(myFinallyBlocks.peek().getElement(), true);
            }
        }
    }

    @Override
    @RequiredReadAction
    public void visitSwitchLabelStatement(@Nonnull PsiSwitchLabelStatement statement) {
        startElement(statement);
        generateCaseValueInstructions(statement.getCaseValues());
        finishElement(statement);
    }

    @Override
    @RequiredReadAction
    public void visitSwitchLabeledRuleStatement(@Nonnull PsiSwitchLabeledRuleStatement statement) {
        startElement(statement);

        generateCaseValueInstructions(statement.getCaseValues());

        PsiStatement body = statement.getBody();
        if (body != null) {
            body.accept(this);
        }

        PsiSwitchBlock switchBlock = statement.getEnclosingSwitchBlock();
        if (switchBlock != null) {
            Instruction instruction =
                new GoToInstruction(0, BranchingInstruction.Role.END, PsiTreeUtil.isAncestor(switchBlock, myCodeFragment, true));
            myCurrentFlow.addInstruction(instruction);
            addElementOffsetLater(switchBlock, false);
        }

        finishElement(statement);
    }

    private void generateCaseValueInstructions(@Nullable PsiExpressionList values) {
        if (values != null) {
            for (PsiExpression caseValue : values.getExpressions()) {
                ProgressManager.checkCanceled();
                generateExpressionInstructions(caseValue);
            }
        }
    }

    @Override
    @RequiredReadAction
    public void visitSwitchStatement(@Nonnull PsiSwitchStatement statement) {
        generateSwitchBlockInstructions(statement);
    }

    @Override
    @RequiredReadAction
    public void visitSwitchExpression(@Nonnull PsiSwitchExpression expression) {
        generateSwitchBlockInstructions(expression);
    }

    @RequiredReadAction
    public void generateSwitchBlockInstructions(PsiSwitchBlock statement) {
        startElement(statement);

        PsiExpression expr = statement.getExpression();
        if (expr != null) {
            expr.accept(this);
        }

        PsiCodeBlock body = statement.getBody();
        if (body != null) {
            PsiStatement[] statements = body.getStatements();
            PsiSwitchLabelStatementBase defaultLabel = null;
            for (PsiStatement aStatement : statements) {
                ProgressManager.checkCanceled();
                if (aStatement instanceof PsiSwitchLabelStatementBase) {
                    if (((PsiSwitchLabelStatementBase)aStatement).isDefaultCase()) {
                        defaultLabel = (PsiSwitchLabelStatementBase)aStatement;
                    }
                    Instruction instruction = new ConditionalGoToInstruction(0, expr);
                    myCurrentFlow.addInstruction(instruction);
                    addElementOffsetLater(aStatement, true);
                }
            }
            if (defaultLabel == null) {
                Instruction instruction = new GoToInstruction(0);
                myCurrentFlow.addInstruction(instruction);
                addElementOffsetLater(body, false);
            }

            body.accept(this);
        }

        finishElement(statement);
    }

    @Override
    @RequiredReadAction
    public void visitSynchronizedStatement(@Nonnull PsiSynchronizedStatement statement) {
        startElement(statement);

        PsiExpression lock = statement.getLockExpression();
        if (lock != null) {
            lock.accept(this);
        }

        PsiCodeBlock body = statement.getBody();
        if (body != null) {
            body.accept(this);
        }

        finishElement(statement);
    }

    @Override
    @RequiredReadAction
    public void visitThrowStatement(@Nonnull PsiThrowStatement statement) {
        startElement(statement);

        PsiExpression exception = statement.getException();
        if (exception != null) {
            exception.accept(this);
        }
        List<PsiElement> blocks = findThrowToBlocks(statement);
        addThrowInstructions(blocks);

        finishElement(statement);
    }

    private void addThrowInstructions(@Nonnull List<? extends PsiElement> blocks) {
        PsiElement element;
        if (blocks.isEmpty() || blocks.get(0) == null) {
            ThrowToInstruction instruction = new ThrowToInstruction(0);
            myCurrentFlow.addInstruction(instruction);
            if (myFinallyBlocks.isEmpty()) {
                element = myCodeFragment;
                addElementOffsetLater(element, false);
            }
            else {
                instruction.offset = -2; // -2 to rethrow exception
                element = myFinallyBlocks.peek().getElement();
                addElementOffsetLater(element, true);
            }
        }
        else {
            for (int i = 0; i < blocks.size(); i++) {
                ProgressManager.checkCanceled();
                element = blocks.get(i);
                BranchingInstruction instruction = i == blocks.size() - 1
                    ? new ThrowToInstruction(0)
                    : new ConditionalThrowToInstruction(0);
                myCurrentFlow.addInstruction(instruction);
                instruction.offset = -1; // -1 to init catch param
                addElementOffsetLater(element, true);
            }
        }
    }

    /**
     * Find offsets of catch(es) corresponding to this throw statement
     * myCatchParameters and myCatchBlocks arrays should be sorted in ascending scope order (from outermost to innermost)
     *
     * @return list of targets or list of single null element if no appropriate targets found
     */
    @Nonnull
    private List<PsiElement> findThrowToBlocks(@Nonnull PsiThrowStatement statement) {
        PsiExpression exceptionExpr = statement.getException();
        if (exceptionExpr == null) {
            return Collections.emptyList();
        }
        if (!(exceptionExpr.getType() instanceof PsiClassType throwClassType)) {
            return Collections.emptyList();
        }
        return findThrowToBlocks(throwClassType);
    }

    @Nonnull
    private List<PsiElement> findThrowToBlocks(@Nonnull PsiClassType throwType) {
        List<PsiElement> blocks = new ArrayList<>();
        for (int i = myCatchParameters.size() - 1; i >= 0; i--) {
            ProgressManager.checkCanceled();
            PsiParameter parameter = myCatchParameters.get(i);
            PsiType catchType = parameter.getType();
            if (ControlFlowUtil.isCaughtExceptionType(throwType, catchType)) {
                blocks.add(myCatchBlocks.get(i));
            }
        }
        if (blocks.isEmpty()) {
            // consider it as throw at the end of the control flow
            blocks.add(null);
        }
        return blocks;
    }

    @Override
    @RequiredReadAction
    public void visitAssertStatement(@Nonnull PsiAssertStatement statement) {
        startElement(statement);

        myStartStatementStack.pushStatement(statement, false);
        myEndStatementStack.pushStatement(statement, false);
        Instruction passByWhenAssertionsDisabled = new ConditionalGoToInstruction(0, BranchingInstruction.Role.END, null);
        myCurrentFlow.addInstruction(passByWhenAssertionsDisabled);
        addElementOffsetLater(statement, false);

        PsiExpression condition = statement.getAssertCondition();
        boolean generateCondition = true;
        boolean throwReachable = true;
        if (myOptions.shouldEvaluateConstantIfCondition()) {
            if (myConstantEvaluationHelper.computeConstantExpression(condition) instanceof Boolean conditionValue) {
                throwReachable = !conditionValue;
                generateCondition = false;
                emitEmptyInstruction();
            }
        }

        if (generateCondition) {
            if (condition != null) {
                myStartStatementStack.pushStatement(statement, false);
                myEndStatementStack.pushStatement(statement, false);

                myEndJumpRoles.push(BranchingInstruction.Role.END);
                myStartJumpRoles.push(BranchingInstruction.Role.END);

                condition.accept(this);

                myStartJumpRoles.pop();
                myEndJumpRoles.pop();

                myStartStatementStack.popStatement();
                myEndStatementStack.popStatement();
            }
            Instruction ifTrue = new ConditionalGoToInstruction(0, BranchingInstruction.Role.END, statement.getAssertCondition());
            myCurrentFlow.addInstruction(ifTrue);
            addElementOffsetLater(statement, false);
        }
        else if (!throwReachable) {
            myCurrentFlow.addInstruction(new GoToInstruction(0, BranchingInstruction.Role.END));
            addElementOffsetLater(statement, false);
        }
        PsiExpression description = statement.getAssertDescription();
        if (description != null) {
            description.accept(this);
        }
        // if description is evaluated, the 'assert' statement cannot complete normally
        // though non-necessarily AssertionError will be thrown (description may throw something, or AssertionError ctor, etc.)
        PsiClassType exceptionClass = JavaPsiFacade.getElementFactory(statement.getProject())
            .createTypeByFQClassName(CommonClassNames.JAVA_LANG_THROWABLE, statement.getResolveScope());
        addThrowInstructions(findThrowToBlocks(exceptionClass));

        myStartStatementStack.popStatement();
        myEndStatementStack.popStatement();

        finishElement(statement);
    }

    @Override
    @RequiredReadAction
    public void visitTryStatement(@Nonnull PsiTryStatement statement) {
        startElement(statement);

        PsiCodeBlock[] catchBlocks = statement.getCatchBlocks();
        PsiParameter[] catchBlockParameters = statement.getCatchBlockParameters();
        int catchNum = Math.min(catchBlocks.length, catchBlockParameters.length);
        myUnhandledExceptionCatchBlocks.push(null);
        for (int i = catchNum - 1; i >= 0; i--) {
            ProgressManager.checkCanceled();
            myCatchParameters.push(catchBlockParameters[i]);
            myCatchBlocks.push(catchBlocks[i]);

            PsiType type = catchBlockParameters[i].getType();
            // todo cast param
            if (type instanceof PsiClassType classType && ExceptionUtil.isUncheckedExceptionOrSuperclass(classType)) {
                myUnhandledExceptionCatchBlocks.push(catchBlocks[i]);
            }
            else if (type instanceof PsiDisjunctionType disjunctionType) {
                PsiType lub = disjunctionType.getLeastUpperBound();
                if (lub instanceof PsiClassType lubClassType && ExceptionUtil.isUncheckedExceptionOrSuperclass(lubClassType)) {
                    myUnhandledExceptionCatchBlocks.push(catchBlocks[i]);
                }
                else if (lub instanceof PsiIntersectionType intersectionType) {
                    for (PsiType conjunct : intersectionType.getConjuncts()) {
                        if (conjunct instanceof PsiClassType conjunctClassType
                            && ExceptionUtil.isUncheckedExceptionOrSuperclass(conjunctClassType)) {
                            myUnhandledExceptionCatchBlocks.push(catchBlocks[i]);
                            break;
                        }
                    }
                }
            }
        }

        PsiCodeBlock finallyBlock = statement.getFinallyBlock();

        FinallyBlockSubroutine finallyBlockSubroutine = null;
        if (finallyBlock != null) {
            finallyBlockSubroutine = new FinallyBlockSubroutine(finallyBlock);
            myFinallyBlocks.push(finallyBlockSubroutine);
        }

        PsiResourceList resourceList = statement.getResourceList();
        if (resourceList != null) {
            generateCheckedExceptionJumps(resourceList);
            resourceList.accept(this);
        }
        PsiCodeBlock tryBlock = statement.getTryBlock();
        if (tryBlock != null) {
            // javac works as if all checked exceptions can occur at the top of the block
            generateCheckedExceptionJumps(tryBlock);
            tryBlock.accept(this);
        }

        //noinspection StatementWithEmptyBody
        while (myUnhandledExceptionCatchBlocks.pop() != null) {
        }

        myCurrentFlow.addInstruction(new GoToInstruction(finallyBlock == null ? 0 : -6));
        if (finallyBlock == null) {
            addElementOffsetLater(statement, false);
        }
        else {
            addElementOffsetLater(finallyBlock, true);
        }

        for (int i = 0; i < catchNum; i++) {
            myCatchParameters.pop();
            myCatchBlocks.pop();
        }

        for (int i = catchNum - 1; i >= 0; i--) {
            ProgressManager.checkCanceled();
            if (myPolicy.isParameterAccepted(catchBlockParameters[i])) {
                generateWriteInstruction(catchBlockParameters[i]);
            }
            PsiCodeBlock catchBlock = catchBlocks[i];
            if (catchBlock != null) {
                catchBlock.accept(this);
            }
            else {
                LOG.error("Catch body is null (" + i + ") " + statement.getText());
            }

            myCurrentFlow.addInstruction(new GoToInstruction(finallyBlock == null ? 0 : -6));
            if (finallyBlock == null) {
                addElementOffsetLater(statement, false);
            }
            else {
                addElementOffsetLater(finallyBlock, true);
            }
        }

        if (finallyBlock != null) {
            myFinallyBlocks.pop();
        }

        if (finallyBlock != null) {
            // normal completion, call finally block and proceed
            CallInstruction normalCompletion = new CallInstruction(0, 0);
            finallyBlockSubroutine.addCall(normalCompletion);
            myCurrentFlow.addInstruction(normalCompletion);
            addElementOffsetLater(finallyBlock, true);
            myCurrentFlow.addInstruction(new GoToInstruction(0));
            addElementOffsetLater(statement, false);
            // return completion, call finally block and return
            CallInstruction returnCompletion = new CallInstruction(0, 0);
            finallyBlockSubroutine.addCall(returnCompletion);
            myCurrentFlow.addInstruction(returnCompletion);
            addElementOffsetLater(finallyBlock, true);
            addReturnInstruction(statement);
            // throw exception completion, call finally block and rethrow
            CallInstruction throwExceptionCompletion = new CallInstruction(0, 0);
            finallyBlockSubroutine.addCall(throwExceptionCompletion);
            myCurrentFlow.addInstruction(throwExceptionCompletion);
            addElementOffsetLater(finallyBlock, true);
            GoToInstruction gotoUncheckedRethrow = new GoToInstruction(0);
            myCurrentFlow.addInstruction(gotoUncheckedRethrow);
            addElementOffsetLater(finallyBlock, false);

            finallyBlock.accept(this);
            int procStart = myCurrentFlow.getStartOffset(finallyBlock);
            int procEnd = myCurrentFlow.getEndOffset(finallyBlock);
            for (CallInstruction callInstruction : finallyBlockSubroutine.getCalls()) {
                callInstruction.procBegin = procStart;
                callInstruction.procEnd = procEnd;
            }

            // generate return instructions
            // first three return instructions are for normal completion, return statement call completion and unchecked exception throwing completion resp.

            // normal completion
            myCurrentFlow.addInstruction(new ReturnInstruction(0, normalCompletion));

            // return statement call completion
            myCurrentFlow.addInstruction(new ReturnInstruction(procStart - 3, returnCompletion));

            // unchecked exception throwing completion
            myCurrentFlow.addInstruction(new ReturnInstruction(procStart - 1, throwExceptionCompletion));

            // checked exception throwing completion; need to dispatch to the correct catch clause
            List<PsiElement> unhandledExceptionCatchBlocks = finallyBlockToUnhandledExceptions.remove(finallyBlock);
            for (int i = 0; unhandledExceptionCatchBlocks != null && i < unhandledExceptionCatchBlocks.size(); i++) {
                ProgressManager.checkCanceled();
                PsiElement catchBlock = unhandledExceptionCatchBlocks.get(i);

                ReturnInstruction returnInstruction = new ReturnInstruction(0, throwExceptionCompletion);
                returnInstruction.setRethrowFromFinally();
                myCurrentFlow.addInstruction(returnInstruction);
                if (catchBlock == null) {
                    // dispatch to rethrowing exception code
                    returnInstruction.offset = procStart - 1;
                }
                else {
                    // dispatch to catch clause
                    returnInstruction.offset--; // -1 for catch block init parameter instruction
                    addElementOffsetLater(catchBlock, true);
                }
            }

            // here generated rethrowing code for unchecked exceptions
            gotoUncheckedRethrow.offset = myCurrentFlow.getSize();
            generateUncheckedExceptionJumps(statement, false);
            // just in case
            myCurrentFlow.addInstruction(new ThrowToInstruction(0));
            addElementOffsetLater(myCodeFragment, false);
        }

        finishElement(statement);
    }

    @Override
    @RequiredReadAction
    public void visitResourceList(@Nonnull PsiResourceList resourceList) {
        startElement(resourceList);

        for (PsiResourceListElement resource : resourceList) {
            ProgressManager.checkCanceled();
            if (resource instanceof PsiResourceVariable resourceVar) {
                processVariable(resourceVar);
            }
            else if (resource instanceof PsiResourceExpression psiResourceExpr) {
                psiResourceExpr.getExpression().accept(this);
            }
        }

        finishElement(resourceList);
    }

    @Override
    @RequiredReadAction
    public void visitWhileStatement(@Nonnull PsiWhileStatement statement) {
        startElement(statement);
        PsiStatement body = statement.getBody();
        if (body == null) {
            myStartStatementStack.pushStatement(statement, false);
        }
        else {
            myStartStatementStack.pushStatement(body, true);
        }
        myEndStatementStack.pushStatement(statement, false);

        PsiExpression condition = statement.getCondition();
        if (condition != null) {
            condition.accept(this);
        }


        Object loopCondition = myConstantEvaluationHelper.computeConstantExpression(statement.getCondition());
        if (loopCondition instanceof Boolean value) {
            if (value) {
                emitEmptyInstruction();
            }
            else {
                myCurrentFlow.addInstruction(new GoToInstruction(0));
                addElementOffsetLater(statement, false);
            }
        }
        else {
            Instruction instruction = new ConditionalGoToInstruction(0, statement.getCondition());
            myCurrentFlow.addInstruction(instruction);
            addElementOffsetLater(statement, false);
        }

        if (body != null) {
            body.accept(this);
        }
        int offset = myCurrentFlow.getStartOffset(statement);
        Instruction instruction = new GoToInstruction(offset);
        myCurrentFlow.addInstruction(instruction);

        myStartStatementStack.popStatement();
        myEndStatementStack.popStatement();
        finishElement(statement);
    }

    @Override
    public void visitExpressionList(PsiExpressionList list) {
        PsiExpression[] expressions = list.getExpressions();
        for (PsiExpression expression : expressions) {
            ProgressManager.checkCanceled();
            generateExpressionInstructions(expression);
        }
    }

    private void generateExpressionInstructions(@Nullable PsiExpression expression) {
        if (expression != null) {
            // handle short circuit
            myStartStatementStack.pushStatement(expression, false);
            myEndStatementStack.pushStatement(expression, false);

            expression.accept(this);
            myStartStatementStack.popStatement();
            myEndStatementStack.popStatement();
        }
    }

    @Override
    @RequiredReadAction
    public void visitArrayAccessExpression(@Nonnull PsiArrayAccessExpression expression) {
        startElement(expression);

        expression.getArrayExpression().accept(this);
        PsiExpression indexExpression = expression.getIndexExpression();
        if (indexExpression != null) {
            indexExpression.accept(this);
        }

        finishElement(expression);
    }

    @Override
    @RequiredReadAction
    public void visitArrayInitializerExpression(@Nonnull PsiArrayInitializerExpression expression) {
        startElement(expression);

        PsiExpression[] initializers = expression.getInitializers();
        for (PsiExpression initializer : initializers) {
            ProgressManager.checkCanceled();
            initializer.accept(this);
        }

        finishElement(expression);
    }

    @Override
    @RequiredReadAction
    public void visitAssignmentExpression(@Nonnull PsiAssignmentExpression expression) {
        startElement(expression);

        PsiExpression rExpr = expression.getRExpression();
        myStartStatementStack.pushStatement(rExpr == null ? expression : rExpr, false);
        myEndStatementStack.pushStatement(rExpr == null ? expression : rExpr, false);

        boolean generatedWriteInstruction = false;
        PsiExpression lExpr = PsiUtil.skipParenthesizedExprDown(expression.getLExpression());
        if (lExpr instanceof PsiReferenceExpression lRefExpr) {
            if (!myImplicitCompactConstructorAssignments.isEmpty() && lRefExpr.resolve() instanceof PsiField field) {
                myImplicitCompactConstructorAssignments.remove(field);
            }
            PsiVariable variable = getUsedVariable((PsiReferenceExpression)lExpr);
            if (variable != null) {
                if (myAssignmentTargetsAreElements) {
                    startElement(lExpr);
                }

                PsiExpression qualifier = ((PsiReferenceExpression)lExpr).getQualifierExpression();
                if (qualifier != null) {
                    qualifier.accept(this);
                }

                if (expression.getOperationTokenType() != JavaTokenType.EQ) {
                    generateReadInstruction(variable);
                }
                if (rExpr != null) {
                    rExpr.accept(this);
                }
                generateWriteInstruction(variable);
                generatedWriteInstruction = true;

                if (myAssignmentTargetsAreElements) {
                    finishElement(lExpr);
                }
            }
            else {
                if (rExpr != null) {
                    rExpr.accept(this);
                }
                lExpr.accept(this); //?
            }
        }
        else if (lExpr instanceof PsiArrayAccessExpression lArrayAccessExpr &&
            lArrayAccessExpr.getArrayExpression() instanceof PsiReferenceExpression lRefExpr) {
            PsiVariable variable = getUsedVariable(lRefExpr);
            if (variable != null) {
                generateReadInstruction(variable);
                PsiExpression indexExpression = lArrayAccessExpr.getIndexExpression();
                if (indexExpression != null) {
                    indexExpression.accept(this);
                }
            }
            else {
                lExpr.accept(this);
            }
            if (rExpr != null) {
                rExpr.accept(this);
            }
        }
        else if (lExpr != null) {
            lExpr.accept(this);
            if (rExpr != null) {
                rExpr.accept(this);
            }
        }
        //each statement should contain at least one instruction in order to getElement(offset) work
        if (!generatedWriteInstruction) {
            emitEmptyInstruction();
        }

        myStartStatementStack.popStatement();
        myEndStatementStack.popStatement();

        finishElement(expression);
    }

    private enum Shortcut {
        NO_SHORTCUT, // a || b
        SKIP_CURRENT_OPERAND, // false || a
        STOP_EXPRESSION // true || a
    }

    @Override
    @RequiredReadAction
    public void visitPolyadicExpression(@Nonnull PsiPolyadicExpression expression) {
        startElement(expression);
        IElementType signTokenType = expression.getOperationTokenType();

        boolean isAndAnd = signTokenType == JavaTokenType.ANDAND;
        boolean isOrOr = signTokenType == JavaTokenType.OROR;

        PsiExpression[] operands = expression.getOperands();
        Boolean lValue = isAndAnd;
        PsiExpression lOperand = null;
        Boolean rValue = null;
        for (int i = 0; i < operands.length; i++) {
            PsiExpression rOperand = operands[i];
            if ((isAndAnd || isOrOr) && myOptions.enableShortCircuit()) {
                if (myConstantEvaluationHelper.computeConstantExpression(rOperand) instanceof Boolean exprValue) {
                    myCurrentFlow.setConstantConditionOccurred(true);
                    rValue = shouldCalculateConstantExpression(expression) ? exprValue : null;
                }
                else {
                    rValue = null;
                }

                BranchingInstruction.Role role = isAndAnd ? myEndJumpRoles.peek() : myStartJumpRoles.peek();
                PsiElement gotoElement = isAndAnd ? myEndStatementStack.peekElement() : myStartStatementStack.peekElement();
                boolean gotoIsAtStart = isAndAnd ? myEndStatementStack.peekAtStart() : myStartStatementStack.peekAtStart();

                Shortcut shortcut;
                if (lValue != null) {
                    shortcut = lValue == isOrOr ? Shortcut.STOP_EXPRESSION : Shortcut.SKIP_CURRENT_OPERAND;
                }
                else if (rValue != null && rValue == isOrOr) {
                    shortcut = Shortcut.STOP_EXPRESSION;
                }
                else {
                    shortcut = Shortcut.NO_SHORTCUT;
                }

                switch (shortcut) {
                    case NO_SHORTCUT:
                        myCurrentFlow.addInstruction(new ConditionalGoToInstruction(0, role, lOperand));
                        addElementOffsetLater(gotoElement, gotoIsAtStart);
                        break;

                    case STOP_EXPRESSION:
                        myCurrentFlow.addInstruction(new GoToInstruction(0, role));
                        addElementOffsetLater(gotoElement, gotoIsAtStart);
                        rValue = null;
                        break;

                    case SKIP_CURRENT_OPERAND:
                        break;
                }
            }
            generateLOperand(rOperand, i == operands.length - 1 ? null : operands[i + 1], signTokenType);

            lOperand = rOperand;
            lValue = rValue;
        }

        finishElement(expression);
    }

    private void generateLOperand(@Nonnull PsiExpression lOperand, @Nullable PsiExpression rOperand, @Nonnull IElementType signTokenType) {
        if (rOperand != null) {
            myStartJumpRoles.push(BranchingInstruction.Role.END);
            myEndJumpRoles.push(BranchingInstruction.Role.END);
            PsiElement then = signTokenType == JavaTokenType.OROR ? myStartStatementStack.peekElement() : rOperand;
            boolean thenAtStart = signTokenType != JavaTokenType.OROR || myStartStatementStack.peekAtStart();
            myStartStatementStack.pushStatement(then, thenAtStart);
            PsiElement elseS = signTokenType == JavaTokenType.ANDAND ? myEndStatementStack.peekElement() : rOperand;
            boolean elseAtStart = signTokenType != JavaTokenType.ANDAND || myEndStatementStack.peekAtStart();
            myEndStatementStack.pushStatement(elseS, elseAtStart);
        }
        lOperand.accept(this);
        if (rOperand != null) {
            myStartStatementStack.popStatement();
            myEndStatementStack.popStatement();
            myStartJumpRoles.pop();
            myEndJumpRoles.pop();
        }
    }

    private static boolean isInsideIfCondition(@Nonnull PsiExpression expression) {
        PsiElement element = expression;
        while (element instanceof PsiExpression) {
            if (element.getParent() instanceof PsiIfStatement ifStmt && element == ifStmt.getCondition()) {
                return true;
            }
            element = element.getParent();
        }
        return false;
    }

    private boolean shouldCalculateConstantExpression(@Nonnull PsiExpression expression) {
        return myOptions.shouldEvaluateConstantIfCondition() || !isInsideIfCondition(expression);
    }

    @Override
    @RequiredReadAction
    public void visitClassObjectAccessExpression(@Nonnull PsiClassObjectAccessExpression expression) {
        visitChildren(expression);
    }

    @RequiredReadAction
    private void visitChildren(@Nonnull PsiElement element) {
        startElement(element);

        PsiElement[] children = element.getChildren();
        for (PsiElement child : children) {
            ProgressManager.checkCanceled();
            child.accept(this);
        }

        finishElement(element);
    }

    @Override
    @RequiredReadAction
    public void visitConditionalExpression(@Nonnull PsiConditionalExpression expression) {
        startElement(expression);

        PsiExpression condition = expression.getCondition();
        PsiExpression thenExpression = expression.getThenExpression();
        PsiExpression elseExpression = expression.getElseExpression();
        generateConditionalStatementInstructions(expression, condition, thenExpression, elseExpression);

        finishElement(expression);
    }

    @Override
    @RequiredReadAction
    public void visitInstanceOfExpression(@Nonnull PsiInstanceOfExpression expression) {
        startElement(expression);

        PsiExpression operand = expression.getOperand();
        operand.accept(this);

        PsiPattern pattern = expression.getPattern();
        if (pattern instanceof PsiTypeTestPattern typeTestPattern) {
            PsiPatternVariable variable = typeTestPattern.getPatternVariable();

            if (variable != null) {
                myCurrentFlow.addInstruction(new WriteVariableInstruction(variable));
            }
        }

        finishElement(expression);
    }

    @Override
    @RequiredReadAction
    public void visitLiteralExpression(@Nonnull PsiLiteralExpression expression) {
        startElement(expression);
        finishElement(expression);
    }

    @Override
    @RequiredReadAction
    public void visitLambdaExpression(@Nonnull PsiLambdaExpression expression) {
        startElement(expression);
        PsiElement body = expression.getBody();
        if (body != null) {
            List<PsiVariable> array = new ArrayList<>();
            addUsedVariables(array, body);
            for (PsiVariable var : array) {
                ProgressManager.checkCanceled();
                generateReadInstruction(var);
            }
        }
        finishElement(expression);
    }

    @Override
    @RequiredReadAction
    public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
        ArrayDeque<PsiMethodCallExpression> calls = new ArrayDeque<>();
        while (true) {
            calls.addFirst(expression);
            startElement(expression);

            PsiExpression qualifierExpression = expression.getMethodExpression().getQualifierExpression();
            expression = ObjectUtil.tryCast(PsiUtil.skipParenthesizedExprDown(qualifierExpression), PsiMethodCallExpression.class);
            if (expression == null) {
                if (qualifierExpression != null) {
                    qualifierExpression.accept(this);
                }
                break;
            }
        }

        for (PsiMethodCallExpression call : calls) {
            PsiExpressionList argumentList = call.getArgumentList();
            argumentList.accept(this);
            // just to increase counter - there is some executable code here
            emitEmptyInstruction();

            //generate jumps to all handled exception handlers
            generateExceptionJumps(call, ExceptionUtil.getUnhandledExceptions(call, call.getParent()));

            finishElement(call);
        }
    }

    @Override
    @RequiredReadAction
    public void visitNewExpression(@Nonnull PsiNewExpression expression) {
        startElement(expression);

        int pc = myCurrentFlow.getSize();
        PsiElement[] children = expression.getChildren();
        for (PsiElement child : children) {
            ProgressManager.checkCanceled();
            child.accept(this);
        }
        //generate jumps to all handled exception handlers
        generateExceptionJumps(expression, ExceptionUtil.getUnhandledExceptions(expression, expression.getParent()));

        if (pc == myCurrentFlow.getSize()) {
            // generate at least one instruction for constructor call
            emitEmptyInstruction();
        }

        finishElement(expression);
    }

    @Override
    @RequiredReadAction
    public void visitParenthesizedExpression(@Nonnull PsiParenthesizedExpression expression) {
        visitChildren(expression);
    }

    @Override
    @RequiredReadAction
    public void visitPostfixExpression(@Nonnull PsiPostfixExpression expression) {
        startElement(expression);

        IElementType op = expression.getOperationTokenType();
        PsiExpression operand = PsiUtil.skipParenthesizedExprDown(expression.getOperand());
        if (operand != null) {
            operand.accept(this);
            if (op == JavaTokenType.PLUSPLUS || op == JavaTokenType.MINUSMINUS) {
                if (operand instanceof PsiReferenceExpression refExpr) {
                    PsiVariable variable = getUsedVariable(refExpr);
                    if (variable != null) {
                        generateWriteInstruction(variable);
                    }
                }
            }
        }

        finishElement(expression);
    }

    @Override
    @RequiredReadAction
    public void visitPrefixExpression(@Nonnull PsiPrefixExpression expression) {
        startElement(expression);

        PsiExpression operand = PsiUtil.skipParenthesizedExprDown(expression.getOperand());
        if (operand != null) {
            IElementType operationSign = expression.getOperationTokenType();
            if (operationSign == JavaTokenType.EXCL) {
                // negation inverts jump targets
                PsiElement topStartStatement = myStartStatementStack.peekElement();
                boolean topAtStart = myStartStatementStack.peekAtStart();
                myStartStatementStack.pushStatement(myEndStatementStack.peekElement(), myEndStatementStack.peekAtStart());
                myEndStatementStack.pushStatement(topStartStatement, topAtStart);
            }

            operand.accept(this);

            if (operationSign == JavaTokenType.EXCL) {
                // negation inverts jump targets
                myStartStatementStack.popStatement();
                myEndStatementStack.popStatement();
            }

            if (operand instanceof PsiReferenceExpression operandRefExpr
                && (operationSign == JavaTokenType.PLUSPLUS || operationSign == JavaTokenType.MINUSMINUS)) {
                PsiVariable variable = getUsedVariable(operandRefExpr);
                if (variable != null) {
                    generateWriteInstruction(variable);
                }
            }
        }

        finishElement(expression);
    }

    @Override
    @RequiredReadAction
    public void visitReferenceExpression(@Nonnull PsiReferenceExpression expression) {
        startElement(expression);

        PsiExpression qualifier = expression.getQualifierExpression();
        if (qualifier != null) {
            qualifier.accept(this);
        }

        PsiVariable variable = getUsedVariable(expression);
        if (variable != null) {
            generateReadInstruction(variable);
        }

        finishElement(expression);
    }

    @Override
    @RequiredReadAction
    public void visitSuperExpression(@Nonnull PsiSuperExpression expression) {
        startElement(expression);
        finishElement(expression);
    }

    @Override
    @RequiredReadAction
    public void visitThisExpression(@Nonnull PsiThisExpression expression) {
        startElement(expression);
        finishElement(expression);
    }

    @Override
    @RequiredReadAction
    public void visitTypeCastExpression(@Nonnull PsiTypeCastExpression expression) {
        startElement(expression);
        PsiExpression operand = expression.getOperand();
        if (operand != null) {
            operand.accept(this);
        }
        finishElement(expression);
    }

    @Override
    @RequiredReadAction
    public void visitClass(@Nonnull PsiClass aClass) {
        startElement(aClass);
        // anonymous or local class
        if (aClass instanceof PsiAnonymousClass) {
            PsiElement arguments = PsiTreeUtil.getChildOfType(aClass, PsiExpressionList.class);
            if (arguments != null) {
                arguments.accept(this);
            }
        }
        List<PsiVariable> array = new ArrayList<>();
        addUsedVariables(array, aClass);
        for (PsiVariable var : array) {
            ProgressManager.checkCanceled();
            generateReadInstruction(var);
        }
        finishElement(aClass);
    }

    @RequiredReadAction
    private void addUsedVariables(@Nonnull List<? super PsiVariable> array, @Nonnull PsiElement scope) {
        if (scope instanceof PsiReferenceExpression scopeRefExpr) {
            PsiVariable variable = getUsedVariable(scopeRefExpr);
            if (variable != null) {
                if (!array.contains(variable)) {
                    array.add(variable);
                }
            }
        }

        PsiElement[] children = scope.getChildren();
        for (PsiElement child : children) {
            ProgressManager.checkCanceled();
            addUsedVariables(array, child);
        }
    }

    private void generateReadInstruction(@Nonnull PsiVariable variable) {
        Instruction instruction = new ReadVariableInstruction(variable);
        myCurrentFlow.addInstruction(instruction);
    }

    private void generateWriteInstruction(@Nonnull PsiVariable variable) {
        Instruction instruction = new WriteVariableInstruction(variable);
        myCurrentFlow.addInstruction(instruction);
    }

    @Nullable
    private PsiVariable getUsedVariable(@Nonnull PsiReferenceExpression refExpr) {
        if (refExpr.getParent() instanceof PsiMethodCallExpression) {
            return null;
        }
        return myPolicy.getUsedVariable(refExpr);
    }

    private static class FinallyBlockSubroutine {
        private final PsiElement myElement;
        private final List<CallInstruction> myCalls;

        FinallyBlockSubroutine(@Nonnull PsiElement element) {
            myElement = element;
            myCalls = new ArrayList<>();
        }

        @Nonnull
        public PsiElement getElement() {
            return myElement;
        }

        @Nonnull
        public List<CallInstruction> getCalls() {
            return myCalls;
        }

        private void addCall(@Nonnull CallInstruction callInstruction) {
            myCalls.add(callInstruction);
        }
    }

    private static final class SubRangeInfo {
        final PsiElement myElement;
        final int myStart;
        final int myEnd;

        private SubRangeInfo(PsiElement element, int start, int end) {
            myElement = element;
            myStart = start;
            myEnd = end;
        }
    }
}
