// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl.impl.java;

import consulo.execution.debug.stream.trace.dsl.Expression;
import consulo.execution.debug.stream.trace.dsl.StatementFactory;
import consulo.execution.debug.stream.trace.dsl.impl.LineSeparatedCodeBlock;
import consulo.execution.debug.stream.trace.dsl.impl.TextExpression;
import jakarta.annotation.Nonnull;

/**
 * @author Vitaliy.Bibaev
 */
public class JavaCodeBlock extends LineSeparatedCodeBlock {
    public JavaCodeBlock(@Nonnull StatementFactory statementFactory) {
        super(statementFactory, ";");
    }

    @Override
    public void doReturn(@Nonnull Expression expression) {
        addStatement(new TextExpression("return " + expression.toCode()));
    }
}
