// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow;

import com.intellij.java.language.codeInsight.Nullability;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiTypesUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import consulo.annotation.access.RequiredReadAction;
import consulo.java.analysis.localize.JavaAnalysisLocalize;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.util.lang.ObjectUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.intellij.java.language.psi.CommonClassNames.JAVA_LANG_NULL_POINTER_EXCEPTION;
import static com.intellij.java.language.psi.CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION;

/**
 * Represents a kind of nullability problem
 *
 * @param <T> a type of anchor element which could be associated with given nullability problem kind
 */
public final class NullabilityProblemKind<T extends PsiElement> {
    private static final String NPE = JAVA_LANG_NULL_POINTER_EXCEPTION;
    private static final String RE = JAVA_LANG_RUNTIME_EXCEPTION;

    private final String myName;
    private final LocalizeValue myAlwaysNullMessage;
    private final LocalizeValue myNormalMessage;
    private final
    @Nullable
    String myException;

    private NullabilityProblemKind(@Nullable String exception, @Nonnull String name) {
        myException = exception;
        myName = name;
        myAlwaysNullMessage = null;
        myNormalMessage = null;
    }

    private NullabilityProblemKind(
        @Nullable String exception, @Nonnull String name,
        @Nonnull LocalizeValue message
    ) {
        this(exception, name, message, message);
    }

    private NullabilityProblemKind(
        @Nullable String exception, @Nonnull String name,
        @Nonnull LocalizeValue alwaysNullMessage,
        @Nonnull LocalizeValue normalMessage
    ) {
        myException = exception;
        myName = name;
        myAlwaysNullMessage = alwaysNullMessage;
        myNormalMessage = normalMessage;
    }

