// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl.impl.java;

import consulo.execution.debug.stream.trace.dsl.Expression;
import consulo.execution.debug.stream.trace.dsl.LambdaBody;
import consulo.execution.debug.stream.trace.dsl.StatementFactory;
import jakarta.annotation.Nonnull;

/**
 * @author Vitaliy.Bibaev
 */
public class JavaLambdaBody extends JavaCodeBlock implements LambdaBody {
    private final Expression lambdaArg;

    public JavaLambdaBody(@Nonnull StatementFactory statementFactory, @Nonnull Expression lambdaArg) {
        super(statementFactory);
        this.lambdaArg = lambdaArg;
    }

    @Nonnull
    @Override
    public Expression getLambdaArg() {
        return lambdaArg;
    }

    @Nonnull
    @Override
    public String toCode(int indent) {
        if (isExpression()) {
            return getStatements().get(0).toCode();
        }
        return super.toCode(indent);
    }

    public boolean isExpression() {
        return getSize() == 1;
    }

    @Override
    public void doReturn(@Nonnull Expression expression) {
        if (getSize() == 0) {
            addStatement(expression);
        } else {
            super.doReturn(expression);
        }
    }
}
