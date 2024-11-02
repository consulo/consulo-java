// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.TrackingDfaMemoryState.FactDefinition;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.TrackingDfaMemoryState.FactExtractor;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.TrackingDfaMemoryState.MemoryStateChange;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.TrackingDfaMemoryState.Relation;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.instructions.*;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.rangeSet.LongRangeBinOp;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfConstantType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.*;
import com.intellij.java.language.codeInsight.Nullability;
import com.intellij.java.language.codeInsight.NullabilityAnnotationInfo;
import com.intellij.java.language.codeInsight.NullableNotNullManager;
import com.intellij.java.language.impl.psi.impl.source.tree.ChildRole;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.JavaElementKind;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import com.intellij.java.language.util.JavaPsiConstructorUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.progress.ProgressManager;
import consulo.document.Document;
import consulo.document.util.Segment;
import consulo.document.util.TextRange;
import consulo.java.analysis.localize.JavaAnalysisLocalize;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.impl.ast.CompositeElement;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ThreeState;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.intellij.java.analysis.impl.codeInspection.dataFlow.DfaUtil.hasImplicitImpureSuperCall;

@SuppressWarnings("SuspiciousNameCombination")
public final class TrackingRunner extends DataFlowRunner {
    private MemoryStateChange myHistoryForContext = null;
    private final PsiExpression myExpression;
    private final List<DfaInstructionState> afterStates = new ArrayList<>();
    private final List<TrackingDfaMemoryState> killedStates = new ArrayList<>();

    private TrackingRunner(
        @Nonnull PsiElement context,
        PsiExpression expression,
        boolean unknownMembersAreNullable,
        boolean ignoreAssertions
    ) {
        super(context.getProject(), context, unknownMembersAreNullable, ThreeState.fromBoolean(ignoreAssertions));
        myExpression = expression;
    }

    @Override
    protected void beforeInstruction(Instruction instruction) {
        afterStates.clear();
        killedStates.clear();
    }

    @Override
    protected void afterInstruction(Instruction instruction) {
        if (afterStates.size() <= 1 && killedStates.isEmpty()) {
            return;
        }
        Map<Instruction, List<TrackingDfaMemoryState>> instructionToState =
            StreamEx.of(afterStates).mapToEntry(s -> s.getInstruction(), s -> (TrackingDfaMemoryState)s.getMemoryState()).grouping();
        if (instructionToState.size() <= 1 && killedStates.isEmpty()) {
            return;
        }
        instructionToState.forEach((target, memStates) -> {
            List<TrackingDfaMemoryState> bridgeChanges =
                StreamEx.of(afterStates).filter(s -> s.getInstruction() != target)
                    .map(s -> ((TrackingDfaMemoryState)s.getMemoryState()))
                    .append(killedStates)
                    .toList();
            for (TrackingDfaMemoryState state : memStates) {
                state.addBridge(instruction, bridgeChanges);
            }
        });
    }

    @Nonnull
    @Override
    protected DfaMemoryState createMemoryState() {
        return new TrackingDfaMemoryState(getFactory());
    }

    @Override
    @Nonnull
    protected DfaInstructionState[] acceptInstruction(@Nonnull InstructionVisitor visitor, @Nonnull DfaInstructionState instructionState) {
        Instruction instruction = instructionState.getInstruction();
        TrackingDfaMemoryState memState = (TrackingDfaMemoryState)instructionState.getMemoryState().createCopy();
        DfaInstructionState[] states = super.acceptInstruction(visitor, instructionState);
        for (DfaInstructionState state : states) {
            afterStates.add(state);
            ((TrackingDfaMemoryState)state.getMemoryState()).recordChange(instruction, memState);
        }
        if (states.length == 0) {
            killedStates.add(memState);
        }
        if (instruction instanceof ExpressionPushingInstruction) {
            ExpressionPushingInstruction<?> pushing = (ExpressionPushingInstruction<?>)instruction;
            if (pushing.getExpression() == myExpression && pushing.getExpressionRange() == null) {
                for (DfaInstructionState state : states) {
                    MemoryStateChange history = ((TrackingDfaMemoryState)state.getMemoryState()).getHistory();
                    myHistoryForContext = myHistoryForContext == null ? history : myHistoryForContext.merge(history);
                }
            }
        }
        return states;
    }

    @Nullable
    @RequiredReadAction
    public static CauseItem findProblemCause(
        boolean unknownAreNullables,
        boolean ignoreAssertions,
        PsiExpression expression,
        DfaProblemType type
    ) {
        PsiElement body = DfaUtil.getDataflowContext(expression);
        if (body == null) {
            return null;
        }
        TrackingRunner runner = new TrackingRunner(body, expression, unknownAreNullables, ignoreAssertions);
        if (!runner.analyze(expression, body)) {
            return null;
        }
        return runner.findProblemCause(expression, type);
    }

    @RequiredReadAction
    private boolean analyze(PsiExpression expression, PsiElement body) {
        List<DfaMemoryState> endOfInitializerStates = new ArrayList<>();
        StandardInstructionVisitor visitor = new StandardInstructionVisitor(true) {
            @Override
            public DfaInstructionState[] visitEndOfInitializer(
                EndOfInitializerInstruction instruction,
                DataFlowRunner runner,
                DfaMemoryState state
            ) {
                if (!instruction.isStatic()) {
                    endOfInitializerStates.add(state.createCopy());
                }
                return super.visitEndOfInitializer(instruction, runner, state);
            }
        };
        RunnerResult result = analyzeMethodRecursively(body, visitor);
        if (result != RunnerResult.OK) {
            return false;
        }
        if (body instanceof PsiClass psiClass) {
            PsiMethod ctor = PsiTreeUtil.getParentOfType(expression, PsiMethod.class, true, PsiClass.class, PsiLambdaExpression.class);
            if (ctor != null && ctor.isConstructor()) {
                List<DfaMemoryState> initialStates;
                PsiCodeBlock ctorBody = ctor.getBody();
                if (ctorBody != null) {
                    PsiMethodCallExpression call = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(ctor);
                    if (JavaPsiConstructorUtil.isChainedConstructorCall(call) ||
                        (call == null && hasImplicitImpureSuperCall(psiClass, ctor))) {
                        initialStates = Collections.singletonList(createMemoryState());
                    }
                    else {
                        initialStates = StreamEx.of(endOfInitializerStates).map(DfaMemoryState::createCopy).toList();
                    }
                    return analyzeBlockRecursively(ctorBody, initialStates, visitor) == RunnerResult.OK;
                }
            }
        }
        return true;
    }

    /*
    TODO: 1. Find causes of other warnings:
          Cause for AIOOBE
          Cause for "modifying an immutable collection"
          Cause for "Collection is always empty" (separate inspection now)
    TODO: 2. Describe causes in more cases:
          Warning caused by complex contracts
          Warning caused by CustomMethodHandler
          Warning caused by polyadic math
          Warning caused by unary minus
          Warning caused by string concatenation
          Warning caused by java.lang.Void nullability
          Warning caused by getClass() equality
          Warning caused by inliners
    TODO: 3. Check how it works with:
          Boxed numbers
     */
    @Nullable
    @RequiredReadAction
    private CauseItem findProblemCause(PsiExpression expression, DfaProblemType type) {
        if (myHistoryForContext == null) {
            return null;
        }
        CauseItem cause = null;
        do {
            CauseItem item = new CauseItem(type, expression);
            MemoryStateChange history = myHistoryForContext.getNonMerge();
            if (history.getExpression() == expression) {
                item.addChildren(type.findCauses(this, expression, history));
            }
            if (cause == null) {
                cause = item;
            }
            else {
                cause = cause.merge(item);
                if (cause == null) {
                    return null;
                }
            }
        }
        while (myHistoryForContext.advance());
        return cause;
    }

    public abstract static class DfaProblemType {
        @Nonnull
        public LocalizeValue getMessage() {
            return LocalizeValue.ofNullable(toString());
        }

        @Override
        public String toString() {
            return getMessage().get();
        }

        CauseItem[] findCauses(TrackingRunner runner, PsiExpression expression, MemoryStateChange history) {
            return new CauseItem[0];
        }

        @Nullable
        DfaProblemType tryMerge(DfaProblemType other) {
            return this.getMessage().equals(other.getMessage()) ? this : null;
        }
    }

    public static class CauseItem {
        private static final String PLACE_POINTER = "___PLACE___";
        @Nonnull
        final List<CauseItem> myChildren;
        @Nonnull
        final DfaProblemType myProblem;
        @Nullable
        final SmartPsiFileRange myTarget;

        private CauseItem(@Nonnull List<CauseItem> children, @Nonnull DfaProblemType problem, @Nullable SmartPsiFileRange target) {
            myChildren = children;
            myProblem = problem;
            myTarget = target;
        }

