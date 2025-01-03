// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaValue;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.RelationType;
import consulo.document.util.TextRange;
import consulo.java.analysis.localize.JavaAnalysisLocalize;
import consulo.localize.LocalizeValue;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * A method contract which is described by {@link ValueConstraint} constraints on arguments.
 * Such contract can be created from {@link Contract} annotation.
 *
 * @author peter
 */
public final class StandardMethodContract extends MethodContract {
    @Nonnull
    private final ValueConstraint[] myParameters;

    public StandardMethodContract(@Nonnull ValueConstraint[] parameters, @Nonnull ContractReturnValue returnValue) {
        super(returnValue);
        myParameters = parameters;
    }

    public int getParameterCount() {
        return myParameters.length;
    }

    public ValueConstraint getParameterConstraint(int parameterIndex) {
        return myParameters[parameterIndex];
    }

    public List<ValueConstraint> getConstraints() {
        return List.of(myParameters);
    }

    public
    @Nonnull
    StandardMethodContract withReturnValue(@Nonnull ContractReturnValue returnValue) {
        return returnValue.equals(getReturnValue()) ? this : new StandardMethodContract(myParameters, returnValue);
    }

    public static StandardMethodContract trivialContract(int paramCount, @Nonnull ContractReturnValue returnValue) {
        return new StandardMethodContract(createConstraintArray(paramCount), returnValue);
    }

    /**
     * Creates a new contract which is an intersection of this and supplied contracts
     *
     * @param contract a contract to intersect with
     * @return intersection contract or null if no intersection is possible
     */
    @Nullable
    public StandardMethodContract intersect(StandardMethodContract contract) {
        ValueConstraint[] result = myParameters.clone();
        assert contract.getParameterCount() == result.length;
        for (int i = 0; i < result.length; i++) {
            ValueConstraint condition = result[i];
            ValueConstraint constraint = contract.getParameterConstraint(i);
            if (condition == constraint || condition == ValueConstraint.ANY_VALUE) {
                result[i] = constraint;
            }
            else if (constraint == ValueConstraint.ANY_VALUE) {
                result[i] = condition;
            }
            else if (condition == ValueConstraint.NOT_NULL_VALUE
                && (constraint == ValueConstraint.TRUE_VALUE || constraint == ValueConstraint.FALSE_VALUE)) {
                // java.lang.Boolean
                result[i] = constraint;
            }
            else if (constraint == ValueConstraint.NOT_NULL_VALUE
                && (condition == ValueConstraint.TRUE_VALUE || condition == ValueConstraint.FALSE_VALUE)) {
                // java.lang.Boolean
                result[i] = condition;
            }
            else {
                return null;
            }
        }
        return new StandardMethodContract(result, getReturnValue().intersect(contract.getReturnValue()));
    }

    /**
     * Creates a stream of contracts which describe all states covered by this contract but not covered by
     * supplied contract.
     *
     * @param contract contract to exclude
     * @return a stream of exclusion contracts (could be empty)
     */
    @Nonnull
    public Stream<StandardMethodContract> excludeContract(StandardMethodContract contract) {
        assert contract.getParameterCount() == myParameters.length;
        List<ValueConstraint> constraints = contract.getConstraints();
        List<ValueConstraint> template = StreamEx.constant(ValueConstraint.ANY_VALUE, myParameters.length).toList();
        List<StandardMethodContract> antiContracts = new ArrayList<>();
        for (int i = 0; i < constraints.size(); i++) {
            ValueConstraint constraint = constraints.get(i);
            if (constraint == ValueConstraint.ANY_VALUE) {
                continue;
            }
            template.set(i, constraint.negate());
            antiContracts.add(new StandardMethodContract(template.toArray(new ValueConstraint[0]), getReturnValue()));
            template.set(i, constraint);
        }
        return StreamEx.of(antiContracts).map(this::intersect).nonNull();
    }

