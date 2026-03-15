// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl.impl.java;

import consulo.execution.debug.stream.trace.dsl.Expression;
import consulo.execution.debug.stream.trace.dsl.ListVariable;
import consulo.execution.debug.stream.trace.dsl.VariableDeclaration;
import consulo.execution.debug.stream.trace.dsl.impl.VariableImpl;
import consulo.execution.debug.stream.trace.impl.handler.type.ListType;

/**
 * @author Vitaliy.Bibaev
 */
public class JavaListVariable extends VariableImpl implements ListVariable {
    private final ListType listType;

    public JavaListVariable(ListType type, String name) {
        super(type, name);
        this.listType = type;
    }

    @Override
    public ListType getType() {
        return listType;
    }

    @Override
    public Expression get(Expression index) {
        return call("get", index);
    }

    @Override
    public Expression set(Expression index, Expression newValue) {
        return call("set", index, newValue);
    }

    @Override
    public Expression add(Expression element) {
        return call("add", element);
    }

    @Override
    public Expression contains(Expression element) {
        return call("contains", element);
    }

    @Override
    public Expression size() {
        return call("size");
    }

    @Override
    public VariableDeclaration defaultDeclaration() {
        return new JavaVariableDeclaration(this, false, listType.getDefaultValue());
    }
}
