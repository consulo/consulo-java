// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.impl.handler.unified;

import com.intellij.debugger.streams.trace.dsl.impl.java.JavaTypes;
import consulo.document.util.TextRange;
import consulo.execution.debug.stream.trace.dsl.*;
import consulo.execution.debug.stream.trace.dsl.impl.TextExpression;
import consulo.execution.debug.stream.trace.impl.handler.type.ClassTypeImpl;
import consulo.execution.debug.stream.trace.impl.handler.unified.HandlerBase;
import consulo.execution.debug.stream.trace.impl.handler.unified.PeekTraceHandler;
import consulo.execution.debug.stream.wrapper.CallArgument;
import consulo.execution.debug.stream.wrapper.IntermediateStreamCall;
import consulo.execution.debug.stream.wrapper.TerminatorStreamCall;
import consulo.execution.debug.stream.wrapper.impl.CallArgumentImpl;
import consulo.execution.debug.stream.wrapper.impl.IntermediateStreamCallImpl;
import consulo.execution.debug.stream.wrapper.impl.TerminatorStreamCallImpl;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class MatchHandler extends HandlerBase.Terminal {
    private static final String PREDICATE_NAME = "predicate42";

    private final TerminatorStreamCall call;
    private final PeekTraceHandler myPeekHandler;
    private final Variable myPredicateVariable;

    public MatchHandler(@Nonnull TerminatorStreamCall call, @Nonnull Dsl dsl) {
        super(dsl);
        this.call = call;
        this.myPeekHandler = new PeekTraceHandler(0, "filterMatch", call.getTypeBefore(), call.getTypeBefore(), dsl);
        this.myPredicateVariable = dsl.variable(new ClassTypeImpl(call.getArguments().get(0).getType()), PREDICATE_NAME);
    }

    @Nonnull
    @Override
    public List<VariableDeclaration> additionalVariablesDeclaration() {
        List<VariableDeclaration> variables = new ArrayList<>();
        variables.addAll(myPeekHandler.additionalVariablesDeclaration());
        CallArgument predicate = call.getArguments().get(0);
        variables.add(dsl.declaration(myPredicateVariable, new TextExpression(predicate.getText()), false));
        return variables;
    }

    @Nonnull
    @Override
    public CodeBlock prepareResult() {
        return dsl.block(block -> {
            var result = block.array(dsl.getTypes().ANY(), "result");
            block.declare(result, dsl.newSizedArray(dsl.getTypes().ANY(), 2), false);
            block.scope(scope -> {
                scope.add(myPeekHandler.prepareResult());
                scope.statement(() -> result.set(0, myPeekHandler.getResultExpression()));
            });
            block.statement(() -> result.set(1, new TextExpression("streamResult")));
        });
    }

    @Nonnull
    @Override
    public TerminatorStreamCall transformCall(@Nonnull TerminatorStreamCall call) {
        List<CallArgument> args = call.getArguments();
        assert args.size() == 1 : "Only predicate should be specified";
        CallArgument predicate = args.get(0);
        String newPredicateBody = "allMatch".equals(call.getName()) ? "false" : "true";
        String newPredicate = dsl.lambda("x", lambdaBody -> {
            lambdaBody.doReturn(new TextExpression(newPredicateBody));
        }).toCode();
        return transformArgs(call, List.of(new CallArgumentImpl(predicate.getType(), newPredicate)));
    }

    @Nonnull
    @Override
    public Expression getResultExpression() {
        return new TextExpression("result");
    }

    @Nonnull
    @Override
    public List<IntermediateStreamCall> additionalCallsBefore() {
        List<IntermediateStreamCall> result = new ArrayList<>(myPeekHandler.additionalCallsBefore());
        String filterPredicate = ("allMatch".equals(call.getName())
            ? myPredicateVariable.call("negate")
            : myPredicateVariable).toCode();
        CallArgument filterArg = new CallArgumentImpl(myPredicateVariable.getType().getVariableTypeName(), filterPredicate);
        result.add(new IntermediateStreamCallImpl("filter", "", List.of(filterArg),
                                                  call.getTypeBefore(), call.getTypeBefore(), TextRange.EMPTY_RANGE));
        result.addAll(myPeekHandler.additionalCallsAfter());
        return result;
    }

    private TerminatorStreamCall transformArgs(@Nonnull TerminatorStreamCall call, @Nonnull List<CallArgument> args) {
        return new TerminatorStreamCallImpl(
            call.getName(),
            call.getGenericArguments(),
            args,
            call.getTypeBefore(),
            call.getResultType(),
            call.getTextRange(),
            call.getResultType().equals(JavaTypes.INSTANCE.VOID())
        );
    }
}