    /**
     * Try merge two contracts into one preserving their full meaning
     *
     * @param other other contract to merge into this
     * @return merged contract or null if unable to merge
     */
    public StandardMethodContract tryCollapse(StandardMethodContract other) {
        if (!other.getReturnValue().equals(getReturnValue())) {
            return null;
        }
        ValueConstraint[] thisParameters = this.myParameters;
        ValueConstraint[] thatParameters = other.myParameters;
        if (thatParameters.length != thisParameters.length) {
            return null;
        }
        ValueConstraint[] result = null;
        for (int i = 0; i < thisParameters.length; i++) {
            ValueConstraint thisConstraint = thisParameters[i];
            ValueConstraint thatConstraint = thatParameters[i];
            if (thisConstraint != thatConstraint) {
                if (result != null || !thisConstraint.canBeNegated() || thisConstraint.negate() != thatConstraint) {
                    return null;
                }
                result = thisParameters.clone();
                result[i] = ValueConstraint.ANY_VALUE;
            }
        }
        return result == null ? null : new StandardMethodContract(result, getReturnValue());
    }

    /**
     * Converts list of contracts which are equivalent to the passed list, but independent on the order
     * (e.g. {@code "null -> null, _ -> !null"} will be converted to {@code "null -> null, !null -> !null"}). Also removes unreachable
     * contracts if any.
     *
     * @param contracts list of input contracts to process (assumed that they are applied in the specified order)
     * @return list of equivalent non-intersecting contracts or null if the result is too big or the input list contains errors
     * (e.g. contracts with different parameter count). null When result is too big or contracts are erroneous
     */
    @Nullable
    public static List<StandardMethodContract> toNonIntersectingStandardContracts(List<StandardMethodContract> contracts) {
        if (contracts.isEmpty()) {
            return contracts;
        }
        int paramCount = contracts.get(0).getParameterCount();
        List<StandardMethodContract> result = new ArrayList<>();
        List<StandardMethodContract> leftovers = Collections.singletonList(trivialContract(paramCount, ContractReturnValue.returnAny()));
        for (StandardMethodContract contract : contracts) {
            if (contract.getParameterCount() != paramCount) {
                return null;
            }
            StreamEx.of(leftovers).map(c -> c.intersect(contract)).nonNull().into(result);
            if (result.size() >= DataFlowRunner.MAX_STATES_PER_BRANCH) {
                return null;
            }
            leftovers = StreamEx.of(leftovers).flatMap(c -> c.excludeContract(contract)).toList();
            if (leftovers.isEmpty()) {
                break;
            }
        }
        return result;
    }

    @Nonnull
    public static ValueConstraint[] createConstraintArray(int paramCount) {
        ValueConstraint[] args = new ValueConstraint[paramCount];
        Arrays.fill(args, ValueConstraint.ANY_VALUE);
        return args;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || o.getClass() != getClass()) {
            return false;
        }