        @RequiredReadAction
        CauseItem(@Nonnull LocalizeValue problem, @Nullable PsiElement target) {
            this(new CustomDfaProblemType(problem), target);
        }

        @RequiredReadAction
        CauseItem(@Nonnull DfaProblemType problem, @Nullable PsiElement target) {
            myChildren = new ArrayList<>();
            myProblem = problem;
            if (target != null) {
                PsiFile file = target.getContainingFile();
                myTarget = SmartPointerManager.getInstance(file.getProject()).createSmartPsiFileRangePointer(file, target.getTextRange());
            }
            else {
                myTarget = null;
            }
        }

        @RequiredReadAction
        CauseItem(@Nonnull LocalizeValue problem, @Nonnull MemoryStateChange change) {
            this(new CustomDfaProblemType(problem), change);
        }

        @RequiredReadAction
        CauseItem(@Nonnull DfaProblemType problem, @Nonnull MemoryStateChange change) {
            this(problem, change.getExpression());
        }

        void addChildren(CauseItem... causes) {
            ContainerUtil.addAllNotNull(myChildren, causes);
        }

        @Override
        public boolean equals(Object o) {
            return this == o
                || o instanceof CauseItem that
                && myChildren.equals(that.myChildren)
                && getProblemName().equals(that.getProblemName())
                && Objects.equals(myTarget, that.myTarget);
        }

        private
        @Nls
        String getProblemName() {
            return myProblem.toString();
        }

        @Override
        public int hashCode() {
            return Objects.hash(myChildren, getProblemName(), myTarget);
        }

        public String dump(Document doc) {
            return dump(doc, 0, null);
        }

        private String dump(Document doc, int indent, CauseItem parent) {
            String text = null;
            if (myTarget != null) {
                Segment range = myTarget.getRange();
                if (range != null) {
                    text = doc.getText(TextRange.create(range));
                    int lineNumber = doc.getLineNumber(range.getStartOffset());
                    text += "; line#" + (lineNumber + 1);
                }
            }
            return StringUtil.repeat("  ", indent) + render(doc, parent) + (text == null ? "" : " (" + text + ")") + "\n" +
                StreamEx.of(myChildren).map(child -> child.dump(doc, indent + 1, this)).joining();
        }

        public Stream<CauseItem> children() {
            return StreamEx.of(myChildren);
        }

        @Nullable
        @RequiredReadAction
        public PsiFile getFile() {
            return myTarget != null ? myTarget.getContainingFile() : null;
        }

        public Segment getTargetSegment() {
            return myTarget == null ? null : myTarget.getRange();
        }

        public String render(Document doc, CauseItem parent) {
            String title = null;
            Segment range = getTargetSegment();
            if (range != null) {
                String cause = getProblemName();
                if (cause.contains(PLACE_POINTER)) {
                    int offset = range.getStartOffset();
                    int number = doc.getLineNumber(offset);
                    title = cause.replaceFirst(PLACE_POINTER, JavaAnalysisLocalize.dfaFindCausePlaceLineNumber(number + 1).get());
                }
            }
            if (title == null) {
                title = toString();
            }
            int childIndex = parent == null ? 0 : parent.myChildren.indexOf(this);
            if (childIndex > 0) {
                title = (parent.myProblem instanceof PossibleExecutionDfaProblemType ? "or " : "and ") + title;
            }
            else {
                title = StringUtil.capitalize(title);
            }
            return title;
        }

        @Override
        public String toString() {
            //noinspection HardCodedStringLiteral
            return getProblemName().replaceFirst(PLACE_POINTER, JavaAnalysisLocalize.dfaFindCausePlaceHere().get());
        }

        @RequiredReadAction
        public CauseItem merge(CauseItem other) {
            if (this.equals(other)) {
                return this;
            }
            if (Objects.equals(this.myTarget, other.myTarget)) {
                if (myChildren.equals(other.myChildren)) {
                    DfaProblemType mergedProblem = myProblem.tryMerge(other.myProblem);
                    if (mergedProblem != null) {
                        return new CauseItem(myChildren, mergedProblem, myTarget);
                    }
                }
                if (getProblemName().equals(other.getProblemName())) {
                    if (tryMergeChildren(other.myChildren)) {
                        return this;
                    }
                    if (other.tryMergeChildren(this.myChildren)) {
                        return other;
                    }
                }
            }
            return null;
        }

        @RequiredReadAction
        private boolean tryMergeChildren(List<CauseItem> children) {
            if (myChildren.isEmpty()) {
                return false;
            }
            if (myChildren.size() != 1 || !(myChildren.get(0).myProblem instanceof PossibleExecutionDfaProblemType)) {
                if (children.size() == myChildren.size()) {
                    List<CauseItem> merged = StreamEx.zip(myChildren, children, CauseItem::merge).toList();
                    if (!merged.contains(null)) {
                        myChildren.clear();
                        myChildren.addAll(merged);
                        return true;
                    }
                }
                insertIntoHierarchy(new CauseItem(new PossibleExecutionDfaProblemType(), (PsiElement)null));
            }
            CauseItem mergePoint = myChildren.get(0);
            if (children.isEmpty()) {
                ((PossibleExecutionDfaProblemType)mergePoint.myProblem).myComplete = false;
            }
            List<CauseItem> mergeChildren = mergePoint.myChildren;
            for (CauseItem child : children) {
                if (!mergeChildren.contains(child)) {
                    boolean merged = false;
                    for (int i = 0; i < mergeChildren.size(); i++) {
                        CauseItem mergeChild = mergeChildren.get(i);
                        CauseItem result = mergeChild.merge(child);
                        if (result != null) {
                            mergeChildren.set(i, result);
                            merged = true;
                            break;
                        }
                    }
                    if (!merged) {
                        mergeChildren.add(child);
                    }
                }
            }
            return true;
        }

        private void insertIntoHierarchy(CauseItem intermediate) {
            intermediate.myChildren.addAll(myChildren);
            myChildren.clear();
            myChildren.add(intermediate);
        }
    }

    public static class CastDfaProblemType extends DfaProblemType {
        @Override
        @RequiredReadAction
        public CauseItem[] findCauses(TrackingRunner runner, PsiExpression expression, MemoryStateChange history) {
            if (expression instanceof PsiTypeCastExpression typeCast) {
                PsiType expressionType = expression.getType();
                MemoryStateChange operandPush = history.findExpressionPush(typeCast.getOperand());
                if (operandPush != null) {
                    return new CauseItem[]{findTypeCause(operandPush, expressionType, false)};
                }
            }
            return new CauseItem[0];
        }

        @Nonnull
        @Override
        public LocalizeValue getMessage() {
            return JavaAnalysisLocalize.dfaFindCauseCastMayFail();
        }
    }

    public static class NullableDfaProblemType extends DfaProblemType {
        @Override
        @RequiredReadAction
        public CauseItem[] findCauses(TrackingRunner runner, PsiExpression expression, MemoryStateChange history) {
            FactDefinition<DfaNullability> nullability = history.findFact(history.myTopOfStack, FactExtractor.nullability());
            if (nullability.myFact == DfaNullability.NULLABLE || nullability.myFact == DfaNullability.NULL) {
                return new CauseItem[]{runner.findNullabilityCause(history, nullability.myFact)};
            }
            return new CauseItem[0];
        }

        @Nonnull
        @Override
        public LocalizeValue getMessage() {
            return JavaAnalysisLocalize.dfaFindCauseMayBeNull();
        }
    }

    public static class FailingCallDfaProblemType extends DfaProblemType {
        @Override
        @RequiredReadAction
        CauseItem[] findCauses(TrackingRunner runner, PsiExpression expression, MemoryStateChange history) {
            return expression instanceof PsiCallExpression call
                ? new CauseItem[]{runner.fromCallContract(history, call, ContractReturnValue.fail())}
                : super.findCauses(runner, expression, history);
        }

        @Nonnull
        @Override
        public LocalizeValue getMessage() {
            return JavaAnalysisLocalize.dfaFindCauseCallAlwaysFails();
        }
    }

    static class PossibleExecutionDfaProblemType extends DfaProblemType {
        boolean myComplete = true;

        @Nonnull
        @Override
        public LocalizeValue getMessage() {
            return myComplete ?
                JavaAnalysisLocalize.dfaFindCauseOneOfTheFollowingHappens() :
                JavaAnalysisLocalize.dfaFindCauseAnExecutionMightExistWhere();
        }
    }

    static class RangeDfaProblemType extends DfaProblemType {
        @Nonnull
        final LocalizeValue myTemplate;
        @Nonnull
        final LongRangeSet myRangeSet;
        @Nullable
        final PsiPrimitiveType myType;

        RangeDfaProblemType(@Nonnull LocalizeValue template, @Nonnull LongRangeSet set, @Nullable PsiPrimitiveType type) {
            myTemplate = template;
            myRangeSet = set;
            myType = type;
        }