    public static final NullabilityProblemKind<PsiMethodCallExpression> callNPE = new NullabilityProblemKind<>(
        NPE,
        "callNPE",
        JavaAnalysisLocalize.dataflowMessageNpeMethodInvocationSure(),
        JavaAnalysisLocalize.dataflowMessageNpeMethodInvocation()
    );
    public static final NullabilityProblemKind<PsiMethodReferenceExpression> callMethodRefNPE =
        new NullabilityProblemKind<>(NPE, "callMethodRefNPE", JavaAnalysisLocalize.dataflowMessageNpeMethodrefInvocation());
    public static final NullabilityProblemKind<PsiNewExpression> innerClassNPE = new NullabilityProblemKind<>(
        NPE,
        "innerClassNPE",
        JavaAnalysisLocalize.dataflowMessageNpeInnerClassConstructionSure(),
        JavaAnalysisLocalize.dataflowMessageNpeInnerClassConstruction()
    );
    public static final NullabilityProblemKind<PsiExpression> fieldAccessNPE = new NullabilityProblemKind<>(
        NPE,
        "fieldAccessNPE",
        JavaAnalysisLocalize.dataflowMessageNpeFieldAccessSure(),
        JavaAnalysisLocalize.dataflowMessageNpeFieldAccess()
    );
    public static final NullabilityProblemKind<PsiArrayAccessExpression> arrayAccessNPE = new NullabilityProblemKind<>(
        NPE,
        "arrayAccessNPE",
        JavaAnalysisLocalize.dataflowMessageNpeArrayAccessSure(),
        JavaAnalysisLocalize.dataflowMessageNpeArrayAccess()
    );
    public static final NullabilityProblemKind<PsiElement> unboxingNullable =
        new NullabilityProblemKind<>(NPE, "unboxingNullable", JavaAnalysisLocalize.dataflowMessageUnboxing());
    public static final NullabilityProblemKind<PsiExpression> assigningToNotNull = new NullabilityProblemKind<>(
        null,
        "assigningToNotNull",
        JavaAnalysisLocalize.dataflowMessageAssigningNull(),
        JavaAnalysisLocalize.dataflowMessageAssigningNullable()
    );
    public static final NullabilityProblemKind<PsiExpression> assigningToNonAnnotatedField = new NullabilityProblemKind<>(
        null,
        "assigningToNonAnnotatedField",
        JavaAnalysisLocalize.dataflowMessageAssigningNullNotannotated(),
        JavaAnalysisLocalize.dataflowMessageAssigningNullableNotannotated()
    );
    public static final NullabilityProblemKind<PsiExpression> storingToNotNullArray = new NullabilityProblemKind<>(
        null,
        "storingToNotNullArray",
        JavaAnalysisLocalize.dataflowMessageStoringArrayNull(),
        JavaAnalysisLocalize.dataflowMessageStoringArrayNullable()
    );
    public static final NullabilityProblemKind<PsiExpression> nullableReturn =
        new NullabilityProblemKind<>(null, "nullableReturn");
    public static final NullabilityProblemKind<PsiExpression> nullableFunctionReturn = new NullabilityProblemKind<>(
        RE,
        "nullableFunctionReturn",
        JavaAnalysisLocalize.dataflowMessageReturnNullableFromNotnullFunction(),
        JavaAnalysisLocalize.dataflowMessageReturnNullableFromNotnullFunction()
    );
    public static final NullabilityProblemKind<PsiExpression> passingToNotNullParameter = new NullabilityProblemKind<>(
        RE,
        "passingToNotNullParameter",
        JavaAnalysisLocalize.dataflowMessagePassingNullArgument(),
        JavaAnalysisLocalize.dataflowMessagePassingNullableArgument()
    );
    public static final NullabilityProblemKind<PsiMethodReferenceExpression> unboxingMethodRefParameter = new NullabilityProblemKind<>(
        NPE,
        "unboxingMethodRefParameter",
        JavaAnalysisLocalize.dataflowMessagePassingNullableArgumentMethodref()
    );
    public static final NullabilityProblemKind<PsiMethodReferenceExpression> passingToNotNullMethodRefParameter =
        new NullabilityProblemKind<>(
            RE,
            "passingToNotNullMethodRefParameter",
            JavaAnalysisLocalize.dataflowMessagePassingNullableArgumentMethodref()
        );
    public static final NullabilityProblemKind<PsiExpression> passingToNonAnnotatedParameter = new NullabilityProblemKind<>(
        null,
        "passingToNonAnnotatedParameter",
        JavaAnalysisLocalize.dataflowMessagePassingNullArgumentNonannotated(),
        JavaAnalysisLocalize.dataflowMessagePassingNullableArgumentNonannotated()
    );
    public static final NullabilityProblemKind<PsiMethodReferenceExpression> passingToNonAnnotatedMethodRefParameter =
        new NullabilityProblemKind<>(
            null,
            "passingToNonAnnotatedMethodRefParameter",
            JavaAnalysisLocalize.dataflowMessagePassingNullableArgumentMethodrefNonannotated()
        );
    // assumeNotNull problem is not reported, just used to force the argument to be not null
    public static final NullabilityProblemKind<PsiExpression> assumeNotNull = new NullabilityProblemKind<>(RE, "assumeNotNull");
    /**
     * noProblem is not reported and used to override another problem
     *
     * @see ControlFlowAnalyzer#addCustomNullabilityProblem(PsiExpression, NullabilityProblemKind)
     * @see CFGBuilder#pushExpression(PsiExpression, NullabilityProblemKind)
     */
    public static final NullabilityProblemKind<PsiExpression> noProblem = new NullabilityProblemKind<>(null, "noProblem");

    /**
     * Creates a new {@link NullabilityProblem} of this kind using given anchor
     *
     * @param anchor     anchor to bind the problem to
     * @param expression shortest expression which is actually violates the nullability
     * @return newly created problem or null if anchor is null
     */
    @Contract("null, _ -> null")
    @Nullable
    public final NullabilityProblem<T> problem(@Nullable T anchor, @Nullable PsiExpression expression) {
        return anchor == null || this == noProblem ? null : new NullabilityProblem<>(this, anchor, expression);
    }

    /**
     * Returns the supplied problem with adjusted type parameter or null if supplied problem kind is not this kind
     *
     * @param problem problem to check
     * @return the supplied problem or null
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public final NullabilityProblem<T> asMyProblem(NullabilityProblem<?> problem) {
        return problem != null && problem.myKind == this ? (NullabilityProblem<T>)problem : null;
    }

    /**
     * Executes given consumer if the supplied problem has the same kind as this kind
     *
     * @param problem  a problem to check
     * @param consumer a consumer to execute. A problem anchor is supplied as the consumer argument.
     */
    @RequiredReadAction
    public void ifMyProblem(NullabilityProblem<?> problem, @RequiredReadAction Consumer<? super T> consumer) {
        NullabilityProblem<T> myProblem = asMyProblem(problem);
        if (myProblem != null) {
            consumer.accept(myProblem.getAnchor());
        }
    }

