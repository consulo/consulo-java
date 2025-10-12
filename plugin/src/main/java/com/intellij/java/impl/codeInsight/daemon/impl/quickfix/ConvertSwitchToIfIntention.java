// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.impl.ig.psiutils.VariableNameGenerator;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.language.impl.codeInsight.BlockUtils;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import com.siyeh.ig.psiutils.BreakConverter;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.editor.localize.CommonQuickFixLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.ObjectUtil;
import jakarta.annotation.Nonnull;
import one.util.streamex.StreamEx;

import java.util.*;
import java.util.stream.Collectors;

public class ConvertSwitchToIfIntention implements SyntheticIntentionAction {
    private final PsiSwitchStatement mySwitchStatement;

    public ConvertSwitchToIfIntention(@Nonnull PsiSwitchStatement switchStatement) {
        mySwitchStatement = switchStatement;
    }

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return CommonQuickFixLocalize.fixReplaceXWithY(PsiKeyword.SWITCH, PsiKeyword.IF);
    }

    @Override
    @RequiredReadAction
    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        return isAvailable(mySwitchStatement);
    }

    @RequiredReadAction
    public static boolean isAvailable(PsiSwitchStatement switchStatement) {
        PsiCodeBlock body = switchStatement.getBody();
        return body != null && !body.isEmpty() && BreakConverter.from(switchStatement) != null && !mayFallThroughNonTerminalDefaultCase(body);
    }

    @RequiredReadAction
    private static boolean mayFallThroughNonTerminalDefaultCase(PsiCodeBlock body) {
        List<PsiSwitchLabelStatementBase> labels = PsiTreeUtil.getChildrenOfTypeAsList(body, PsiSwitchLabelStatementBase.class);
        return StreamEx.of(labels).pairMap((prev, next) -> {
            if (prev.isDefaultCase()) {
                Set<PsiSwitchLabelStatementBase> targets = getFallThroughTargets(body);
                return targets.contains(prev) || targets.contains(next);
            }
            return false;
        }).has(true);
    }

    @Override
    @RequiredReadAction
    public void invoke(@Nonnull Project project, Editor editor, PsiFile file) {
        doProcessIntention(mySwitchStatement);
    }

    @Nonnull
    @Override
    public PsiElement getElementToMakeWritable(@Nonnull PsiFile file) {
        return mySwitchStatement;
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }

    @RequiredReadAction
    public static void doProcessIntention(@Nonnull PsiSwitchStatement switchStatement) {
        PsiExpression switchExpression = switchStatement.getExpression();
        if (switchExpression == null) {
            return;
        }
        PsiType switchExpressionType = RefactoringUtil.getTypeByExpressionWithExpectedType(switchExpression);
        if (switchExpressionType == null) {
            return;
        }
        CommentTracker commentTracker = new CommentTracker();
        boolean isSwitchOnString = switchExpressionType.equalsToText(CommonClassNames.JAVA_LANG_STRING);
        boolean useEquals = isSwitchOnString;
        if (!useEquals) {
            PsiClass aClass = PsiUtil.resolveClassInType(switchExpressionType);
            useEquals = aClass != null && !aClass.isEnum() && !TypeConversionUtil.isPrimitiveWrapper(aClass.getQualifiedName());
        }
        PsiCodeBlock body = switchStatement.getBody();
        if (body == null) {
            return;
        }
        // Should execute getFallThroughTargets and statementMayCompleteNormally before converting breaks
        Set<PsiSwitchLabelStatementBase> fallThroughTargets = getFallThroughTargets(body);
        boolean mayCompleteNormally = ControlFlowUtils.statementMayCompleteNormally(switchStatement);
        BreakConverter converter = BreakConverter.from(switchStatement);
        if (converter == null) {
            return;
        }
        converter.process();
        List<SwitchStatementBranch> allBranches = extractBranches(commentTracker, body, fallThroughTargets);

        String declarationString;
        boolean hadSideEffects;
        String expressionText;
        Project project = switchStatement.getProject();
        int totalCases = allBranches.stream().mapToInt(br -> br.getCaseValues().size()).sum();
        if (totalCases > 0) {
            commentTracker.markUnchanged(switchExpression);
        }
        if (totalCases > 1 && RemoveUnusedVariableUtil.checkSideEffects(switchExpression, null, new ArrayList<>())) {
            hadSideEffects = true;

            String variableName = new VariableNameGenerator(switchExpression, VariableKind.LOCAL_VARIABLE)
                .byExpression(switchExpression)
                .byType(switchExpressionType)
                .byName(isSwitchOnString ? "s" : "i")
                .generate(true);
            expressionText = variableName;
            declarationString = switchExpressionType.getCanonicalText() + ' ' + variableName + " = " + switchExpression.getText() + ';';
        }
        else {
            hadSideEffects = false;
            declarationString = null;
            expressionText = ParenthesesUtils.getPrecedence(switchExpression) > ParenthesesUtils.EQUALITY_PRECEDENCE
                ? '(' + switchExpression.getText() + ')'
                : switchExpression.getText();
        }

        StringBuilder ifStatementBuilder = new StringBuilder();
        boolean firstBranch = true;
        SwitchStatementBranch defaultBranch = null;
        for (SwitchStatementBranch branch : allBranches) {
            if (branch.isDefault()) {
                defaultBranch = branch;
            }
            else {
                dumpBranch(branch, expressionText, firstBranch, useEquals, ifStatementBuilder, commentTracker);
                firstBranch = false;
            }
        }
        boolean unwrapDefault = false;
        if (defaultBranch != null) {
            unwrapDefault =
                defaultBranch.isAlwaysExecuted() || (switchStatement.getParent() instanceof PsiCodeBlock && !mayCompleteNormally);
            if (!unwrapDefault && defaultBranch.hasStatements()) {
                ifStatementBuilder.append("else ");
                dumpBody(defaultBranch, ifStatementBuilder, commentTracker);
            }
        }
        String ifStatementText = ifStatementBuilder.toString();
        if (ifStatementText.isEmpty()) {
            if (!unwrapDefault) {
                return;
            }
            ifStatementText = ";";
        }
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        PsiCodeBlock parent = ObjectUtil.tryCast(switchStatement.getParent(), PsiCodeBlock.class);
        if (unwrapDefault || hadSideEffects) {
            if (parent == null) {
                commentTracker.grabComments(switchStatement);
                switchStatement = BlockUtils.expandSingleStatementToBlockStatement(switchStatement);
                parent = (PsiCodeBlock)(switchStatement.getParent());
            }
        }
        JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
        if (hadSideEffects) {
            PsiStatement declarationStatement = factory.createStatementFromText(declarationString, switchStatement);
            javaCodeStyleManager.shortenClassReferences(parent.addBefore(declarationStatement, switchStatement));
        }
        PsiStatement ifStatement = factory.createStatementFromText(ifStatementText, switchStatement);
        if (unwrapDefault) {
            PsiElement addedIf = parent.addBefore(ifStatement, switchStatement);
            StringBuilder sb = new StringBuilder();
            dumpBody(defaultBranch, sb, commentTracker);
            PsiBlockStatement defaultBody = (PsiBlockStatement)factory.createStatementFromText(sb.toString(), switchStatement);
            if (!BlockUtils.containsConflictingDeclarations(Objects.requireNonNull(switchStatement.getBody()), parent)) {
                commentTracker.grabComments(switchStatement);
                BlockUtils.inlineCodeBlock(switchStatement, defaultBody.getCodeBlock());
            }
            else {
                commentTracker.replace(switchStatement, defaultBody);
            }
            commentTracker.insertCommentsBefore(addedIf);
            if (ifStatementText.equals(";")) {
                addedIf.delete();
            }
            else {
                javaCodeStyleManager.shortenClassReferences(addedIf);
            }
        }
        else {
            javaCodeStyleManager.shortenClassReferences(commentTracker.replaceAndRestoreComments(switchStatement, ifStatement));
        }
    }

    @Nonnull
    @RequiredReadAction
    private static List<SwitchStatementBranch> extractBranches(
        CommentTracker commentTracker,
        PsiCodeBlock body,
        Set<PsiSwitchLabelStatementBase> fallThroughTargets
    ) {
        List<SwitchStatementBranch> openBranches = new ArrayList<>();
        Set<PsiElement> declaredElements = new HashSet<>();
        List<SwitchStatementBranch> allBranches = new ArrayList<>();
        SwitchStatementBranch currentBranch = null;
        PsiElement[] children = body.getChildren();
        List<PsiSwitchLabelStatementBase> labels = PsiTreeUtil.getChildrenOfTypeAsList(body, PsiSwitchLabelStatementBase.class);
        boolean defaultAlwaysExecuted = !labels.isEmpty() &&
            Objects.requireNonNull(ContainerUtil.getLastItem(labels)).isDefaultCase() &&
            fallThroughTargets.containsAll(labels.subList(1, labels.size()));
        for (int i = 1; i < children.length - 1; i++) {
            PsiElement statement = children[i];
            if (statement instanceof PsiSwitchLabelStatement label) {
                if (currentBranch == null || !fallThroughTargets.contains(statement)) {
                    openBranches.clear();
                    currentBranch = new SwitchStatementBranch();
                    currentBranch.addPendingDeclarations(declaredElements);
                    allBranches.add(currentBranch);
                    openBranches.add(currentBranch);
                }
                else if (currentBranch.hasStatements()) {
                    currentBranch = new SwitchStatementBranch();
                    allBranches.add(currentBranch);
                    openBranches.add(currentBranch);
                }
                if (label.isDefaultCase() && defaultAlwaysExecuted) {
                    openBranches.retainAll(Collections.singleton(currentBranch));
                }
                currentBranch.addCaseValues(label, defaultAlwaysExecuted, commentTracker);
            }
            else if (statement instanceof PsiSwitchLabeledRuleStatement rule) {
                openBranches.clear();
                currentBranch = new SwitchStatementBranch();

                PsiStatement ruleBody = rule.getBody();
                if (ruleBody != null) {
                    currentBranch.addStatement(ruleBody);
                }
                currentBranch.addCaseValues(rule, defaultAlwaysExecuted, commentTracker);
                openBranches.add(currentBranch);
                allBranches.add(currentBranch);
            }
            else if (statement instanceof PsiStatement psiStatement) {
                if (statement instanceof PsiDeclarationStatement declarationStatement) {
                    Collections.addAll(declaredElements, declarationStatement.getDeclaredElements());
                }
                for (SwitchStatementBranch branch : openBranches) {
                    branch.addStatement(psiStatement);
                }
            }
            else {
                for (SwitchStatementBranch branch : openBranches) {
                    if (statement instanceof PsiWhiteSpace) {
                        branch.addWhiteSpace(statement);
                    }
                    else {
                        branch.addComment(statement);
                    }
                }
            }
        }
        return allBranches;
    }

    private static Set<PsiSwitchLabelStatementBase> getFallThroughTargets(PsiCodeBlock body) {
        return StreamEx.of(body.getStatements())
            .pairMap(
                (s1, s2) -> s2 instanceof PsiSwitchLabelStatement switchLabelStmt2
                    && !(s1 instanceof PsiSwitchLabeledRuleStatement)
                    && ControlFlowUtils.statementMayCompleteNormally(s1) ? switchLabelStmt2 : null
            )
            .nonNull()
            .collect(Collectors.toSet());
    }

    @RequiredReadAction
    private static void dumpBranch(
        SwitchStatementBranch branch,
        String expressionText,
        boolean firstBranch,
        boolean useEquals,
        StringBuilder out,
        CommentTracker commentTracker
    ) {
        if (!firstBranch) {
            out.append("else ");
        }
        dumpCaseValues(expressionText, branch.getCaseValues(), useEquals, out);
        dumpBody(branch, out, commentTracker);
    }

    private static void dumpCaseValues(String expressionText, List<String> caseValues, boolean useEquals, StringBuilder out) {
        out.append("if(");
        boolean firstCaseValue = true;
        for (String caseValue : caseValues) {
            if (!firstCaseValue) {
                out.append("||");
            }
            firstCaseValue = false;
            if (useEquals) {
                out.append(caseValue).append(".equals(").append(expressionText).append(')');
            }
            else {
                out.append(expressionText).append("==").append(caseValue);
            }
        }
        out.append(')');
    }

    @RequiredReadAction
    private static void dumpBody(SwitchStatementBranch branch, StringBuilder out, CommentTracker commentTracker) {
        List<PsiElement> bodyStatements = branch.getBodyElements();
        out.append('{');
        if (!bodyStatements.isEmpty()) {
            PsiElement firstBodyElement = bodyStatements.get(0);
            PsiElement prev = PsiTreeUtil.skipWhitespacesAndCommentsBackward(firstBodyElement);
            if (prev instanceof PsiSwitchLabelStatementBase switchLabelStatementBase) {
                PsiExpressionList values = switchLabelStatementBase.getCaseValues();
                if (values != null) {
                    out.append(CommentTracker.commentsBetween(values, firstBodyElement));
                }
            }
        }
        for (PsiElement element : branch.getPendingDeclarations()) {
            if (ReferencesSearch.search(element, new LocalSearchScope(bodyStatements.toArray(PsiElement.EMPTY_ARRAY)))
                .findFirst() != null) {
                if (element instanceof PsiVariable variable) {
                    out.append(variable.getType().getCanonicalText()).append(' ').append(variable.getName()).append(';');
                }
                else {
                    // Class
                    out.append(element.getText());
                }
            }
        }

        for (PsiElement bodyStatement : bodyStatements) {
            if (bodyStatement instanceof PsiBlockStatement blockStmt) {
                PsiCodeBlock codeBlock = blockStmt.getCodeBlock();
                PsiElement start = PsiTreeUtil.skipWhitespacesForward(codeBlock.getFirstBodyElement());
                PsiElement end = PsiTreeUtil.skipWhitespacesBackward(codeBlock.getLastBodyElement());
                if (start != null && end != null && start != codeBlock.getRBrace()) {
                    for (PsiElement child = start; child != null; child = child.getNextSibling()) {
                        out.append(commentTracker.text(child));
                        if (child == end) {
                            break;
                        }
                    }
                }
            }
            else {
                out.append(commentTracker.text(bodyStatement));
            }
        }
        out.append("\n").append("}");
    }
}