        @Nullable
        @Override
        DfaProblemType tryMerge(DfaProblemType other) {
            return other instanceof RangeDfaProblemType rangeProblem
                && myTemplate.equals(rangeProblem.myTemplate)
                && Objects.equals(myType, rangeProblem.myType)
                ? new RangeDfaProblemType(myTemplate, myRangeSet.unite(rangeProblem.myRangeSet), myType)
                : super.tryMerge(other);
        }

        @Nonnull
        @Override
        public LocalizeValue getMessage() {
            return LocalizeValue.of(String.format(myTemplate.get(), myRangeSet.getPresentationText(myType)));
        }
    }

    public static class ValueDfaProblemType extends DfaProblemType {
        final Object myValue;

        public ValueDfaProblemType(Object value) {
            myValue = value;
        }

        @Override
        @RequiredReadAction
        public CauseItem[] findCauses(TrackingRunner runner, PsiExpression expression, MemoryStateChange history) {
            return runner.findConstantValueCause(expression, history, myValue);
        }

        @Nonnull
        @Override
        public LocalizeValue getMessage() {
            return JavaAnalysisLocalize.dfaFindCauseValueIsAlwaysTheSame(myValue);
        }
    }

    static class CustomDfaProblemType extends DfaProblemType {
        private final LocalizeValue myMessage;

        CustomDfaProblemType(LocalizeValue message) {
            myMessage = message;
        }

        @Nonnull
        @Override
        public LocalizeValue getMessage() {
            return myMessage;
        }
    }

    @Nonnull
    @RequiredReadAction
    private CauseItem[] findConstantValueCause(PsiExpression expression, MemoryStateChange history, Object expectedValue) {
        if (expression instanceof PsiLiteralExpression) {
            return new CauseItem[0];
        }
        Object constantExpressionValue = ExpressionUtils.computeConstantExpression(expression);
        DfaValue value = history.myTopOfStack;
        if (constantExpressionValue != null && constantExpressionValue.equals(expectedValue)) {
            return new CauseItem[]{new CauseItem(JavaAnalysisLocalize.dfaFindCauseCompileTimeConstant(value), expression)};
        }
        if (value.getDfType() instanceof DfConstantType) {
            Object constValue = ((DfConstantType<?>)value.getDfType()).getValue();
            if (Objects.equals(constValue, expectedValue) && constValue instanceof Boolean booleanValue) {
                return findBooleanResultCauses(expression, history, booleanValue);
            }
        }
        if (value instanceof DfaVariableValue dfaVarValue) {
            MemoryStateChange change = history.findRelation(
                dfaVarValue,
                rel -> rel.myRelationType == RelationType.EQ && DfConstantType.isConst(rel.myCounterpart.getDfType(), expectedValue),
                false
            );
            if (change != null) {
                PsiExpression varSourceExpression = change.getExpression();
                if (change.myInstruction instanceof AssignInstruction assignInsn && change.myTopOfStack == value) {
                    PsiExpression rValue = assignInsn.getRExpression();
                    CauseItem item = createAssignmentCause(assignInsn, value);
                    MemoryStateChange push = change.findSubExpressionPush(rValue);
                    if (push != null) {
                        item.addChildren(findConstantValueCause(rValue, push, expectedValue));
                    }
                    return new CauseItem[]{item};
                }
                else if (varSourceExpression != null) {
                    return new CauseItem[]{
                        new CauseItem(
                            JavaAnalysisLocalize.dfaFindCauseEqualityEstablishedFromCondition(value + " == " + expectedValue),
                            varSourceExpression
                        )
                    };
                }
            }
        }
        return new CauseItem[0];
    }

    @Nonnull
    @Contract("_, _ -> new")
    @RequiredReadAction
    private static CauseItem createAssignmentCause(AssignInstruction instruction, DfaValue target) {
        PsiExpression rExpression = instruction.getRExpression();
        PsiElement anchor = null;
        String targetName = target.toString();
        if (rExpression != null) {
            PsiElement parent = PsiUtil.skipParenthesizedExprUp(rExpression.getParent());
            if (parent instanceof PsiAssignmentExpression assignment) {
                anchor = assignment.getOperationSign();
                targetName = assignment.getLExpression().getText();
            }
            else if (parent instanceof PsiVariable variable) {
                ASTNode node = parent.getNode();
                if (node instanceof CompositeElement compositeElement) {
                    anchor = compositeElement.findChildByRoleAsPsiElement(ChildRole.INITIALIZER_EQ);
                }
                targetName = variable.getName();
            }
            if (anchor == null) {
                anchor = rExpression;
            }
        }
        PsiExpression stripped = PsiUtil.skipParenthesizedExprDown(rExpression);
        LocalizeValue message;
        if (stripped instanceof PsiLiteralExpression literal) {
            message =
                JavaAnalysisLocalize.dfaFindCauseWasAssignedTo(targetName, StringUtil.shortenTextWithEllipsis(literal.getText(), 40, 5));
        }
        else {
            message = JavaAnalysisLocalize.dfaFindCauseWasAssigned(targetName);
        }
        return new CauseItem(message, anchor);
    }