    @Override
    public String toString() {
        return myName;
    }

    @Nullable
    @RequiredReadAction
    public static NullabilityProblem<?> fromContext(
        @Nonnull PsiExpression expression,
        Map<PsiExpression, NullabilityProblemKind<? super PsiExpression>> customNullabilityProblems
    ) {
        if (TypeConversionUtil.isPrimitiveAndNotNull(expression.getType()) ||
            expression instanceof PsiReferenceExpression refExpr && refExpr.resolve() instanceof PsiClass) {
            return null;
        }
        PsiExpression context = findTopExpression(expression);
        NullabilityProblemKind<? super PsiExpression> kind = customNullabilityProblems.get(context);
        if (kind != null) {
            return kind.problem(context, expression);
        }
        PsiElement parent = context.getParent();
        if (parent instanceof PsiReferenceExpression refExpr) {
            if (refExpr.resolve() instanceof PsiMember member && member.isStatic()) {
                return null;
            }
            if (parent.getParent() instanceof PsiMethodCallExpression methodCall) {
                return callNPE.problem(methodCall, expression);
            }
            return fieldAccessNPE.problem(context, expression);
        }
        PsiType targetType = null;
        if (parent instanceof PsiLambdaExpression lambda) {
            targetType = LambdaUtil.getFunctionalInterfaceReturnType(lambda);
        }
        else if (parent instanceof PsiReturnStatement) {
            targetType = PsiTypesUtil.getMethodReturnType(parent);
        }
        if (targetType != null && !PsiType.VOID.equals(targetType)) {
            if (TypeConversionUtil.isPrimitiveAndNotNull(targetType)) {
                return createUnboxingProblem(context, expression);
            }
            return nullableReturn.problem(context, expression);
        }
        if (parent instanceof PsiVariable var) {
            if (var.getType() instanceof PsiPrimitiveType) {
                return createUnboxingProblem(context, expression);
            }
            Nullability nullability = DfaPsiUtil.getElementNullability(var.getType(), var);
            if (nullability == Nullability.NOT_NULL) {
                return assigningToNotNull.problem(context, expression);
            }
        }
        if (parent instanceof PsiAssignmentExpression assignment) {
            return getAssignmentProblem(assignment, expression, context);
        }
        if (parent instanceof PsiExpressionList expressionList) {
            return getExpressionListProblem(expressionList, expression, context);
        }
        if (parent instanceof PsiArrayInitializerExpression arrayInitializer) {
            return getArrayInitializerProblem(arrayInitializer, expression, context);
        }
        if (parent instanceof PsiTypeCastExpression) {
            if (TypeConversionUtil.isAssignableFromPrimitiveWrapper(context.getType())) {
                // Only casts to primitives are here; casts to objects were already traversed by findTopExpression
                return unboxingNullable.problem(context, expression);
            }
        }
        else if (parent instanceof PsiIfStatement || parent instanceof PsiWhileStatement || parent instanceof PsiDoWhileStatement
            || parent instanceof PsiUnaryExpression || parent instanceof PsiConditionalExpression
            || (parent instanceof PsiForStatement forStatement && forStatement.getCondition() == context)
            || (parent instanceof PsiAssertStatement assertStatement && assertStatement.getAssertCondition() == context)) {
            return createUnboxingProblem(context, expression);
        }
        if (parent instanceof PsiSwitchBlock) {
            NullabilityProblem<PsiElement> problem = createUnboxingProblem(context, expression);
            return problem == null ? fieldAccessNPE.problem(context, expression) : problem;
        }
        if (parent instanceof PsiForeachStatement || parent instanceof PsiThrowStatement || parent instanceof PsiSynchronizedStatement) {
            return fieldAccessNPE.problem(context, expression);
        }
        if (parent instanceof PsiNewExpression newExpression) {
            return innerClassNPE.problem(newExpression, expression);
        }
        if (parent instanceof PsiPolyadicExpression polyadic) {
            IElementType type = polyadic.getOperationTokenType();
            boolean noUnboxing = (type == JavaTokenType.PLUS && TypeUtils.isJavaLangString(polyadic.getType()))
                || ((type == JavaTokenType.EQEQ || type == JavaTokenType.NE)
                && StreamEx.of(polyadic.getOperands()).noneMatch(op -> TypeConversionUtil.isPrimitiveAndNotNull(op.getType())));
            if (!noUnboxing) {
                return createUnboxingProblem(context, expression);
            }
        }
        if (parent instanceof PsiArrayAccessExpression arrayAccess) {
            return arrayAccess.getArrayExpression() == context
                ? arrayAccessNPE.problem(arrayAccess, expression)
                : createUnboxingProblem(context, expression);
        }
        return null;
    }

