// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl.impl.java;

import consulo.execution.debug.stream.trace.dsl.ArrayVariable;
import consulo.execution.debug.stream.trace.dsl.Expression;
import consulo.execution.debug.stream.trace.dsl.VariableDeclaration;
import consulo.execution.debug.stream.trace.dsl.impl.TextExpression;
import consulo.execution.debug.stream.trace.dsl.impl.VariableImpl;
import consulo.execution.debug.stream.trace.impl.handler.type.ArrayType;

/**
 * @author Vitaliy.Bibaev
 */
public class JavaArrayVariable extends VariableImpl implements ArrayVariable {
    private final ArrayType arrayType;

    public JavaArrayVariable(ArrayType type, String name) {
        super(type, name);
        this.arrayType = type;
    }

    @Override
    public ArrayType getType() {
        return arrayType;
    }

    @Override
    public Expression get(Expression index) {
        return new TextExpression(getName() + "[" + index.toCode() + "]");
    }

    @Override
    public Expression set(Expression index, Expression value) {
        return new TextExpression(getName() + "[" + index.toCode() + "] = " + value.toCode());
    }

    @Override
    public VariableDeclaration defaultDeclaration(Expression size) {
        return new JavaVariableDeclaration(this, false, arrayType.sizedDeclaration(size.toCode()));
    }
}