    @RequiredReadAction
    private CauseItem[] findBooleanResultCauses(
        PsiExpression expression,
        MemoryStateChange history,
        boolean value
    ) {
        if (BoolUtils.isNegation(expression)) {
            PsiExpression negated = BoolUtils.getNegated(expression);
            if (negated != null) {
                MemoryStateChange negatedPush = history.findExpressionPush(negated);
                if (negatedPush != null) {
                    CauseItem cause =
                        new CauseItem(JavaAnalysisLocalize.dfaFindCauseValueXIsAlwaysTheSame(negated.getText(), !value), negated);
                    cause.addChildren(findConstantValueCause(negated, negatedPush, !value));
                    return new CauseItem[]{cause};
                }
            }
        }
        if (expression instanceof PsiPolyadicExpression polyadic) {
            IElementType tokenType = polyadic.getOperationTokenType();
            boolean and = tokenType.equals(JavaTokenType.ANDAND);
            if (and || tokenType.equals(JavaTokenType.OROR)) {
                if (value != and) {
                    MemoryStateChange push = history;
                    if (history.myInstruction instanceof ResultOfInstruction) {
                        MemoryStateChange previous = history.getPrevious();
                        if (previous != null) {
                            previous = previous.getNonMerge();
                        }
                        if (previous != null && previous.myInstruction instanceof GotoInstruction) {
                            previous = previous.getPrevious();
                        }
                        if (previous != null) {
                            push = previous;
                        }
                    }
                    if (push.myInstruction instanceof PushValueInstruction pushValueInsn
                        && DfConstantType.isConst(pushValueInsn.getValue(), value)
                        && pushValueInsn.getExpression() == expression) {
                        push = push.getPrevious();
                    }
                    if (push != null && push.myInstruction instanceof ConditionalGotoInstruction) {
                        push = push.getPrevious();
                    }
                    if (push != null && push.myInstruction instanceof ExpressionPushingInstruction) {
                        ExpressionPushingInstruction<?> instruction = (ExpressionPushingInstruction<?>)push.myInstruction;
                        if (instruction.getExpressionRange() == null) {
                            PsiExpression operand = instruction.getExpression();
                            if (operand != null && expression.equals(PsiUtil.skipParenthesizedExprUp(operand.getParent()))) {
                                int i = IntStreamEx.ofIndices(
                                        ((PsiPolyadicExpression)expression).getOperands(),
                                        e -> PsiTreeUtil.isAncestor(e, operand, false)
                                    )
                                    .findFirst().orElse(-1);
                                if (i >= 0) {
                                    CauseItem cause = new CauseItem(
                                        JavaAnalysisLocalize.dfaFindCauseOperandOfBooleanExpressionIsTheSame(i + 1, and ? 0 : 1, value),
                                        operand
                                    );
                                    cause.addChildren(findConstantValueCause(operand, push, value));
                                    return new CauseItem[]{cause};
                                }
                            }
                        }
                    }
                    return new CauseItem[0];
                }
                PsiExpression[] operands = ((PsiPolyadicExpression)expression).getOperands();
                List<CauseItem> operandCauses = new ArrayList<>();
                for (int i = 0; i < operands.length; i++) {
                    PsiExpression operand = operands[i];
                    operand = PsiUtil.skipParenthesizedExprDown(operand);
                    MemoryStateChange push = history.findExpressionPush(operand);
                    if (push != null
                        && ((push.myInstruction instanceof ConditionalGotoInstruction condGotoInsn
                        && condGotoInsn.isTarget(value, history.myInstruction))
                        || DfConstantType.isConst(push.myTopOfStack.getDfType(), value))) {
                        int andVal = and ? 1 : 0;
                        CauseItem cause = new CauseItem(
                            JavaAnalysisLocalize.dfaFindCauseOperandOfBooleanExpressionIsTheSame(i + 1, andVal == 1 ? 0 : 1, value),
                            operand
                        );
                        cause.addChildren(findBooleanResultCauses(operand, push, value));
                        operandCauses.add(cause);
                    }
                }
                if (operandCauses.size() == operands.length) {
                    return operandCauses.toArray(new CauseItem[0]);
                }
            }
        }
        if (expression instanceof PsiBinaryExpression binOp) {
            RelationType relationType = RelationType.fromElementType(binOp.getOperationTokenType());
            if (relationType != null) {
                if (!value) {
                    relationType = relationType.getNegated();
                }
                PsiExpression leftOperand = PsiUtil.skipParenthesizedExprDown(binOp.getLOperand());
                PsiExpression rightOperand = PsiUtil.skipParenthesizedExprDown(binOp.getROperand());
                MemoryStateChange leftChange = history.findExpressionPush(leftOperand);
                MemoryStateChange rightChange = history.findExpressionPush(rightOperand);
                if (leftChange != null && rightChange != null) {
                    DfaValue leftValue = leftChange.myTopOfStack;
                    DfaValue rightValue = rightChange.myTopOfStack;
                    CauseItem[] causes = findRelationCause(relationType, leftChange, rightChange);
                    if (causes.length > 0) {
                        return causes;
                    }
                    if (leftValue == rightValue &&
                        (leftValue instanceof DfaVariableValue || leftValue.getDfType() instanceof DfConstantType)) {
                        List<CauseItem> constCauses = new ArrayList<>();
                        CauseItem leftCause = constantInitializerCause(leftValue, leftChange.getExpression());
                        CauseItem rightCause = constantInitializerCause(rightValue, rightChange.getExpression());
                        ContainerUtil.addAllNotNull(constCauses, leftCause, rightCause);
                        if (constCauses.isEmpty()) {
                            return new CauseItem[]{
                                new CauseItem(
                                    JavaAnalysisLocalize.dfaFindCauseComparisonArgumentsAreTheSame(),
                                    binOp.getOperationSign()
                                )
                            };
                        }
                        return constCauses.toArray(new CauseItem[0]);
                    }
                    if (leftValue != rightValue
                        && relationType.isInequality()
                        && leftValue.getDfType() instanceof DfConstantType
                        && rightValue.getDfType() instanceof DfConstantType) {
                        CauseItem causeItem = new CauseItem(
                            JavaAnalysisLocalize.dfaFindCauseComparisonArgumentsAreDifferentConstants(),
                            binOp.getOperationSign()
                        );
                        causeItem.addChildren(
                            constantInitializerCause(leftValue, leftChange.getExpression()),
                            constantInitializerCause(rightValue, rightChange.getExpression())
                        );
                        return new CauseItem[]{causeItem};
                    }
                }
            }
        }
        if (expression instanceof PsiInstanceOfExpression instanceOf) {
            PsiExpression operand = instanceOf.getOperand();
            MemoryStateChange operandHistory = history.findExpressionPush(operand);
            if (operandHistory != null) {
                DfaValue operandValue = operandHistory.myTopOfStack;
                if (!value) {
                    FactDefinition<DfaNullability> nullability = operandHistory.findFact(operandValue, FactExtractor.nullability());
                    if (nullability.myFact == DfaNullability.NULL) {
                        CauseItem causeItem =
                            new CauseItem(JavaAnalysisLocalize.dfaFindCauseValueXIsAlwaysTheSame(operand.getText(), "null"), operand);
                        causeItem.addChildren(findConstantValueCause(operand, operandHistory, null));
                        return new CauseItem[]{causeItem};
                    }
                }
                PsiTypeElement typeElement = instanceOf.getCheckType();
                if (typeElement != null) {
                    PsiType type = typeElement.getType();
                    CauseItem causeItem = findTypeCause(operandHistory, type, value);
                    if (causeItem != null) {
                        return new CauseItem[]{causeItem};
                    }
                }
            }
        }
        if (expression instanceof PsiMethodCallExpression methodCall) {
            return new CauseItem[]{fromCallContract(history, methodCall, ContractReturnValue.returnBoolean(value))};
        }
        return new CauseItem[0];
    }