    @Nullable
    private static NullabilityProblem<?> getExpressionListProblem(
        @Nonnull PsiExpressionList expressionList,
        @Nonnull PsiExpression expression,
        @Nonnull PsiExpression context
    ) {
        if (expressionList.getParent() instanceof PsiSwitchLabelStatementBase) {
            return fieldAccessNPE.problem(context, expression);
        }
        PsiParameter parameter = MethodCallUtils.getParameterForArgument(context);
        PsiElement grandParent = expressionList.getParent();
        if (parameter != null) {
            if (parameter.getType() instanceof PsiPrimitiveType) {
                return createUnboxingProblem(context, expression);
            }
            if (grandParent instanceof PsiAnonymousClass) {
                grandParent = grandParent.getParent();
            }
            if (grandParent instanceof PsiCall call) {
                PsiSubstitutor substitutor = call.resolveMethodGenerics().getSubstitutor();
                Nullability nullability = DfaPsiUtil.getElementNullability(substitutor.substitute(parameter.getType()), parameter);
                if (nullability == Nullability.NOT_NULL) {
                    return passingToNotNullParameter.problem(context, expression);
                }
                if (nullability == Nullability.UNKNOWN) {
                    return passingToNonAnnotatedParameter.problem(context, expression);
                }
            }
        }
        else if (grandParent instanceof PsiCall call && MethodCallUtils.isVarArgCall(call)) {
            Nullability nullability = DfaPsiUtil.getVarArgComponentNullability(call.resolveMethod());
            if (nullability == Nullability.NOT_NULL) {
                return passingToNotNullParameter.problem(context, expression);
            }
        }
        return null;
    }

