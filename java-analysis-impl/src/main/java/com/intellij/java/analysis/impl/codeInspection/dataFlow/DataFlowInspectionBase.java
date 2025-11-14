// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow;

import com.intellij.java.analysis.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.java.analysis.impl.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.java.analysis.impl.codeInspection.ControlFlowUtils;
import com.intellij.java.analysis.impl.codeInspection.EquivalenceChecker;
import com.intellij.java.analysis.impl.codeInspection.SetInspectionOptionFix;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.NullabilityProblemKind.NullabilityProblem;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.fix.*;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.instructions.InstanceofInstruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfConstantType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaValue;
import com.intellij.java.analysis.impl.codeInspection.nullable.NullableStuffInspectionBase;
import com.intellij.java.language.codeInsight.*;
import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.*;
import com.intellij.java.language.util.JavaPsiConstructorUtil;
import com.siyeh.ig.bugs.EqualsWithItselfInspection;
import com.siyeh.ig.fixes.EqualsToEqualityFix;
import com.siyeh.ig.psiutils.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.Application;
import consulo.application.util.registry.Registry;
import consulo.java.analysis.localize.JavaAnalysisLocalize;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.*;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.intention.BaseIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.SmartList;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ThreeState;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

import static consulo.util.lang.ObjectUtil.tryCast;

public abstract class DataFlowInspectionBase extends AbstractBaseJavaLocalInspectionTool<DataFlowInspectionStateBase> {
    static final Logger LOG = Logger.getInstance(DataFlowInspectionBase.class);

    private static final String SHORT_NAME = "ConstantConditions";
    private static final Set<String> TRUE_OR_FALSE = Set.of("TRUE", "FALSE");

    @Override
    @Nonnull
    public PsiElementVisitor buildVisitorImpl(
        @Nonnull ProblemsHolder holder,
        boolean isOnTheFly,
        LocalInspectionToolSession session,
        DataFlowInspectionStateBase state
    ) {
        return new JavaElementVisitor() {
            @Override
            @RequiredReadAction
            public void visitClass(@Nonnull PsiClass aClass) {
                if (aClass instanceof PsiTypeParameter) {
                    return;
                }
                if (PsiUtil.isLocalOrAnonymousClass(aClass) && !(aClass instanceof PsiEnumConstantInitializer)) {
                    return;
                }

                DataFlowRunner runner = new DataFlowRunner(
                    holder.getProject(),
                    aClass,
                    state.TREAT_UNKNOWN_MEMBERS_AS_NULLABLE,
                    ThreeState.fromBoolean(state.IGNORE_ASSERT_STATEMENTS)
                );
                DataFlowInstructionVisitor visitor =
                    analyzeDfaWithNestedClosures(aClass, holder, runner, Collections.singletonList(runner.createMemoryState()), state);
                List<DfaMemoryState> states = visitor.getEndOfInitializerStates();
                boolean physical = aClass.isPhysical();
                for (PsiMethod method : aClass.getConstructors()) {
                    if (physical && !method.isPhysical()) {
                        // Constructor could be provided by, e.g. Lombok plugin: ignore it, we won't report any problems inside anyway
                        continue;
                    }
                    List<DfaMemoryState> initialStates;
                    PsiMethodCallExpression call = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(method);
                    if (JavaPsiConstructorUtil.isChainedConstructorCall(call)
                        || (call == null && DfaUtil.hasImplicitImpureSuperCall(aClass, method))) {
                        initialStates = Collections.singletonList(runner.createMemoryState());
                    }
                    else {
                        initialStates = StreamEx.of(states).map(DfaMemoryState::createCopy).toList();
                    }
                    analyzeMethod(method, runner, initialStates);
                }
            }

            @Override
            @RequiredReadAction
            public void visitMethod(@Nonnull PsiMethod method) {
                if (method.isConstructor()) {
                    return;
                }
                DataFlowRunner runner = new DataFlowRunner(
                    holder.getProject(),
                    method.getBody(),
                    state.TREAT_UNKNOWN_MEMBERS_AS_NULLABLE,
                    ThreeState.fromBoolean(state.IGNORE_ASSERT_STATEMENTS)
                );
                analyzeMethod(method, runner, Collections.singletonList(runner.createMemoryState()));
            }

            @RequiredReadAction
            private void analyzeMethod(PsiMethod method, DataFlowRunner runner, List<DfaMemoryState> initialStates) {
                PsiCodeBlock scope = method.getBody();
                if (scope == null) {
                    return;
                }
                PsiClass containingClass = PsiTreeUtil.getParentOfType(method, PsiClass.class);
                if (containingClass != null && PsiUtil.isLocalOrAnonymousClass(containingClass)
                    && !(containingClass instanceof PsiEnumConstantInitializer)) {
                    return;
                }

                analyzeDfaWithNestedClosures(scope, holder, runner, initialStates, state);
                analyzeNullLiteralMethodArguments(method, holder, state);
            }

            @Override
            @RequiredReadAction
            public void visitMethodReferenceExpression(@Nonnull PsiMethodReferenceExpression expression) {
                super.visitMethodReferenceExpression(expression);
                if (!state.REPORT_UNSOUND_WARNINGS) {
                    return;
                }
                if (expression.resolve() instanceof PsiMethod method
                    && TypeConversionUtil.isPrimitiveWrapper(method.getReturnType())
                    && NullableNotNullManager.isNullable(method)) {
                    PsiType returnType = LambdaUtil.getFunctionalInterfaceReturnType(expression);
                    if (TypeConversionUtil.isPrimitiveAndNotNull(returnType)) {
                        holder.newProblem(JavaAnalysisLocalize.dataflowMessageUnboxingMethodReference())
                            .range((PsiElement) expression)
                            .create();
                    }
                }
            }

            @Override
            public void visitIfStatement(@Nonnull PsiIfStatement statement) {
                PsiExpression condition = PsiUtil.skipParenthesizedExprDown(statement.getCondition());
                if (BoolUtils.isBooleanLiteral(condition)) {
                    holder.newProblem(JavaAnalysisLocalize.dataflowMessageConstantNoRef(condition.textMatches(PsiKeyword.TRUE) ? 1 : 0))
                        .range(condition)
                        .withFixes(createSimplifyBooleanExpressionFix(condition, condition.textMatches(PsiKeyword.TRUE)))
                        .create();
                }
            }

            @Override
            public void visitWhileStatement(@Nonnull PsiWhileStatement statement) {
                checkLoopCondition(statement.getCondition());
            }

            @Override
            public void visitDoWhileStatement(@Nonnull PsiDoWhileStatement statement) {
                checkLoopCondition(statement.getCondition());
            }

            @Override
            public void visitForStatement(@Nonnull PsiForStatement statement) {
                checkLoopCondition(statement.getCondition());
            }

            private void checkLoopCondition(PsiExpression condition) {
                condition = PsiUtil.skipParenthesizedExprDown(condition);
                if (condition != null && condition.textMatches(PsiKeyword.FALSE)) {
                    holder.newProblem(JavaAnalysisLocalize.dataflowMessageConstantNoRef(0))
                        .range(condition)
                        .withFix(createSimplifyBooleanExpressionFix(condition, false))
                        .create();
                }
            }
        };
    }

    protected LocalQuickFix createNavigateToNullParameterUsagesFix(PsiParameter parameter) {
        return null;
    }

    private void analyzeNullLiteralMethodArguments(PsiMethod method, ProblemsHolder holder, DataFlowInspectionStateBase state) {
        if (state.REPORT_NULLS_PASSED_TO_NOT_NULL_PARAMETER && holder.isOnTheFly()) {
            for (PsiParameter parameter : NullParameterConstraintChecker.checkMethodParameters(method)) {
                PsiIdentifier name = parameter.getNameIdentifier();
                if (name != null) {
                    holder.newProblem(JavaAnalysisLocalize.dataflowMethodFailsWithNullArgument())
                        .range(name)
                        .withFix(createNavigateToNullParameterUsagesFix(parameter))
                        .create();
                }
            }
        }
    }