    @RequiredReadAction
    private static CauseItem constantInitializerCause(DfaValue value, PsiExpression ref) {
        if (value.getDfType() instanceof DfConstantType
            && ref instanceof PsiReferenceExpression refExpr
            && refExpr.resolve() instanceof PsiVariable targetVar
            && targetVar.hasModifierProperty(PsiModifier.FINAL)) {
            PsiExpression initializer = PsiUtil.skipParenthesizedExprDown(targetVar.getInitializer());
            if (initializer != null) {
                return new CauseItem(
                    JavaAnalysisLocalize.dfaFindCauseVariableIsInitialized(
                        JavaElementKind.fromElement(targetVar).subject(),
                        targetVar.getName(),
                        initializer.getText()
                    ),
                    initializer.getContainingFile() == ref.getContainingFile() ? initializer : ref
                );
            }
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    private static CauseItem findTypeCause(MemoryStateChange operandHistory, PsiType type, boolean isInstance) {
        PsiExpression operand = Objects.requireNonNull(operandHistory.getExpression());
        TypeConstraint wanted = TypeConstraints.instanceOf(type);
        PsiType operandType = operand.getType();
        if (operandType != null) {
            TypeConstraint constraint = TypeConstraints.instanceOf(operandType);
            LocalizeValue name = JavaAnalysisLocalize.dfaFindCauseObjectKindExpression();
            if (operand instanceof PsiMethodCallExpression) {
                name = JavaAnalysisLocalize.dfaFindCauseObjectKindMethodReturn();
            }
            else if (operand instanceof PsiReferenceExpression operandRefExpr) {
                PsiElement target = operandRefExpr.resolve();
                if (target != null) {
                    name = JavaElementKind.fromElement(target).subject();
                }
            }
            LocalizeValue explanation = LocalizeValue.ofNullable(constraint.getAssignabilityExplanation(wanted, isInstance, name.get()));
            if (explanation != LocalizeValue.empty()) {
                if (constraint.equals(wanted)) {
                    explanation = JavaAnalysisLocalize.dfaFindCauseTypeKnown(name, constraint.toShortString());
                }
                return new CauseItem(explanation, operand);
            }
        }
        DfaValue operandValue = operandHistory.myTopOfStack;

        FactDefinition<TypeConstraint> fact = operandHistory.findFact(operandValue, FactExtractor.constraint());
        LocalizeValue explanation = LocalizeValue.ofNullable(fact.myFact.getAssignabilityExplanation(
            wanted,
            isInstance,
            JavaAnalysisLocalize.dfaFindCauseObjectKindGeneric().get()
        ));
        while (explanation != LocalizeValue.empty()) {
            MemoryStateChange causeLocation = fact.myChange;
            if (causeLocation == null) {
                break;
            }
            MemoryStateChange prevHistory = causeLocation.getPrevious();
            if (prevHistory == null) {
                break;
            }
            fact = prevHistory.findFact(operandValue, FactExtractor.constraint());
            TypeConstraint prevConstraint = fact.myFact;
            LocalizeValue prevExplanation = LocalizeValue.ofNullable(prevConstraint.getAssignabilityExplanation(
                wanted,
                isInstance,
                JavaAnalysisLocalize.dfaFindCauseObjectKindGeneric().get()
            ));
            if (prevExplanation == LocalizeValue.empty()) {
                if (causeLocation.myInstruction instanceof AssignInstruction assignInsn && causeLocation.myTopOfStack == operandValue) {
                    PsiExpression rExpression = assignInsn.getRExpression();
                    if (rExpression != null) {
                        MemoryStateChange rValuePush = causeLocation.findSubExpressionPush(rExpression);
                        if (rValuePush != null) {
                            CauseItem assignmentItem = createAssignmentCause((AssignInstruction)causeLocation.myInstruction, operandValue);
                            assignmentItem.addChildren(findTypeCause(rValuePush, type, isInstance));
                            return assignmentItem;
                        }
                    }
                }
                CauseItem causeItem = new CauseItem(explanation, operand);
                causeItem.addChildren(new CauseItem(
                    JavaAnalysisLocalize.dfaFindCauseTypeIsKnownFromPlace(operand.getText()),
                    causeLocation
                ));
                return causeItem;
            }
            explanation = prevExplanation;
        }
        return null;
    }

    @Nonnull
    @RequiredReadAction
    private CauseItem[] findRelationCause(RelationType relationType, MemoryStateChange leftChange, MemoryStateChange rightChange) {
        return findRelationCause(relationType, leftChange, leftChange.myTopOfStack, rightChange, rightChange.myTopOfStack);
    }

    @Nonnull
    @RequiredReadAction
    private CauseItem[] findRelationCause(
        RelationType relationType,
        MemoryStateChange leftChange,
        DfaValue leftValue,
        MemoryStateChange rightChange,
        DfaValue rightValue
    ) {
        ProgressManager.checkCanceled();
        FactDefinition<DfaNullability> leftNullability = leftChange.findFact(leftValue, FactExtractor.nullability());
        FactDefinition<DfaNullability> rightNullability = rightChange.findFact(rightValue, FactExtractor.nullability());
        if ((leftNullability.myFact == DfaNullability.NULL && rightNullability.myFact == DfaNullability.NOT_NULL) ||
            (rightNullability.myFact == DfaNullability.NULL && leftNullability.myFact == DfaNullability.NOT_NULL)) {
            return new CauseItem[]{
                findNullabilityCause(leftChange, leftNullability.myFact),
                findNullabilityCause(rightChange, rightNullability.myFact)
            };
        }

        FactDefinition<LongRangeSet> leftRange = leftChange.findFact(leftValue, FactExtractor.range());
        FactDefinition<LongRangeSet> rightRange = rightChange.findFact(rightValue, FactExtractor.range());
        LongRangeSet fromRelation = rightRange.myFact.fromRelation(relationType.getNegated());
        if (fromRelation != null && !fromRelation.intersects(leftRange.myFact)) {
            return new CauseItem[]{
                findRangeCause(
                    leftChange,
                    leftValue,
                    leftRange.myFact,
                    JavaAnalysisLocalize.dfaFindCauseLeftOperandRangeTemplate()
                ),
                findRangeCause(
                    rightChange,
                    rightValue,
                    rightRange.myFact,
                    JavaAnalysisLocalize.dfaFindCauseRightOperandRangeTemplate()
                )
            };
        }
        if (leftValue instanceof DfaVariableValue leftDfaVarValue) {
            if (leftDfaVarValue == rightValue) {
                PsiExpression leftExpression = leftChange.getExpression();
                PsiExpression rightExpression = rightChange.getExpression();
                if (leftExpression instanceof PsiMethodCallExpression leftExprMethodCall) {
                    CauseItem cause = fromCallContract(leftChange, leftExprMethodCall, rightExpression);
                    if (cause != null) {
                        return new CauseItem[]{cause};
                    }
                }
                if (rightExpression instanceof PsiMethodCallExpression rightExprMethodCall) {
                    CauseItem cause = fromCallContract(rightChange, rightExprMethodCall, leftExpression);
                    if (cause != null) {
                        return new CauseItem[]{cause};
                    }
                }
            }
            Relation relation = new Relation(relationType, rightValue);
            MemoryStateChange change = findRelationAddedChange(leftChange, leftDfaVarValue, relation);
            if (change != null) {
                CauseItem cause = findRelationCause(change, leftDfaVarValue, relation, rightChange);
                if (cause != null && change.myInstruction instanceof AssignInstruction assignInsn) {
                    MemoryStateChange assignmentChange = change.findExpressionPush(assignInsn.getRExpression());
                    if (assignmentChange != null) {
                        DfaValue target = change.myTopOfStack;
                        if (target == rightValue) {
                            return ArrayUtil.prepend(cause, findRelationCause(relationType, leftChange, assignmentChange));
                        }
                    }
                }
                return new CauseItem[]{cause};
            }
        }
        if (rightValue instanceof DfaVariableValue rightDfaVarValue) {
            Relation relation = new Relation(
                Objects.requireNonNull(relationType.getFlipped()), leftValue);
            MemoryStateChange change = findRelationAddedChange(rightChange, rightDfaVarValue, relation);
            if (change != null) {
                return new CauseItem[]{findRelationCause(change, rightDfaVarValue, relation, leftChange)};
            }
        }
        if (relationType == RelationType.NE) {
            SpecialField leftField = SpecialField.fromQualifier(leftValue);
            SpecialField rightField = SpecialField.fromQualifier(rightValue);
            if (leftField != null && leftField == rightField) {
                DfaValue leftSpecial = leftField.createValue(getFactory(), leftValue);
                DfaValue rightSpecial = rightField.createValue(getFactory(), rightValue);
                CauseItem[] specialCause = findRelationCause(relationType, leftChange, leftSpecial, rightChange, rightSpecial);
                if (specialCause.length > 0) {
                    CauseItem item = new CauseItem(
                        JavaAnalysisLocalize.dfaFindCauseValuesCannotBeEqualBecause(leftValue + "." + leftField + " != " + rightValue + "." + rightField),
                        (PsiElement)null
                    );
                    item.addChildren(specialCause);
                    return new CauseItem[]{item};
                }
            }
        }
        return new CauseItem[0];
    }

    @RequiredReadAction
    private CauseItem findRelationCause(
        MemoryStateChange change,
        DfaVariableValue value,
        Relation relation,
        MemoryStateChange counterPartChange
    ) {
        String condition = value + " " + relation;
        if (change.myInstruction instanceof AssignInstruction assignInsn) {
            DfaValue target = change.myTopOfStack;
            PsiExpression rValue = assignInsn.getRExpression();
            CauseItem item = createAssignmentCause(assignInsn, target);
            if (target == value) {
                MemoryStateChange rValuePush = change.findSubExpressionPush(rValue);
                if (rValuePush != null) {
                    item.addChildren(findRelationCause(relation.myRelationType, rValuePush, counterPartChange));
                }
                return item;
            }
            if (target == relation.myCounterpart) {
                return item;
            }
        }
        PsiExpression expression = change.getExpression();
        if (expression != null) {
            Collection<DfaRelation> relations = Collections.emptyList();
            if (expression instanceof PsiBinaryExpression binaryExpression) {
                DfaRelation rel = getBinaryExpressionRelation(change, binaryExpression);
                if (rel != null) {
                    if (isSameRelation(rel, value, relation)) {
                        return new CauseItem(
                            new CustomDfaProblemType(JavaAnalysisLocalize.dfaFindCauseConditionWasCheckedBefore(condition)),
                            binaryExpression
                        );
                    }
                    relations = Collections.singleton(rel);
                }
            }
            if (expression instanceof PsiCallExpression call) {
                relations = getCallRelations(call);
            }
            List<DfaRelation> chain = findDeductionChain(change, relations, value, relation);
            if (!chain.isEmpty()) {
                CauseItem[] result = new CauseItem[0];
                for (DfaRelation deduced : chain) {
                    CauseItem[] cause =
                        findRelationCause(deduced.getRelation(), change, deduced.getLeftOperand(), change, deduced.getRightOperand());
                    result = ArrayUtil.mergeArrays(result, cause);
                }
                if (result.length > 1) {
                    CauseItem item = new CauseItem(
                        new CustomDfaProblemType(JavaAnalysisLocalize.dfaFindCauseConditionWasDeduced(condition)),
                        (PsiElement)null
                    );
                    item.addChildren(result);
                    return item;
                }
            }
            return new CauseItem(
                new CustomDfaProblemType(JavaAnalysisLocalize.dfaFindCauseConditionIsKnownFromPlace(condition)),
                expression
            );
        }
        return null;
    }

    private static List<DfaRelation> findDeductionChain(
        MemoryStateChange change,
        Collection<DfaRelation> knownRelations,
        DfaVariableValue value,
        Relation relation
    ) {
        for (DfaRelation rel : knownRelations) {
            if (isSameRelation(rel, value, relation)) {
                continue;
            }
            for (Map.Entry<DfaVariableValue, TrackingDfaMemoryState.Change> entry : change.myChanges.entrySet()) {
                DfaVariableValue actualVar = entry.getKey();
                for (Relation actualRelation : entry.getValue().myAddedRelations) {
                    if (isSameRelation(rel, actualVar, actualRelation)) {
                        DfaValue left;
                        DfaValue right;
                        RelationType type;
                        if (actualRelation.myRelationType == RelationType.EQ ||
                            (relation.myRelationType != RelationType.NE && relation.myRelationType == actualRelation.myRelationType)) {
                            type = relation.myRelationType;
                        }
                        else if (relation.myRelationType == RelationType.EQ) {
                            type = actualRelation.myRelationType;
                        }
                        else {
                            continue;
                        }
                        if (actualVar == value) {
                            left = actualRelation.myCounterpart;
                            right = relation.myCounterpart;
                        }
                        else if (actualVar == relation.myCounterpart) {
                            left = value;
                            right = actualRelation.myCounterpart;
                        }
                        else if (actualRelation.myCounterpart == relation.myCounterpart) {
                            left = value;
                            right = actualVar;
                        }
                        else if (actualRelation.myCounterpart == value) {
                            left = actualVar;
                            right = relation.myCounterpart;
                        }
                        else {
                            continue;
                        }
                        DfaRelation rel1 = DfaRelation.createRelation(left, type, right);
                        DfaRelation rel2 =
                            DfaRelation.createRelation(actualVar, actualRelation.myRelationType, actualRelation.myCounterpart);
                        return StreamEx.of(rel1, rel2).nonNull().toImmutableList();
                    }
                }
            }
        }
        return Collections.emptyList();
    }

    private static boolean isSameRelation(DfaRelation dfaRel, DfaVariableValue var, Relation relation) {
        DfaValue counterpart;
        RelationType type;
        if (dfaRel.getLeftOperand() == var) {
            type = dfaRel.getRelation();
            counterpart = dfaRel.getRightOperand();
        }
        else if (dfaRel.getRightOperand() == var) {
            type = dfaRel.getRelation().getFlipped();
            counterpart = dfaRel.getLeftOperand();
        }
        else {
            return false;
        }
        return counterpart == relation.myCounterpart && type != null;
    }

    @Nullable
    private static DfaRelation getBinaryExpressionRelation(MemoryStateChange change, PsiBinaryExpression binOp) {
        PsiExpression lOperand = binOp.getLOperand();
        PsiExpression rOperand = binOp.getROperand();
        MemoryStateChange leftPos = change.findExpressionPush(lOperand);
        MemoryStateChange rightPos = change.findExpressionPush(rOperand);
        if (leftPos != null && rightPos != null) {
            DfaValue leftValue = leftPos.myTopOfStack;
            DfaValue rightValue = rightPos.myTopOfStack;
            RelationType type = RelationType.fromElementType(binOp.getOperationTokenType());
            if (type != null) {
                return DfaRelation.createRelation(leftValue, type, rightValue);
            }
        }
        return null;
    }

    private Collection<DfaRelation> getCallRelations(PsiCallExpression callExpression) {
        List<? extends MethodContract> contracts = JavaMethodContractUtil.getMethodCallContracts(callExpression);
        Set<DfaRelation> results = new LinkedHashSet<>();
        for (MethodContract contract : contracts) {
            for (ContractValue condition : contract.getConditions()) {
                DfaCondition rel = condition.fromCall(getFactory(), callExpression);
                ContainerUtil.addIfNotNull(results, ObjectUtil.tryCast(rel, DfaRelation.class));
            }
        }
        return results;
    }

    @RequiredReadAction
    private CauseItem findNullabilityCause(MemoryStateChange factUse, DfaNullability nullability) {
        PsiExpression expression = factUse.getExpression();
        if (expression instanceof PsiTypeCastExpression typeCast) {
            MemoryStateChange operandPush = factUse.findSubExpressionPush(typeCast.getOperand());
            if (operandPush != null) {
                return findNullabilityCause(operandPush, nullability);
            }
        }
        if (expression instanceof PsiMethodCallExpression call) {
            PsiMethod method = call.resolveMethod();
            CauseItem causeItem = fromMemberNullability(
                nullability,
                method,
                JavaElementKind.METHOD,
                call.getMethodExpression().getReferenceNameElement()
            );
            if (causeItem == null) {
                switch (nullability) {
                    case NULL:
                    case NULLABLE:
                        causeItem = fromCallContract(factUse, call, ContractReturnValue.returnNull());
                        break;
                    case NOT_NULL:
                        causeItem = fromCallContract(factUse, call, ContractReturnValue.returnNotNull());
                        break;
                    default:
                }
            }
            if (causeItem != null) {
                return causeItem;
            }
        }
        if (expression instanceof PsiReferenceExpression refExpr) {
            PsiVariable variable = ObjectUtil.tryCast(refExpr.resolve(), PsiVariable.class);
            if (variable != null) {
                CauseItem causeItem = fromMemberNullability(nullability, variable, JavaElementKind.fromElement(variable), expression);
                if (causeItem != null) {
                    return causeItem;
                }
            }
        }
        FactDefinition<DfaNullability> info = factUse.findFact(factUse.myTopOfStack, FactExtractor.nullability());
        MemoryStateChange factDef = info.myFact == nullability ? info.myChange : null;
        if (nullability == DfaNullability.NOT_NULL) {
            LocalizeValue explanation = getObviouslyNonNullExplanation(expression);
            if (explanation != LocalizeValue.empty()) {
                return new CauseItem(JavaAnalysisLocalize.dfaFindCauseObviouslyNonNullExpression(explanation), expression);
            }
            if (factDef != null) {
                if (factDef.myInstruction instanceof CheckNotNullInstruction checkNotNullInsn) {
                    NullabilityProblemKind.NullabilityProblem<?> problem = checkNotNullInsn.getProblem();
                    PsiExpression dereferenced = problem.getDereferencedExpression();
                    String text = dereferenced == null ? factUse.myTopOfStack.toString() : dereferenced.getText();
                    if (dereferenced != null && problem.getKind() == NullabilityProblemKind.passingToNotNullParameter) {
                        PsiExpression arg = dereferenced;
                        while (arg.getParent() instanceof PsiParenthesizedExpression parenthesized) {
                            arg = parenthesized;
                        }
                        PsiParameter parameter = MethodCallUtils.getParameterForArgument(dereferenced);
                        if (parameter != null) {
                            CauseItem item = new CauseItem(
                                JavaAnalysisLocalize.dfaFindCauseWasPassedAsNonNullParameter(text),
                                dereferenced
                            );
                            item.addChildren(fromMemberNullability(
                                DfaNullability.NOT_NULL,
                                parameter,
                                JavaElementKind.PARAMETER,
                                dereferenced
                            ));
                            return item;
                        }
                    }
                    return new CauseItem(JavaAnalysisLocalize.dfaFindCauseWasDereferenced(text), dereferenced);
                }
                if (factDef.myInstruction instanceof InstanceofInstruction instanceofInsn) {
                    PsiExpression operand = instanceofInsn.getExpression();
                    return new CauseItem(JavaAnalysisLocalize.dfaFindCauseInstanceofImpliesNonNullity(), operand);
                }
            }
        }
        if (factDef != null && expression != null) {
            DfaValue value = factUse.myTopOfStack;
            if (factDef.myInstruction instanceof AssignInstruction assignInsn && factDef.myTopOfStack == value) {
                PsiExpression rExpression = assignInsn.getRExpression();
                if (rExpression != null) {
                    MemoryStateChange rValuePush = factDef.findSubExpressionPush(rExpression);
                    if (rValuePush != null) {
                        CauseItem assignmentItem = createAssignmentCause((AssignInstruction)factDef.myInstruction, value);
                        assignmentItem.addChildren(findNullabilityCause(rValuePush, nullability));
                        return assignmentItem;
                    }
                }
            }
            PsiExpression defExpression = factDef.getExpression();
            if (defExpression != null) {
                return new CauseItem(
                    JavaAnalysisLocalize.dfaFindCauseValueIsKnownFromPlace(
                        expression.getText(),
                        nullability.getPresentationName()
                    ),
                    defExpression
                );
            }
        }
        return null;
    }

    @RequiredReadAction
    private CauseItem fromMemberNullability(
        DfaNullability nullability,
        PsiModifierListOwner owner,
        JavaElementKind memberKind,
        PsiElement anchor
    ) {
        if (owner != null) {
            NullabilityAnnotationInfo info = NullableNotNullManager.getInstance(owner.getProject()).findEffectiveNullabilityInfo(owner);
            String name = ((PsiNamedElement)owner).getName();
            if (info != null && DfaNullability.fromNullability(info.getNullability()) == nullability) {
                LocalizeValue message;
                if (info.isInferred()) {
                    if (owner instanceof PsiParameter
                        && anchor instanceof PsiReferenceExpression refExpr
                        && refExpr.isReferenceTo(owner)) {
                        // Do not use inference inside method itself
                        return null;
                    }
                    message = JavaAnalysisLocalize.dfaFindCauseNullabilityInferred(
                        memberKind.subject(),
                        name,
                        nullability.getPresentationName()
                    );
                }
                else if (info.isExternal()) {
                    message = JavaAnalysisLocalize.dfaFindCauseNullabilityExternallyAnnotated(
                        memberKind.subject(),
                        name,
                        nullability.getPresentationName().get()
                    );
                }
                else if (info.isContainer()) {
                    PsiAnnotationOwner annoOwner = info.getAnnotation().getOwner();
                    message = JavaAnalysisLocalize.dfaFindCauseNullabilityInheritedFromContainer(
                        memberKind.subject(),
                        name,
                        nullability.getPresentationName()
                    );
                    if (annoOwner instanceof PsiModifierList modifierList && modifierList.getParent() instanceof PsiClass aClass) {
                        message = JavaAnalysisLocalize.dfaFindCauseNullabilityInheritedFromClass(
                            memberKind.subject(),
                            name,
                            aClass.getName(),
                            nullability.getPresentationName()
                        );
                        if ("package-info".equals(aClass.getName()) && aClass.getContainingFile() instanceof PsiJavaFile javaFile) {
                            message = JavaAnalysisLocalize.dfaFindCauseNullabilityInheritedFromPackage(
                                memberKind.subject(),
                                name,
                                javaFile.getPackageName(),
                                nullability.getPresentationName()
                            );
                        }
                    }
                    if (annoOwner instanceof PsiNamedElement namedElement) {
                        message = JavaAnalysisLocalize.dfaFindCauseNullabilityInheritedFromNamedElement(
                            memberKind.subject(),
                            name,
                            namedElement.getName(),
                            nullability.getPresentationName()
                        );
                    }
                }
                else {
                    message = JavaAnalysisLocalize.dfaFindCauseNullabilityExplicitlyAnnotated(
                        memberKind.subject(),
                        name,
                        nullability.getPresentationName()
                    );
                }
                if (info.getAnnotation().getContainingFile() == anchor.getContainingFile()) {
                    anchor = info.getAnnotation();
                }
                else if (owner.getContainingFile() == anchor.getContainingFile()) {
                    anchor = owner.getNavigationElement();
                    if (anchor instanceof PsiNameIdentifierOwner nameIdentifierOwner) {
                        anchor = nameIdentifierOwner.getNameIdentifier();
                    }
                }
                return new CauseItem(message, anchor);
            }
            if (owner instanceof PsiField field && getFactory().canTrustFieldInitializer(field)) {
                Pair<PsiExpression, Nullability> fieldNullability =
                    NullabilityUtil.getNullabilityFromFieldInitializers(field, Nullability.UNKNOWN);
                if (fieldNullability.second == DfaNullability.toNullability(nullability)) {
                    PsiExpression initializer = fieldNullability.first;
                    if (initializer != null) {
                        if (initializer.getContainingFile() == anchor.getContainingFile()) {
                            anchor = initializer;
                        }
                        return new CauseItem(
                            JavaAnalysisLocalize.dfaFindCauseFieldInitializerNullability(
                                name,
                                DfaNullability.fromNullability(fieldNullability.second).getPresentationName()
                            ),
                            anchor
                        );
                    }
                    if (field.getContainingFile() == anchor.getContainingFile()) {
                        anchor = field;
                    }
                    return new CauseItem(
                        JavaAnalysisLocalize.dfaFindCauseFieldAssignedNullability(
                            name,
                            DfaNullability.fromNullability(fieldNullability.second).getPresentationName()
                        ),
                        anchor
                    );
                }
            }
        }
        return null;
    }

    @RequiredReadAction
    private CauseItem fromCallContract(MemoryStateChange history, PsiMethodCallExpression call, PsiExpression target) {
        PsiExpression[] args = call.getArgumentList().getExpressions();
        for (int i = 0; i < args.length; i++) {
            if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(args[i], target)) {
                return fromCallContract(history, call, ContractReturnValue.returnParameter(i));
            }
        }
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(qualifier, target)) {
            return fromCallContract(history, call, ContractReturnValue.returnThis());
        }
        return null;
    }

