// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection;

import com.intellij.java.analysis.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.java.analysis.impl.psi.controlFlow.AllVariablesControlFlowPolicy;
import com.intellij.java.language.JavaFeature;
import com.intellij.java.language.impl.codeInsight.BlockUtils;
import com.intellij.java.language.impl.psi.controlFlow.*;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.StatementExtractor;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.analysis.impl.localize.JavaInspectionsLocalize;
import consulo.language.editor.inspection.*;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import one.util.streamex.StreamEx;

import java.util.*;

@ExtensionImpl
public final class EnhancedSwitchMigrationInspection extends AbstractBaseJavaLocalInspectionTool<EnhancedSwitchMigrationInspectionState> {
    private static final SwitchConversion[] ourInspections = new SwitchConversion[]{
        EnhancedSwitchMigrationInspection::inspectReturningSwitch,
        EnhancedSwitchMigrationInspection::inspectVariableAssigningSwitch,
        (statement, branches, isExhaustive, maxNumberStatementsForExpression) ->
            inspectReplacementWithStatement(statement, branches)
    };

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Nonnull
    @Override
    public InspectionToolState<? extends EnhancedSwitchMigrationInspectionState> createStateProvider() {
        return new EnhancedSwitchMigrationInspectionState();
    }

    @Nonnull
    @Override
    public Set<JavaFeature> requiredFeatures() {
        return Set.of(JavaFeature.ENHANCED_SWITCH);
    }

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return JavaInspectionsLocalize.groupNamesLanguageLevelSpecificIssuesAndMigrationAids14();
    }

    @Nonnull
    @Override
    public PsiElementVisitor buildVisitorImpl(
        @Nonnull ProblemsHolder holder,
        boolean isOnTheFly,
        LocalInspectionToolSession localInspectionToolSession,
        EnhancedSwitchMigrationInspectionState state
    ) {
        return new JavaElementVisitor() {
            @Override
            @RequiredReadAction
            public void visitSwitchExpression(@Nonnull PsiSwitchExpression expression) {
                PsiElement switchKeyword = expression.getFirstChild();
                if (switchKeyword == null) {
                    return;
                }
                PsiCodeBlock body = expression.getBody();
                if (body == null) {
                    return;
                }
                boolean onlyOneYieldAfterLabel = true;
                boolean isOldSwitchWithoutRule = true;
                int statementAfterLabelCount = 0;
                PsiStatement[] statements = body.getStatements();
                if (statements.length == 0) {
                    return;
                }
                for (PsiStatement statement : statements) {
                    if (statement instanceof PsiSwitchLabeledRuleStatement) {
                        isOldSwitchWithoutRule = false;
                        break;
                    }
                    if (statement instanceof PsiSwitchLabelStatement) {
                        statementAfterLabelCount = 0;
                    }
                    else {
                        statementAfterLabelCount++;
                        if (!(statement instanceof PsiYieldStatement || statement instanceof PsiThrowStatement)) {
                            onlyOneYieldAfterLabel = false;
                        }
                        else if (statementAfterLabelCount > 1) {
                            onlyOneYieldAfterLabel = false;
                        }
                    }
                }
                if (!isOldSwitchWithoutRule) {
                    return;
                }
                ProblemHighlightType warningType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
                if (!onlyOneYieldAfterLabel) {
                    warningType = ProblemHighlightType.INFORMATION;
                }
                holder.newProblem(JavaInspectionsLocalize.inspectionSwitchExpressionMigrationInspectionSwitchExpressionDescription())
                    .range(switchKeyword)
                    .withFix(new ReplaceExpressionWithEnhancedSwitchExpressionFix())
                    .highlightType(warningType)
                    .create();
            }

            @Override
            @RequiredReadAction
            public void visitSwitchStatement(@Nonnull PsiSwitchStatement statement) {
                PsiElement switchKeyword = statement.getFirstChild();
                if (switchKeyword == null) {
                    return;
                }
                List<SwitchReplacer> replacers = findSwitchReplacers(statement, state.myMaxNumberStatementsForBranch);
                if (replacers.isEmpty()) {
                    return;
                }
                Optional<SwitchReplacer> replacerWithWarningLevel = replacers.stream().filter(this::isWarningLevel).findFirst();
                if (replacerWithWarningLevel.isPresent()) {
                    SwitchReplacer replacer = replacerWithWarningLevel.get();
                    List<LocalQuickFix> fixes = new ArrayList<>();
                    fixes.add(new ReplaceWithSwitchExpressionFix(replacer.getType()));
                    if (!state.myWarnOnlyOnExpressionConversion && replacer.getType() == ReplacementType.Statement) {
                        fixes.add(new UpdateInspectionOptionFix<EnhancedSwitchMigrationInspection, EnhancedSwitchMigrationInspectionState>(
                            EnhancedSwitchMigrationInspection.this,
                            JavaInspectionsLocalize.inspectionSwitchExpressionMigrationWarnOnlyOnExpression(),
                            state -> state.myWarnOnlyOnExpressionConversion = true
                        ));
                    }
                    if (replacer.getType() == ReplacementType.Expression &&
                        replacer.getMaxNumberStatementsInBranch() != null &&
                        replacer.getMaxNumberStatementsInBranch() > 1) {
                        int newMaxValue = replacer.getMaxNumberStatementsInBranch() - 1;

                        fixes.add(new UpdateInspectionOptionFix<EnhancedSwitchMigrationInspection, EnhancedSwitchMigrationInspectionState>(
                                EnhancedSwitchMigrationInspection.this,
                                JavaInspectionsLocalize.inspectionSwitchExpressionMigrationOptionExpressionMaxStatements(newMaxValue),
                                state -> state.myMaxNumberStatementsForBranch = newMaxValue
                            )
                        );
                    }
                    holder.newProblem(JavaInspectionsLocalize.inspectionSwitchExpressionMigrationInspectionSwitchDescription())
                        .range(switchKeyword)
                        .withFixes(fixes)
                        .create();
                    replacers.remove(replacer);
                }
                if (!holder.isOnTheFly()) {
                    return;
                }
                if (replacers.isEmpty()) {
                    return;
                }
                List<LocalQuickFix> fixes =
                    ContainerUtil.map(replacers, replacer -> new ReplaceWithSwitchExpressionFix(replacer.getType()));
                holder.newProblem(JavaInspectionsLocalize.inspectionSwitchExpressionMigrationInspectionSwitchDescription())
                    .range(switchKeyword)
                    .highlightType(ProblemHighlightType.INFORMATION)
                    .withFixes(fixes)
                    .create();
            }

            private boolean isWarningLevel(@Nonnull SwitchReplacer replacer) {
                if (replacer.isInformLevel()) {
                    return false;
                }
                return !(state.myWarnOnlyOnExpressionConversion && replacer.getType() == ReplacementType.Statement);
            }
        };
    }

    private static List<SwitchReplacer> runInspections(
        @Nonnull PsiStatement statement,
        boolean isExhaustive,
        @Nonnull List<OldSwitchStatementBranch> branches,
        int maxNumberStatementsForExpression
    ) {
        List<SwitchReplacer> replacers = new ArrayList<>();
        for (SwitchConversion inspection : ourInspections) {
            SwitchReplacer replacer = inspection.suggestReplacer(statement, branches, isExhaustive, maxNumberStatementsForExpression);
            if (replacer != null) {
                replacers.add(replacer);
            }
        }
        return replacers;
    }

    private static OldSwitchStatementBranch addBranch(
        List<? super OldSwitchStatementBranch> branches,
        PsiStatement[] statements,
        int unmatchedCaseIndex,
        int endIndexExcl,
        boolean isFallthrough, PsiBreakStatement current
    ) {
        PsiSwitchLabelStatement labelStatement = (PsiSwitchLabelStatement) statements[unmatchedCaseIndex];
        PsiStatement[] branchStatements = Arrays.copyOfRange(statements, unmatchedCaseIndex + 1, endIndexExcl);
        OldSwitchStatementBranch branch = new OldSwitchStatementBranch(isFallthrough, branchStatements, labelStatement, current);
        branches.add(branch);
        return branch;
    }

    private static @Nullable List<OldSwitchStatementBranch> extractBranches(
        @Nonnull PsiCodeBlock body,
        PsiSwitchStatement switchStatement
    ) {
        List<OldSwitchStatementBranch> branches = new ArrayList<>();
        PsiStatement[] statements = body.getStatements();
        int unmatchedCaseIndex = -1;
        OldSwitchStatementBranch previousBranch = null;
        for (int i = 0, length = statements.length; i < length; i++) {
            PsiStatement current = statements[i];
            if (current instanceof PsiSwitchLabelStatement) {
                if (unmatchedCaseIndex != -1) {
                    boolean isFallthrough = i != 0 && ControlFlowUtils.statementMayCompleteNormally(statements[i - 1]);
                    OldSwitchStatementBranch newBranch = addBranch(branches, statements, unmatchedCaseIndex, i, isFallthrough, null);
                    newBranch.myPreviousSwitchBranch = previousBranch;
                    previousBranch = newBranch;
                }
                unmatchedCaseIndex = i;
            }
            else if (current instanceof PsiBreakStatement breakStmt) {
                if (breakStmt.findExitedStatement() != switchStatement) {
                    return null;
                }
                if (unmatchedCaseIndex == -1) {
                    return null;
                }
                OldSwitchStatementBranch newBranch = addBranch(branches, statements, unmatchedCaseIndex, i, false, breakStmt);
                newBranch.myPreviousSwitchBranch = previousBranch;
                previousBranch = newBranch;
                unmatchedCaseIndex = -1;
            }
            else if (current instanceof PsiSwitchLabeledRuleStatement) {
                return null;
            }
        }
        // tail
        if (unmatchedCaseIndex != -1) {
            OldSwitchStatementBranch branch = addBranch(branches, statements, unmatchedCaseIndex, statements.length, false, null);
            branch.myPreviousSwitchBranch = previousBranch;
        }
        return branches;
    }

    /**
     * Before using this method, make sure you are using a correct version of Java.
     */
    public static @Nullable SwitchReplacer findSwitchReplacer(@Nonnull PsiSwitchStatement switchStatement) {
        List<SwitchReplacer> replacers = findSwitchReplacers(switchStatement, 1);
        for (SwitchReplacer replacer : replacers) {
            if (!replacer.isInformLevel()) {
                return replacer;
            }
        }
        return null;
    }

    private static @Nonnull List<SwitchReplacer> findSwitchReplacers(
        @Nonnull PsiSwitchStatement switchStatement,
        int maxNumberStatementsForExpression
    ) {
        PsiExpression expression = switchStatement.getExpression();
        if (expression == null) {
            return List.of();
        }
        PsiCodeBlock body = switchStatement.getBody();
        if (body == null) {
            return List.of();
        }
        List<OldSwitchStatementBranch> branches = extractBranches(body, switchStatement);
        if (branches == null || branches.isEmpty()) {
            return List.of();
        }
        boolean isExhaustive = isExhaustiveSwitch(branches, switchStatement);
        return runInspections(switchStatement, isExhaustive, branches, maxNumberStatementsForExpression);
    }

    @RequiredReadAction
    private static @Nullable PsiSwitchBlock generateEnhancedSwitch(
        @Nonnull PsiStatement statementToReplace,
        List<SwitchBranch> newBranches,
        CommentTracker ct,
        boolean isExpr
    ) {
        if (!(statementToReplace instanceof PsiSwitchStatement switchStmt)) {
            return null;
        }
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(switchStmt.getProject());
        PsiCodeBlock body = switchStmt.getBody();
        if (body == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (PsiElement e = switchStmt.getFirstChild(); e != null && e != body; e = e.getNextSibling()) {
            sb.append(ct.text(e));
        }
        PsiJavaToken lBrace = body.getLBrace();
        sb.append(lBrace != null ? ct.textWithComments(lBrace) : "{");
        for (SwitchBranch newBranch : newBranches) {
            sb.append(newBranch.generate(ct));
        }
        PsiJavaToken rBrace = body.getRBrace();
        sb.append(rBrace != null ? ct.textWithComments(rBrace) : "}");
        PsiSwitchBlock switchBlock;
        if (isExpr) {
            switchBlock = (PsiSwitchBlock) factory.createExpressionFromText(sb.toString(), switchStmt);
        }
        else {
            switchBlock = (PsiSwitchBlock) factory.createStatementFromText(sb.toString(), switchStmt);
        }
        StreamEx.ofTree((PsiElement) switchBlock, block -> Arrays.stream(block.getChildren()))
            .select(PsiBreakStatement.class)
            .filter(
                breakStmt -> ControlFlowUtils.statementCompletesWithStatement(switchBlock, breakStmt)
                    && breakStmt.findExitedStatement() == switchBlock
            )
            .forEach(statement -> new CommentTracker().delete(statement));
        return switchBlock;
    }

    private static boolean isExhaustiveSwitch(List<OldSwitchStatementBranch> branches, PsiSwitchStatement switchStatement) {
        for (OldSwitchStatementBranch branch : branches) {
            if (branch.isDefault()) {
                return true;
            }
            if (existsDefaultLabelElement(branch.myLabelStatement)) {
                return true;
            }
        }
        SwitchUtils.SwitchExhaustivenessState completenessResult = SwitchUtils.evaluateSwitchCompleteness(switchStatement, true);
        return completenessResult == SwitchUtils.SwitchExhaustivenessState.EXHAUSTIVE_CAN_ADD_DEFAULT
            || completenessResult == SwitchUtils.SwitchExhaustivenessState.EXHAUSTIVE_NO_DEFAULT;
    }

    private static boolean isConvertibleBranch(@Nonnull OldSwitchStatementBranch branch, boolean hasNext) {
        int length = branch.getStatements().length;
        if (length == 0) {
            return (branch.isFallthrough() && hasNext) || (!branch.isFallthrough() && branch.isDefault());
        }
        return !branch.isFallthrough();
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return JavaInspectionsLocalize.inspectionSwitchExpressionMigrationInspectionSwitchDescription();
    }

    private enum ReplacementType {
        Expression(JavaInspectionsLocalize.inspectionReplaceWithSwitchExpressionFixName()),
        Statement(JavaInspectionsLocalize.inspectionReplaceWithEnhancedSwitchStatementFixName());
        private final LocalizeValue key;

        ReplacementType(LocalizeValue key) {
            this.key = key;
        }

        LocalizeValue getFixName() {
            return key;
        }
    }

    public interface SwitchReplacer {
        void replace(@Nonnull PsiStatement switchStatement);

        ReplacementType getType();

        boolean isInformLevel();

        //if null, it is not applicable
        @Nullable
        Integer getMaxNumberStatementsInBranch();
    }

    private interface SwitchConversion {
        @Nullable
        SwitchReplacer suggestReplacer(
            @Nonnull PsiStatement statement,
            @Nonnull List<OldSwitchStatementBranch> branches,
            boolean isExhaustive,
            int maxNumberStatementsForExpression
        );
    }

    //Right part of switch rule (case labels -> result)
    private interface SwitchRuleResult {
        String generate(CommentTracker ct, SwitchBranch branch);
    }

    private static class ReplaceExpressionWithEnhancedSwitchExpressionFix implements LocalQuickFix {

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return JavaInspectionsLocalize.inspectionReplaceWithSwitchRuleExpressionFixFamilyName();
        }

        @Override
        @RequiredWriteAction
        public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor problemDescriptor) {
            PsiElement element = problemDescriptor.getPsiElement();

            StringBuilder builder = new StringBuilder();
            if (!(element.getParent() instanceof PsiSwitchExpression switchExpression)) {
                return;
            }
            for (@Nonnull PsiElement switchExpressionChild : switchExpression.getChildren()) {
                if (switchExpressionChild instanceof PsiCodeBlock codeBlock) {
                    boolean previousHasBodyStatement = true;
                    for (@Nonnull PsiElement codeBlockChildren : codeBlock.getChildren()) {
                        if (codeBlockChildren instanceof PsiWhiteSpace && builder.charAt(builder.length() - 1) == ',') {
                            continue;
                        }
                        if (codeBlockChildren instanceof PsiSwitchLabelStatement switchLabelStatement) {
                            boolean nextIsBodyStatement = checkNextIsStatement(codeBlockChildren);
                            for (@Nonnull PsiElement labelStatementChild : switchLabelStatement.getChildren()) {
                                if (!previousHasBodyStatement
                                    && labelStatementChild instanceof PsiJavaToken javaToken
                                    && javaToken.textMatches("case")) {
                                    continue;
                                }
                                if (labelStatementChild instanceof PsiJavaToken javaToken && javaToken.textMatches(":")) {
                                    if (nextIsBodyStatement) {
                                        builder.append("->"); //replace ':' with '->'
                                    }
                                    else {
                                        //next label
                                        builder.append(",");
                                    }
                                }
                                else {
                                    builder.append(labelStatementChild.getText());
                                }
                            }
                            PsiElement nextOfSwitchLabelStatement = PsiTreeUtil.skipWhitespacesAndCommentsForward(switchLabelStatement);
                            if (nextIsBodyStatement && !(nextOfSwitchLabelStatement instanceof PsiBlockStatement)
                                && findOneYieldOrThrowStatement(PsiTreeUtil.skipWhitespacesAndCommentsForward(codeBlockChildren)) == null) {
                                builder.append("{"); //wrap multiline rule into '{}'
                            }
                            previousHasBodyStatement = nextIsBodyStatement;
                        }
                        else {
                            PsiStatement yieldStatement = findOneYieldOrThrowStatement(codeBlockChildren);
                            if (yieldStatement != null) {
                                for (@Nonnull PsiElement yieldStatementChild : yieldStatement.getChildren()) {
                                    if (!(yieldStatementChild instanceof PsiJavaToken javaToken && javaToken.textMatches("yield"))) {
                                        builder.append(yieldStatementChild.getText()); // skip 'yield' for one-line rule
                                    }
                                }
                            }
                            else {
                                builder.append(codeBlockChildren.getText());
                                PsiElement nextOfCodeBlockChildren = PsiTreeUtil.skipWhitespacesAndCommentsForward(codeBlockChildren);
                                if (!(codeBlockChildren instanceof PsiComment || codeBlockChildren instanceof PsiWhiteSpace)
                                    && !(PsiTreeUtil.skipWhitespacesAndCommentsBackward(codeBlockChildren) instanceof PsiJavaToken)
                                    && findOneYieldOrThrowStatement(PsiTreeUtil.skipWhitespacesAndCommentsBackward(codeBlockChildren)) == null
                                    && !(codeBlockChildren instanceof PsiJavaToken)
                                    && (nextOfCodeBlockChildren instanceof PsiSwitchLabelStatement ||
                                    nextOfCodeBlockChildren instanceof PsiJavaToken javaToken && javaToken.textMatches("}"))) {
                                    builder.append("\n}"); //wrap multiline rule into '{}'
                                }
                            }
                        }
                    }
                }
                else {
                    builder.append(switchExpressionChild.getText());
                }
            }
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());
            PsiExpression newSwitchExpression = factory.createExpressionFromText(builder.toString(), element);
            switchExpression.replace(newSwitchExpression);
        }

        @RequiredReadAction
        private static boolean checkNextIsStatement(@Nullable PsiElement statement) {
            PsiElement forward = PsiTreeUtil.skipWhitespacesAndCommentsForward(statement);
            return statement instanceof PsiSwitchLabelStatement
                && !(forward instanceof PsiSwitchLabelStatement)
                && forward instanceof PsiStatement;
        }

        @Nullable
        @RequiredReadAction
        private static PsiStatement findOneYieldOrThrowStatement(@Nullable PsiElement switchBlockChild) {
            if (switchBlockChild == null) {
                return null;
            }
            boolean isOldOrThrow = switchBlockChild instanceof PsiYieldStatement
                || switchBlockChild instanceof PsiThrowStatement;
            if (!isOldOrThrow) {
                return null;
            }
            boolean hasSwitchLabelBefore =
                PsiTreeUtil.skipWhitespacesAndCommentsBackward(switchBlockChild) instanceof PsiSwitchLabelStatement;
            if (!hasSwitchLabelBefore) {
                return null;
            }
            PsiElement nextElement = PsiTreeUtil.skipWhitespacesAndCommentsForward(switchBlockChild);
            boolean nextElementIsSwitchLabel = nextElement instanceof PsiSwitchLabelStatement;
            boolean isClosingBrace = nextElement instanceof PsiJavaToken javaToken && javaToken.textMatches("}");
            if (!(nextElementIsSwitchLabel || isClosingBrace)) {
                return null;
            }
            return (PsiStatement) switchBlockChild;
        }
    }

    private static class ReplaceWithSwitchExpressionFix implements LocalQuickFix {
        private final ReplacementType myReplacementType;

        ReplaceWithSwitchExpressionFix(ReplacementType replacementType) {
            myReplacementType = replacementType;
        }

        @Override
        public @Nonnull LocalizeValue getName() {
            return myReplacementType.getFixName();
        }

        @Override
        @RequiredWriteAction
        public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor problemDescriptor) {
            PsiElement element = problemDescriptor.getPsiElement();

            PsiSwitchStatement statement = PsiTreeUtil.getParentOfType(element, PsiSwitchStatement.class);
            if (statement == null) {
                return;
            }
            SwitchReplacer replacer =
                ContainerUtil.find(findSwitchReplacers(statement, Integer.MAX_VALUE), t -> t.getType() == myReplacementType);
            if (replacer == null) {
                return;
            }
            replacer.replace(statement);
        }
    }

    /**
     * This method is used to rearrange branches.
     * Now it can change the order of branches or divide some branches into several separate branches.
     * For example, a null branch will be extracted from others.
     *
     * @return rearranged branches
     */
    @RequiredReadAction
    private static @Nonnull List<SwitchBranch> rearrangeBranches(@Nonnull List<SwitchBranch> branches, @Nonnull PsiElement context) {
        if (branches.isEmpty()) {
            return branches;
        }
        if (!PsiUtil.isAvailable(JavaFeature.PATTERNS_IN_SWITCH, context)) {
            return branches;
        }
        List<SwitchBranch> result = new ArrayList<>();
        for (SwitchBranch branch : branches) {
            if (branch.myIsDefault) {
                result.add(branch);
                continue;
            }
            List<? extends PsiCaseLabelElement> caseExpressions = branch.myCaseExpressions;
            if (caseExpressions == null || caseExpressions.size() <= 1) {
                result.add(branch);
                continue;
            }
            rearrangeCases(branch, caseExpressions, result);
        }
        return result;
    }

    /**
     * This method is used to rearrange case elements.
     * Method is used by {@link #rearrangeBranches(List, PsiElement)}.
     *
     * @param result - container, where sorted cases will be added
     */
    private static void rearrangeCases(
        @Nonnull SwitchBranch branch,
        @Nonnull List<? extends PsiCaseLabelElement> caseExpressions,
        @Nonnull List<SwitchBranch> result
    ) {
        List<PsiCaseLabelElement> previousExpressions = new ArrayList<>();
        for (PsiCaseLabelElement expression : caseExpressions) {
            PsiCaseLabelElement external = findNullLabel(List.of(expression));
            if (external == null && expression instanceof PsiPattern) {
                external = expression;
            }
            if (external == null) {
                previousExpressions.add(expression);
            }
            else {
                if (!previousExpressions.isEmpty()) {
                    SwitchBranch otherBranch = branch.withLabels(new ArrayList<>(previousExpressions));
                    result.add(otherBranch);
                    previousExpressions.clear();
                }
                SwitchBranch specialBranch = branch.withLabels(List.of(external));
                result.add(specialBranch);
            }
        }
        if (!previousExpressions.isEmpty()) {
            SwitchBranch otherBranch = branch.withLabels(previousExpressions);
            result.add(otherBranch);
        }
    }

    private static @Nullable PsiCaseLabelElement findNullLabel(@Nonnull List<? extends PsiCaseLabelElement> expressions) {
        return ContainerUtil.find(
            expressions,
            label -> label instanceof PsiExpression literal && TypeConversionUtil.isNullType(literal.getType())
        );
    }

    private static final class ReturningSwitchReplacer implements SwitchReplacer {
        final @Nonnull PsiStatement myStatement;
        final List<SwitchBranch> myNewBranches;
        final @Nullable PsiReturnStatement myReturnToDelete;
        final @Nullable PsiThrowStatement myThrowStatementToDelete;
        private final @Nonnull List<? extends PsiStatement> myStatementsToDelete;
        private final boolean myIsInfo;
        private final int myMaxNumberStatementsInBranch;

        @RequiredReadAction
        private ReturningSwitchReplacer(
            @Nonnull PsiStatement statement,
            @Nonnull List<SwitchBranch> newBranches,
            @Nullable PsiReturnStatement returnToDelete,
            @Nullable PsiThrowStatement throwToDelete,
            @Nonnull List<? extends PsiStatement> statementsToDelete,
            boolean isInfo,
            int maxNumberStatementsInBranch
        ) {
            myStatement = statement;
            myNewBranches = rearrangeBranches(newBranches, statement);
            myReturnToDelete = returnToDelete;
            myThrowStatementToDelete = throwToDelete;
            myStatementsToDelete = statementsToDelete;
            myIsInfo = isInfo;
            myMaxNumberStatementsInBranch = maxNumberStatementsInBranch;
        }

        @Override
        public Integer getMaxNumberStatementsInBranch() {
            return myMaxNumberStatementsInBranch;
        }

        @Override
        public boolean isInformLevel() {
            return myIsInfo;
        }

        @Override
        @RequiredWriteAction
        public void replace(@Nonnull PsiStatement statement) {
            CommentTracker commentTracker = new CommentTracker();
            PsiSwitchBlock switchBlock = generateEnhancedSwitch(statement, myNewBranches, commentTracker, true);
            if (switchBlock == null) {
                return;
            }

            if (myReturnToDelete != null) {
                CommentTracker ct = new CommentTracker();
                commentTracker.markUnchanged(myReturnToDelete.getReturnValue());
                ct.delete(myReturnToDelete);
            }
            if (myThrowStatementToDelete != null) {
                CommentTracker ct = new CommentTracker();
                commentTracker.markUnchanged(myThrowStatementToDelete.getException());
                ct.delete(myThrowStatementToDelete);
            }
            for (PsiStatement toDelete : myStatementsToDelete) {
                commentTracker.delete(toDelete);
            }
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(statement.getProject());
            PsiStatement returnStatement = factory.createStatementFromText("return " + switchBlock.getText() + ";", switchBlock);
            commentTracker.replaceAndRestoreComments(statement, returnStatement);
        }

        @Override
        public ReplacementType getType() {
            return ReplacementType.Expression;
        }
    }

    /**
     * <pre>
     * switch (n) {
     *   case 1:
     *     return "a";
     *   case 2:
     *     return "b";
     *   default:
     *     return "?";
     * }
     * </pre>
     */

    @RequiredReadAction
    private static @Nullable SwitchReplacer inspectReturningSwitch(
        @Nonnull PsiStatement statement,
        @Nonnull List<OldSwitchStatementBranch> branches,
        boolean isExhaustive,
        int maxNumberStatementsForExpression
    ) {
        PsiReturnStatement returnAfterSwitch =
            ObjectUtil.tryCast(PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class), PsiReturnStatement.class);
        PsiThrowStatement throwAfterSwitch =
            ObjectUtil.tryCast(PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class), PsiThrowStatement.class);
        if (returnAfterSwitch == null && throwAfterSwitch == null && !isExhaustive) {
            return null;
        }
        List<SwitchBranch> newBranches = new ArrayList<>();
        boolean hasReturningBranch = false;
        boolean isInfo = false;
        int maxLines = 0;
        for (int i = 0, size = branches.size(); i < size; i++) {
            OldSwitchStatementBranch branch = branches.get(i);
            if (!isConvertibleBranch(branch, i != size - 1)) {
                return null;
            }
            if (branch.isFallthrough()) {
                continue;
            }
            PsiStatement[] statements = branch.getStatements();
            if (statements.length == 1 && statements[0] instanceof PsiBlockStatement psiCodeBlock) {
                statements = psiCodeBlock.getCodeBlock().getStatements();
            }
            if (statements.length == 0) {
                if ((i == branches.size() - 1) || branch.isDefault()) {
                    if (returnAfterSwitch != null) {
                        statements = new PsiStatement[]{returnAfterSwitch};
                    }
                    else if (throwAfterSwitch != null) {
                        statements = new PsiStatement[]{throwAfterSwitch};
                    }
                    else {
                        return null;
                    }
                }
                else {
                    return null;
                }
            }
            if (maxLines < statements.length) {
                maxLines = statements.length;
            }
            if (statements.length > maxNumberStatementsForExpression) {
                isInfo = true;
            }
            int lastIndex = statements.length - 1;
            if (ContainerUtil.exists(
                statements,
                st -> !PsiTreeUtil.findChildrenOfAnyType(
                    st,
                    PsiContinueStatement.class,
                    PsiBreakStatement.class,
                    PsiYieldStatement.class
                ).isEmpty()
            )) {
                return null;
            }
            PsiReturnStatement returnStmt = ObjectUtil.tryCast(statements[lastIndex], PsiReturnStatement.class);
            SwitchRuleResult result;
            if (returnStmt == null) {
                PsiThrowStatement throwStatement = ObjectUtil.tryCast(statements[lastIndex], PsiThrowStatement.class);
                if (throwStatement == null) {
                    return null;
                }
                PsiStatement[] psiStatements = replaceAllReturnWithYield(statements);
                if (psiStatements == null) {
                    return null;
                }
                result = new SwitchStatementBranch(psiStatements, statements);
            }
            else {
                PsiExpression returnExpr = returnStmt.getReturnValue();
                if (returnExpr == null) {
                    return null;
                }
                if (statements.length == 1) {
                    result = new SwitchRuleExpressionResult(returnExpr);
                }
                else {
                    PsiStatement[] psiStatements = replaceAllReturnWithYield(statements);
                    if (psiStatements == null) {
                        return null;
                    }
                    result = new SwitchStatementBranch(psiStatements, statements);
                }
                hasReturningBranch = true;
            }
            newBranches.add(SwitchBranch.fromOldBranch(branch, result, branch.getUsedElements()));
        }
        if (!hasReturningBranch) {
            return null;
        }
        if (!isExhaustive && returnAfterSwitch != null) {
            PsiExpression returnExpr = returnAfterSwitch.getReturnValue();
            if (returnExpr == null) {
                return null;
            }
            newBranches.add(SwitchBranch.createDefault(new SwitchRuleExpressionResult(returnExpr)));
        }
        if (!isExhaustive && throwAfterSwitch != null) {
            newBranches.add(SwitchBranch.createDefault(new SwitchStatementBranch(new PsiStatement[]{throwAfterSwitch})));
        }
        List<PsiStatement> statementsToDelete = new ArrayList<>();
        if (isExhaustive && returnAfterSwitch == null && throwAfterSwitch == null) {
            PsiElement current = statement.getNextSibling();
            while (current != null) {
                if (current instanceof PsiStatement stmt) {
                    if (current instanceof PsiSwitchLabelStatement) {
                        break;
                    }
                    statementsToDelete.add(stmt);
                    if (stmt instanceof PsiReturnStatement || stmt instanceof PsiThrowStatement) {
                        break;
                    }
                }
                current = current.getNextSibling();
            }
        }
        return new ReturningSwitchReplacer(
            statement,
            newBranches,
            returnAfterSwitch,
            throwAfterSwitch,
            statementsToDelete,
            isInfo,
            maxLines
        );
    }

    @RequiredReadAction
    private static PsiStatement[] replaceAllReturnWithYield(PsiStatement[] statements) {
        PsiStatement[] result = ArrayUtil.copyOf(statements);
        for (int i = 0; i < statements.length; i++) {
            PsiStatement statement = result[i];
            if (statement instanceof PsiReturnStatement returnStatement) {
                PsiExpression returnValue = returnStatement.getReturnValue();
                if (returnValue == null || !returnValue.isValid() || PsiTreeUtil.hasErrorElements(returnValue)
                    //skip PsiCall not to resolve and get exceptions
                    || (!(returnValue instanceof PsiCall) && returnValue.getType() == null)) {
                    return null;
                }
                result[i] = createYieldStatement(returnValue);
                continue;
            }
            PsiStatement copy = (PsiStatement) statement.copy();
            result[i] = copy;
            Collection<PsiReturnStatement> returnStatements = PsiTreeUtil.findChildrenOfType(copy, PsiReturnStatement.class);
            for (PsiReturnStatement returnStatement : returnStatements) {
                PsiExpression returnValue = returnStatement.getReturnValue();
                if (returnValue == null
                    || PsiTreeUtil.hasErrorElements(returnValue)
                    || !returnValue.isValid()
                    || returnValue.getType() == null) {
                    return null;
                }
                //noinspection RequiredXAction
                returnStatement.replace(createYieldStatement(returnValue));
            }
        }
        return result;
    }

    @RequiredReadAction
    private static PsiStatement[] withLastStatementReplacedWithYield(PsiStatement[] statements, @Nonnull PsiExpression expr) {
        PsiStatement[] result = ArrayUtil.copyOf(statements);
        PsiStatement yieldStatement = createYieldStatement(expr);
        result[result.length - 1] = yieldStatement;
        return result;
    }

    @RequiredReadAction
    private static @Nonnull PsiStatement createYieldStatement(@Nonnull PsiExpression expr) {
        Project project = expr.getProject();
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        return factory.createStatementFromText("yield " + StringUtil.trim(expr.getText()) + ";", expr);
    }

    private static final class SwitchExistingVariableReplacer implements SwitchReplacer {
        final @Nonnull PsiVariable myVariableToAssign;
        final @Nonnull PsiStatement myStatement;
        final List<SwitchBranch> myNewBranches;
        final boolean myIsRightAfterDeclaration;
        private final boolean myIsInfo;
        private final int myMaxNumberStatementsInBranch;

        @RequiredReadAction
        private SwitchExistingVariableReplacer(
            @Nonnull PsiVariable variableToAssign,
            @Nonnull PsiStatement statement,
            List<SwitchBranch> newBranches,
            boolean isRightAfterDeclaration,
            boolean isInfo,
            int maxNumberStatementsInBranch
        ) {
            myVariableToAssign = variableToAssign;
            myStatement = statement;
            myNewBranches = rearrangeBranches(newBranches, statement);
            myIsRightAfterDeclaration = isRightAfterDeclaration;
            myIsInfo = isInfo;
            myMaxNumberStatementsInBranch = maxNumberStatementsInBranch;
        }

        @Override
        public Integer getMaxNumberStatementsInBranch() {
            return myMaxNumberStatementsInBranch;
        }

        @Override
        public boolean isInformLevel() {
            return myIsInfo;
        }

        @Override
        @RequiredWriteAction
        public void replace(@Nonnull PsiStatement switchStatement) {
            PsiLabeledStatement labeledStatement = ObjectUtil.tryCast(switchStatement.getParent(), PsiLabeledStatement.class);
            CommentTracker commentTracker = new CommentTracker();
            PsiSwitchBlock replacement = generateEnhancedSwitch(switchStatement, myNewBranches, commentTracker, true);
            if (replacement == null) {
                return;
            }
            PsiExpression initializer = myVariableToAssign.getInitializer();
            if (myIsRightAfterDeclaration &&
                (isNotUsed(myVariableToAssign, switchStatement) && isNotUsed(myVariableToAssign, replacement))) {
                if (initializer != null) {
                    List<PsiExpression> sideEffectExpressions = SideEffectChecker.extractSideEffectExpressions(initializer);
                    PsiStatement[] sideEffectStatements = StatementExtractor.generateStatements(sideEffectExpressions, initializer);
                    if (sideEffectStatements.length > 0) {
                        PsiStatement statement = ObjectUtil.tryCast(myVariableToAssign.getParent(), PsiStatement.class);
                        if (statement == null) {
                            return;
                        }
                        BlockUtils.addBefore(statement, sideEffectStatements);
                    }
                }
                myVariableToAssign.setInitializer((PsiSwitchExpression) replacement);
                commentTracker.delete(switchStatement);
                commentTracker.insertCommentsBefore(myVariableToAssign);
                if (labeledStatement != null) {
                    new CommentTracker().deleteAndRestoreComments(labeledStatement);
                }
            }
            else {
                String text = myVariableToAssign.getName() + "=" + replacement.getText() + ";";
                PsiStatement statementToReplace = labeledStatement != null ? labeledStatement : switchStatement;
                commentTracker.replaceAndRestoreComments(statementToReplace, text);
            }
        }

        @RequiredReadAction
        private static boolean isNotUsed(@Nonnull PsiVariable variable, @Nonnull PsiElement switchElement) {
            try {
                ControlFlow controlFlow = ControlFlowFactory
                    .getControlFlow(switchElement, AllVariablesControlFlowPolicy.getInstance(), ControlFlowOptions.NO_CONST_EVALUATE);
                List<PsiReferenceExpression> references = ControlFlowUtil.getReadBeforeWrite(controlFlow);
                for (PsiReferenceExpression reference : references) {
                    if (reference != null && reference.resolve() == variable) {
                        return false;
                    }
                }
                return true;
            }
            catch (AnalysisCanceledException e) {
                return false;
            }
        }

        @Override
        public ReplacementType getType() {
            return ReplacementType.Expression;
        }
    }

    /**
     * <pre>
     * int result;
     * switch(s) {
     *   case "a": result = 1; break;
     *   case "b": result = 2; break;
     *   default: result = 0;
     * }
     * </pre>
     */
    @RequiredReadAction
    private static @Nullable SwitchReplacer inspectVariableAssigningSwitch(
        @Nonnull PsiStatement statement,
        @Nonnull List<OldSwitchStatementBranch> branches,
        boolean isExhaustive,
        int maxNumberStatementsForExpression
    ) {
        PsiElement parent = statement.getParent();
        PsiElement anchor = parent instanceof PsiLabeledStatement ? parent : statement;
        PsiLocalVariable assignedVariable = null;
        List<SwitchBranch> newBranches = new ArrayList<>();
        boolean hasAssignedBranch = false;
        boolean wasDefault = false;
        boolean isInfo = false;
        int maxNumberStatementsInBranch = 0;
        for (int i = 0, size = branches.size(); i < size; i++) {
            OldSwitchStatementBranch branch = branches.get(i);
            if (!isConvertibleBranch(branch, i != size - 1)) {
                return null;
            }
            PsiStatement[] statements = branch.getStatements();
            if (branch.isFallthrough() && statements.length == 0) {
                continue;
            }
            if (statements.length == 0) {
                return null;
            }
            if (ContainerUtil.exists(
                statements,
                st -> !PsiTreeUtil.findChildrenOfAnyType(st, PsiContinueStatement.class,
                    PsiYieldStatement.class, PsiReturnStatement.class
                ).isEmpty()
            )) {
                return null;
            }
            if (ContainerUtil.exists(
                Arrays.stream(statements).toList().subList(0, statements.length - 1),
                st -> !PsiTreeUtil.findChildrenOfAnyType(st, PsiBreakStatement.class).isEmpty()
            )) {
                return null;
            }
            if (statements.length > maxNumberStatementsForExpression) {
                isInfo = true;
            }
            if (maxNumberStatementsInBranch < statements.length) {
                maxNumberStatementsInBranch = statements.length;
            }
            PsiStatement last = statements[statements.length - 1];
            PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(last);
            PsiExpression rExpression = null;
            if (assignment != null) {
                rExpression = assignment.getRExpression();
                PsiLocalVariable var = ExpressionUtils.resolveLocalVariable(assignment.getLExpression());
                if (var == null) {
                    return null;
                }
                if (assignedVariable == null) {
                    assignedVariable = var;
                }
                else if (assignedVariable != var) {
                    return null;
                }
            }
            SwitchRuleResult result;
            if (rExpression == null) {
                PsiThrowStatement throwStatement = ObjectUtil.tryCast(last, PsiThrowStatement.class);
                if (throwStatement == null) {
                    return null;
                }
                result = new SwitchStatementBranch(statements);
            }
            else {
                hasAssignedBranch = true;
                if (statements.length == 1) {
                    result = new SwitchRuleExpressionResult(rExpression);
                }
                else {
                    if (PsiTreeUtil.hasErrorElements(rExpression) || rExpression.getType() == null) {
                        return null;
                    }
                    result = new SwitchStatementBranch(withLastStatementReplacedWithYield(statements, rExpression));
                }
            }
            wasDefault = branch.isDefault() || existsDefaultLabelElement(branch.myLabelStatement);
            newBranches.add(SwitchBranch.fromOldBranch(branch, result, branch.getRelatedStatements()));
        }
        if (assignedVariable == null || !hasAssignedBranch) {
            return null;
        }
        boolean isRightAfterDeclaration = isRightAfterDeclaration(anchor, assignedVariable);
        if (!wasDefault && !isExhaustive) {
            SwitchBranch defaultBranch = getVariableAssigningDefaultBranch(assignedVariable, isRightAfterDeclaration, statement);
            if (defaultBranch != null) {
                newBranches.add(defaultBranch);
            }
            else {
                return null;
            }
        }
        return new SwitchExistingVariableReplacer(
            assignedVariable,
            statement,
            newBranches,
            isRightAfterDeclaration,
            isInfo,
            maxNumberStatementsInBranch
        );
    }

    private static boolean existsDefaultLabelElement(@Nonnull PsiSwitchLabelStatement statement) {
        PsiCaseLabelElementList labelElementList = statement.getCaseLabelElementList();
        if (labelElementList == null) {
            return false;
        }
        return ContainerUtil.exists(labelElementList.getElements(), el -> el instanceof PsiDefaultCaseLabelElement);
    }

    private static @Nullable EnhancedSwitchMigrationInspection.SwitchBranch getVariableAssigningDefaultBranch(
        @Nullable PsiLocalVariable assignedVariable,
        boolean isRightAfterDeclaration,
        @Nonnull PsiStatement statement
    ) {
        if (assignedVariable == null) {
            return null;
        }
        PsiExpression initializer = assignedVariable.getInitializer();
        if (isRightAfterDeclaration && initializer instanceof PsiLiteralExpression) {
            return SwitchBranch.createDefault(new SwitchRuleExpressionResult(initializer));
        }
        PsiDeclarationStatement declaration = ObjectUtil.tryCast(assignedVariable.getParent(), PsiDeclarationStatement.class);
        if (declaration == null || declaration.getParent() == null) {
            return null;
        }
        try {
            LocalsOrMyInstanceFieldsControlFlowPolicy policy = LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance();
            ControlFlow controlFlow =
                ControlFlowFactory.getInstance(declaration.getProject()).getControlFlow(declaration.getParent(), policy);
            int switchStart = controlFlow.getStartOffset(statement);
            if (switchStart <= 0) {
                return null;
            }
            ControlFlow beforeFlow = new ControlFlowSubRange(controlFlow, 0, switchStart);
            if (!ControlFlowUtil.isVariableDefinitelyAssigned(assignedVariable, beforeFlow)) {
                return null;
            }
        }
        catch (AnalysisCanceledException e) {
            return null;
        }
        Project project = assignedVariable.getProject();
        PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
        PsiExpression reference = factory.createExpressionFromText(assignedVariable.getName(), assignedVariable);
        return SwitchBranch.createDefault(new SwitchRuleExpressionResult(reference));
    }

    @RequiredReadAction
    private static boolean isRightAfterDeclaration(PsiElement anchor, PsiVariable assignedVariable) {
        PsiDeclarationStatement declaration =
            ObjectUtil.tryCast(PsiTreeUtil.getPrevSiblingOfType(anchor, PsiStatement.class), PsiDeclarationStatement.class);
        if (declaration != null) {
            PsiElement[] elements = declaration.getDeclaredElements();
            if (elements.length == 1) {
                PsiLocalVariable localVariable = ObjectUtil.tryCast(elements[0], PsiLocalVariable.class);
                if (localVariable != null && localVariable == assignedVariable) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Replaces with an enhanced switch statement
     */
    private static final class SwitchStatementReplacer implements SwitchReplacer {
        final @Nonnull PsiStatement myStatement;
        final @Nonnull List<SwitchBranch> myExpressionBranches;

        @RequiredReadAction
        private SwitchStatementReplacer(@Nonnull PsiStatement statement, @Nonnull List<SwitchBranch> ruleResults) {
            myStatement = statement;
            myExpressionBranches = rearrangeBranches(ruleResults, statement);
        }

        @Override
        public boolean isInformLevel() {
            return false;
        }

        @Override
        public Integer getMaxNumberStatementsInBranch() {
            return null;
        }

        @Override
        @RequiredWriteAction
        public void replace(@Nonnull PsiStatement switchStatement) {
            CommentTracker commentTracker = new CommentTracker();
            PsiSwitchBlock switchBlock = generateEnhancedSwitch(switchStatement, myExpressionBranches, commentTracker, false);
            if (switchBlock == null) {
                return;
            }
            commentTracker.replaceAndRestoreComments(switchStatement, switchBlock);
        }

        @Override
        public ReplacementType getType() {
            return ReplacementType.Statement;
        }
    }

    /**
     * Suggest replacement with an enhanced switch statement
     */
    @Nullable
    @RequiredReadAction
    private static SwitchReplacer inspectReplacementWithStatement(
        @Nonnull PsiStatement statement,
        @Nonnull List<OldSwitchStatementBranch> branches
    ) {
        for (int i = 0, size = branches.size(); i < size; i++) {
            OldSwitchStatementBranch branch = branches.get(i);
            if (!isConvertibleBranch(branch, i != size - 1) &&
                //example:
                //case 0: break
                !(!branch.isFallthrough() && branch.getStatements().length == 0)) {
                return null;
            }
        }
        List<SwitchBranch> switchRules = new ArrayList<>();
        for (int i = 0, branchesSize = branches.size(); i < branchesSize; i++) {
            OldSwitchStatementBranch branch = branches.get(i);
            if (branch.isFallthrough() && branch.getStatements().length == 0) {
                continue;
            }
            boolean allBranchRefsWillBeValid = StreamEx.of(branch.getStatements())
                .limit(i) // only previous branches
                .flatMap((PsiElement stmt) -> StreamEx.ofTree(stmt, el -> StreamEx.of(el.getChildren())))
                .select(PsiReferenceExpression.class)
                .map(PsiReference::resolve)
                .select(PsiLocalVariable.class)
                .allMatch(variable -> isInBranchOrOutside(statement, branch, variable));
            if (!allBranchRefsWillBeValid) {
                return null;
            }
            if (branch.isFallthrough() && branch.getStatements().length == 0) {
                continue;
            }
            PsiStatement[] statements = branch.getStatements();
            switchRules.add(SwitchBranch.fromOldBranch(branch, new SwitchStatementBranch(statements), branch.getRelatedStatements()));
        }
        return new SwitchStatementReplacer(statement, switchRules);
    }

    private static boolean isInBranchOrOutside(
        @Nonnull PsiStatement switchStmt,
        OldSwitchStatementBranch branch,
        PsiLocalVariable variable
    ) {
        return !PsiTreeUtil.isAncestor(switchStmt, variable, false)
            || ContainerUtil.or(branch.getStatements(), stmt -> PsiTreeUtil.isAncestor(stmt, variable, false));
    }

    private static final class SwitchStatementBranch implements SwitchRuleResult {

        private final @Nullable PsiStatement[] myResultStatements;

        private final @Nullable PsiStatement[] myOriginalResultStatements;

        private SwitchStatementBranch(@Nullable PsiStatement[] resultStatements) {
            myResultStatements = resultStatements;
            myOriginalResultStatements = null;
        }

        private SwitchStatementBranch(
            @Nullable PsiStatement[] resultStatements,
            @Nullable PsiStatement[] originalResultStatements
        ) {
            myResultStatements = resultStatements;
            myOriginalResultStatements = originalResultStatements;
        }

        @Override
        @RequiredReadAction
        public String generate(CommentTracker ct, SwitchBranch branch) {
            @Nullable PsiStatement[] resultStatements = myResultStatements;
            if (resultStatements == null) {
                return "";
            }
            if (resultStatements.length == 1) {
                PsiStatement first = resultStatements[0];
                if (first instanceof PsiExpressionStatement || first instanceof PsiBlockStatement || first instanceof PsiThrowStatement) {
                    return ct.textWithComments(resultStatements[0]) + "\n";
                }
            }
            StringBuilder sb = new StringBuilder("{");
            for (int i = 0, length = resultStatements.length; i < length; i++) {
                PsiStatement element = resultStatements[i];
                if (element == null) {
                    continue;
                }
                if (i == 0) {
                    PsiElement current = getElementForComments(element, i);
                    if (current != null) {
                        current = current.getPrevSibling();
                    }
                    while (current instanceof PsiWhiteSpace || current instanceof PsiComment) {
                        current = current.getPrevSibling();
                    }
                    addWhiteSpaceAndComments(current, sb, ct);
                }
                sb.append(ct.text(element));
                if (i + 1 < length) {
                    PsiElement current = getElementForComments(element, i);
                    addWhiteSpaceAndComments(current, sb, ct);
                }
                if (element.getNextSibling() == null
                    && element.getLastChild() instanceof PsiComment comment
                    && comment.getTokenType() == JavaTokenType.END_OF_LINE_COMMENT) {
                    addNewLine(sb);
                }
            }
            addCommentsUntilNextLabel(ct, branch, sb);
            addNewLine(sb);
            sb.append("}");
            for (PsiElement element : branch.myUsedElements) {
                ct.markUnchanged(element);
            }
            return sb.toString();
        }

        private static void addNewLine(@Nonnull StringBuilder sb) {
            String string = sb.toString();
            String trimmed = string.trim();
            if (!string.substring(trimmed.length()).contains("\n")) {
                sb.append("\n");
            }
        }

        private @Nullable PsiElement getElementForComments(@Nullable PsiStatement element, int i) {
            PsiElement current = element;
            if (myOriginalResultStatements != null &&
                myOriginalResultStatements.length > i &&
                myOriginalResultStatements[i] != null) {
                current = myOriginalResultStatements[i];
            }
            return current;
        }

        @RequiredReadAction
        private static void addWhiteSpaceAndComments(@Nullable PsiElement element, @Nonnull StringBuilder sb, CommentTracker ct) {
            if (element == null) {
                return;
            }
            PsiElement current = element.getNextSibling();
            while (current instanceof PsiComment || current instanceof PsiWhiteSpace) {
                sb.append(ct.text(current));
                current = current.getNextSibling();
            }
        }
    }

    private static final class SwitchRuleExpressionResult implements SwitchRuleResult {
        private final PsiExpression myExpression;

        private SwitchRuleExpressionResult(@Nonnull PsiExpression expression) {
            myExpression = expression;
        }

        @Override
        @RequiredReadAction
        public String generate(CommentTracker ct, SwitchBranch branch) {
            return ct.textWithComments(myExpression) + ";";
        }
    }

    /**
     * Adds comments until the next label statement in a switch branch.
     * If comments exist, <code>builder</code> will end with '\n'
     *
     * @param ct      the CommentTracker object for tracking comments
     * @param branch  the SwitchBranch object representing a switch branch
     * @param builder the StringBuilder object to append comments to
     */
    @RequiredReadAction
    private static void addCommentsUntilNextLabel(CommentTracker ct, SwitchBranch branch, StringBuilder builder) {
        PsiElement label = ContainerUtil.find(branch.myUsedElements, e -> e instanceof PsiSwitchLabelStatement);
        if (!(label instanceof PsiSwitchLabelStatement labelStatement)) {
            return;
        }
        PsiSwitchLabelStatement nextLabelStatement = PsiTreeUtil.getNextSiblingOfType(labelStatement, PsiSwitchLabelStatement.class);
        PsiElement untilComment = null;
        if (nextLabelStatement != null) {
            untilComment = PsiTreeUtil.getPrevSiblingOfType(nextLabelStatement, PsiStatement.class);
        }
        if (untilComment == null) {
            PsiElement next = labelStatement.getNextSibling();
            if (next != null) {
                while (next.getNextSibling() != null) {
                    next = next.getNextSibling();
                }
            }
            untilComment = next;
        }
        if (untilComment != null) {
            String commentsBefore = grubCommentsBefore(untilComment, ct, branch).stripTrailing();
            String previousText = builder.toString().stripTrailing();
            if (previousText.length() > 1 && previousText.charAt(builder.length() - 1) == '\n') {
                commentsBefore = StringUtil.trimStart(commentsBefore, "\n");
            }
            if (!commentsBefore.isEmpty()) {
                commentsBefore += '\n';
            }
            builder.append(commentsBefore);
        }
    }

    @RequiredReadAction
    private static @Nonnull String grubCommentsBefore(@Nonnull PsiElement untilComment, @Nonnull CommentTracker ct, SwitchBranch branch) {
        List<String> comments = new ArrayList<>();
        PsiElement current = (untilComment instanceof PsiComment || untilComment instanceof PsiWhiteSpace)
            ? untilComment
            : PsiTreeUtil.prevLeaf(untilComment);
        while (current != null) {
            if (current instanceof PsiComment || current instanceof PsiWhiteSpace) {
                if (branch.myUsedElements.isEmpty()
                    || !PsiTreeUtil.isAncestor(branch.myUsedElements.get(branch.myUsedElements.size() - 1), current, false)) {
                    comments.add(ct.text(current));
                }
            }
            else {
                break;
            }
            current = PsiTreeUtil.prevLeaf(current);
        }
        Collections.reverse(comments);
        return StringUtil.join(comments, "");
    }

    private static final class SwitchBranch {
        final boolean myIsDefault;
        final List<? extends PsiCaseLabelElement> myCaseExpressions;
        final @Nullable PsiExpression myGuard;
        final @Nonnull List<? extends PsiElement> myUsedElements; // used elements only for this branch
        private final @Nonnull SwitchRuleResult myRuleResult;

        private SwitchBranch(
            boolean isDefault,
            @Nonnull List<? extends PsiCaseLabelElement> caseExpressions,
            @Nullable PsiExpression guard, @Nonnull SwitchRuleResult ruleResult,
            @Nonnull List<? extends PsiElement> usedElements
        ) {
            if (ContainerUtil.exists(caseExpressions, exp -> exp instanceof PsiDefaultCaseLabelElement)) {
                myIsDefault = true;
            }
            else {
                myIsDefault = isDefault;
            }
            myGuard = guard;
            if (myIsDefault) {
                PsiCaseLabelElement nullLabel = findNullLabel(caseExpressions);
                if (nullLabel != null) {
                    myCaseExpressions = List.of(nullLabel);
                }
                else {
                    myCaseExpressions = List.of();
                }
            }
            else {
                myCaseExpressions = caseExpressions;
            }
            myRuleResult = ruleResult;
            myUsedElements = usedElements;
        }

        @RequiredReadAction
        private String generate(CommentTracker ct) {
            StringBuilder sb = new StringBuilder();
            PsiElement label = ContainerUtil.find(myUsedElements, e -> e instanceof PsiSwitchLabelStatement);
            if (label != null) {
                sb.append(ct.commentsBefore(label.getFirstChild()));
            }
            if (!myCaseExpressions.isEmpty()) {
                String labels = StreamEx.of(myCaseExpressions).map(ct::textWithComments).joining(",");
                sb.append("case");
                if (!labels.startsWith(" ")) {
                    sb.append(" ");
                }
                sb.append(labels);
            }
            else if (!myIsDefault) {
                sb.append("case ");
            }
            if (myIsDefault) {
                if (!myCaseExpressions.isEmpty()) {
                    sb.append(",");
                }
                sb.append("default");
            }
            if (myGuard != null) {
                sb.append(" when ").append(ct.text(myGuard));
            }
            grabCommentsBeforeColon(label, ct, sb);
            sb.append("->");
            sb.append(myRuleResult.generate(ct, this));
            if (!myUsedElements.isEmpty()) {
                PsiElement element = PsiTreeUtil.nextCodeLeaf(myUsedElements.get(myUsedElements.size() - 1));
                if (element instanceof PsiJavaToken javaToken
                    && javaToken.textMatches("}")
                    && element.getParent() instanceof PsiCodeBlock codeBlock
                    && codeBlock.getParent() instanceof PsiSwitchBlock) {
                    sb.append(ct.commentsBefore(javaToken));
                }
            }
            return sb.toString();
        }

        @RequiredReadAction
        private static void grabCommentsBeforeColon(PsiElement label, CommentTracker ct, StringBuilder sb) {
            if (label != null) {
                PsiElement child = label.getLastChild();
                while (child != null && !child.textMatches(":")) {
                    child = child.getPrevSibling();
                }
                if (child != null) {
                    sb.append(ct.commentsBefore(child));
                }
            }
        }

        private @Nonnull SwitchBranch withLabels(@Nonnull List<PsiCaseLabelElement> caseElements) {
            return new SwitchBranch(myIsDefault, caseElements, myGuard, myRuleResult, myUsedElements);
        }

        private static @Nonnull SwitchBranch createDefault(@Nonnull SwitchRuleResult ruleResult) {
            return new SwitchBranch(true, Collections.emptyList(), null, ruleResult, Collections.emptyList());
        }

        private static @Nonnull SwitchBranch fromOldBranch(
            @Nonnull OldSwitchStatementBranch branch,
            @Nonnull SwitchRuleResult result,
            @Nonnull List<? extends PsiElement> usedElements
        ) {
            return new SwitchBranch(branch.isDefault(), branch.getCaseLabelElements(), branch.getGuardExpression(), result, usedElements);
        }
    }

    private static final class OldSwitchStatementBranch {
        final boolean myIsFallthrough;
        final PsiStatement[] myStatements;
        final @Nonnull PsiSwitchLabelStatement myLabelStatement;
        final @Nullable PsiBreakStatement myBreakStatement;
        @Nullable
        OldSwitchStatementBranch myPreviousSwitchBranch;

        private OldSwitchStatementBranch(
            boolean isFallthrough,
            PsiStatement[] statements,
            @Nonnull PsiSwitchLabelStatement switchLabelStatement,
            @Nullable PsiBreakStatement breakStatement
        ) {
            myIsFallthrough = isFallthrough;
            myStatements = statements;
            myLabelStatement = switchLabelStatement;
            myBreakStatement = breakStatement;
        }

        private boolean isDefault() {
            List<OldSwitchStatementBranch> branches = getWithFallthroughBranches();
            return ContainerUtil.or(branches, branch -> branch.myLabelStatement.isDefaultCase());
        }

        private boolean isFallthrough() {
            return myIsFallthrough;
        }

        public @Nullable PsiExpression getGuardExpression() {
            return myLabelStatement.getGuardExpression();
        }

        private List<PsiCaseLabelElement> getCaseLabelElements() {
            List<OldSwitchStatementBranch> branches = getWithFallthroughBranches();
            Collections.reverse(branches);
            return StreamEx.of(branches).flatMap(branch -> {
                PsiCaseLabelElementList caseLabelElementList = branch.myLabelStatement.getCaseLabelElementList();
                if (caseLabelElementList == null) {
                    return StreamEx.empty();
                }
                return StreamEx.of(caseLabelElementList.getElements());
            }).toList();
        }

        /**
         * @return only meaningful statements, without break and case statements
         */
        private PsiStatement[] getStatements() {
            return myStatements;
        }

        private List<PsiStatement> getRelatedStatements() {
            StreamEx<PsiStatement> withoutBreak = StreamEx.of(myStatements).prepend(myLabelStatement);
            return withoutBreak.prepend(myBreakStatement).toList();
        }

        private List<OldSwitchStatementBranch> getWithFallthroughBranches() {
            List<OldSwitchStatementBranch> withPrevious = new ArrayList<>();
            OldSwitchStatementBranch current = this;
            while (true) {
                withPrevious.add(current);
                current = current.myPreviousSwitchBranch;
                if (current == null || current.myStatements.length != 0 || !current.myIsFallthrough) {
                    return withPrevious;
                }
            }
        }

        private List<? extends PsiElement> getUsedElements() {
            return StreamEx.of(getWithFallthroughBranches()).flatMap(branch -> StreamEx.of(branch.getRelatedStatements())).toList();
        }
    }
}