    @RequiredReadAction
    private DataFlowInstructionVisitor analyzeDfaWithNestedClosures(
        PsiElement scope,
        ProblemsHolder holder,
        DataFlowRunner dfaRunner,
        Collection<? extends DfaMemoryState> initialStates,
        DataFlowInspectionStateBase state
    ) {
        DataFlowInstructionVisitor visitor = new DataFlowInstructionVisitor();
        RunnerResult rc = dfaRunner.analyzeMethod(scope, visitor, initialStates);
        if (rc == RunnerResult.OK) {
            if (dfaRunner.wasForciblyMerged()
                && (Application.get().isUnitTestMode() || Registry.is("ide.dfa.report.imprecise", false))) {
                reportAnalysisQualityProblem(holder, scope, JavaAnalysisLocalize::dataflowNotPrecise);
            }
            createDescription(dfaRunner, holder, visitor, scope, state);
            dfaRunner.forNestedClosures((closure, states) -> analyzeDfaWithNestedClosures(closure, holder, dfaRunner, states, state));
        }
        else if (rc == RunnerResult.TOO_COMPLEX) {
            reportAnalysisQualityProblem(holder, scope, JavaAnalysisLocalize::dataflowTooComplex);
        }
        return visitor;
    }

    private static void reportAnalysisQualityProblem(
        ProblemsHolder holder,
        PsiElement scope,
        Function<Object, LocalizeValue> problemMessageGenerator
    ) {
        PsiIdentifier name = null;
        LocalizeValue message = LocalizeValue.empty();
        if (scope.getParent() instanceof PsiMethod method) {
            name = method.getNameIdentifier();
            message = problemMessageGenerator.apply(JavaAnalysisLocalize.dataflowMethodWithNameTemplate());
        }
        else if (scope instanceof PsiClass psiClass) {
            name = psiClass.getNameIdentifier();
            message = problemMessageGenerator.apply(JavaAnalysisLocalize.dataflowConstructor());
        }
        if (name != null) { // Might be null for synthetic methods like JSP page.
            holder.newProblem(message)
                .range(name)
                .highlightType(ProblemHighlightType.WEAK_WARNING)
                .create();
        }
    }

    @Nonnull
    protected List<LocalQuickFix> createCastFixes(
        PsiTypeCastExpression castExpression,
        PsiType realType,
        boolean onTheFly,
        boolean alwaysFails
    ) {
        return Collections.emptyList();
    }

    @Nonnull
    protected List<LocalQuickFix> createNPEFixes(
        PsiExpression qualifier,
        PsiExpression expression,
        boolean onTheFly,
        DataFlowInspectionStateBase state
    ) {
        return Collections.emptyList();
    }

    protected List<LocalQuickFix> createMethodReferenceNPEFixes(PsiMethodReferenceExpression methodRef, boolean onTheFly) {
        return Collections.emptyList();
    }

    protected
    @Nullable
    LocalQuickFix createUnwrapSwitchLabelFix() {
        return null;
    }

    protected
    @Nullable
    LocalQuickFix createIntroduceVariableFix() {
        return null;
    }

    protected LocalQuickFix createRemoveAssignmentFix(PsiAssignmentExpression assignment) {
        return null;
    }

    protected LocalQuickFix createReplaceWithTrivialLambdaFix(Object value) {
        return null;
    }

    @RequiredReadAction
    private void createDescription(
        DataFlowRunner runner,
        ProblemsHolder holder,
        DataFlowInstructionVisitor visitor,
        PsiElement scope,
        DataFlowInspectionStateBase state
    ) {
        ProblemReporter reporter = new ProblemReporter(holder, scope);

        Map<PsiExpression, ConstantResult> constantExpressions = visitor.getConstantExpressions();
        reportFailingCasts(reporter, visitor, constantExpressions, state);
        reportUnreachableSwitchBranches(visitor.getSwitchLabelsReachability(), holder);

        reportAlwaysFailingCalls(reporter, visitor, state);

        List<NullabilityProblem<?>> problems = NullabilityProblemKind.postprocessNullabilityProblems(visitor.problems().toList());
        reportNullabilityProblems(reporter, problems, constantExpressions, state);

        reportNullableReturns(reporter, problems, constantExpressions, scope, state);

        reportOptionalOfNullableImprovements(reporter, visitor.getOfNullableCalls());

        reportRedundantInstanceOf(runner, visitor, reporter);

        reportConstants(reporter, visitor, state);

        reportMethodReferenceProblems(holder, visitor);

        reportArrayAccessProblems(holder, visitor);

        reportArrayStoreProblems(holder, visitor);

        if (state.REPORT_NULLABLE_METHODS_RETURNING_NOT_NULL && visitor.isAlwaysReturnsNotNull(runner.getInstructions())) {
            reportAlwaysReturnsNotNull(holder, scope);
        }

        reportMutabilityViolations(
            holder,
            visitor.getMutabilityViolations(true),
            JavaAnalysisLocalize.dataflowMessageImmutableModified()
        );
        reportMutabilityViolations(
            holder,
            visitor.getMutabilityViolations(false),
            JavaAnalysisLocalize.dataflowMessageImmutablePassed()
        );

        reportDuplicateAssignments(reporter, visitor);
        reportPointlessSameArguments(reporter, visitor);
    }

    @RequiredReadAction
    private static void reportRedundantInstanceOf(
        DataFlowRunner runner,
        DataFlowInstructionVisitor visitor,
        ProblemReporter reporter
    ) {
        for (Instruction instruction : runner.getInstructions()) {
            if (instruction instanceof InstanceofInstruction instanceOf && visitor.isInstanceofRedundant(instanceOf)) {
                PsiExpression expression = instanceOf.getExpression();
                if (expression != null
                    && (!JavaPsiPatternUtil.getExposedPatternVariables(expression).isEmpty() || shouldBeSuppressed(expression))) {
                    continue;
                }
                reporter.newProblem(JavaAnalysisLocalize.dataflowMessageRedundantInstanceof())
                    .range(expression)
                    .withFix(new RedundantInstanceofFix())
                    .create();
            }
        }
    }

    @RequiredReadAction
    private void reportUnreachableSwitchBranches(Map<PsiExpression, ThreeState> labelReachability, ProblemsHolder holder) {
        Set<PsiSwitchBlock> coveredSwitches = new HashSet<>();

        for (Map.Entry<PsiExpression, ThreeState> entry : labelReachability.entrySet()) {
            if (entry.getValue() != ThreeState.YES) {
                continue;
            }
            PsiExpression label = entry.getKey();
            PsiSwitchLabelStatementBase labelStatement = Objects.requireNonNull(PsiImplUtil.getSwitchLabel(label));
            PsiSwitchBlock statement = labelStatement.getEnclosingSwitchBlock();
            if (statement == null || !canRemoveUnreachableBranches(labelStatement, statement)) {
                continue;
            }
            if (!StreamEx.iterate(
                    labelStatement,
                    Objects::nonNull,
                    l -> PsiTreeUtil.getPrevSiblingOfType(l, PsiSwitchLabelStatementBase.class)
                )
                .skip(1).map(PsiSwitchLabelStatementBase::getCaseValues)
                .nonNull().flatArray(PsiExpressionList::getExpressions)
                .append(StreamEx.iterate(label, Objects::nonNull, l -> PsiTreeUtil.getPrevSiblingOfType(l, PsiExpression.class)).skip(1))
                .allMatch(l -> labelReachability.get(l) == ThreeState.NO)) {
                continue;
            }
            coveredSwitches.add(statement);
            holder.newProblem(JavaAnalysisLocalize.dataflowMessageOnlySwitchLabel())
                .range(label)
                .withFix(createUnwrapSwitchLabelFix())
                .create();
        }
        for (Map.Entry<PsiExpression, ThreeState> entry : labelReachability.entrySet()) {
            if (entry.getValue() != ThreeState.NO) {
                continue;
            }
            PsiExpression label = entry.getKey();
            PsiSwitchLabelStatementBase labelStatement = Objects.requireNonNull(PsiImplUtil.getSwitchLabel(label));
            if (!coveredSwitches.contains(labelStatement.getEnclosingSwitchBlock())) {
                holder.newProblem(JavaAnalysisLocalize.dataflowMessageUnreachableSwitchLabel())
                    .range(label)
                    .withFix(new DeleteSwitchLabelFix(label))
                    .create();
            }
        }
    }

