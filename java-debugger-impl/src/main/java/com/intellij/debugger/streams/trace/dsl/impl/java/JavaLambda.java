// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl.impl.java;

import consulo.execution.debug.stream.trace.dsl.Expression;
import consulo.execution.debug.stream.trace.dsl.Lambda;
import consulo.execution.debug.stream.trace.dsl.LambdaBody;
import consulo.execution.debug.stream.trace.dsl.impl.TextExpression;
import jakarta.annotation.Nonnull;

/**
 * @author Vitaliy.Bibaev
 */
public class JavaLambda implements Lambda {
    private final String variableName;
    private final JavaLambdaBody body;

    public JavaLambda(@Nonnull String variableName, @Nonnull JavaLambdaBody body) {
        this.variableName = variableName;
        this.body = body;
    }

    @Nonnull
    @Override
    public String getVariableName() {
        return variableName;
    }

    @Nonnull
    @Override
    public LambdaBody getBody() {
        return body;
    }

    @Nonnull
    @Override
    public Expression call(@Nonnull String callName, @Nonnull Expression... args) {
        return new TextExpression("(" + toCode(0) + ")").call(callName, args);
    }

    @Nonnull
    @Override
    public String toCode(int indent) {
        return IndentUtil.withIndent(variableName + " -> " + convert(body, indent), indent);
    }

    private String convert(JavaLambdaBody lambdaBody, int indent) {
        if (lambdaBody.isExpression()) {
            return lambdaBody.toCode(0);
        }
        return "{\n" + lambdaBody.toCode(indent + 1) + IndentUtil.withIndent("}", indent);
    }
}
