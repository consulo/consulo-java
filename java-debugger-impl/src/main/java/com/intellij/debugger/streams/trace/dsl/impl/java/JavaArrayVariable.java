// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl.impl.java;

import consulo.execution.debug.stream.trace.dsl.ArrayVariable;
import consulo.execution.debug.stream.trace.dsl.Expression;
import consulo.execution.debug.stream.trace.dsl.VariableDeclaration;
import consulo.execution.debug.stream.trace.dsl.impl.TextExpression;
import consulo.execution.debug.stream.trace.dsl.impl.VariableImpl;
import consulo.execution.debug.stream.trace.impl.handler.type.ArrayType;
import jakarta.annotation.Nonnull;

/**
 * @author Vitaliy.Bibaev
 */
public class JavaArrayVariable extends VariableImpl implements ArrayVariable {
    private final ArrayType arrayType;

    public JavaArrayVariable(@Nonnull ArrayType type, @Nonnull String name) {
        super(type, name);
        this.arrayType = type;
    }

    @Nonnull
    @Override
    public ArrayType getType() {
        return arrayType;
    }

    @Nonnull
    @Override
    public Expression get(@Nonnull Expression index) {
        return new TextExpression(getName() + "[" + index.toCode() + "]");
    }

    @Nonnull
    @Override
    public Expression set(@Nonnull Expression index, @Nonnull Expression value) {
        return new TextExpression(getName() + "[" + index.toCode() + "] = " + value.toCode());
    }

    @Nonnull
    @Override
    public VariableDeclaration defaultDeclaration(@Nonnull Expression size) {
        return new JavaVariableDeclaration(this, false, arrayType.sizedDeclaration(size.toCode()));
    }
}
