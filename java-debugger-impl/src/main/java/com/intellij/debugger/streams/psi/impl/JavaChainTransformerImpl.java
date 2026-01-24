// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.psi.impl;

import com.intellij.debugger.streams.psi.ChainTransformer;
import com.intellij.debugger.streams.trace.dsl.impl.java.JavaTypes;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.document.util.TextRange;
import consulo.execution.debug.stream.trace.impl.handler.type.GenericType;
import consulo.execution.debug.stream.wrapper.CallArgument;
import consulo.execution.debug.stream.wrapper.IntermediateStreamCall;
import consulo.execution.debug.stream.wrapper.StreamChain;
import consulo.execution.debug.stream.wrapper.TerminatorStreamCall;
import consulo.execution.debug.stream.wrapper.impl.*;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;
import one.util.streamex.StreamEx;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Vitaliy.Bibaev
 */
public class JavaChainTransformerImpl implements ChainTransformer.Java {
    @Override
    public @Nonnull StreamChain transform(@Nonnull List<PsiMethodCallExpression> streamExpressions,
                                          @Nonnull PsiElement context) {
        final PsiMethodCallExpression firstCall = streamExpressions.get(0);

        final PsiExpression qualifierExpression = firstCall.getMethodExpression().getQualifierExpression();
        final PsiType qualifierType = qualifierExpression == null ? null : qualifierExpression.getType();
        final GenericType typeAfterQualifier = qualifierType == null
            ? getGenericTypeOfThis(qualifierExpression)
            : JavaTypes.INSTANCE.fromStreamPsiType(qualifierType);
        final QualifierExpressionImpl qualifier =
            qualifierExpression == null
                ? new QualifierExpressionImpl("", TextRange.EMPTY_RANGE, typeAfterQualifier)
                : new QualifierExpressionImpl(qualifierExpression.getText(), qualifierExpression.getTextRange(), typeAfterQualifier);
        final List<IntermediateStreamCall> intermediateCalls =
            createIntermediateCalls(typeAfterQualifier, streamExpressions.subList(0, streamExpressions.size() - 1));

        final GenericType typeBefore =
            intermediateCalls.isEmpty() ? qualifier.getTypeAfter() : intermediateCalls.get(intermediateCalls.size() - 1).getTypeAfter();
        final TerminatorStreamCall terminationCall = createTerminationCall(typeBefore, streamExpressions.get(streamExpressions.size() - 1));

        return new StreamChainImpl(qualifier, intermediateCalls, terminationCall, context);
    }

    private static @Nonnull GenericType getGenericTypeOfThis(PsiExpression expression) {
        final PsiClass klass = PsiUtil.getContainingClass(expression);

        return klass == null ? JavaTypes.INSTANCE.ANY()
            : JavaTypes.INSTANCE.fromPsiClass(klass);
    }

    private static @Nonnull List<IntermediateStreamCall> createIntermediateCalls(@Nonnull GenericType producerAfterType,
                                                                                 @Nonnull List<PsiMethodCallExpression> expressions) {
        final List<IntermediateStreamCall> result = new ArrayList<>();

        GenericType typeBefore = producerAfterType;
        for (final PsiMethodCallExpression callExpression : expressions) {
            final String name = resolveMethodName(callExpression);
            final List<CallArgument> args = resolveArguments(callExpression);
            final GenericType type = resolveType(callExpression);
            result.add(new IntermediateStreamCallImpl(name, "", args, typeBefore, type, callExpression.getTextRange()));
            typeBefore = type;
        }

        return result;
    }

    @Nonnull
    private static TerminatorStreamCall createTerminationCall(@Nonnull GenericType typeBefore, @Nonnull PsiMethodCallExpression expression) {
        final String name = resolveMethodName(expression);
        final List<CallArgument> args = resolveArguments(expression);
        final GenericType resultType = resolveTerminationCallType(expression);
        return new TerminatorStreamCallImpl(name, "", args, typeBefore, resultType, expression.getTextRange(), resultType.equals(JavaTypes.INSTANCE.VOID()));
    }

    private static @Nonnull List<CallArgument> resolveArguments(@Nonnull PsiMethodCallExpression methodCall) {
        final PsiExpressionList list = methodCall.getArgumentList();
        return StreamEx.of(list.getExpressions())
            .zipWith(StreamEx.of(list.getExpressionTypes()),
                (expression, type) -> new CallArgumentImpl(GenericsUtil.getVariableTypeByExpressionType(type).getCanonicalText(),
                    expression.getText()))
            .collect(Collectors.toList());
    }

    private static @Nonnull String resolveMethodName(@Nonnull PsiMethodCallExpression methodCall) {
        final String name = methodCall.getMethodExpression().getReferenceName();
        Objects.requireNonNull(name, "Method reference must be not null" + methodCall.getText());
        return name;
    }

    private static @Nonnull PsiType extractType(@Nonnull PsiMethodCallExpression expression) {
        final PsiType returnType = expression.getType();
        Objects.requireNonNull(returnType, "Method return type must be not null" + expression.getText());
        return returnType;
    }

    private static @Nonnull GenericType resolveType(@Nonnull PsiMethodCallExpression call) {
        return JavaTypes.INSTANCE.fromStreamPsiType(extractType(call));
    }

    private static @Nonnull GenericType resolveTerminationCallType(@Nonnull PsiMethodCallExpression call) {
        return JavaTypes.INSTANCE.fromPsiType(extractType(call));
    }
}