    @RequiredReadAction
    private CauseItem fromCallContract(MemoryStateChange history, PsiCallExpression call, ContractReturnValue contractReturnValue) {
        PsiMethod method = call.resolveMethod();
        if (method == null) {
            return null;
        }
        List<? extends MethodContract> contracts = JavaMethodContractUtil.getMethodCallContracts(method, call);
        if (contracts.isEmpty()) {
            return null;
        }
        MethodContract contract = contracts.get(0);
        if (call instanceof PsiMethodCallExpression methodCall) {
            PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
            String name = methodExpression.getReferenceName();
            if (contracts.size() == 1 && contract.isTrivial() && contractReturnValue.isSuperValueOf(contract.getReturnValue())) {
                LocalizeValue message =
                    JavaAnalysisLocalize.dfaFindCauseContractTrivial(getContractKind(methodCall), name, contract.getReturnValue());
                return new CauseItem(message, methodExpression.getReferenceNameElement());
            }
            List<? extends MethodContract> nonIntersecting = MethodContract.toNonIntersectingContracts(contracts);
            if (nonIntersecting != null) {
                Predicate<MethodContract> condition = contractReturnValue instanceof ContractReturnValue.ParameterReturnValue
                    ? mc -> contractReturnValue.equals(mc.getReturnValue())
                    : mc -> contractReturnValue.isSuperValueOf(mc.getReturnValue());
                MethodContract onlyContract = ContainerUtil.getOnlyItem(ContainerUtil.filter(nonIntersecting, condition));
                if (onlyContract != null) {
                    return fromSingleContract(history, methodCall, method, onlyContract);
                }
            }
            for (MethodContract c : contracts) {
                ThreeState applies = contractApplies(methodCall, c);
                if (applies == ThreeState.NO) {
                    continue;
                }
                if (applies == ThreeState.UNSURE) {
                    break;
                }
                if (applies == ThreeState.YES && contractReturnValue.isSuperValueOf(c.getReturnValue())) {
                    return fromSingleContract(history, methodCall, method, c);
                }
            }
        }
        return null;
    }

