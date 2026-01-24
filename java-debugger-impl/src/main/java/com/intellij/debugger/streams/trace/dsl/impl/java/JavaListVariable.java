// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl.impl.java;

import consulo.execution.debug.stream.trace.dsl.Expression;
import consulo.execution.debug.stream.trace.dsl.ListVariable;
import consulo.execution.debug.stream.trace.dsl.VariableDeclaration;
import consulo.execution.debug.stream.trace.dsl.impl.VariableImpl;
import consulo.execution.debug.stream.trace.impl.handler.type.ListType;
import jakarta.annotation.Nonnull;

/**
 * @author Vitaliy.Bibaev
 */
public class JavaListVariable extends VariableImpl implements ListVariable {
    private final ListType listType;

    public JavaListVariable(@Nonnull ListType type, @Nonnull String name) {
        super(type, name);
        this.listType = type;
    }

    @Nonnull
    @Override
    public ListType getType() {
        return listType;
    }

    @Nonnull
    @Override
    public Expression get(@Nonnull Expression index) {
        return call("get", index);
    }

    @Nonnull
    @Override
    public Expression set(@Nonnull Expression index, @Nonnull Expression newValue) {
        return call("set", index, newValue);
    }

    @Nonnull
    @Override
    public Expression add(@Nonnull Expression element) {
        return call("add", element);
    }

    @Nonnull
    @Override
    public Expression contains(@Nonnull Expression element) {
        return call("contains", element);
    }

    @Nonnull
    @Override
    public Expression size() {
        return call("size");
    }

    @Nonnull
    @Override
    public VariableDeclaration defaultDeclaration() {
        return new JavaVariableDeclaration(this, false, listType.getDefaultValue());
    }
}