    @RequiredReadAction
    private static boolean canRemoveUnreachableBranches(PsiSwitchLabelStatementBase labelStatement, PsiSwitchBlock statement) {
        if (Objects.requireNonNull(labelStatement.getCaseValues()).getExpressionCount() != 1) {
            return true;
        }
        List<PsiSwitchLabelStatementBase> allBranches =
            PsiTreeUtil.getChildrenOfTypeAsList(statement.getBody(), PsiSwitchLabelStatementBase.class);
        if (statement instanceof PsiSwitchStatement) {
            // Cannot do anything if we have already single branch and we cannot restore flow due to non-terminal breaks
            return allBranches.size() != 1 || BreakConverter.from(statement) != null;
        }
        // Expression switch: if we cannot unwrap existing branch and the other one is default case, we cannot kill it either
        return (allBranches.size() <= 2
            && !allBranches.stream().allMatch(branch -> branch == labelStatement || branch.isDefaultCase()))
            || (labelStatement instanceof PsiSwitchLabeledRuleStatement switchLabeledRuleStatement
            && switchLabeledRuleStatement.getBody() instanceof PsiExpressionStatement);
    }

    private void reportConstants(ProblemReporter reporter, DataFlowInstructionVisitor visitor, DataFlowInspectionStateBase state) {
        visitor.getConstantExpressionChunks().forEach((chunk, result) -> {
            if (result == ConstantResult.UNKNOWN) {
                return;
            }
            PsiExpression expression = chunk.myExpression;
            if (chunk.myRange != null) {
                if (result.value() instanceof Boolean booleanValue) {
                    // report rare cases like a == b == c where "a == b" part is constant
                    reporter.myHolder.newProblem(JavaAnalysisLocalize.dataflowMessageConstantCondition(booleanValue ? 1 : 0))
                        .range(expression, chunk.myRange)
                        .create();
                    // do not add to reported anchors if only part of expression was reported
                }
                return;
            }
            if (!isCondition(expression)) {
                reportConstantReferenceValue(reporter, expression, result, state);
            }
            else if (result.value() instanceof Boolean booleanValue) {
                reportConstantBoolean(reporter, expression, booleanValue, state);
            }
        });
    }

    private static boolean isCondition(@Nonnull PsiExpression expression) {
        PsiType type = expression.getType();
        if (type == null || !PsiType.BOOLEAN.isAssignableFrom(type)) {
            return false;
        }
        if (!(expression instanceof PsiMethodCallExpression) && !(expression instanceof PsiReferenceExpression)) {
            return true;
        }
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
        if (parent instanceof PsiStatement) {
            return !(parent instanceof PsiReturnStatement);
        }
        if (parent instanceof PsiPolyadicExpression polyadic) {
            IElementType tokenType = polyadic.getOperationTokenType();
            return JavaTokenType.ANDAND.equals(tokenType) || JavaTokenType.OROR.equals(tokenType)
                || JavaTokenType.AND.equals(tokenType) || JavaTokenType.OR.equals(tokenType);
        }
        if (parent instanceof PsiConditionalExpression conditional) {
            return PsiTreeUtil.isAncestor(conditional.getCondition(), expression, false);
        }
        return PsiUtil.isAccessedForWriting(expression);
    }

    @RequiredReadAction
    private void reportConstantReferenceValue(
        ProblemReporter reporter,
        PsiExpression ref,
        ConstantResult constant,
        DataFlowInspectionStateBase state
    ) {
        if (!state.REPORT_CONSTANT_REFERENCE_VALUES && ref instanceof PsiReferenceExpression) {
            return;
        }
        if (shouldBeSuppressed(ref) || constant == ConstantResult.UNKNOWN) {
            return;
        }
        if (Integer.valueOf(0).equals(constant.value()) && !shouldReportZero(ref)) {
            return;
        }
        boolean isAssertion = isAssertionEffectively(ref, constant);
        if (isAssertion && state.DONT_REPORT_TRUE_ASSERT_STATEMENTS) {
            return;
        }
        String presentableName = constant.toString();
        ProblemBuilder pBuilder;
        if (ref instanceof PsiMethodCallExpression || ref instanceof PsiPolyadicExpression) {
            pBuilder = reporter.newProblem(JavaAnalysisLocalize.dataflowMessageConstantExpression(presentableName));
        }
        else {
            pBuilder = reporter.newProblem(JavaAnalysisLocalize.dataflowMessageConstantValue(presentableName))
                .highlightType(ProblemHighlightType.WEAK_WARNING);
        }
        pBuilder.range(ref);
        if (constant.value() instanceof Boolean booleanValue) {
            pBuilder.withOptionalFix(createSimplifyBooleanExpressionFix(ref, booleanValue));
        }
        else {
            pBuilder.withFix(new ReplaceWithConstantValueFix(presentableName, presentableName));
        }
        Object value = constant.value();
        if (value instanceof Boolean booleanValue) {
            pBuilder.withOptionalFix(createReplaceWithNullCheckFix(ref, booleanValue));
        }
        if (reporter.isOnTheFly()) {
            if (ref instanceof PsiReferenceExpression) {
                pBuilder.withFix(new SetInspectionOptionFix<>(
                    this,
                    (i, v) -> i.REPORT_CONSTANT_REFERENCE_VALUES = v,
                    JavaAnalysisLocalize.inspectionDataFlowTurnOffConstantReferencesQuickfix(),
                    false
                ));
            }
            if (isAssertion) {
                pBuilder.withFix(new SetInspectionOptionFix<>(
                    this,
                    (i, v) -> i.DONT_REPORT_TRUE_ASSERT_STATEMENTS = v,
                    JavaAnalysisLocalize.inspectionDataFlowTurnOffTrueAssertsQuickfix(),
                    true
                ));
            }
        }
        if (reporter.isOnTheFly()) {
            pBuilder.withOptionalFix(createExplainFix(ref, new TrackingRunner.ValueDfaProblemType(value), state));
        }

        pBuilder.create();
    }

    private static boolean shouldReportZero(PsiExpression ref) {
        if (ref instanceof PsiPolyadicExpression polyadic) {
            if (PsiUtil.isConstantExpression(polyadic)) {
                return false;
            }
            IElementType tokenType = polyadic.getOperationTokenType();
            if (tokenType.equals(JavaTokenType.ASTERISK)) {
                PsiMethod method = PsiTreeUtil.getParentOfType(polyadic, PsiMethod.class, true, PsiLambdaExpression.class, PsiClass.class);
                if (MethodUtils.isHashCode(method)) {
                    // Standard hashCode template generates int result = 0; result = result * 31 + ...;
                    // so annoying warnings might be produced there
                    return false;
                }
            }
        }
        else if (ref instanceof PsiMethodCallExpression call) {
            PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
            if (PsiUtil.isConstantExpression(qualifier)
                && ContainerUtil.and(call.getArgumentList().getExpressions(), PsiUtil::isConstantExpression)) {
                return false;
            }
        }
        else {
            return false;
        }
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(ref.getParent());
        PsiBinaryExpression binOp = tryCast(parent, PsiBinaryExpression.class);
        return binOp == null || !ComparisonUtils.isEqualityComparison(binOp)
            || (!ExpressionUtils.isZero(binOp.getLOperand()) && !ExpressionUtils.isZero(binOp.getROperand()));
    }

    private static void reportPointlessSameArguments(ProblemReporter reporter, DataFlowInstructionVisitor visitor) {
        visitor.pointlessSameArguments().forKeyValue((expr, eq) -> {
            PsiElement name = expr.getReferenceNameElement();
            if (name == null) {
                return;
            }
            PsiExpression[] expressions = PsiExpression.EMPTY_ARRAY;
            if (expr.getParent() instanceof PsiMethodCallExpression call) {
                expressions = call.getArgumentList().getExpressions();
                if (expressions.length == 2
                    && PsiUtil.isConstantExpression(expressions[0])
                    && PsiUtil.isConstantExpression(expressions[1])
                    && !EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(expressions[0], expressions[1])) {
                    return;
                }
            }
            if (eq.firstArgEqualToResult) {
                LocalizeValue message = eq.argsEqual
                    ? JavaAnalysisLocalize.dataflowMessagePointlessSameArguments()
                    : JavaAnalysisLocalize.dataflowMessagePointlessSameArgumentAndResult(1);
                reporter.newProblem(message)
                    .range(name)
                    .withOptionalFix(expressions.length == 2 ? new ReplaceWithArgumentFix(expressions[0], 0) : null)
                    .create();
            }
            else if (eq.argsEqual) {
                reporter.newProblem(JavaAnalysisLocalize.dataflowMessagePointlessSameArguments())
                    .range(name)
                    .create();
            }
            else if (eq.secondArgEqualToResult) {
                reporter.newProblem(JavaAnalysisLocalize.dataflowMessagePointlessSameArgumentAndResult(2))
                    .range(name)
                    .withOptionalFix(expressions.length == 2 ? new ReplaceWithArgumentFix(expressions[1], 1) : null)
                    .create();
            }
        });
    }

