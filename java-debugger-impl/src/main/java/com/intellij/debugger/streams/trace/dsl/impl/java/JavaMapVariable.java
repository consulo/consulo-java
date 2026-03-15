// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl.impl.java;

import consulo.execution.debug.stream.trace.dsl.*;
import consulo.execution.debug.stream.trace.dsl.impl.common.MapVariableBase;
import consulo.execution.debug.stream.trace.impl.handler.type.MapType;

/**
 * @author Vitaliy.Bibaev
 */
public class JavaMapVariable extends MapVariableBase {
    public JavaMapVariable(MapType type, String name) {
        super(type, name);
    }

    @Override
    public Expression get(Expression key) {
        return call("get", key);
    }

    @Override
    public Expression set(Expression key, Expression newValue) {
        return call("put", key, newValue);
    }

    @Override
    public Expression contains(Expression key) {
        return call("containsKey", key);
    }

    @Override
    public Expression keys() {
        return call("keySet");
    }

    @Override
    public Expression size() {
        return call("size");
    }

    @Override
    public CodeBlock computeIfAbsent(Dsl dsl, Expression key, Expression valueIfAbsent, Variable target) {
        return dsl.block(block -> {
            block.assign(target, call("computeIfAbsent", key, dsl.lambda("compIfAbsentKey", lambdaBody -> {
                lambdaBody.doReturn(valueIfAbsent);
            })));
        });
    }

    @Override
    public VariableDeclaration defaultDeclaration(boolean isMutable) {
        return new JavaVariableDeclaration(this, false, getType().getDefaultValue());
    }

    @Override
    public Expression entries() {
        return call("entrySet");
    }
}
