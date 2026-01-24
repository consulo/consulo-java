// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.impl.handler.unified;

import com.intellij.debugger.streams.trace.dsl.impl.java.JavaTypes;
import consulo.execution.debug.stream.trace.dsl.CodeBlock;
import consulo.execution.debug.stream.trace.dsl.Dsl;
import consulo.execution.debug.stream.trace.dsl.Expression;
import consulo.execution.debug.stream.trace.dsl.VariableDeclaration;
import consulo.execution.debug.stream.trace.dsl.impl.TextExpression;
import consulo.execution.debug.stream.trace.impl.handler.type.GenericType;
import consulo.execution.debug.stream.trace.impl.handler.unified.HandlerBase;
import consulo.execution.debug.stream.trace.impl.handler.unified.TerminatorTraceHandler;
import consulo.execution.debug.stream.wrapper.IntermediateStreamCall;
import consulo.execution.debug.stream.wrapper.TerminatorStreamCall;
import jakarta.annotation.Nonnull;

import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class OptionalTerminationHandler extends HandlerBase.Terminal {
    private final TerminatorStreamCall call;
    private final String resultExpression;
    private final TerminatorTraceHandler myTerminatorHandler;

    public OptionalTerminationHandler(@Nonnull TerminatorStreamCall call,
                                      @Nonnull String resultExpression,
                                      @Nonnull Dsl dsl) {
        super(dsl);
        this.call = call;
        this.resultExpression = resultExpression;
        this.myTerminatorHandler = new TerminatorTraceHandler(call, dsl);
    }

    @Nonnull
    @Override
    public List<VariableDeclaration> additionalVariablesDeclaration() {
        return myTerminatorHandler.additionalVariablesDeclaration();
    }

    @Nonnull
    @Override
    public CodeBlock prepareResult() {
        return myTerminatorHandler.prepareResult();
    }

    @Nonnull
    @Override
    public Expression getResultExpression() {
        Expression isPresent = dsl.newArray(dsl.getTypes().BOOLEAN(),
                                            new TextExpression(resultExpression).call("isPresent"));
        GenericType optionalType = JavaTypes.INSTANCE.unwrapOptional(call.getResultType());
        Expression optionalContent = dsl.newArray(optionalType,
                                                  new TextExpression(resultExpression).call("orElse",
                                                                                            new TextExpression(optionalType.getDefaultValue())));
        Expression optionalData = dsl.newArray(dsl.getTypes().ANY(), isPresent, optionalContent);
        return dsl.newArray(dsl.getTypes().ANY(), myTerminatorHandler.getResultExpression(), optionalData);
    }

    @Nonnull
    @Override
    public List<IntermediateStreamCall> additionalCallsBefore() {
        return myTerminatorHandler.additionalCallsBefore();
    }
}