        StandardMethodContract contract = (StandardMethodContract)o;
        return Arrays.equals(myParameters, contract.myParameters) && getReturnValue().equals(contract.getReturnValue());
    }

    @Override
    public int hashCode() {
        int result = 0;
        for (ValueConstraint argument : myParameters) {
            result = 31 * result + argument.ordinal();
        }
        result = 31 * result + getReturnValue().hashCode();
        return result;
    }

    @Override
    String getArgumentsPresentation() {
        return StringUtil.join(myParameters, ValueConstraint::toString, ", ");
    }

    @Override
    public List<ContractValue> getConditions() {
        return IntStreamEx.ofIndices(myParameters)
            .mapToObj(idx -> myParameters[idx].getCondition(idx))
            .without(ContractValue.booleanValue(true))
            .toList();
    }

    public static List<StandardMethodContract> parseContract(@Nonnull String text) throws ParseException {
        if (StringUtil.isEmptyOrSpaces(text)) {
            return Collections.emptyList();
        }

        List<StandardMethodContract> result = new ArrayList<>();
        String[] split = StringUtil.replace(text, " ", "").split(";");
        for (int clauseIndex = 0; clauseIndex < split.length; clauseIndex++) {
            String clause = split[clauseIndex];
            result.add(fromText(text, clauseIndex, clause));
        }
        return result;
    }

    /**
     * Create single contract from text. Used to initialize some hard-coded contracts only.
     *
     * @param clause contract clause like "_, null -> false"
     * @return created contract
     * @throws RuntimeException in case of parse error
     * @see HardcodedContracts
     */
    static
    @Nonnull
    StandardMethodContract fromText(@Nonnull String clause) {
        try {
            return fromText(clause, 0, clause);
        }
        catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private static
    @Nonnull
    StandardMethodContract fromText(@Nonnull String text, int clauseIndex, @Nonnull String clause)
        throws ParseException {
        String arrow = "->";
        int arrowIndex = clause.indexOf(arrow);
        if (arrowIndex < 0) {
            throw ParseException.forClause(JavaAnalysisLocalize.inspectionContractCheckerClauseSyntax(), text, clauseIndex);
        }

        String beforeArrow = clause.substring(0, arrowIndex);
        ValueConstraint[] args;
        if (StringUtil.isNotEmpty(beforeArrow)) {
            String[] argStrings = beforeArrow.split(",");
            args = new ValueConstraint[argStrings.length];
            for (int i = 0; i < args.length; i++) {
                args[i] = parseConstraint(argStrings[i], text, clauseIndex, i);
            }
        }
        else {
            args = new ValueConstraint[0];
        }
        String returnValueString = clause.substring(arrowIndex + arrow.length());
        ContractReturnValue returnValue = ContractReturnValue.valueOf(returnValueString);
        if (returnValue == null) {
            String possibleValues = "null, !null, true, false, this, new, paramN, fail, _";
            LocalizeValue message = JavaAnalysisLocalize.inspectionContractCheckerUnknownReturnValue(possibleValues, returnValueString);
            throw ParseException.forReturnValue(message, text, clauseIndex);
        }
        return new StandardMethodContract(args, returnValue);
    }

    private static ValueConstraint parseConstraint(String name, String text, int clauseIndex, int constraintIndex) throws ParseException {
        if (StringUtil.isEmpty(name)) {
            throw new ParseException(JavaAnalysisLocalize.inspectionContractCheckerEmptyConstraint());
        }
        for (ValueConstraint constraint : ValueConstraint.values()) {
            if (constraint.toString().equals(name)) {
                return constraint;
            }
        }
        String allowedClause = StreamEx.of(ValueConstraint.values()).joining(", ");
        LocalizeValue message = JavaAnalysisLocalize.inspectionContractCheckerUnknownConstraint(allowedClause, name);
        throw ParseException.forConstraint(message, text, clauseIndex, constraintIndex);
    }

    public enum ValueConstraint {
        ANY_VALUE("_", ContractReturnValue.returnAny()),
        NULL_VALUE("null", ContractReturnValue.returnNull()),
        NOT_NULL_VALUE("!null", ContractReturnValue.returnNotNull()),
        TRUE_VALUE("true", ContractReturnValue.returnTrue()),
        FALSE_VALUE("false", ContractReturnValue.returnFalse());

        private final String myPresentableName;
        private final ContractReturnValue myCorrespondingReturnValue;

        ValueConstraint(String presentableName, ContractReturnValue correspondingReturnValue) {
            myPresentableName = presentableName;
            myCorrespondingReturnValue = correspondingReturnValue;
        }

        public ContractReturnValue asReturnValue() {
            return myCorrespondingReturnValue;
        }

        @Nullable
        DfaValue getComparisonValue(DfaValueFactory factory) {
            if (this == NULL_VALUE || this == NOT_NULL_VALUE) {
                return factory.getNull();
            }
            if (this == TRUE_VALUE || this == FALSE_VALUE) {
                return factory.getBoolean(true);
            }
            return null;
        }

        boolean shouldUseNonEqComparison() {
            return this == NOT_NULL_VALUE || this == FALSE_VALUE;
        }

        /**
         * Returns a condition value which should be applied to memory state to satisfy this constraint
         *
         * @param argumentIndex argument number to test
         * @return a condition
         */
        public ContractValue getCondition(int argumentIndex) {
            ContractValue left;
            if (this == NULL_VALUE || this == NOT_NULL_VALUE) {
                left = ContractValue.nullValue();
            }
            else if (this == TRUE_VALUE || this == FALSE_VALUE) {
                left = ContractValue.booleanValue(true);
            }
            else {
                return ContractValue.booleanValue(true);
            }
            return ContractValue.condition(
                left,
                RelationType.equivalence(!shouldUseNonEqComparison()),
                ContractValue.argument(argumentIndex)
            );
        }

        /**
         * @return true if constraint can be negated
         * @see #negate()
         */
        public boolean canBeNegated() {
            return this != ANY_VALUE;
        }

        /**
         * @return negated constraint
         * @throws IllegalStateException if constraint cannot be negated
         * @see #canBeNegated()
         */
        public ValueConstraint negate() {
            switch (this) {
                case NULL_VALUE:
                    return NOT_NULL_VALUE;
                case NOT_NULL_VALUE:
                    return NULL_VALUE;
                case TRUE_VALUE:
                    return FALSE_VALUE;
                case FALSE_VALUE:
                    return TRUE_VALUE;
                default:
                    throw new IllegalStateException("ValueConstraint = " + this);
            }
        }

        @Override
        public String toString() {
            return myPresentableName;
        }
    }

    public static class ParseException extends Exception {
        @Nullable
        private final TextRange myRange;

        ParseException(@Nonnull LocalizeValue message) {
            this(message, null);
        }

        ParseException(@Nonnull LocalizeValue message, @Nullable TextRange range) {
            super(message.get());
            myRange = range != null && range.isEmpty() ? null : range;
        }

        @Override
        public String getMessage() {
            return super.getMessage();
        }

        @Nullable
        public TextRange getRange() {
            return myRange;
        }

        public static ParseException forConstraint(@Nonnull LocalizeValue message, String text, int clauseNumber, int constraintNumber) {
            TextRange range = findClauseRange(text, clauseNumber);
            if (range == null) {
                return new ParseException(message);
            }
            int start = range.getStartOffset();
            while (constraintNumber > 0) {
                start = text.indexOf(',', start);
                if (start == -1) {
                    return new ParseException(message, range);
                }
                start++;
                constraintNumber--;
            }
            int end = text.indexOf(',', start);
            if (end == -1 || end > range.getEndOffset()) {
                end = text.indexOf("->", start);
                if (end == -1 || end > range.getEndOffset()) {
                    end = range.getEndOffset();
                }
            }
            if (!text.substring(start, end).trim().isEmpty()) {
                while (text.charAt(start) == ' ') {
                    start++;
                }
                while (end > start && text.charAt(end - 1) == ' ') {
                    end--;
                }
            }
            return new ParseException(message, new TextRange(start, end));
        }

        public static ParseException forReturnValue(@Nonnull LocalizeValue message, String text, int clauseNumber) {
            TextRange range = findClauseRange(text, clauseNumber);
            if (range == null) {
                return new ParseException(message);
            }
            int index = text.indexOf("->", range.getStartOffset());
            if (index == -1 || index > range.getEndOffset()) {
                return new ParseException(message, range);
            }
            index += "->".length();
            while (index < range.getEndOffset() && text.charAt(index) == ' ') {
                index++;
            }
            if (index == range.getEndOffset()) {
                return new ParseException(message, range);
            }
            return new ParseException(message, new TextRange(index, range.getEndOffset()));
        }

        public static ParseException forClause(LocalizeValue message, String text, int clauseNumber) {
            TextRange range = findClauseRange(text, clauseNumber);
            return range == null ? new ParseException(message) : new ParseException(message, range);
        }

        private static TextRange findClauseRange(String text, int clauseNumber) {
            int start = 0;
            while (clauseNumber > 0) {
                start = text.indexOf(';', start);
                if (start == -1) {
                    return null;
                }
                start++;
                clauseNumber--;
            }
            int end = text.indexOf(';', start);
            if (end == -1) {
                end = text.length();
            }
            if (text.substring(start, end).trim().isEmpty()) {
                return new TextRange(start, end);
            }

            while (text.charAt(start) == ' ') {
                start++;
            }
            while (end > start && text.charAt(end - 1) == ' ') {
                end--;
            }

            return new TextRange(start, end);
        }
    }
}
