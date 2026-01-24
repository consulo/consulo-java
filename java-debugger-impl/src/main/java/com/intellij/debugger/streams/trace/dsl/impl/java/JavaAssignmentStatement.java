// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl.impl.java;

import consulo.execution.debug.stream.trace.dsl.Expression;
import consulo.execution.debug.stream.trace.dsl.Variable;
import consulo.execution.debug.stream.trace.dsl.impl.AssignmentStatement;
import jakarta.annotation.Nonnull;

/**
 * @author Vitaliy.Bibaev
 */
public class JavaAssignmentStatement implements AssignmentStatement {
    private final Variable variable;
    private final Expression expression;

    public JavaAssignmentStatement(@Nonnull Variable variable, @Nonnull Expression expression) {
        this.variable = variable;
        this.expression = expression;
    }

    @Nonnull
    @Override
    public Variable getVariable() {
        return variable;
    }

    @Nonnull
    @Override
    public Expression getExpression() {
        return expression;
    }

    @Nonnull
    @Override
    public String toCode(int indent) {
        return IndentUtil.withIndent(variable.getName() + " = " + expression.toCode(), indent);
    }
}