    @Nonnull
    private static LocalizeValue getContractKind(PsiCallExpression call) {
        PsiMethod method = call.resolveMethod();
        if (method == null || JavaMethodContractUtil.hasExplicitContractAnnotation(method)) {
            return JavaAnalysisLocalize.dfaFindCauseContractKindExplicit();
        }
        List<? extends MethodContract> contracts = JavaMethodContractUtil.getMethodCallContracts(method, call);
        if (contracts.isEmpty()) {
            return JavaAnalysisLocalize.dfaFindCauseContractKindExplicit();
        }
        if (contracts.stream().allMatch(c -> c instanceof StandardMethodContract)) {
            return JavaAnalysisLocalize.dfaFindCauseContractKindInferred();
        }
        return JavaAnalysisLocalize.dfaFindCauseContractKindHardCoded();
    }

    @Nonnull
    private ThreeState contractApplies(@Nonnull PsiMethodCallExpression call, @Nonnull MethodContract contract) {
        List<ContractValue> conditions = contract.getConditions();
        for (ContractValue condition : conditions) {
            DfaCondition cond = condition.fromCall(getFactory(), call);
            if (cond == DfaCondition.getTrue()) {
                return ThreeState.YES;
            }
            if (cond == DfaCondition.getFalse()) {
                return ThreeState.NO;
            }
        }
        return ThreeState.UNSURE;
    }

    @Nonnull
    @RequiredReadAction
    private CauseItem fromSingleContract(
        @Nonnull MemoryStateChange history,
        @Nonnull PsiMethodCallExpression call,
        @Nonnull PsiMethod method,
        @Nonnull MethodContract contract
    ) {
        List<ContractValue> conditions = contract.getConditions();
        String conditionsText = StringUtil.join(
            conditions,
            c -> c.getPresentationText(method),
            JavaAnalysisLocalize.dfaFindCauseConditionJoiner().get()
        );
        LocalizeValue message;
        if (contract.getReturnValue().isFail()) {
            message =
                JavaAnalysisLocalize.dfaFindCauseContractThrowsOnCondition(getContractKind(call), method.getName(), conditionsText);
        }
        else {
            message = JavaAnalysisLocalize.dfaFindCauseContractReturnsOnCondition(getContractKind(call),
                method.getName(),
                contract.getReturnValue(),
                conditionsText
            );
        }
        CauseItem causeItem = new CauseItem(message, call.getMethodExpression().getReferenceNameElement());
        for (ContractValue contractValue : conditions) {
            if (!(contractValue instanceof ContractValue.Condition)) {
                continue;
            }
            ContractValue.Condition condition = (ContractValue.Condition)contractValue;
            ContractValue leftVal = condition.getLeft();
            ContractValue rightVal = condition.getRight();
            RelationType type = condition.getRelationType();
            DfaCallArguments arguments = DfaCallArguments.fromCall(getFactory(), call);
            PsiExpression leftPlace = leftVal.findPlace(call);
            MemoryStateChange leftPush = history.findSubExpressionPush(leftPlace);
            if (leftPush == null && arguments != null) {
                DfaValue left = leftVal.makeDfaValue(getFactory(), arguments);
                leftPush = MemoryStateChange.create(history.getPrevious(), new PushInstruction(left, null), Collections.emptyMap(), left);
            }
            PsiExpression rightPlace = rightVal.findPlace(call);
            MemoryStateChange rightPush = history.findSubExpressionPush(rightPlace);
            if (rightPush == null && arguments != null) {
                DfaValue right = rightVal.makeDfaValue(getFactory(), arguments);
                rightPush =
                    MemoryStateChange.create(history.getPrevious(), new PushInstruction(right, null), Collections.emptyMap(), right);
            }
            if (leftPush != null && rightPush != null) {
                causeItem.addChildren(findRelationCause(type, leftPush, rightPush));
            }
        }
        return causeItem;
    }

