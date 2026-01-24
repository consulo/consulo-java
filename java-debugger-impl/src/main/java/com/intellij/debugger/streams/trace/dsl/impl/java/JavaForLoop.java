// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl.impl.java;

import consulo.execution.debug.stream.trace.dsl.Convertable;
import consulo.execution.debug.stream.trace.dsl.Expression;
import consulo.execution.debug.stream.trace.dsl.ForLoopBody;
import consulo.execution.debug.stream.trace.dsl.VariableDeclaration;
import jakarta.annotation.Nonnull;

/**
 * @author Vitaliy.Bibaev
 */
public class JavaForLoop implements Convertable {
    private final VariableDeclaration initialization;
    private final Expression condition;
    private final Expression afterThought;
    private final ForLoopBody loopBody;

    public JavaForLoop(@Nonnull VariableDeclaration initialization,
                       @Nonnull Expression condition,
                       @Nonnull Expression afterThought,
                       @Nonnull ForLoopBody loopBody) {
        this.initialization = initialization;
        this.condition = condition;
        this.afterThought = afterThought;
        this.loopBody = loopBody;
    }

    @Nonnull
    @Override
    public String toCode(int indent) {
        return IndentUtil.withIndent("for (" + initialization.toCode() + "; " +
                                     condition.toCode() + "; " + afterThought.toCode() + ") {\n", indent) +
               loopBody.toCode(indent + 1) +
               IndentUtil.withIndent("}", indent);
    }
}
