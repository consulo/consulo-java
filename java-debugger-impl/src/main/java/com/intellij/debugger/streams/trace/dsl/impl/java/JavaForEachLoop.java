// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl.impl.java;

import consulo.execution.debug.stream.trace.dsl.Convertable;
import consulo.execution.debug.stream.trace.dsl.Expression;
import consulo.execution.debug.stream.trace.dsl.ForLoopBody;
import consulo.execution.debug.stream.trace.dsl.Variable;
import jakarta.annotation.Nonnull;

/**
 * @author Vitaliy.Bibaev
 */
public class JavaForEachLoop implements Convertable {
    private final Variable iterateVariable;
    private final Expression collection;
    private final ForLoopBody loopBody;

    public JavaForEachLoop(@Nonnull Variable iterateVariable,
                           @Nonnull Expression collection,
                           @Nonnull ForLoopBody loopBody) {
        this.iterateVariable = iterateVariable;
        this.collection = collection;
        this.loopBody = loopBody;
    }

    @Nonnull
    @Override
    public String toCode(int indent) {
        return IndentUtil.withIndent("for (" + iterateVariable.getType().getVariableTypeName() + " " +
                                     iterateVariable.getName() + " : " + collection.toCode(0) + ") {\n", indent) +
               loopBody.toCode(indent + 1) +
               IndentUtil.withIndent("}", indent);
    }
}