    @RequiredReadAction
    private void reportDuplicateAssignments(ProblemReporter reporter, DataFlowInstructionVisitor visitor) {
        visitor.sameValueAssignments().forEach(expr -> {
            expr = PsiUtil.skipParenthesizedExprDown(expr);
            if (expr == null) {
                return;
            }
            PsiAssignmentExpression assignment = PsiTreeUtil.getParentOfType(expr, PsiAssignmentExpression.class);
            PsiElement context = PsiTreeUtil.getParentOfType(expr, PsiForStatement.class, PsiClassInitializer.class);
            if (context instanceof PsiForStatement forStatement && PsiTreeUtil.isAncestor(forStatement.getInitialization(), expr, true)) {
                return;
            }
            if (context instanceof PsiClassInitializer classInitializer
                && expr instanceof PsiReferenceExpression ref
                && assignment != null) {
                Object constValue = ExpressionUtils.computeConstantExpression(assignment.getRExpression());
                if (constValue == PsiTypesUtil.getDefaultValue(expr.getType())
                    && ref.resolve() instanceof PsiField field
                    && (field.isStatic() || ExpressionUtil.isEffectivelyUnqualified(ref))
                    && field.getContainingClass() == classInitializer.getContainingClass()) {
                    return;
                }
            }
            LocalizeValue message = assignment != null && !JavaTokenType.EQ.equals(assignment.getOperationTokenType())
                ? JavaAnalysisLocalize.dataflowMessageRedundantUpdate()
                : JavaAnalysisLocalize.dataflowMessageRedundantAssignment();
            reporter.newProblem(message)
                .range(expr)
                .withFix(createRemoveAssignmentFix(assignment))
                .create();
        });
    }

    private void reportMutabilityViolations(ProblemsHolder holder, Set<PsiElement> violations, @Nonnull LocalizeValue message) {
        for (PsiElement violation : violations) {
            holder.newProblem(message)
                .range(violation)
                .withFix(createMutabilityViolationFix(violation, holder.isOnTheFly()))
                .create();
        }
    }

    protected LocalQuickFix createMutabilityViolationFix(PsiElement violation, boolean onTheFly) {
        return null;
    }

    @RequiredReadAction
    protected void reportNullabilityProblems(
        ProblemReporter reporter,
        List<NullabilityProblem<?>> problems,
        Map<PsiExpression, ConstantResult> expressions,
        DataFlowInspectionStateBase state
    ) {
        for (NullabilityProblem<?> problem : problems) {
            PsiExpression expression = problem.getDereferencedExpression();
            if (!state.REPORT_UNSOUND_WARNINGS) {
                if (expression == null) {
                    continue;
                }
                PsiExpression unwrapped = PsiUtil.skipParenthesizedExprDown(expression);
                if (!ExpressionUtils.isNullLiteral(unwrapped) && expressions.get(expression) != ConstantResult.NULL) {
                    continue;
                }
            }
            NullabilityProblemKind.innerClassNPE.ifMyProblem(
                problem,
                newExpression -> reporter.newProblem(problem.getMessage(expressions))
                    .range(getElementToHighlight(newExpression))
                    .withFixes(createNPEFixes(newExpression.getQualifier(), newExpression, reporter.isOnTheFly(), state))
                    .create()
            );
            NullabilityProblemKind.callMethodRefNPE.ifMyProblem(
                problem,
                methodRef -> reporter.newProblem(JavaAnalysisLocalize.dataflowMessageNpeMethodrefInvocation())
                    .range((PsiElement) methodRef)
                    .withFixes(createMethodReferenceNPEFixes(methodRef, reporter.isOnTheFly()))
                    .create()
            );
            NullabilityProblemKind.callNPE.ifMyProblem(
                problem,
                call -> reportCallMayProduceNpe(reporter, problem.getMessage(expressions), call, state)
            );
            NullabilityProblemKind.passingToNotNullParameter.ifMyProblem(
                problem,
                expr -> reporter.newProblem(problem.getMessage(expressions))
                    .range(expression)
                    .withFixes(createNPEFixes(expression, expression, reporter.isOnTheFly(), state))
                    .create()
            );
            NullabilityProblemKind.passingToNotNullMethodRefParameter.ifMyProblem(
                problem,
                methodRef -> reporter.newProblem(JavaAnalysisLocalize.dataflowMessagePassingNullableArgumentMethodref())
                    .range((PsiElement) methodRef)
                    .withFixes(createMethodReferenceNPEFixes(methodRef, reporter.isOnTheFly()))
                    .create()
            );
            NullabilityProblemKind.unboxingMethodRefParameter.ifMyProblem(
                problem,
                methodRef -> reporter.newProblem(JavaAnalysisLocalize.dataflowMessageUnboxingNullableArgumentMethodref())
                    .range((PsiElement) methodRef)
                    .withFixes(createMethodReferenceNPEFixes(methodRef, reporter.isOnTheFly()))
                    .create()
            );
            NullabilityProblemKind.arrayAccessNPE.ifMyProblem(
                problem,
                arrayAccess -> reporter.newProblem(problem.getMessage(expressions))
                    .range(arrayAccess)
                    .withFixes(createNPEFixes(
                        arrayAccess.getArrayExpression(),
                        arrayAccess,
                        reporter.isOnTheFly(),
                        state
                    ))
                    .create()
            );
            NullabilityProblemKind.fieldAccessNPE.ifMyProblem(
                problem,
                element -> {
                    PsiExpression fieldAccess = element.getParent() instanceof PsiReferenceExpression refExpr ? refExpr : element;
                    reporter.newProblem(problem.getMessage(expressions))
                        .range(element)
                        .withFixes(createNPEFixes(element, fieldAccess, reporter.isOnTheFly(), state))
                        .create();
                }
            );
            NullabilityProblemKind.unboxingNullable.ifMyProblem(
                problem,
                element -> {
                    PsiExpression anchor = expression;
                    if (anchor instanceof PsiTypeCastExpression typeCastExpression && anchor.getType() instanceof PsiPrimitiveType) {
                        anchor = Objects.requireNonNull(typeCastExpression.getOperand());
                    }
                    reporter.newProblem(problem.getMessage(expressions))
                        .range(anchor)
                        .create();
                }
            );
            NullabilityProblemKind.nullableFunctionReturn.ifMyProblem(
                problem,
                expr -> reporter.newProblem(problem.getMessage(expressions))
                    .range(expression == null ? expr : expression)
                    .create()
            );
            Consumer<PsiExpression> reportNullability = expr -> reportNullabilityProblem(reporter, problem, expression, expressions, state);
            NullabilityProblemKind.assigningToNotNull.ifMyProblem(problem, reportNullability);
            NullabilityProblemKind.storingToNotNullArray.ifMyProblem(problem, reportNullability);
            if (state.SUGGEST_NULLABLE_ANNOTATIONS) {
                NullabilityProblemKind.passingToNonAnnotatedMethodRefParameter.ifMyProblem(
                    problem,
                    methodRef -> reporter.newProblem(problem.getMessage(expressions))
                        .range((PsiElement) methodRef)
                        .create()
                );
                NullabilityProblemKind.passingToNonAnnotatedParameter.ifMyProblem(
                    problem,
                    top -> reportNullableArgumentsPassedToNonAnnotated(reporter, problem.getMessage(expressions), expression, top, state)
                );
                NullabilityProblemKind.assigningToNonAnnotatedField.ifMyProblem(
                    problem,
                    top -> reportNullableAssignedToNonAnnotatedField(reporter, top, expression, problem.getMessage(expressions), state)
                );
            }
        }
    }

    private void reportNullabilityProblem(
        ProblemReporter reporter,
        NullabilityProblem<?> problem,
        PsiExpression expr,
        Map<PsiExpression, ConstantResult> expressions, DataFlowInspectionStateBase state
    ) {
        reporter.newProblem(problem.getMessage(expressions))
            .range(expr)
            .withFixes(createNPEFixes(expr, expr, reporter.isOnTheFly(), state))
            .create();
    }

