/*
 * Copyright 2003-2016 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.analysis.impl.codeInspection;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.JavaPsiPatternUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;

public class ControlFlowUtils {

    private ControlFlowUtils() {
    }

    public static boolean isElseIf(PsiIfStatement ifStatement) {
        final PsiElement parent = ifStatement.getParent();
        if (!(parent instanceof PsiIfStatement)) {
            return false;
        }
        final PsiIfStatement parentStatement = (PsiIfStatement) parent;
        final PsiStatement elseBranch = parentStatement.getElseBranch();
        return ifStatement.equals(elseBranch);
    }

    public static boolean statementMayCompleteNormally(@Nullable PsiStatement statement) {
        return statementMayCompleteNormally(statement, null);
    }

    private static boolean statementMayCompleteNormally(@Nullable PsiStatement statement, @Nullable PsiMethod psiMethod) {
        if (statement == null) {
            return true;
        }
        if (statement instanceof PsiBreakStatement || statement instanceof PsiContinueStatement || statement instanceof PsiYieldStatement ||
            statement instanceof PsiReturnStatement || statement instanceof PsiThrowStatement) {
            return false;
        }
        else if (statement instanceof PsiExpressionListStatement || statement instanceof PsiEmptyStatement ||
            statement instanceof PsiAssertStatement || statement instanceof PsiDeclarationStatement ||
            statement instanceof PsiSwitchLabelStatement || statement instanceof PsiForeachStatementBase) {
            return true;
        }
        else if (statement instanceof final PsiExpressionStatement expressionStatement) {
            final PsiExpression expression = expressionStatement.getExpression();
            if (!(expression instanceof final PsiMethodCallExpression methodCallExpression)) {
                return true;
            }
            final PsiMethod method = methodCallExpression.resolveMethod();
            if (method == null) {
                return true;
            }
            if (method.equals(psiMethod)) {
                return false;
            }
            final String methodName = method.getName();
            if (!methodName.equals("exit")) {
                return true;
            }
            final PsiClass aClass = method.getContainingClass();
            if (aClass == null) {
                return true;
            }
            final String className = aClass.getQualifiedName();
            return !"java.lang.System".equals(className);
        }
        else if (statement instanceof PsiForStatement forStatement) {
            return forStatementMayCompleteNormally(forStatement);
        }
        else if (statement instanceof PsiWhileStatement whileStatement) {
            return whileStatementMayCompleteNormally(whileStatement);
        }
        else if (statement instanceof PsiDoWhileStatement doWhileStatement) {
            return doWhileStatementMayCompleteNormally(doWhileStatement, psiMethod);
        }
        else if (statement instanceof PsiSynchronizedStatement synchronizedStatement) {
            return codeBlockMayCompleteNormally(synchronizedStatement.getBody(), psiMethod);
        }
        else if (statement instanceof PsiBlockStatement block) {
            return codeBlockMayCompleteNormally(block.getCodeBlock(), psiMethod);
        }
        else if (statement instanceof PsiLabeledStatement labeled) {
            return labeledStatementMayCompleteNormally(labeled, psiMethod);
        }
        else if (statement instanceof PsiIfStatement ifStatement) {
            return ifStatementMayCompleteNormally(ifStatement, psiMethod);
        }
        else if (statement instanceof PsiTryStatement tryStatement) {
            return tryStatementMayCompleteNormally(tryStatement, psiMethod);
        }
        else if (statement instanceof PsiSwitchStatement switchStatement) {
            return switchStatementMayCompleteNormally(switchStatement, psiMethod);
        }
        else if (statement instanceof PsiSwitchLabeledRuleStatement rule) {
            PsiStatement body = rule.getBody();
            return body != null && statementMayCompleteNormally(body, psiMethod);
        }
        else if (statement instanceof PsiTemplateStatement || statement instanceof PsiClassLevelDeclarationStatement) {
            return true;
        }
        else {
            assert false : "unknown statement type: " + statement.getClass();
            return true;
        }
    }

    private static boolean doWhileStatementMayCompleteNormally(@Nonnull PsiDoWhileStatement loopStatement, @Nullable PsiMethod method) {
        final PsiExpression condition = loopStatement.getCondition();
        final Object value = ExpressionUtils.computeConstantExpression(condition);
        final PsiStatement body = loopStatement.getBody();
        return statementMayCompleteNormally(body, method) && value != Boolean.TRUE
            || statementContainsBreakToStatementOrAncestor(loopStatement) || statementContainsContinueToAncestor(loopStatement);
    }

    private static boolean statementContainsBreakToStatementOrAncestor(@Nonnull PsiStatement statement) {
        final BreakFinder breakFinder = new BreakFinder(statement, true);
        statement.accept(breakFinder);
        return breakFinder.breakFound();
    }

    private static boolean whileStatementMayCompleteNormally(@Nonnull PsiWhileStatement loopStatement) {
        final PsiExpression condition = loopStatement.getCondition();
        final Object value = ExpressionUtils.computeConstantExpression(condition);
        return value != Boolean.TRUE || statementIsBreakTarget(loopStatement) || statementContainsContinueToAncestor(loopStatement);
    }

    private static boolean forStatementMayCompleteNormally(@Nonnull PsiForStatement loopStatement) {
        if (statementIsBreakTarget(loopStatement)) {
            return true;
        }
        if (statementContainsContinueToAncestor(loopStatement)) {
            return true;
        }
        final PsiExpression condition = loopStatement.getCondition();
        if (condition == null) {
            return false;
        }
        final Object value = ExpressionUtils.computeConstantExpression(condition);
        return Boolean.TRUE != value;
    }

    private static boolean switchStatementMayCompleteNormally(@Nonnull PsiSwitchStatement switchStatement, @Nullable PsiMethod method) {
        if (statementIsBreakTarget(switchStatement)) {
            return true;
        }
        final PsiExpression selectorExpression = switchStatement.getExpression();
        if (selectorExpression == null) {
            return true;
        }
        final PsiType selectorType = selectorExpression.getType();
        if (selectorType == null) {
            return true;
        }
        final PsiCodeBlock body = switchStatement.getBody();
        if (body == null) {
            return true;
        }
        final PsiStatement[] statements = body.getStatements();
        if (statements.length == 0) {
            return true;
        }
        int numCases = 0;
        boolean hasDefaultCase = false, hasUnconditionalPattern = false;
        for (PsiStatement statement : statements) {
            if (statement instanceof PsiSwitchLabelStatementBase switchLabelStatement) {
                if (statement instanceof PsiSwitchLabelStatement) {
                    numCases++;
                }
                if (hasDefaultCase || hasUnconditionalPattern) {
                    continue;
                }
                if (switchLabelStatement.isDefaultCase()) {
                    hasDefaultCase = true;
                    continue;
                }
                // this information doesn't exist in spec draft (14.22) for pattern in switch as expected
                // but for now javac considers the switch statement containing at least either case default label element or an unconditional pattern "incomplete normally"
                PsiCaseLabelElementList labelElementList = switchLabelStatement.getCaseLabelElementList();
                if (labelElementList == null) {
                    continue;
                }
                for (PsiCaseLabelElement labelElement : labelElementList.getElements()) {
                    if (labelElement instanceof PsiDefaultCaseLabelElement) {
                        hasDefaultCase = true;
                    }
                    else if (labelElement instanceof PsiPattern) {
                        hasUnconditionalPattern = JavaPsiPatternUtil.isUnconditionalForType(labelElement, selectorType);
                    }
                }
            }
            else if (statement instanceof final PsiBreakStatement breakStatement && breakStatement.getLabelIdentifier() == null) {
                return true;
            }
        }
        // todo actually there is no information about an impact of enum constants on switch statements being complete normally in spec (Unreachable statements)
        // todo comparing to javac that produces some false-negative highlighting in enum switch statements containing all possible constants
        final boolean isEnum = isEnumSwitch(switchStatement);
        if (!hasDefaultCase && !hasUnconditionalPattern && !isEnum) {
            return true;
        }
        if (!hasDefaultCase && !hasUnconditionalPattern) {
            final PsiClass aClass = ((PsiClassType) selectorType).resolve();
            if (aClass == null) {
                return true;
            }
            if (!hasChildrenOfTypeCount(aClass, numCases, PsiEnumConstant.class)) {
                return true;
            }
        }
        // todo replace the code and comments below with the method that helps to understand whether
        // todo we need to check every statement or only the last statement in the code block
        // 14.22. Unreachable Statements
        // We need to check every rule's body not just the last one if the switch block includes the switch rules
        boolean isLabeledRuleSwitch = statements[0] instanceof PsiSwitchLabeledRuleStatement;
        if (isLabeledRuleSwitch) {
            for (PsiStatement statement : statements) {
                if (statementMayCompleteNormally(statement, method)) {
                    return true;
                }
            }
            return false;
        }
        return statementMayCompleteNormally(statements[statements.length - 1], method);
    }

    private static boolean isEnumSwitch(PsiSwitchStatement statement) {
        final PsiExpression expression = statement.getExpression();
        if (expression == null) {
            return false;
        }
        final PsiType type = expression.getType();
        if (type == null) {
            return false;
        }
        if (!(type instanceof PsiClassType)) {
            return false;
        }
        final PsiClass aClass = ((PsiClassType) type).resolve();
        return aClass != null && aClass.isEnum();
    }

    private static boolean tryStatementMayCompleteNormally(@Nonnull PsiTryStatement tryStatement, @Nullable PsiMethod method) {
        final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
        if (finallyBlock != null) {
            if (!codeBlockMayCompleteNormally(finallyBlock, method)) {
                return false;
            }
        }
        final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
        if (codeBlockMayCompleteNormally(tryBlock, method)) {
            return true;
        }
        final PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
        for (final PsiCodeBlock catchBlock : catchBlocks) {
            if (codeBlockMayCompleteNormally(catchBlock, method)) {
                return true;
            }
        }
        return false;
    }

    private static boolean ifStatementMayCompleteNormally(@Nonnull PsiIfStatement ifStatement, @Nullable PsiMethod method) {
        final PsiExpression condition = ifStatement.getCondition();
        final Object value = ExpressionUtils.computeConstantExpression(condition);
        final PsiStatement thenBranch = ifStatement.getThenBranch();
        if (value == Boolean.TRUE) {
            return statementMayCompleteNormally(thenBranch, method);
        }
        final PsiStatement elseBranch = ifStatement.getElseBranch();
        if (value == Boolean.FALSE) {
            return statementMayCompleteNormally(elseBranch, method);
        }
        // process branch with fewer statements first
        PsiStatement branch1;
        PsiStatement branch2;
        if ((thenBranch == null ? 0 : thenBranch.getTextLength()) < (elseBranch == null ? 0 : elseBranch.getTextLength())) {
            branch1 = thenBranch;
            branch2 = elseBranch;
        }
        else {
            branch2 = thenBranch;
            branch1 = elseBranch;
        }
        return statementMayCompleteNormally(branch1, method) || statementMayCompleteNormally(branch2, method);
    }

    private static boolean labeledStatementMayCompleteNormally(@Nonnull PsiLabeledStatement labeledStatement, @Nullable PsiMethod method) {
        final PsiStatement statement = labeledStatement.getStatement();
        if (statement == null) {
            return false;
        }
        return statementMayCompleteNormally(statement, method) || statementContainsBreakToStatementOrAncestor(statement);
    }

    public static boolean codeBlockMayCompleteNormally(@Nullable PsiCodeBlock block) {
        return codeBlockMayCompleteNormally(block, null);
    }

    private static boolean codeBlockMayCompleteNormally(@Nullable PsiCodeBlock block, @Nullable PsiMethod method) {
        if (block == null) {
            return true;
        }
        final PsiStatement[] statements = block.getStatements();
        for (final PsiStatement statement : statements) {
            if (!statementMayCompleteNormally(statement, method)) {
                return false;
            }
        }
        return true;
    }

    private static boolean statementIsBreakTarget(@Nonnull PsiStatement statement) {
        final BreakFinder breakFinder = new BreakFinder(statement, false);
        statement.accept(breakFinder);
        return breakFinder.breakFound();
    }

    private static boolean statementContainsContinueToAncestor(@Nonnull PsiStatement statement) {
        PsiElement parent = statement.getParent();
        while (parent instanceof PsiLabeledStatement) {
            statement = (PsiStatement) parent;
            parent = parent.getParent();
        }
        final ContinueToAncestorFinder continueToAncestorFinder = new ContinueToAncestorFinder(statement);
        statement.accept(continueToAncestorFinder);
        return continueToAncestorFinder.continueToAncestorFound();
    }

    public static boolean containsReturn(@Nonnull PsiElement element) {
        final ReturnFinder returnFinder = new ReturnFinder();
        element.accept(returnFinder);
        return returnFinder.returnFound();
    }

    public static boolean statementIsContinueTarget(@Nonnull PsiStatement statement) {
        final ContinueFinder continueFinder = new ContinueFinder(statement);
        statement.accept(continueFinder);
        return continueFinder.continueFound();
    }

    public static boolean containsSystemExit(@Nonnull PsiElement element) {
        final SystemExitFinder systemExitFinder = new SystemExitFinder();
        element.accept(systemExitFinder);
        return systemExitFinder.exitFound();
    }

    public static boolean elementContainsCallToMethod(PsiElement context,
                                                      String containingClassName,
                                                      PsiType returnType,
                                                      String methodName,
                                                      PsiType... parameterTypes) {
        final MethodCallFinder methodCallFinder = new MethodCallFinder(containingClassName, returnType, methodName, parameterTypes);
        context.accept(methodCallFinder);
        return methodCallFinder.containsCallToMethod();
    }

    public static boolean isInLoop(@Nonnull PsiElement element) {
        final PsiLoopStatement loopStatement = PsiTreeUtil.getParentOfType(element, PsiLoopStatement.class, true, PsiClass.class);
        if (loopStatement == null) {
            return false;
        }
        final PsiStatement body = loopStatement.getBody();
        return body != null && PsiTreeUtil.isAncestor(body, element, true);
    }

    public static boolean isInFinallyBlock(@Nonnull PsiElement element) {
        PsiElement currentElement = element;
        while (true) {
            final PsiTryStatement tryStatement =
                PsiTreeUtil.getParentOfType(currentElement, PsiTryStatement.class, true, PsiClass.class, PsiLambdaExpression.class);
            if (tryStatement == null) {
                return false;
            }
            final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
            if (finallyBlock != null) {
                if (PsiTreeUtil.isAncestor(finallyBlock, currentElement, true)) {
                    final PsiMethod elementMethod = PsiTreeUtil.getParentOfType(currentElement, PsiMethod.class);
                    final PsiMethod finallyMethod = PsiTreeUtil.getParentOfType(finallyBlock, PsiMethod.class);
                    return elementMethod != null && elementMethod.equals(finallyMethod);
                }
            }
            currentElement = tryStatement;
        }
    }

    public static boolean isInCatchBlock(@Nonnull PsiElement element) {
        return PsiTreeUtil.getParentOfType(element, PsiCatchSection.class, true, PsiClass.class) != null;
    }

    public static boolean isInExitStatement(@Nonnull PsiExpression expression) {
        return isInReturnStatementArgument(expression) || isInThrowStatementArgument(expression);
    }

    private static boolean isInReturnStatementArgument(@Nonnull PsiExpression expression) {
        return PsiTreeUtil.getParentOfType(expression, PsiReturnStatement.class) != null;
    }

    public static boolean isInThrowStatementArgument(@Nonnull PsiExpression expression) {
        return PsiTreeUtil.getParentOfType(expression, PsiThrowStatement.class) != null;
    }

    @Nullable
    public static PsiStatement stripBraces(@Nullable PsiStatement statement) {
        if (statement instanceof PsiBlockStatement) {
            final PsiBlockStatement block = (PsiBlockStatement) statement;
            final PsiStatement onlyStatement = getOnlyStatementInBlock(block.getCodeBlock());
            return (onlyStatement != null) ? onlyStatement : block;
        }
        else {
            return statement;
        }
    }

    public static boolean statementCompletesWithStatement(@Nonnull PsiElement containingStatement, @Nonnull PsiStatement statement) {
        PsiElement statementToCheck = statement;
        while (true) {
            if (statementToCheck.equals(containingStatement)) {
                return true;
            }
            final PsiElement container = getContainingStatementOrBlock(statementToCheck);
            if (container == null) {
                return false;
            }
            if (container instanceof PsiCodeBlock) {
                if (!statementIsLastInBlock((PsiCodeBlock) container, (PsiStatement) statementToCheck)) {
                    return false;
                }
            }
            if (container instanceof PsiLoopStatement) {
                return false;
            }
            statementToCheck = container;
        }
    }

    public static boolean blockCompletesWithStatement(@Nonnull PsiCodeBlock body, @Nonnull PsiStatement statement) {
        PsiElement statementToCheck = statement;
        while (true) {
            if (statementToCheck == null) {
                return false;
            }
            final PsiElement container = getContainingStatementOrBlock(statementToCheck);
            if (container == null) {
                return false;
            }
            if (container instanceof PsiLoopStatement) {
                return false;
            }
            if (container instanceof PsiCodeBlock) {
                if (!statementIsLastInBlock((PsiCodeBlock) container, (PsiStatement) statementToCheck)) {
                    return false;
                }
                if (container.equals(body)) {
                    return true;
                }
                statementToCheck = PsiTreeUtil.getParentOfType(container, PsiStatement.class);
            }
            else {
                statementToCheck = container;
            }
        }
    }

    @Nullable
    private static PsiElement getContainingStatementOrBlock(@Nonnull PsiElement statement) {
        return PsiTreeUtil.getParentOfType(statement, PsiStatement.class, PsiCodeBlock.class);
    }

    private static boolean statementIsLastInBlock(@Nonnull PsiCodeBlock block, @Nonnull PsiStatement statement) {
        for (PsiElement child = block.getLastChild(); child != null; child = child.getPrevSibling()) {
            if (!(child instanceof PsiStatement)) {
                continue;
            }
            final PsiStatement childStatement = (PsiStatement) child;
            if (statement.equals(childStatement)) {
                return true;
            }
            if (!(statement instanceof PsiEmptyStatement)) {
                return false;
            }
        }
        return false;
    }

    @Nullable
    public static PsiStatement getFirstStatementInBlock(@Nullable PsiCodeBlock codeBlock) {
        return PsiTreeUtil.getChildOfType(codeBlock, PsiStatement.class);
    }

    @Nullable
    public static PsiStatement getLastStatementInBlock(@Nullable PsiCodeBlock codeBlock) {
        return getLastChildOfType(codeBlock, PsiStatement.class);
    }

    private static <T extends PsiElement> T getLastChildOfType(@Nullable PsiElement element, @Nonnull Class<T> aClass) {
        if (element == null) {
            return null;
        }
        for (PsiElement child = element.getLastChild(); child != null; child = child.getPrevSibling()) {
            if (aClass.isInstance(child)) {
                //noinspection unchecked
                return (T) child;
            }
        }
        return null;
    }

    /**
     * @return null, if zero or more than one statements in the specified code block.
     */
    @Nullable
    public static PsiStatement getOnlyStatementInBlock(@Nullable PsiCodeBlock codeBlock) {
        return getOnlyChildOfType(codeBlock, PsiStatement.class);
    }

    public static <T extends PsiElement> T getOnlyChildOfType(@Nullable PsiElement element, @Nonnull Class<T> aClass) {
        if (element == null) {
            return null;
        }
        T result = null;
        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (aClass.isInstance(child)) {
                if (result == null) {
                    //noinspection unchecked
                    result = (T) child;
                }
                else {
                    return null;
                }
            }
        }
        return result;
    }

    public static boolean hasStatementCount(@Nullable PsiCodeBlock codeBlock, int count) {
        return hasChildrenOfTypeCount(codeBlock, count, PsiStatement.class);
    }

    public static <T extends PsiElement> boolean hasChildrenOfTypeCount(@Nullable PsiElement element, int count, @Nonnull Class<T> aClass) {
        if (element == null) {
            return false;
        }
        int i = 0;
        for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (aClass.isInstance(child)) {
                i++;
                if (i > count) {
                    return false;
                }
            }
        }
        return i == count;
    }

    public static boolean isEmptyCodeBlock(PsiCodeBlock codeBlock) {
        return hasStatementCount(codeBlock, 0);
    }

    public static boolean methodAlwaysThrowsException(@Nonnull PsiMethod method) {
        final PsiCodeBlock body = method.getBody();
        if (body == null) {
            return true;
        }
        return !containsReturn(body) && !codeBlockMayCompleteNormally(body);
    }

    public static boolean lambdaExpressionAlwaysThrowsException(PsiLambdaExpression expression) {
        final PsiElement body = expression.getBody();
        if (body instanceof PsiExpression) {
            return false;
        }
        if (!(body instanceof PsiCodeBlock)) {
            return true;
        }
        final PsiCodeBlock codeBlock = (PsiCodeBlock) body;
        return !containsReturn(codeBlock) && !codeBlockMayCompleteNormally(codeBlock);
    }

    public static boolean statementContainsNakedBreak(PsiStatement statement) {
        if (statement == null) {
            return false;
        }
        final NakedBreakFinder breakFinder = new NakedBreakFinder();
        statement.accept(breakFinder);
        return breakFinder.breakFound();
    }

    /**
     * Checks whether the given statement effectively breaks given loop. Returns true
     * if the statement is {@link PsiBreakStatement} having given loop as a target. Also may return
     * true in other cases if the statement is semantically equivalent to break like this:
     * <p>
     * <pre>{@code
     * int myMethod(int[] data) {
     *   for(int val : data) {
     *     if(val == 5) {
     *       System.out.println(val);
     *       return 0; // this statement is semantically equivalent to break.
     *     }
     *   }
     *   return 0;
     * }}</pre>
     *
     * @param statement statement which may break the loop
     * @param loop      a loop to break
     * @return true if the statement actually breaks the loop
     */
    @Contract("null, _ -> false")
    public static boolean statementBreaksLoop(PsiStatement statement, PsiLoopStatement loop) {
        if (statement instanceof PsiBreakStatement) {
            return ((PsiBreakStatement) statement).findExitedStatement() == loop;
        }
        if (statement instanceof PsiReturnStatement) {
            PsiExpression returnValue = ((PsiReturnStatement) statement).getReturnValue();
            PsiElement cur = loop;
            for (PsiElement parent = cur.getParent(); ; parent = cur.getParent()) {
                if (parent instanceof PsiLabeledStatement) {
                    cur = parent;
                }
                else if (parent instanceof PsiCodeBlock) {
                    PsiCodeBlock block = (PsiCodeBlock) parent;
                    PsiStatement[] statements = block.getStatements();
                    if (block.getParent() instanceof PsiBlockStatement && statements.length > 0 && statements[statements.length - 1] == cur) {
                        cur = block.getParent();
                    }
                    else {
                        break;
                    }
                }
                else if (parent instanceof PsiIfStatement) {
                    if (cur == ((PsiIfStatement) parent).getThenBranch() || cur == ((PsiIfStatement) parent).getElseBranch()) {
                        cur = parent;
                    }
                    else {
                        break;
                    }
                }
                else {
                    break;
                }
            }
            PsiElement nextElement = PsiTreeUtil.skipSiblingsForward(cur, PsiComment.class, PsiWhiteSpace.class);
            if (nextElement instanceof PsiReturnStatement) {
                return EquivalenceChecker.getCanonicalPsiEquivalence()
                    .expressionsAreEquivalent(returnValue, ((PsiReturnStatement) nextElement).getReturnValue());
            }
            if (nextElement == null && returnValue == null && cur.getParent() instanceof PsiMethod) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether control flow after executing given statement will definitely not go into the next iteration of given loop.
     *
     * @param statement executed statement. It's not checked whether this statement itself breaks the loop.
     * @param loop      a surrounding loop. Must be parent of statement
     * @return true if it can be statically defined that next loop iteration will not be executed.
     */
    @Contract("null, _ -> false")
    public static boolean flowBreaksLoop(PsiStatement statement, PsiLoopStatement loop) {
        if (statement == null || statement == loop) {
            return false;
        }
        for (PsiStatement sibling = nextExecutedStatement(statement); sibling != null; sibling = nextExecutedStatement(sibling)) {
            if (sibling instanceof PsiContinueStatement) {
                return false;
            }
            if (sibling instanceof PsiThrowStatement || sibling instanceof PsiReturnStatement) {
                return true;
            }
            if (sibling instanceof PsiBreakStatement) {
                PsiBreakStatement breakStatement = (PsiBreakStatement) sibling;
                PsiStatement exitedStatement = breakStatement.findExitedStatement();
                if (exitedStatement == loop) {
                    return true;
                }
                return flowBreaksLoop(exitedStatement, loop);
            }
        }
        return false;
    }

    /**
     * Returns true if given element is an empty statement
     *
     * @param element          element to check
     * @param commentIsContent if true, empty statement containing comments is not considered empty
     * @param emptyBlocks      if true, empty block (or nested empty block like {@code {{}}}) is considered an empty statement
     * @return true if given element is an empty statement
     */
    public static boolean isEmpty(PsiElement element, boolean commentIsContent, boolean emptyBlocks) {
        if (!commentIsContent && element instanceof PsiComment) {
            return true;
        }
        else if (element instanceof PsiEmptyStatement) {
            return !commentIsContent ||
                PsiTreeUtil.getChildOfType(element, PsiComment.class) == null &&
                    !(PsiTreeUtil.skipWhitespacesBackward(element) instanceof PsiComment);
        }
        else if (element instanceof PsiWhiteSpace) {
            return true;
        }
        else if (element instanceof PsiBlockStatement) {
            final PsiBlockStatement block = (PsiBlockStatement) element;
            return isEmpty(block.getCodeBlock(), commentIsContent, emptyBlocks);
        }
        else if (emptyBlocks && element instanceof PsiCodeBlock) {
            final PsiCodeBlock codeBlock = (PsiCodeBlock) element;
            final PsiElement[] children = codeBlock.getChildren();
            if (children.length == 2) {
                return true;
            }
            for (int i = 1; i < children.length - 1; i++) {
                final PsiElement child = children[i];
                if (!isEmpty(child, commentIsContent, true)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    @Nullable
    private static PsiStatement nextExecutedStatement(PsiStatement statement) {
        PsiStatement next = PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class);
        while (next instanceof PsiBlockStatement) {
            PsiStatement[] statements = ((PsiBlockStatement) next).getCodeBlock().getStatements();
            if (statements.length == 0) {
                break;
            }
            next = statements[0];
        }
        if (next == null) {
            PsiElement parent = statement.getParent();
            if (parent instanceof PsiCodeBlock) {
                PsiElement gParent = parent.getParent();
                if (gParent instanceof PsiBlockStatement || gParent instanceof PsiSwitchStatement) {
                    return nextExecutedStatement((PsiStatement) gParent);
                }
            }
            else if (parent instanceof PsiLabeledStatement || parent instanceof PsiIfStatement || parent instanceof PsiSwitchLabelStatement || parent instanceof PsiSwitchStatement) {
                return nextExecutedStatement((PsiStatement) parent);
            }
        }
        return next;
    }

    private static class NakedBreakFinder extends JavaRecursiveElementWalkingVisitor {
        private boolean m_found;

        private boolean breakFound() {
            return m_found;
        }

        @Override
        public void visitElement(PsiElement element) {
            if (m_found) {
                return;
            }
            super.visitElement(element);
        }

        @Override
        public void visitReferenceExpression(PsiReferenceExpression expression) {
        }

        @Override
        public void visitBreakStatement(PsiBreakStatement statement) {
            if (statement.getLabelIdentifier() != null) {
                return;
            }
            m_found = true;
        }

        @Override
        public void visitDoWhileStatement(PsiDoWhileStatement statement) {
            // don't drill down
        }

        @Override
        public void visitForStatement(PsiForStatement statement) {
            // don't drill down
        }

        @Override
        public void visitForeachStatement(PsiForeachStatement statement) {
            // don't drill down
        }

        @Override
        public void visitWhileStatement(PsiWhileStatement statement) {
            // don't drill down
        }

        @Override
        public void visitSwitchStatement(PsiSwitchStatement statement) {
            // don't drill down
        }
    }

    private static class SystemExitFinder extends JavaRecursiveElementWalkingVisitor {

        private boolean m_found;

        private boolean exitFound() {
            return m_found;
        }

        @Override
        public void visitClass(@Nonnull PsiClass aClass) {
            // do nothing to keep from drilling into inner classes
        }

        @Override
        public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
            if (m_found) {
                return;
            }
            super.visitMethodCallExpression(expression);
            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return;
            }
            @NonNls final String methodName = method.getName();
            if (!methodName.equals("exit")) {
                return;
            }
            final PsiClass aClass = method.getContainingClass();
            if (aClass == null) {
                return;
            }
            final String className = aClass.getQualifiedName();
            if (!"java.lang.System".equals(className) && !"java.lang.Runtime".equals(className)) {
                return;
            }
            m_found = true;
        }
    }

    private static class ReturnFinder extends JavaRecursiveElementWalkingVisitor {

        private boolean m_found;

        private boolean returnFound() {
            return m_found;
        }

        @Override
        public void visitClass(@Nonnull PsiClass psiClass) {
            // do nothing, to keep drilling into inner classes
        }

        @Override
        public void visitLambdaExpression(PsiLambdaExpression expression) {
        }

        @Override
        public void visitReturnStatement(@Nonnull PsiReturnStatement returnStatement) {
            if (m_found) {
                return;
            }
            super.visitReturnStatement(returnStatement);
            m_found = true;
        }
    }

    private static class BreakFinder extends JavaRecursiveElementWalkingVisitor {

        private boolean m_found;
        private final PsiStatement m_target;
        private final boolean myAncestor;

        BreakFinder(@Nonnull PsiStatement target, boolean ancestor) {
            m_target = target;
            myAncestor = ancestor;
        }

        boolean breakFound() {
            return m_found;
        }

        @Override
        public void visitBreakStatement(@Nonnull PsiBreakStatement statement) {
            if (m_found) {
                return;
            }
            super.visitBreakStatement(statement);
            final PsiStatement exitedStatement = statement.findExitedStatement();
            if (exitedStatement == null) {
                return;
            }
            if (myAncestor) {
                if (PsiTreeUtil.isAncestor(exitedStatement, m_target, false)) {
                    m_found = true;
                }
            }
            else if (exitedStatement == m_target) {
                m_found = true;
            }
        }

        @Override
        public void visitIfStatement(@Nonnull PsiIfStatement statement) {
            if (m_found) {
                return;
            }
            final PsiExpression condition = statement.getCondition();
            final Object value = ExpressionUtils.computeConstantExpression(condition);
            if (Boolean.FALSE != value) {
                final PsiStatement thenBranch = statement.getThenBranch();
                if (thenBranch != null) {
                    thenBranch.accept(this);
                }
            }
            if (Boolean.TRUE != value) {
                final PsiStatement elseBranch = statement.getElseBranch();
                if (elseBranch != null) {
                    elseBranch.accept(this);
                }
            }
        }
    }

    private static class ContinueFinder extends JavaRecursiveElementWalkingVisitor {

        private boolean m_found;
        private final PsiStatement m_target;

        private ContinueFinder(@Nonnull PsiStatement target) {
            m_target = target;
        }

        private boolean continueFound() {
            return m_found;
        }

        @Override
        public void visitContinueStatement(@Nonnull PsiContinueStatement statement) {
            if (m_found) {
                return;
            }
            super.visitContinueStatement(statement);
            final PsiStatement continuedStatement = statement.findContinuedStatement();
            if (continuedStatement == null) {
                return;
            }
            if (PsiTreeUtil.isAncestor(continuedStatement, m_target, false)) {
                m_found = true;
            }
        }

        @Override
        public void visitIfStatement(PsiIfStatement statement) {
            if (m_found) {
                return;
            }
            final PsiExpression condition = statement.getCondition();
            final Object value = ExpressionUtils.computeConstantExpression(condition);
            if (Boolean.FALSE != value) {
                final PsiStatement thenBranch = statement.getThenBranch();
                if (thenBranch != null) {
                    thenBranch.accept(this);
                }
            }
            if (Boolean.TRUE != value) {
                final PsiStatement elseBranch = statement.getElseBranch();
                if (elseBranch != null) {
                    elseBranch.accept(this);
                }
            }
        }
    }

    private static class MethodCallFinder extends JavaRecursiveElementWalkingVisitor {

        private final String containingClassName;
        private final PsiType returnType;
        private final String methodName;
        private final PsiType[] parameterTypeNames;
        private boolean containsCallToMethod;

        private MethodCallFinder(String containingClassName, PsiType returnType, String methodName, PsiType... parameterTypeNames) {
            this.containingClassName = containingClassName;
            this.returnType = returnType;
            this.methodName = methodName;
            this.parameterTypeNames = parameterTypeNames;
        }

        @Override
        public void visitElement(PsiElement element) {
            if (containsCallToMethod) {
                return;
            }
            super.visitElement(element);
        }

        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            if (containsCallToMethod) {
                return;
            }
            super.visitMethodCallExpression(expression);
            if (!MethodCallUtils.isCallToMethod(expression, containingClassName, returnType, methodName, parameterTypeNames)) {
                return;
            }
            containsCallToMethod = true;
        }

        private boolean containsCallToMethod() {
            return containsCallToMethod;
        }
    }

    private static class ContinueToAncestorFinder extends JavaRecursiveElementWalkingVisitor {

        private final PsiStatement statement;
        private boolean found;

        private ContinueToAncestorFinder(PsiStatement statement) {
            this.statement = statement;
        }

        @Override
        public void visitElement(PsiElement element) {
            if (found) {
                return;
            }
            super.visitElement(element);
        }

        @Override
        public void visitContinueStatement(PsiContinueStatement continueStatement) {
            if (found) {
                return;
            }
            super.visitContinueStatement(continueStatement);
            final PsiIdentifier labelIdentifier = continueStatement.getLabelIdentifier();
            if (labelIdentifier == null) {
                return;
            }
            final PsiStatement continuedStatement = continueStatement.findContinuedStatement();
            if (continuedStatement == null) {
                return;
            }
            if (PsiTreeUtil.isAncestor(continuedStatement, statement, true)) {
                found = true;
            }
        }

        private boolean continueToAncestorFound() {
            return found;
        }
    }
}
