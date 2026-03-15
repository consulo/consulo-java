// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl.impl.java;

import consulo.execution.debug.stream.trace.dsl.Expression;
import consulo.execution.debug.stream.trace.dsl.Lambda;
import consulo.execution.debug.stream.trace.dsl.LambdaBody;
import consulo.execution.debug.stream.trace.dsl.impl.TextExpression;

/**
 * @author Vitaliy.Bibaev
 */
public class JavaLambda implements Lambda {
    private final String variableName;
    private final JavaLambdaBody body;

    public JavaLambda(String variableName, JavaLambdaBody body) {
        this.variableName = variableName;
        this.body = body;
    }

    @Override
    public String getVariableName() {
        return variableName;
    }

    @Override
    public LambdaBody getBody() {
        return body;
    }

    @Override
    public Expression call(String callName, Expression... args) {
        return new TextExpression("(" + toCode(0) + ")").call(callName, args);
    }

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