    private static void reportArrayAccessProblems(ProblemsHolder holder, DataFlowInstructionVisitor visitor) {
        visitor.outOfBoundsArrayAccesses().forEach(access -> {
            PsiExpression indexExpression = access.getIndexExpression();
            if (indexExpression != null) {
                holder.newProblem(JavaAnalysisLocalize.dataflowMessageArrayIndexOutOfBounds())
                    .range(indexExpression)
                    .create();
            }
        });
    }

    private static void reportArrayStoreProblems(ProblemsHolder holder, DataFlowInstructionVisitor visitor) {
        visitor.getArrayStoreProblems().forEach(
            (assignment, types) ->
                holder.newProblem(JavaAnalysisLocalize.dataflowMessageArraystore(
                        types.getFirst().getCanonicalText(),
                        types.getSecond().getCanonicalText()
                    ))
                    .range(assignment.getOperationSign())
                    .create()
        );
    }

    private void reportMethodReferenceProblems(ProblemsHolder holder, DataFlowInstructionVisitor visitor) {
        visitor.getMethodReferenceResults().forEach((methodRef, result) -> {
            if (result != ConstantResult.UNKNOWN) {
                Object value = result.value();
                holder.newProblem(JavaAnalysisLocalize.dataflowMessageConstantMethodReference(value))
                    .range((PsiElement) methodRef)
                    .withFix(createReplaceWithTrivialLambdaFix(value))
                    .create();
            }
        });
    }

    @RequiredReadAction
    private void reportAlwaysReturnsNotNull(ProblemsHolder holder, PsiElement scope) {
        if (!(scope.getParent() instanceof PsiMethod method && !PsiUtil.canBeOverridden(method))) {
            return;
        }

        NullabilityAnnotationInfo info = NullableNotNullManager.getInstance(scope.getProject()).findOwnNullabilityInfo(method);
        if (info == null || info.getNullability() != Nullability.NULLABLE) {
            return;
        }

        PsiAnnotation annotation = info.getAnnotation();
        if (!annotation.isPhysical() || alsoAppliesToInternalSubType(annotation, method)) {
            return;
        }

        PsiJavaCodeReferenceElement annoName = annotation.getNameReferenceElement();
        assert annoName != null;
        holder.newProblem(JavaAnalysisLocalize.dataflowMessageReturnNotnullFromNullable(
                NullableStuffInspectionBase.getPresentableAnnoName(annotation),
                method.getName()
            ))
            .range((PsiElement) annoName)
            .withOptionalFix(AddAnnotationPsiFix.createAddNotNullFix(method))
            .withOptionalFix(
                holder.isOnTheFly()
                    ? new SetInspectionOptionFix<>(
                        this,
                        (i, v) -> i.REPORT_NULLABLE_METHODS_RETURNING_NOT_NULL = v,
                        JavaAnalysisLocalize.inspectionDataFlowTurnOffNullableReturningNotnullQuickfix(),
                        false
                    )
                    : null
            )
            .create();
    }

    @RequiredReadAction
    private static boolean alsoAppliesToInternalSubType(PsiAnnotation annotation, PsiMethod method) {
        return AnnotationTargetUtil.isTypeAnnotation(annotation) && method.getReturnType() instanceof PsiArrayType;
    }

    private void reportAlwaysFailingCalls(ProblemReporter reporter, DataFlowInstructionVisitor visitor, DataFlowInspectionStateBase state) {
        visitor.alwaysFailingCalls()
            .remove(TestUtils::isExceptionExpected)
            .forEach(call -> reporter.newProblem(getContractMessage(JavaMethodContractUtil.getMethodCallContracts(call)))
                .range(getElementToHighlight(call))
                .withOptionalFix(
                    reporter.isOnTheFly() ? createExplainFix(call, new TrackingRunner.FailingCallDfaProblemType(), state) : null
                )
                .create()
            );
    }

    @Nonnull
    private static LocalizeValue getContractMessage(List<? extends MethodContract> contracts) {
        return contracts.stream().allMatch(mc -> mc.getConditions().stream().allMatch(ContractValue::isBoundCheckingCondition))
            ? JavaAnalysisLocalize.dataflowMessageContractFailIndex()
            : JavaAnalysisLocalize.dataflowMessageContractFail();
    }

    @Nonnull
    private static PsiElement getElementToHighlight(@Nonnull PsiCall call) {
        PsiJavaCodeReferenceElement ref;
        if (call instanceof PsiNewExpression newExpression) {
            ref = newExpression.getClassReference();
        }
        else if (call instanceof PsiMethodCallExpression methodCall) {
            ref = methodCall.getMethodExpression();
        }
        else {
            return call;
        }
        if (ref != null) {
            PsiElement name = ref.getReferenceNameElement();
            return name != null ? name : ref;
        }
        return call;
    }

    private static void reportOptionalOfNullableImprovements(ProblemReporter reporter, Map<PsiElement, ThreeState> nullArgs) {
        nullArgs.forEach((anchor, alwaysPresent) -> {
            if (alwaysPresent == ThreeState.UNSURE) {
                return;
            }
            if (alwaysPresent.toBoolean()) {
                reporter.newProblem(JavaAnalysisLocalize.dataflowMessagePassingNonNullArgumentToOptional())
                    .range(anchor)
                    .withOptionalFix(DfaOptionalSupport.createReplaceOptionalOfNullableWithOfFix(anchor))
                    .create();
            }
            else {
                reporter.newProblem(JavaAnalysisLocalize.dataflowMessagePassingNullArgumentToOptional())
                    .range(anchor)
                    .withOptionalFix(DfaOptionalSupport.createReplaceOptionalOfNullableWithEmptyFix(anchor))
                    .create();
            }
        });
    }

    @RequiredReadAction
    private void reportNullableArgumentsPassedToNonAnnotated(
        ProblemReporter reporter,
        @Nonnull LocalizeValue message,
        PsiExpression expression,
        PsiExpression top,
        DataFlowInspectionStateBase state
    ) {
        PsiParameter parameter = MethodCallUtils.getParameterForArgument(top);
        if (parameter != null && BaseIntentionAction.canModify(parameter) && AnnotationUtil.isAnnotatingApplicable(parameter)) {
            reporter.newProblem(message)
                .range(expression)
                .withFixes(createNPEFixes(expression, top, reporter.isOnTheFly(), state))
                .withOptionalFix(AddAnnotationPsiFix.createAddNullableFix(parameter))
                .create();
        }
    }

    @RequiredReadAction
    private void reportNullableAssignedToNonAnnotatedField(
        ProblemReporter reporter,
        PsiExpression top,
        PsiExpression expression,
        @Nonnull LocalizeValue message,
        DataFlowInspectionStateBase state
    ) {
        PsiField field = getAssignedField(top);
        if (field != null) {
            reporter.newProblem(message)
                .range(expression)
                .withFixes(createNPEFixes(expression, top, reporter.isOnTheFly(), state))
                .withOptionalFix(AddAnnotationPsiFix.createAddNullableFix(field))
                .create();
        }
    }

    @Nullable
    @RequiredReadAction
    private static PsiField getAssignedField(PsiElement assignedValue) {
        if (PsiUtil.skipParenthesizedExprUp(assignedValue.getParent()) instanceof PsiAssignmentExpression assignment) {
            PsiElement target = assignment.getLExpression() instanceof PsiReferenceExpression refExpr ? refExpr.resolve() : null;
            return tryCast(target, PsiField.class);
        }
        return null;
    }

    private void reportCallMayProduceNpe(
        ProblemReporter reporter,
        @Nonnull LocalizeValue message,
        PsiMethodCallExpression callExpression,
        DataFlowInspectionStateBase state
    ) {
        PsiReferenceExpression methodExpression = callExpression.getMethodExpression();

        reporter.newProblem(message)
            .range(getElementToHighlight(callExpression))
            .withFixes(createNPEFixes(methodExpression.getQualifierExpression(), callExpression, reporter.isOnTheFly(), state))
            .withOptionalFix(ReplaceWithObjectsEqualsFix.createFix(callExpression, methodExpression))
            .create();
    }

