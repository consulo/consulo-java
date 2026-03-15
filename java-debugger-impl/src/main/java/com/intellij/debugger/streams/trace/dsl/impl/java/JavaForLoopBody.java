// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl.impl.java;

import consulo.execution.debug.stream.trace.dsl.Expression;
import consulo.execution.debug.stream.trace.dsl.ForLoopBody;
import consulo.execution.debug.stream.trace.dsl.StatementFactory;
import consulo.execution.debug.stream.trace.dsl.Variable;
import consulo.execution.debug.stream.trace.dsl.impl.TextExpression;

/**
 * @author Vitaliy.Bibaev
 */
public class JavaForLoopBody extends JavaCodeBlock implements ForLoopBody {
    private static final Expression BREAK = new TextExpression("break");

    private final Variable loopVariable;

    public JavaForLoopBody(StatementFactory statementFactory, Variable loopVariable) {
        super(statementFactory);
        this.loopVariable = loopVariable;
    }

    @Override
    public Variable getLoopVariable() {
        return loopVariable;
    }

    @Override
    public Expression breakIteration() {
        return BREAK;
    }
}