    @RequiredReadAction
    private static CauseItem findRangeCause(MemoryStateChange factUse, DfaValue value, LongRangeSet range, @Nonnull LocalizeValue template) {
        if (value instanceof DfaVariableValue variableValue) {
            if (variableValue.getDescriptor() instanceof SpecialField specialField && range.equals(LongRangeSet.indexRange())) {
                switch (specialField) {
                    case ARRAY_LENGTH:
                        return new CauseItem(JavaAnalysisLocalize.dfaFindCauseArrayLengthIsAlwaysNonNegative(), factUse);
                    case STRING_LENGTH:
                        return new CauseItem(JavaAnalysisLocalize.dfaFindCauseStringLengthIsAlwaysNonNegative(), factUse);
                    case COLLECTION_SIZE:
                        return new CauseItem(JavaAnalysisLocalize.dfaFindCauseCollectionSizeIsAlwaysNonNegative(), factUse);
                    default:
                }
            }
        }
        PsiExpression expression = factUse.myTopOfStack == value ? factUse.getExpression() : null;
        if (expression != null) {
            PsiType type = expression.getType();
            if (expression instanceof PsiLiteralExpression) {
                return null; // Literal range is quite evident
            }
            if (expression instanceof PsiMethodCallExpression call) {
                PsiMethod method = call.resolveMethod();
                if (method != null) {
                    LongRangeSet fromAnnotation = LongRangeSet.fromPsiElement(method);
                    if (fromAnnotation.equals(range)) {
                        return new CauseItem(
                            JavaAnalysisLocalize.dfaFindCauseRangeIsSpecifiedByAnnotation(method.getName(), range),
                            call.getMethodExpression().getReferenceNameElement()
                        );
                    }
                }
            }
            if (expression instanceof PsiTypeCastExpression typeCast
                && type instanceof PsiPrimitiveType primitiveType
                && TypeConversionUtil.isNumericType(type)) {

                PsiExpression operand = typeCast.getOperand();
                MemoryStateChange operandPush = factUse.findExpressionPush(operand);
                if (operandPush != null) {
                    DfaValue castedValue = operandPush.myTopOfStack;
                    FactDefinition<LongRangeSet> operandInfo = operandPush.findFact(castedValue, FactExtractor.range());
                    LongRangeSet operandRange = operandInfo.myFact;
                    LongRangeSet result = operandRange.castTo(primitiveType);
                    if (range.equals(result)) {
                        CauseItem cause = new CauseItem(
                            new RangeDfaProblemType(
                                JavaAnalysisLocalize.dfaFindCauseResultOfPrimitiveCastTemplate(primitiveType.getCanonicalText()),
                                range,
                                null
                            ),
                            expression
                        );
                        if (!operandRange.equals(LongRangeSet.fromType(operand.getType()))) {
                            cause.addChildren(findRangeCause(
                                operandPush,
                                castedValue,
                                operandRange,
                                JavaAnalysisLocalize.dfaFindCauseNumericCastOperandTemplate()
                            ));
                        }
                        return cause;
                    }
                }
            }
            if (range.equals(LongRangeSet.fromType(type))) {
                return null; // Range is any value of given type: no need to explain (except narrowing cast)
            }
            if ((PsiType.LONG.equals(type) || PsiType.INT.equals(type)) && expression instanceof PsiBinaryExpression binOp) {
                boolean isLong = PsiType.LONG.equals(type);
                PsiExpression left = PsiUtil.skipParenthesizedExprDown(binOp.getLOperand());
                PsiExpression right = PsiUtil.skipParenthesizedExprDown(binOp.getROperand());
                MemoryStateChange leftPush = factUse.findExpressionPush(left);
                MemoryStateChange rightPush = factUse.findExpressionPush(right);
                if (leftPush != null && rightPush != null) {
                    DfaValue leftVal = leftPush.myTopOfStack;
                    FactDefinition<LongRangeSet> leftSet = leftPush.findFact(leftVal, FactExtractor.range());
                    DfaValue rightVal = rightPush.myTopOfStack;
                    FactDefinition<LongRangeSet> rightSet = rightPush.findFact(rightVal, FactExtractor.range());
                    LongRangeSet fromType = Objects.requireNonNull(LongRangeSet.fromType(type));
                    LongRangeSet leftRange = leftSet.myFact.intersect(fromType);
                    LongRangeSet rightRange = rightSet.myFact.intersect(fromType);
                    LongRangeBinOp op = LongRangeBinOp.fromToken(binOp.getOperationTokenType());
                    if (op != null) {
                        LongRangeSet result = op.eval(leftRange, rightRange, isLong);
                        if (range.equals(result)) {
                            String sign = binOp.getOperationSign().getText();
                            CauseItem cause = new CauseItem(
                                new RangeDfaProblemType(
                                    JavaAnalysisLocalize.dfaFindCauseResultOfNumericOperationTemplate(sign.equals("%") ? "%%" : sign),
                                    range,
                                    ObjectUtil.tryCast(type, PsiPrimitiveType.class)
                                ),
                                factUse
                            );
                            CauseItem leftCause = null, rightCause = null;
                            if (!leftRange.equals(fromType)) {
                                leftCause = findRangeCause(
                                    leftPush,
                                    leftVal,
                                    leftRange,
                                    JavaAnalysisLocalize.dfaFindCauseLeftOperandRangeTemplate()
                                );
                            }
                            if (!rightRange.equals(fromType)) {
                                rightCause = findRangeCause(
                                    rightPush,
                                    rightVal,
                                    rightRange,
                                    JavaAnalysisLocalize.dfaFindCauseRightOperandRangeTemplate()
                                );
                            }
                            cause.addChildren(leftCause, rightCause);
                            return cause;
                        }
                    }
                }
            }
        }
        PsiPrimitiveType type = expression != null ? ObjectUtil.tryCast(expression.getType(), PsiPrimitiveType.class) : null;
        CauseItem item = new CauseItem(new RangeDfaProblemType(template, range, type), factUse);
        FactDefinition<LongRangeSet> info = factUse.findFact(value, FactExtractor.range());
        MemoryStateChange factDef = range.equals(info.myFact) ? info.myChange : null;
        if (factDef != null) {
            if (factDef.myInstruction instanceof AssignInstruction assignInsn && factDef.myTopOfStack == value) {
                PsiExpression rExpression = assignInsn.getRExpression();
                if (rExpression != null) {
                    MemoryStateChange rValuePush = factDef.findSubExpressionPush(rExpression);
                    if (rValuePush != null) {
                        CauseItem assignmentItem = createAssignmentCause((AssignInstruction)factDef.myInstruction, value);
                        assignmentItem.addChildren(findRangeCause(
                            rValuePush,
                            rValuePush.myTopOfStack,
                            range,
                            JavaAnalysisLocalize.dfaFindCauseNumericRangeGenericTemplate()
                        ));
                        item.addChildren(assignmentItem);
                        return item;
                    }
                }
            }
            PsiExpression defExpression = factDef.getExpression();
            if (defExpression != null) {
                item.addChildren(new CauseItem(JavaAnalysisLocalize.dfaFindCauseRangeIsKnownFromPlace(), defExpression));
            }
        }
        return item;
    }

    @Nonnull
    public static LocalizeValue getObviouslyNonNullExplanation(PsiExpression arg) {
        if (arg == null || ExpressionUtils.isNullLiteral(arg)) {
            return LocalizeValue.empty();
        }
        if (arg instanceof PsiNewExpression) {
            return JavaAnalysisLocalize.dfaFindCauseNonnullExpressionKindNewlyCreatedObject();
        }
        if (arg instanceof PsiLiteralExpression) {
            return JavaAnalysisLocalize.dfaFindCauseNonnullExpressionKindLiteral();
        }
        if (arg.getType() instanceof PsiPrimitiveType) {
            return JavaAnalysisLocalize.dfaFindCauseNonnullExpressionKindPrimitiveType(arg.getType().getCanonicalText());
        }
        if (arg instanceof PsiPolyadicExpression polyadic && polyadic.getOperationTokenType() == JavaTokenType.PLUS) {
            return JavaAnalysisLocalize.dfaFindCauseNonnullExpressionKindConcatenation();
        }
        if (arg instanceof PsiThisExpression) {
            return JavaAnalysisLocalize.dfaFindCauseNonnullExpressionKindThisObject();
        }
        return LocalizeValue.empty();
    }

    private static MemoryStateChange findRelationAddedChange(MemoryStateChange history, DfaVariableValue var, Relation relation) {
        if (relation.myRelationType == RelationType.NE && relation.myCounterpart.getDfType() instanceof DfConstantType) {
            return history.findRelation(
                var,
                rel -> rel.equals(relation)
                    || rel.myRelationType == RelationType.EQ && rel.myCounterpart.getDfType() instanceof DfConstantType,
                true
            );
        }
        MemoryStateChange exact = history.findRelation(
            var,
            rel -> rel.myCounterpart == relation.myCounterpart && relation.myRelationType.equals(rel.myRelationType),
            true
        );
        if (exact != null) {
            return exact;
        }
        return history.findRelation(
            var,
            rel -> rel.myCounterpart == relation.myCounterpart && relation.myRelationType.isSubRelation(rel.myRelationType),
            true
        );
    }
}