    private void reportFailingCasts(
        @Nonnull ProblemReporter reporter,
        @Nonnull DataFlowInstructionVisitor visitor,
        @Nonnull Map<PsiExpression, ConstantResult> constantExpressions,
        DataFlowInspectionStateBase state
    ) {
        visitor.getFailingCastExpressions().forKeyValue((typeCast, info) -> {
            boolean alwaysFails = info.getFirst();
            PsiType realType = info.getSecond();
            if (!state.REPORT_UNSOUND_WARNINGS && !alwaysFails) {
                return;
            }
            PsiExpression operand = typeCast.getOperand();
            PsiTypeElement castType = typeCast.getCastType();
            ConstantResult result = constantExpressions.get(PsiUtil.skipParenthesizedExprDown(operand));
            // Skip reporting if cast operand is always null: null can be cast to anything
            if (result == ConstantResult.NULL || ExpressionUtils.isNullLiteral(operand)) {
                return;
            }
            assert castType != null;
            assert operand != null;
            String text = PsiExpressionTrimRenderer.render(operand);
            LocalizeValue message = alwaysFails
                ? JavaAnalysisLocalize.dataflowMessageCceAlways(text)
                : JavaAnalysisLocalize.dataflowMessageCce(text);
            reporter.newProblem(message)
                .range(castType)
                .withFixes(createCastFixes(typeCast, realType, reporter.isOnTheFly(), alwaysFails))
                .withOptionalFix(reporter.isOnTheFly() ? createExplainFix(typeCast, new TrackingRunner.CastDfaProblemType(), state) : null)
                .create();
        });
    }

    @RequiredReadAction
    private void reportConstantBoolean(
        ProblemReporter reporter,
        PsiElement psiAnchor,
        boolean evaluatesToTrue,
        DataFlowInspectionStateBase state
    ) {
        while (psiAnchor instanceof PsiParenthesizedExpression parenthesized) {
            psiAnchor = parenthesized.getExpression();
        }
        if (psiAnchor == null || shouldBeSuppressed(psiAnchor)) {
            return;
        }
        boolean isAssertion = isAssertionEffectively(psiAnchor, evaluatesToTrue);
        if (state.DONT_REPORT_TRUE_ASSERT_STATEMENTS && isAssertion) {
            return;
        }

        PsiElement parent = PsiUtil.skipParenthesizedExprUp(psiAnchor.getParent());
        if (parent instanceof PsiAssignmentExpression assignment
            && PsiTreeUtil.isAncestor(assignment.getLExpression(), psiAnchor, false)) {
            reporter.newProblem(JavaAnalysisLocalize.dataflowMessagePointlessAssignmentExpression(Boolean.toString(evaluatesToTrue)))
                .range(psiAnchor)
                .withFixes(createConditionalAssignmentFixes(evaluatesToTrue, assignment, reporter.isOnTheFly()))
                .create();
            return;
        }

        LocalizeValue message = isAtRHSOfBooleanAnd(psiAnchor)
            ? JavaAnalysisLocalize.dataflowMessageConstantConditionWhenReached(evaluatesToTrue ? 1 : 0)
            : JavaAnalysisLocalize.dataflowMessageConstantCondition(evaluatesToTrue ? 1 : 0);
        ProblemBuilder pBuilder = reporter.newProblem(message).range(psiAnchor);
        if (!isCoveredBySurroundingFix(psiAnchor, evaluatesToTrue)) {
            pBuilder.withOptionalFix(createSimplifyBooleanExpressionFix(psiAnchor, evaluatesToTrue));
            if (isAssertion && reporter.isOnTheFly()) {
                pBuilder.withFix(new SetInspectionOptionFix<>(
                    this,
                    (i, v) -> i.DONT_REPORT_TRUE_ASSERT_STATEMENTS = v,
                    JavaAnalysisLocalize.inspectionDataFlowTurnOffTrueAssertsQuickfix(),
                    true
                ));
            }
            pBuilder.withOptionalFix(createReplaceWithNullCheckFix(psiAnchor, evaluatesToTrue));
        }
        if (reporter.isOnTheFly() && psiAnchor instanceof PsiExpression expr) {
            pBuilder.withOptionalFix(createExplainFix(expr, new TrackingRunner.ValueDfaProblemType(evaluatesToTrue), state));
        }
        pBuilder.create();
    }

    @Nullable
    protected LocalQuickFix createExplainFix(
        PsiExpression anchor,
        TrackingRunner.DfaProblemType problemType,
        DataFlowInspectionStateBase state
    ) {
        return null;
    }

    private static boolean isCoveredBySurroundingFix(PsiElement anchor, boolean evaluatesToTrue) {
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(anchor.getParent());
        if (parent instanceof PsiPolyadicExpression polyadic) {
            IElementType tokenType = polyadic.getOperationTokenType();
            return JavaTokenType.ANDAND.equals(tokenType) && !evaluatesToTrue
                || JavaTokenType.OROR.equals(tokenType) && evaluatesToTrue;
        }
        return parent instanceof PsiExpression expr && BoolUtils.isNegation(expr);
    }