    @Nullable
    private static NullabilityProblem<?> getArrayInitializerProblem(
        @Nonnull PsiArrayInitializerExpression initializer,
        @Nonnull PsiExpression expression,
        @Nonnull PsiExpression context
    ) {
        PsiType type = initializer.getType();
        if (type instanceof PsiArrayType arrayType) {
            PsiType componentType = arrayType.getComponentType();
            if (TypeConversionUtil.isPrimitiveAndNotNull(componentType)) {
                return createUnboxingProblem(context, expression);
            }
            Nullability nullability = DfaPsiUtil.getTypeNullability(componentType);
            if (nullability == Nullability.UNKNOWN
                && initializer.getParent() instanceof PsiNewExpression newExpr
                && ExpectedTypeUtils.findExpectedType(newExpr, false) instanceof PsiArrayType expectedArrayType) {
                nullability = DfaPsiUtil.getTypeNullability(expectedArrayType.getComponentType());
            }
            if (nullability == Nullability.NOT_NULL) {
                return storingToNotNullArray.problem(context, expression);
            }
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    private static NullabilityProblem<?> getAssignmentProblem(
        @Nonnull PsiAssignmentExpression assignment,
        @Nonnull PsiExpression expression,
        @Nonnull PsiExpression context
    ) {
        IElementType tokenType = assignment.getOperationTokenType();
        if (assignment.getRExpression() == context) {
            PsiExpression lho = PsiUtil.skipParenthesizedExprDown(assignment.getLExpression());
            if (lho != null) {
                PsiType type = lho.getType();
                if (tokenType.equals(JavaTokenType.PLUSEQ) && TypeUtils.isJavaLangString(type)) {
                    return null;
                }
                if (type instanceof PsiPrimitiveType) {
                    return createUnboxingProblem(context, expression);
                }
                Nullability nullability = Nullability.UNKNOWN;
                PsiVariable target = null;
                if (lho instanceof PsiReferenceExpression lhoRefExpr) {
                    target = ObjectUtil.tryCast(lhoRefExpr.resolve(), PsiVariable.class);
                    if (target != null) {
                        nullability = DfaPsiUtil.getElementNullability(type, target);
                    }
                }
                else {
                    nullability = DfaPsiUtil.getTypeNullability(type);
                }
                boolean forceDeclaredNullity = !(target instanceof PsiParameter && target.getParent() instanceof PsiParameterList);
                if (forceDeclaredNullity && nullability == Nullability.NOT_NULL) {
                    return (lho instanceof PsiArrayAccessExpression ? storingToNotNullArray : assigningToNotNull)
                        .problem(context, expression);
                }
                if (nullability == Nullability.UNKNOWN && lho instanceof PsiReferenceExpression lhoRefExpr) {
                    PsiField field = ObjectUtil.tryCast(lhoRefExpr.resolve(), PsiField.class);
                    if (field != null && !field.isFinal()) {
                        return assigningToNonAnnotatedField.problem(context, expression);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Looks for top expression with the same nullability as given expression. That is: skips casts or conditionals, which don't unbox;
     * goes up from switch expression breaks or expression-branches.
     *
     * @param expression expression to find the top expression for
     * @return the top expression
     */
    @Nonnull
    static PsiExpression findTopExpression(@Nonnull PsiExpression expression) {
        PsiExpression context = expression;
        while (true) {
            PsiElement parent = context.getParent();
            if (parent instanceof PsiParenthesizedExpression || parent instanceof PsiTypeCastExpression
                || (parent instanceof PsiConditionalExpression conditional && conditional.getCondition() != context)) {
                PsiExpression parentExpression = (PsiExpression)parent;
                if (TypeConversionUtil.isPrimitiveAndNotNull(parentExpression.getType())) {
                    return context;
                }
                context = parentExpression;
                continue;
            }
            if (parent instanceof PsiExpressionStatement parentExpression
                && parentExpression.getParent() instanceof PsiSwitchLabeledRuleStatement switchLabeledRule
                && switchLabeledRule.getEnclosingSwitchBlock() instanceof PsiSwitchExpression switchExpr) {
                context = switchExpr;
                continue;
            }
            if (parent instanceof PsiYieldStatement yieldStatement) {
                PsiSwitchExpression enclosing = yieldStatement.findEnclosingExpression();
                if (enclosing != null) {
                    context = enclosing;
                    continue;
                }
            }
            return context;
        }
    }

    private static NullabilityProblem<PsiElement> createUnboxingProblem(
        @Nonnull PsiExpression context,
        @Nonnull PsiExpression expression
    ) {
        if (!TypeConversionUtil.isPrimitiveWrapper(context.getType())) {
            return null;
        }
        return unboxingNullable.problem(context, expression);
    }

    static List<NullabilityProblem<?>> postprocessNullabilityProblems(Collection<NullabilityProblem<?>> problems) {
        List<NullabilityProblem<?>> unchanged = new ArrayList<>();
        Map<PsiExpression, NullabilityProblem<?>> expressionToProblem = new HashMap<>();
        for (NullabilityProblem<?> problem : problems) {
            PsiExpression expression = problem.getDereferencedExpression();
            NullabilityProblemKind<?> kind = problem.getKind();
            if (expression == null) {
                unchanged.add(problem);
                continue;
            }
            if (innerClassNPE == kind || callNPE == kind || arrayAccessNPE == kind || fieldAccessNPE == kind) {
                // Qualifier-problems are reported on top-expression level for now as it's rare case to have
                // something complex in qualifier and we highlight not the qualifier itself, but something else (e.g. called method name)
                unchanged.add(problem.withExpression(findTopExpression(expression)));
                continue;
            }
            // Merge ternary problems reported for both branches into single problem
            while (true) {
                PsiExpression top = skipParenthesesAndObjectCastsUp(expression);
                PsiConditionalExpression ternary = ObjectUtil.tryCast(top.getParent(), PsiConditionalExpression.class);
                if (ternary != null) {
                    PsiExpression otherBranch = null;
                    if (ternary.getThenExpression() == top) {
                        otherBranch = ternary.getElseExpression();
                    }
                    else if (ternary.getElseExpression() == top) {
                        otherBranch = ternary.getThenExpression();
                    }
                    if (otherBranch != null) {
                        otherBranch = skipParenthesesAndObjectCastsDown(otherBranch);
                        NullabilityProblem<?> otherBranchProblem = expressionToProblem.remove(otherBranch);
                        if (otherBranchProblem != null) {
                            expression = ternary;
                            problem = problem.withExpression(ternary);
                            continue;
                        }
                    }
                }
                break;
            }
            expressionToProblem.put(expression, problem);
        }
        return StreamEx.of(unchanged, expressionToProblem.values()).toFlatList(Function.identity());
    }

    private static PsiExpression skipParenthesesAndObjectCastsDown(PsiExpression expression) {
        while (true) {
            if (expression instanceof PsiParenthesizedExpression parenthesized) {
                expression = parenthesized.getExpression();
            }
            else if (expression instanceof PsiTypeCastExpression typeCast && !(typeCast.getType() instanceof PsiPrimitiveType)) {
                expression = typeCast.getOperand();
            }
            else {
                return expression;
            }
        }
    }

    @Nonnull
    private static PsiExpression skipParenthesesAndObjectCastsUp(PsiExpression expression) {
        PsiExpression top = expression;
        while (true) {
            PsiElement parent = top.getParent();
            if (parent instanceof PsiParenthesizedExpression
                || (parent instanceof PsiTypeCastExpression typeCast && !(typeCast.getType() instanceof PsiPrimitiveType))) {
                top = (PsiExpression)parent;
            }
            else {
                return top;
            }
        }
    }

    /**
     * Represents a concrete nullability problem on PSI which consists of PSI element (anchor) and {@link NullabilityProblemKind}.
     *
     * @param <T> a type of anchor element
     */
    public static final class NullabilityProblem<T extends PsiElement> {
        @Nonnull
        private final NullabilityProblemKind<T> myKind;
        @Nonnull
        private final T myAnchor;
        @Nullable
        private final PsiExpression myDereferencedExpression;

        NullabilityProblem(@Nonnull NullabilityProblemKind<T> kind, @Nonnull T anchor, @Nullable PsiExpression dereferencedExpression) {
            myKind = kind;
            myAnchor = anchor;
            myDereferencedExpression = dereferencedExpression;
        }

        @Nonnull
        public T getAnchor() {
            return myAnchor;
        }

        /**
         * @return name of exception (or its superclass) which is thrown if violation occurs,
         * or null if no exception is thrown (e.g. when assigning null to variable annotated as notnull).
         */
        @Nullable
        public String thrownException() {
            return myKind.myException;
        }

        /**
         * @return a minimal nullable expression which causes the problem
         */
        @Nullable
        public PsiExpression getDereferencedExpression() {
            return myDereferencedExpression;
        }

        @Nonnull
        public LocalizeValue getMessage(Map<PsiExpression, DataFlowInspectionBase.ConstantResult> expressions) {
            if (myKind.myAlwaysNullMessage == null || myKind.myNormalMessage == null) {
                throw new IllegalStateException("This problem kind has no message associated: " + myKind);
            }
            PsiExpression expression = PsiUtil.skipParenthesizedExprDown(getDereferencedExpression());
            if (expression != null) {
                if (ExpressionUtils.isNullLiteral(expression) || expressions.get(expression) == DataFlowInspectionBase.ConstantResult.NULL) {
                    return myKind.myAlwaysNullMessage;
                }
            }
            return myKind.myNormalMessage;
        }

        @Nonnull
        public NullabilityProblemKind<T> getKind() {
            return myKind;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof NullabilityProblem)) {
                return false;
            }
            NullabilityProblem<?> problem = (NullabilityProblem<?>)o;
            return myKind.equals(problem.myKind) && myAnchor.equals(problem.myAnchor)
                && Objects.equals(myDereferencedExpression, problem.myDereferencedExpression);
        }

        @Override
        public int hashCode() {
            return Objects.hash(myKind, myAnchor, myDereferencedExpression);
        }

        @Override
        @RequiredReadAction
        public String toString() {
            return "[" + myKind + "] " + myAnchor.getText();
        }

        public NullabilityProblem<T> withExpression(PsiExpression expression) {
            return expression == myDereferencedExpression ? this : new NullabilityProblem<>(myKind, myAnchor, expression);
        }
    }
}
