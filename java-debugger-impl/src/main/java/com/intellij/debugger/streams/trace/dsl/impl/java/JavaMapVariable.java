// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl.impl.java;

import consulo.execution.debug.stream.trace.dsl.*;
import consulo.execution.debug.stream.trace.dsl.impl.common.MapVariableBase;
import consulo.execution.debug.stream.trace.impl.handler.type.MapType;
import jakarta.annotation.Nonnull;

/**
 * @author Vitaliy.Bibaev
 */
public class JavaMapVariable extends MapVariableBase {
    public JavaMapVariable(@Nonnull MapType type, @Nonnull String name) {
        super(type, name);
    }

    @Nonnull
    @Override
    public Expression get(@Nonnull Expression key) {
        return call("get", key);
    }

    @Nonnull
    @Override
    public Expression set(@Nonnull Expression key, @Nonnull Expression newValue) {
        return call("put", key, newValue);
    }

    @Nonnull
    @Override
    public Expression contains(@Nonnull Expression key) {
        return call("containsKey", key);
    }

    @Nonnull
    @Override
    public Expression keys() {
        return call("keySet");
    }

    @Nonnull
    @Override
    public Expression size() {
        return call("size");
    }

    @Nonnull
    @Override
    public CodeBlock computeIfAbsent(@Nonnull Dsl dsl, @Nonnull Expression key, @Nonnull Expression valueIfAbsent, @Nonnull Variable target) {
        return dsl.block(block -> {
            block.assign(target, call("computeIfAbsent", key, dsl.lambda("compIfAbsentKey", lambdaBody -> {
                lambdaBody.doReturn(valueIfAbsent);
            })));
        });
    }

    @Nonnull
    @Override
    public VariableDeclaration defaultDeclaration(boolean isMutable) {
        return new JavaVariableDeclaration(this, false, getType().getDefaultValue());
    }

    @Nonnull
    @Override
    public Expression entries() {
        return call("entrySet");
    }
}
