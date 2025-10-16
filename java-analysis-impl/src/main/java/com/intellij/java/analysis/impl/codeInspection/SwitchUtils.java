/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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

import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.intellij.java.language.JavaFeature;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiErrorElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SwitchUtils {
    /**
     * State of switch exhaustiveness.
     */
    public enum SwitchExhaustivenessState {
        /**
         * Switch is malformed and produces a compilation error (no body, no selector, etc.),
         * no exhaustiveness analysis is performed
         */
        MALFORMED,
        /**
         * Switch contains no labels (except probably default label)
         */
        EMPTY,
        /**
         * Switch should not be exhaustive (classic switch statement)
         */
        UNNECESSARY,
        /**
         * Switch is not exhaustive
         */
        INCOMPLETE,
        /**
         * Switch is exhaustive (complete), and adding a default branch would be a compilation error.
         * This includes a switch over boolean having both true and false branches,
         * or a switch that has an unconditional pattern branch.
         */
        EXHAUSTIVE_NO_DEFAULT,
        /**
         * Switch is exhaustive (complete), but it's possible to add a default branch.
         */
        EXHAUSTIVE_CAN_ADD_DEFAULT
    }

    private SwitchUtils() {
    }

    /**
     * Evaluates the exhaustiveness state of a switch block.
     *
     * @param switchBlock                          the PsiSwitchBlock to evaluate
     * @param considerNestedDeconstructionPatterns flag indicating whether to consider nested deconstruction patterns. It is necessary to take into account,
     *                                             because nested deconstruction patterns don't cover null values
     * @return exhaustiveness state.
     */
    public static @Nonnull SwitchExhaustivenessState evaluateSwitchCompleteness(@Nonnull PsiSwitchBlock switchBlock,
                                                                                boolean considerNestedDeconstructionPatterns) {
        PsiExpression selector = switchBlock.getExpression();
        if (selector == null) {
            return SwitchExhaustivenessState.MALFORMED;
        }
        PsiType selectorType = selector.getType();
        if (selectorType == null) {
            return SwitchExhaustivenessState.MALFORMED;
        }
        PsiCodeBlock switchBody = switchBlock.getBody();
        if (switchBody == null) {
            return SwitchExhaustivenessState.MALFORMED;
        }
        List<PsiCaseLabelElement> labelElements = StreamEx.of(JavaPsiSwitchUtil.getSwitchBranches(switchBlock)).select(PsiCaseLabelElement.class)
            .filter(element -> !(element instanceof PsiDefaultCaseLabelElement)).toList();
        if (labelElements.isEmpty()) {
            return SwitchExhaustivenessState.EMPTY;
        }
        boolean needToCheckCompleteness = ExpressionUtil.isEnhancedSwitch(switchBlock);
        boolean isEnumSelector = JavaPsiSwitchUtil.getSwitchSelectorKind(selectorType) == JavaPsiSwitchUtil.SelectorKind.ENUM;
        if (ContainerUtil.find(labelElements, element -> JavaPsiPatternUtil.isUnconditionalForType(element, selectorType)) != null) {
            return SwitchExhaustivenessState.EXHAUSTIVE_NO_DEFAULT;
        }
        if (JavaPsiSwitchUtil.isBooleanSwitchWithTrueAndFalse(switchBlock)) {
            return SwitchExhaustivenessState.EXHAUSTIVE_NO_DEFAULT;
        }
        if (!needToCheckCompleteness && !isEnumSelector) {
            return SwitchExhaustivenessState.INCOMPLETE;
        }
        // It is necessary because deconstruction patterns don't cover cases
        // when some of their components are null and deconstructionPattern too
        if (!considerNestedDeconstructionPatterns) {
            labelElements = ContainerUtil.filter(
                labelElements, label -> !(label instanceof PsiDeconstructionPattern deconstructionPattern &&
                    ContainerUtil.or(
                        deconstructionPattern.getDeconstructionList().getDeconstructionComponents(),
                        component -> component instanceof PsiDeconstructionPattern)));
        }
        boolean hasError = hasExhaustivenessError(switchBlock, labelElements);
        // if a switch block is needed to check completeness and switch is incomplete we let highlighting to inform about it as it's a compilation error
        if (!hasError) {
            return SwitchExhaustivenessState.EXHAUSTIVE_CAN_ADD_DEFAULT;
        }
        if (needToCheckCompleteness) {
            return SwitchExhaustivenessState.UNNECESSARY;
        }
        return SwitchExhaustivenessState.INCOMPLETE;
    }

    /**
     * @param block switch block to analyze
     * @return true if this block is not exhaustive while it should be
     */
    public static boolean hasExhaustivenessError(@Nonnull PsiSwitchBlock block) {
        return hasExhaustivenessError(block, JavaPsiSwitchUtil.getCaseLabelElements(block));
    }

    /**
     * @param block    switch block to analyze
     * @param elements list of labels to analyze (can be a subset of all labels of the block)
     * @return true if this block is not exhaustive while it should be
     */
    public static boolean hasExhaustivenessError(@Nonnull PsiSwitchBlock block, @Nonnull List<PsiCaseLabelElement> elements) {
        PsiExpression selector = block.getExpression();
        if (selector == null) {
            return false;
        }
        PsiType selectorType = selector.getType();
        if (selectorType == null) {
            return false;
        }
        PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(TypeConversionUtil.erasure(selectorType));
        if (unboxedType != null) {
            for (PsiCaseLabelElement t : elements) {
                if (JavaPsiPatternUtil.findUnconditionalPattern(t) instanceof PsiTypeTestPattern testPattern &&
                    JavaPsiPatternUtil.getPatternType(testPattern) instanceof PsiPrimitiveType primitiveType &&
                    JavaPsiPatternUtil.isUnconditionallyExactForType(t, unboxedType, primitiveType)) {
                    return false;
                }
            }
        }
        if (JavaPsiSwitchUtil.isBooleanSwitchWithTrueAndFalse(block)) {
            return false;
        }
        //enums are final; checking intersections is not needed
        PsiClass selectorClass = PsiUtil.resolveClassInClassTypeOnly(TypeConversionUtil.erasure(selectorType));
        if (selectorClass != null && JavaPsiSwitchUtil.getSwitchSelectorKind(selectorType) == JavaPsiSwitchUtil.SelectorKind.ENUM) {
            List<PsiEnumConstant> enumElements = getEnumConstants(elements);
            if (enumElements.isEmpty()) {
                return true;
            }
            return StreamEx.of(selectorClass.getFields()).select(PsiEnumConstant.class).anyMatch(e -> !enumElements.contains(e));
        }
        boolean hasAbstractSealedType = StreamEx.of(JavaPsiPatternUtil.deconstructSelectorType(selectorType))
            .map(type -> PsiUtil.resolveClassInClassTypeOnly(TypeConversionUtil.erasure(type)))
            .nonNull()
            .anyMatch(JavaPsiSealedUtil::isAbstractSealed);
        if (hasAbstractSealedType) {
            return !JavaPatternExhaustivenessUtil.findMissedClasses(block, selectorType, elements).isEmpty();
        }
        //records are final; checking intersections is not needed
        boolean recordExhaustive = selectorClass != null &&
            selectorClass.isRecord() &&
            JavaPatternExhaustivenessUtil.checkRecordExhaustiveness(elements, selectorType, block).isExhaustive();
        return !recordExhaustive;
    }

    private static @Nonnull List<PsiEnumConstant> getEnumConstants(@Nonnull List<? extends PsiCaseLabelElement> elements) {
        return StreamEx.of(elements).map(JavaPsiSwitchUtil::getEnumConstant).nonNull().toList();
    }

    /**
     * Calculates the number of branches in the specified switch statement.
     * When a default case is present the count will be returned as a negative number,
     * e.g. if a switch statement contains 4 labeled cases and a default case, it will return -5
     *
     * @param statement the statement to count the cases of.
     * @return a negative number if a default case was encountered.
     */
    public static int calculateBranchCount(@Nonnull PsiSwitchStatement statement) {
        // preserved for plugin compatibility
        return calculateBranchCount((PsiSwitchBlock) statement);
    }

    /**
     * Calculates the number of branches in the specified switch block.
     * When a default case is present the count will be returned as a negative number,
     * e.g. if a switch block contains 4 labeled cases and a default case, it will return -5
     *
     * @param block the switch block to count the cases of.
     * @return a negative number if a default case was encountered.
     */
    public static int calculateBranchCount(@Nonnull PsiSwitchBlock block) {
        final PsiCodeBlock body = block.getBody();
        if (body == null) {
            return 0;
        }
        int branches = 0;
        boolean defaultFound = false;
        for (final PsiSwitchLabelStatementBase child : PsiTreeUtil.getChildrenOfTypeAsList(body, PsiSwitchLabelStatementBase.class)) {
            if (child.isDefaultCase()) {
                defaultFound = true;
            }
            else {
                branches++;
            }
        }
        return defaultFound ? -branches - 1 : branches;
    }

    @Nullable
    public static PsiExpression getSwitchExpression(PsiIfStatement statement, int minimumBranches) {
        final PsiExpression condition = statement.getCondition();
        final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(statement);
        final PsiExpression possibleSwitchExpression = determinePossibleSwitchExpressions(condition, languageLevel);
        if (!canBeSwitchExpression(possibleSwitchExpression, languageLevel)) {
            return null;
        }
        int branchCount = 0;
        while (true) {
            branchCount++;
            if (!canBeMadeIntoCase(statement.getCondition(), possibleSwitchExpression, languageLevel)) {
                break;
            }
            final PsiStatement elseBranch = statement.getElseBranch();
            if (!(elseBranch instanceof PsiIfStatement)) {
                if (elseBranch != null) {
                    branchCount++;
                }
                if (branchCount < minimumBranches) {
                    return null;
                }
                return possibleSwitchExpression;
            }
            statement = (PsiIfStatement) elseBranch;
        }
        return null;
    }

    private static boolean canBeMadeIntoCase(PsiExpression expression, PsiExpression switchExpression, LanguageLevel languageLevel) {
        expression = ParenthesesUtils.stripParentheses(expression);
        if (languageLevel.isAtLeast(LanguageLevel.JDK_1_7)) {
            final PsiExpression stringSwitchExpression = determinePossibleStringSwitchExpression(expression);
            if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(switchExpression, stringSwitchExpression)) {
                return true;
            }
        }
        if (!(expression instanceof PsiPolyadicExpression)) {
            return false;
        }
        final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression) expression;
        final IElementType operation = polyadicExpression.getOperationTokenType();
        final PsiExpression[] operands = polyadicExpression.getOperands();
        if (operation.equals(JavaTokenType.OROR)) {
            for (PsiExpression operand : operands) {
                if (!canBeMadeIntoCase(operand, switchExpression, languageLevel)) {
                    return false;
                }
            }
            return true;
        }
        else if (operation.equals(JavaTokenType.EQEQ) && operands.length == 2) {
            return (canBeCaseLabel(operands[0], languageLevel) && EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(switchExpression, operands[1])) ||
                (canBeCaseLabel(operands[1], languageLevel) && EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(switchExpression, operands[0]));
        }
        else {
            return false;
        }
    }

    private static boolean canBeSwitchExpression(PsiExpression expression, LanguageLevel languageLevel) {
        if (expression == null || SideEffectChecker.mayHaveSideEffects(expression)) {
            return false;
        }
        final PsiType type = expression.getType();
        if (PsiType.CHAR.equals(type) || PsiType.BYTE.equals(type) || PsiType.SHORT.equals(type) || PsiType.INT.equals(type)) {
            return true;
        }
        else if (type instanceof PsiClassType) {
            if (type.equalsToText(CommonClassNames.JAVA_LANG_CHARACTER) || type.equalsToText(CommonClassNames.JAVA_LANG_BYTE) ||
                type.equalsToText(CommonClassNames.JAVA_LANG_SHORT) || type.equalsToText(CommonClassNames.JAVA_LANG_INTEGER)) {
                return true;
            }
            if (languageLevel.isAtLeast(LanguageLevel.JDK_1_5)) {
                final PsiClassType classType = (PsiClassType) type;
                final PsiClass aClass = classType.resolve();
                if (aClass != null && aClass.isEnum()) {
                    return true;
                }
            }
            if (languageLevel.isAtLeast(LanguageLevel.JDK_1_7) && type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
                return true;
            }
        }
        return false;
    }

    private static PsiExpression determinePossibleSwitchExpressions(PsiExpression expression, LanguageLevel languageLevel) {
        expression = ParenthesesUtils.stripParentheses(expression);
        if (expression == null) {
            return null;
        }
        if (languageLevel.isAtLeast(LanguageLevel.JDK_1_7)) {
            final PsiExpression jdk17Expression = determinePossibleStringSwitchExpression(expression);
            if (jdk17Expression != null) {
                return jdk17Expression;
            }
        }
        if (!(expression instanceof PsiPolyadicExpression)) {
            return null;
        }
        final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression) expression;
        final IElementType operation = polyadicExpression.getOperationTokenType();
        final PsiExpression[] operands = polyadicExpression.getOperands();
        if (operation.equals(JavaTokenType.OROR) && operands.length > 0) {
            return determinePossibleSwitchExpressions(operands[0], languageLevel);
        }
        else if (operation.equals(JavaTokenType.EQEQ) && operands.length == 2) {
            final PsiExpression lhs = operands[0];
            final PsiExpression rhs = operands[1];
            if (canBeCaseLabel(lhs, languageLevel)) {
                return rhs;
            }
            else if (canBeCaseLabel(rhs, languageLevel)) {
                return lhs;
            }
        }
        return null;
    }

    private static PsiExpression determinePossibleStringSwitchExpression(PsiExpression expression) {
        if (!(expression instanceof PsiMethodCallExpression)) {
            return null;
        }
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) expression;
        final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        @NonNls final String referenceName = methodExpression.getReferenceName();
        if (!"equals".equals(referenceName)) {
            return null;
        }
        final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
        if (qualifierExpression == null) {
            return null;
        }
        final PsiType type = qualifierExpression.getType();
        if (type == null || !type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
            return null;
        }
        final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
        final PsiExpression[] arguments = argumentList.getExpressions();
        if (arguments.length != 1) {
            return null;
        }
        final PsiExpression argument = arguments[0];
        final PsiType argumentType = argument.getType();
        if (argumentType == null || !argumentType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
            return null;
        }
        if (PsiUtil.isConstantExpression(qualifierExpression)) {
            return argument;
        }
        else if (PsiUtil.isConstantExpression(argument)) {
            return qualifierExpression;
        }
        return null;
    }

    private static boolean canBeCaseLabel(PsiExpression expression, LanguageLevel languageLevel) {
        if (expression == null) {
            return false;
        }
        if (languageLevel.isAtLeast(LanguageLevel.JDK_1_5) && expression instanceof PsiReferenceExpression) {
            final PsiElement referent = ((PsiReference) expression).resolve();
            if (referent instanceof PsiEnumConstant) {
                return true;
            }
        }
        final PsiType type = expression.getType();
        return (PsiType.INT.equals(type) || PsiType.SHORT.equals(type) || PsiType.BYTE.equals(type) || PsiType.CHAR.equals(type)) &&
            PsiUtil.isConstantExpression(expression);
    }

    public static String findUniqueLabelName(PsiStatement statement, @NonNls String baseName) {
        final PsiElement ancestor = PsiTreeUtil.getParentOfType(statement, PsiMember.class);
        if (!checkForLabel(baseName, ancestor)) {
            return baseName;
        }
        int val = 1;
        while (true) {
            final String name = baseName + val;
            if (!checkForLabel(name, ancestor)) {
                return name;
            }
            val++;
        }
    }

    private static boolean checkForLabel(String name, PsiElement ancestor) {
        final LabelSearchVisitor visitor = new LabelSearchVisitor(name);
        ancestor.accept(visitor);
        return visitor.isUsed();
    }

    /**
     * Returns true if given switch block has a rule-based format (like 'case 0 ->')
     *
     * @param block block to test
     * @return true if given switch block has a rule-based format; false if it has conventional label-based format (like 'case 0:')
     * If switch body has no labels yet and language level permits, rule-based format is assumed.
     */
    @RequiredReadAction
    public static boolean isRuleFormatSwitch(@Nonnull PsiSwitchBlock block) {
        if (!PsiUtil.isAvailable(JavaFeature.ENHANCED_SWITCH, block)) {
            return false;
        }

        final PsiCodeBlock switchBody = block.getBody();
        if (switchBody != null) {
            for (var child = switchBody.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child instanceof PsiSwitchLabelStatementBase && !isBeingCompleted((PsiSwitchLabelStatementBase) child)) {
                    return child instanceof PsiSwitchLabeledRuleStatement;
                }
            }
        }

        return true;
    }

    /**
     * Checks if the label is being completed and there are no other case label elements in the list of the case label's elements
     *
     * @param label the label to analyze
     * @return true if the label is currently being completed
     */
    @Contract(pure = true)
    @RequiredReadAction
    private static boolean isBeingCompleted(@Nonnull PsiSwitchLabelStatementBase label) {
        if (!(label.getLastChild() instanceof PsiErrorElement)) {
            return false;
        }

        final PsiCaseLabelElementList list = label.getCaseLabelElementList();
        return list != null && list.getElements().length == 1;
    }

    /**
     * @param label a switch label statement
     * @return list of enum constants which are targets of the specified label; empty list if the supplied element is not a switch label,
     * or it is not an enum switch.
     */
    @Nonnull
    public static List<PsiEnumConstant> findEnumConstants(PsiSwitchLabelStatementBase label) {
        if (label == null) {
            return Collections.emptyList();
        }
        final PsiExpressionList list = label.getCaseValues();
        if (list == null) {
            return Collections.emptyList();
        }
        List<PsiEnumConstant> constants = new ArrayList<>();
        for (PsiExpression value : list.getExpressions()) {
            if (value instanceof PsiReferenceExpression) {
                final PsiElement target = ((PsiReferenceExpression) value).resolve();
                if (target instanceof PsiEnumConstant) {
                    constants.add((PsiEnumConstant) target);
                    continue;
                }
            }
            return Collections.emptyList();
        }
        return constants;
    }

    private static class LabelSearchVisitor extends JavaRecursiveElementWalkingVisitor {

        private final String m_labelName;
        private boolean m_used = false;

        LabelSearchVisitor(String name) {
            m_labelName = name;
        }

        @Override
        public void visitElement(PsiElement element) {
            if (m_used) {
                return;
            }
            super.visitElement(element);
        }

        @Override
        public void visitLabeledStatement(PsiLabeledStatement statement) {
            final PsiIdentifier labelIdentifier = statement.getLabelIdentifier();
            final String labelText = labelIdentifier.getText();
            if (labelText.equals(m_labelName)) {
                m_used = true;
            }
        }

        public boolean isUsed() {
            return m_used;
        }
    }
}