    @Contract("null -> false")
    @RequiredReadAction
    private static boolean shouldBeSuppressed(PsiElement anchor) {
        if (!(anchor instanceof PsiExpression expression)) {
            return false;
        }
        // Don't report System.out.println(b = false) or doSomething((Type)null)
        if (anchor instanceof PsiAssignmentExpression || anchor instanceof PsiTypeCastExpression) {
            return true;
        }
        // For conditional the root cause (constant condition or both branches constant) should be already reported for branches
        if (anchor instanceof PsiConditionalExpression) {
            return true;
        }
        if (expression instanceof PsiReferenceExpression ref
            && TRUE_OR_FALSE.contains(ref.getReferenceName())
            && ref.resolve() instanceof PsiField field) {
            PsiClass containingClass = field.getContainingClass();
            if (containingClass != null && CommonClassNames.JAVA_LANG_BOOLEAN.equals(containingClass.getQualifiedName())) {
                return true;
            }
        }
        if (expression instanceof PsiInstanceOfExpression instanceOfExpression) {
            PsiType type = instanceOfExpression.getOperand().getType();
            if (type == null || !TypeConstraints.instanceOf(type).isResolved()) {
                return true;
            }
        }
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
        // Don't report "x" in "x == null" as will be anyways reported as "always true"
        if (parent instanceof PsiBinaryExpression binaryExpression && ExpressionUtils.getValueComparedWithNull(binaryExpression) != null) {
            return true;
        }
        // Dereference of null will be covered by other warning
        if (ExpressionUtils.isVoidContext(expression) || isDereferenceContext(expression)) {
            return true;
        }
        // We assume all Void variables as null because you cannot instantiate it without dirty hacks
        // However reporting them as "always null" looks redundant (dereferences or comparisons will be reported though).
        if (TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_VOID, expression.getType())) {
            return true;
        }
        if (isFlagCheck(anchor)) {
            return true;
        }
        boolean condition = isCondition(expression);
        if (!condition && expression instanceof PsiReferenceExpression referenceExpression) {
            PsiVariable variable = tryCast(referenceExpression.resolve(), PsiVariable.class);
            return variable instanceof PsiField field && field.isStatic()
                && ExpressionUtils.isNullLiteral(field.getInitializer())
                || variable instanceof PsiLocalVariable localVar && localVar.hasModifierProperty(PsiModifier.FINAL)
                && PsiUtil.isCompileTimeConstant(localVar);
        }
        if (!condition && expression instanceof PsiMethodCallExpression methodCall) {
            List<? extends MethodContract> contracts = JavaMethodContractUtil.getMethodCallContracts(methodCall);
            ContractReturnValue value = JavaMethodContractUtil.getNonFailingReturnValue(contracts);
            if (value != null) {
                return true;
            }
            if (!(parent instanceof PsiAssignmentExpression)
                && !(parent instanceof PsiVariable)
                && !(parent instanceof PsiReturnStatement)) {
                PsiMethod method = methodCall.resolveMethod();
                if (method == null || !JavaMethodContractUtil.isPure(method)) {
                    return true;
                }
            }
        }
        while (expression != null && BoolUtils.isNegation(expression)) {
            expression = BoolUtils.getNegated(expression);
        }
        PsiMethodCallExpression call = tryCast(expression, PsiMethodCallExpression.class);
        // Reported by "Equals with itself" inspection; avoid double reporting
        return call != null && EqualsWithItselfInspection.isEqualsWithItself(call);
    }

    private static boolean isDereferenceContext(PsiExpression ref) {
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(ref.getParent());
        return parent instanceof PsiReferenceExpression || parent instanceof PsiArrayAccessExpression
            || parent instanceof PsiSwitchStatement || parent instanceof PsiSynchronizedStatement;
    }

    private static LocalQuickFix createReplaceWithNullCheckFix(PsiElement psiAnchor, boolean evaluatesToTrue) {
        if (evaluatesToTrue) {
            return null;
        }
        if (!(psiAnchor instanceof PsiMethodCallExpression methodCallExpression)) {
            return null;
        }
        if (!MethodCallUtils.isEqualsCall(methodCallExpression)) {
            return null;
        }
        PsiExpression arg = ArrayUtil.getFirstElement(methodCallExpression.getArgumentList().getExpressions());
        if (!ExpressionUtils.isNullLiteral(arg)) {
            return null;
        }
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(psiAnchor.getParent());
        return EqualsToEqualityFix.buildFix(
            methodCallExpression,
            parent instanceof PsiExpression expr && BoolUtils.isNegation(expr)
        );
    }

    protected LocalQuickFix[] createConditionalAssignmentFixes(boolean evaluatesToTrue, PsiAssignmentExpression parent, boolean onTheFly) {
        return LocalQuickFix.EMPTY_ARRAY;
    }

    @Nullable
    private static PsiMethod getScopeMethod(PsiElement block) {
        PsiElement parent = block.getParent();
        if (parent instanceof PsiMethod method) {
            return method;
        }
        if (parent instanceof PsiLambdaExpression lambda) {
            return LambdaUtil.getFunctionalInterfaceMethod(lambda.getFunctionalInterfaceType());
        }
        return null;
    }

    @RequiredReadAction
    private void reportNullableReturns(
        ProblemReporter reporter,
        List<NullabilityProblem<?>> problems,
        Map<PsiExpression, ConstantResult> expressions,
        @Nonnull PsiElement block, DataFlowInspectionStateBase state
    ) {
        PsiMethod method = getScopeMethod(block);
        if (method == null) {
            return;
        }
        NullableNotNullManager manager = NullableNotNullManager.getInstance(method.getProject());
        NullabilityAnnotationInfo info = manager.findEffectiveNullabilityInfo(method);
        if (info == null) {
            info = DfaPsiUtil.getTypeNullabilityInfo(PsiTypesUtil.getMethodReturnType(block));
        }
        PsiAnnotation anno = info == null ? null : info.getAnnotation();
        Nullability nullability = info == null ? Nullability.UNKNOWN : info.getNullability();
        if (nullability == Nullability.NULLABLE) {
            if (!AnnotationUtil.isInferredAnnotation(anno)) {
                return;
            }
            if (DfaPsiUtil.getTypeNullability(method.getReturnType()) == Nullability.NULLABLE) {
                return;
            }
        }

        if (nullability != Nullability.NOT_NULL && (!state.SUGGEST_NULLABLE_ANNOTATIONS || block.getParent() instanceof PsiLambdaExpression)) {
            return;
        }

        PsiType returnType = method.getReturnType();
        // no warnings in void lambdas, where the expression is not returned anyway
        if (block instanceof PsiExpression && block.getParent() instanceof PsiLambdaExpression && PsiType.VOID.equals(returnType)) {
            return;
        }

        // no warnings for Void methods, where only null can be possibly returned
        if (returnType == null || returnType.equalsToText(CommonClassNames.JAVA_LANG_VOID)) {
            return;
        }

        StreamEx<NullabilityProblem<PsiExpression>> nullabilityProblems =
            StreamEx.of(problems).map(NullabilityProblemKind.nullableReturn::asMyProblem).nonNull();
        for (NullabilityProblem<PsiExpression> problem : nullabilityProblems) {
            PsiExpression anchor = problem.getAnchor();
            PsiExpression expr = problem.getDereferencedExpression();

            boolean exactlyNull = isNullLiteralExpression(expr) || expressions.get(expr) == ConstantResult.NULL;
            if (!state.REPORT_UNSOUND_WARNINGS && !exactlyNull) {
                continue;
            }
            if (nullability == Nullability.NOT_NULL) {
                String presentable = NullableStuffInspectionBase.getPresentableAnnoName(anno);
                LocalizeValue text = exactlyNull
                    ? JavaAnalysisLocalize.dataflowMessageReturnNullFromNotnull(presentable)
                    : JavaAnalysisLocalize.dataflowMessageReturnNullableFromNotnull(presentable);
                reporter.newProblem(text)
                    .range(expr)
                    .withFixes(createNPEFixes(expr, expr, reporter.isOnTheFly(), state))
                    .create();
            }
            else if (AnnotationUtil.isAnnotatingApplicable(anchor)) {
                String defaultNullable = manager.getDefaultNullable();
                String presentableNullable = StringUtil.getShortName(defaultNullable);
                LocalizeValue text = exactlyNull
                    ? JavaAnalysisLocalize.dataflowMessageReturnNullFromNotnullable(presentableNullable)
                    : JavaAnalysisLocalize.dataflowMessageReturnNullableFromNotnullable(presentableNullable);
                PsiMethod surroundingMethod = PsiTreeUtil.getParentOfType(anchor, PsiMethod.class, true, PsiLambdaExpression.class);
                reporter.newProblem(text)
                    .range(expr)
                    .withOptionalFix(
                        surroundingMethod == null
                            ? null
                            : new AddAnnotationPsiFix(defaultNullable, surroundingMethod, ArrayUtil.toStringArray(manager.getNotNulls()))
                    )
                    .create();
            }
        }
    }

    private static boolean isAssertionEffectively(@Nonnull PsiElement anchor, ConstantResult result) {
        Object value = result.value();
        if (value instanceof Boolean booleanValue) {
            return isAssertionEffectively(anchor, booleanValue);
        }
        return value == null && isAssertCallArgument(anchor, ContractValue.nullValue());
    }

    private static boolean isAssertionEffectively(@Nonnull PsiElement anchor, boolean evaluatesToTrue) {
        PsiElement parent;
        while (true) {
            parent = anchor.getParent();
            if (parent instanceof PsiExpression expression && BoolUtils.isNegation(expression)) {
                evaluatesToTrue = !evaluatesToTrue;
                anchor = parent;
                continue;
            }
            if (parent instanceof PsiParenthesizedExpression) {
                anchor = parent;
                continue;
            }
            if (parent instanceof PsiPolyadicExpression polyadicExpression) {
                IElementType tokenType = polyadicExpression.getOperationTokenType();
                if (tokenType.equals(JavaTokenType.ANDAND) || tokenType.equals(JavaTokenType.OROR)) {
                    // always true operand makes always true OR-chain and does not affect the result of AND-chain
                    // Note that in `assert unknownExpression && trueExpression;` the trueExpression should not be reported
                    // because this assert is essentially the shortened `assert unknownExpression; assert trueExpression;`
                    // which is not reported.
                    boolean causesShortCircuit = (tokenType.equals(JavaTokenType.OROR) == evaluatesToTrue) &&
                        ArrayUtil.getLastElement(((PsiPolyadicExpression) parent).getOperands()) != anchor;
                    if (!causesShortCircuit) {
                        // We still report `assert trueExpression || unknownExpression`, because here `unknownExpression` is never checked
                        // which is probably not intended.
                        anchor = parent;
                        continue;
                    }
                }
            }
            break;
        }
        if (parent instanceof PsiAssertStatement) {
            return evaluatesToTrue;
        }
        if (parent instanceof PsiIfStatement && anchor == ((PsiIfStatement) parent).getCondition()) {
            PsiStatement thenBranch = ControlFlowUtils.stripBraces(((PsiIfStatement) parent).getThenBranch());
            if (thenBranch instanceof PsiThrowStatement) {
                return !evaluatesToTrue;
            }
        }
        return isAssertCallArgument(anchor, ContractValue.booleanValue(evaluatesToTrue));
    }

    private static boolean isAssertCallArgument(@Nonnull PsiElement anchor, @Nonnull ContractValue wantedConstraint) {
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(anchor.getParent());
        if (parent instanceof PsiExpressionList expressionList) {
            int index = ArrayUtil.indexOf(expressionList.getExpressions(), anchor);
            if (index >= 0) {
                PsiMethodCallExpression call = tryCast(parent.getParent(), PsiMethodCallExpression.class);
                if (call != null) {
                    MethodContract contract = ContainerUtil.getOnlyItem(JavaMethodContractUtil.getMethodCallContracts(call));
                    if (contract != null && contract.getReturnValue().isFail()) {
                        ContractValue condition = ContainerUtil.getOnlyItem(contract.getConditions());
                        if (condition != null) {
                            return condition.getArgumentComparedTo(wantedConstraint, false).orElse(-1) == index;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean isAtRHSOfBooleanAnd(PsiElement expr) {
        PsiElement cur = expr;

        while (cur != null && !(cur instanceof PsiMember)) {
            PsiElement parent = cur.getParent();

            if (parent instanceof PsiBinaryExpression && cur == ((PsiBinaryExpression) parent).getROperand()) {
                return true;
            }

            cur = parent;
        }

        return false;
    }

    private static boolean isFlagCheck(PsiElement element) {
        PsiElement scope = PsiTreeUtil.getParentOfType(element, PsiStatement.class, PsiVariable.class);
        PsiExpression topExpression = scope instanceof PsiIfStatement ifStatement
            ? ifStatement.getCondition()
            : scope instanceof PsiVariable variable
            ? variable.getInitializer()
            : null;
        return PsiTreeUtil.isAncestor(topExpression, element, false)
            && StreamEx.<PsiElement>ofTree(topExpression, e -> StreamEx.of(e.getChildren()))
            .anyMatch(DataFlowInspectionBase::isCompileTimeFlagCheck);
    }

    @RequiredReadAction
    private static boolean isCompileTimeFlagCheck(PsiElement element) {
        if (element instanceof PsiBinaryExpression binOp && ComparisonUtils.isComparisonOperation(binOp.getOperationTokenType())) {
            PsiExpression comparedWith = null;
            if (ExpressionUtils.isLiteral(binOp.getROperand())) {
                comparedWith = binOp.getLOperand();
            }
            else if (ExpressionUtils.isLiteral(binOp.getLOperand())) {
                comparedWith = binOp.getROperand();
            }
            comparedWith = PsiUtil.skipParenthesizedExprDown(comparedWith);
            if (isConstantOfType(comparedWith, PsiType.INT, PsiType.LONG)) {
                // like "if (DEBUG_LEVEL > 2)"
                return true;
            }
            if (comparedWith instanceof PsiBinaryExpression subOp && subOp.getOperationTokenType().equals(JavaTokenType.AND)) {
                PsiExpression left = PsiUtil.skipParenthesizedExprDown(subOp.getLOperand());
                PsiExpression right = PsiUtil.skipParenthesizedExprDown(subOp.getROperand());
                if (isConstantOfType(left, PsiType.INT, PsiType.LONG)
                    || isConstantOfType(right, PsiType.INT, PsiType.LONG)) {
                    // like "if ((FLAGS & SOME_FLAG) != 0)"
                    return true;
                }
            }
        }
        // like "if (DEBUG)"
        return isConstantOfType(element, PsiType.BOOLEAN);
    }

    @RequiredReadAction
    private static boolean isConstantOfType(PsiElement element, PsiPrimitiveType... types) {
        PsiElement resolved = element instanceof PsiReferenceExpression refExpr ? refExpr.resolve() : null;
        return resolved instanceof PsiField field
            && field.isStatic()
            && PsiUtil.isCompileTimeConstant((PsiVariable) field)
            && ArrayUtil.contains(field.getType(), types);
    }

    private static boolean isNullLiteralExpression(PsiElement expr) {
        return expr instanceof PsiExpression expression && ExpressionUtils.isNullLiteral(expression);
    }

    private
    @Nullable
    LocalQuickFix createSimplifyBooleanExpressionFix(PsiElement element, final boolean value) {
        LocalQuickFixOnPsiElement fix = createSimplifyBooleanFix(element, value);
        if (fix == null) {
            return null;
        }
        final LocalizeValue text = fix.getText();
        return new LocalQuickFix() {
            @Nonnull
            @Override
            public LocalizeValue getName() {
                return text;
            }

            @Override
            @RequiredReadAction
            public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
                PsiElement psiElement = descriptor.getPsiElement();
                if (psiElement == null) {
                    return;
                }
                LocalQuickFixOnPsiElement fix = createSimplifyBooleanFix(psiElement, value);
                if (fix == null) {
                    return;
                }
                try {
                    LOG.assertTrue(psiElement.isValid());
                    fix.applyFix();
                }
                catch (IncorrectOperationException e) {
                    LOG.error(e);
                }
            }
        };
    }

    @Nonnull
    protected static LocalQuickFix createSimplifyToAssignmentFix() {
        return new SimplifyToAssignmentFix();
    }

    protected LocalQuickFixOnPsiElement createSimplifyBooleanFix(PsiElement element, boolean value) {
        return null;
    }

    @Override
    @Nonnull
    public LocalizeValue getGroupDisplayName() {
        return InspectionLocalize.groupNamesProbableBugs();
    }

    @Override
    public
    @Nonnull
    String getShortName() {
        return SHORT_NAME;
    }

    protected enum ConstantResult {
        TRUE(Boolean.TRUE),
        FALSE(Boolean.FALSE),
        NULL(null),
        ZERO(0) {
            @Nonnull
            @Override
            public String toString() {
                return "0";
            }
        },
        UNKNOWN(Void.TYPE);

        private final Object myValue;

        ConstantResult(Object value) {
            myValue = value;
        }

        public Object value() {
            return myValue;
        }

        @Nonnull
        @Override
        public String toString() {
            return StringUtil.toLowerCase(name());
        }

        @Nonnull
        static ConstantResult fromDfType(@Nonnull DfType dfType) {
            if (dfType == DfTypes.NULL) {
                return NULL;
            }
            if (dfType == DfTypes.TRUE) {
                return TRUE;
            }
            if (dfType == DfTypes.FALSE) {
                return FALSE;
            }
            if (DfConstantType.isConst(dfType, 0) || DfConstantType.isConst(dfType, 0L)) {
                return ZERO;
            }
            return UNKNOWN;
        }

        @Nonnull
        static ConstantResult mergeValue(@Nullable ConstantResult state, @Nonnull DfaMemoryState memState, @Nullable DfaValue value) {
            if (state == UNKNOWN || value == null) {
                return UNKNOWN;
            }
            ConstantResult nextState = fromDfType(memState.getUnboxedDfType(value));
            return state == null || state == nextState ? nextState : UNKNOWN;
        }
    }

    /**
     * {@link ProblemsHolder} wrapper to avoid reporting two problems on the same anchor
     */
    protected static class ProblemReporter {
        private class MyProblemBuilder extends ProblemBuilderWrapper {
            private Boolean registered = null;

            private MyProblemBuilder(@Nonnull ProblemBuilder subBuilder) {
                super(subBuilder);
            }

            @Nonnull
            @Override
            public ProblemBuilder range(@Nonnull PsiElement element) {
                checkRegistered(element);
                return super.range(element);
            }

            @Override
            public void create() {
                if (registered == null) {
                    throw new IllegalStateException("Range was not set");
                }
                else if (registered == Boolean.TRUE) {
                    super.create();
                }
            }

            private void checkRegistered(@Nonnull PsiElement element) {
                if (registered != null) {
                    throw new IllegalStateException("Range was already set");
                }
                registered = register(element);
            }
        }

        private final Set<PsiElement> myReportedAnchors = new HashSet<>();
        private final ProblemsHolder myHolder;
        private final PsiElement myScope;

        ProblemReporter(ProblemsHolder holder, PsiElement scope) {
            myHolder = holder;
            myScope = scope;
        }

        public ProblemBuilder newProblem(@Nonnull LocalizeValue message) {
            return new MyProblemBuilder(myHolder.newProblem(message));
        }

        private boolean register(PsiElement element) {
            // Suppress reporting for inlined simple methods
            if (!PsiTreeUtil.isAncestor(myScope, element, false)) {
                return false;
            }
            if (myScope instanceof PsiClass) {
                PsiMember member = PsiTreeUtil.getParentOfType(element, PsiMember.class);
                if (member instanceof PsiMethod method && !method.isConstructor()) {
                    return false;
                }
            }
            if (!myReportedAnchors.add(element)) {
                return false;
            }
            if (element instanceof PsiParenthesizedExpression parenthesizedExpression) {
                PsiExpression deparenthesized = PsiUtil.skipParenthesizedExprDown(parenthesizedExpression);
                if (deparenthesized != null) {
                    myReportedAnchors.add(deparenthesized);
                }
            }
            return true;
        }

        boolean isOnTheFly() {
            return myHolder.isOnTheFly();
        }
    }
}
