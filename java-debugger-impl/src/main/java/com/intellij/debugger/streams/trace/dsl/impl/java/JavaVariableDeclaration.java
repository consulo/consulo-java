// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl.impl.java;

import consulo.execution.debug.stream.trace.dsl.Variable;
import consulo.execution.debug.stream.trace.dsl.VariableDeclaration;
import jakarta.annotation.Nonnull;

/**
 * @author Vitaliy.Bibaev
 */
public class JavaVariableDeclaration implements VariableDeclaration {
    private final Variable variable;
    private final boolean isMutable;
    private final String init;

    public JavaVariableDeclaration(@Nonnull Variable variable, boolean isMutable) {
        this(variable, isMutable, "");
    }

    public JavaVariableDeclaration(@Nonnull Variable variable, boolean isMutable, @Nonnull String init) {
        this.variable = variable;
        this.isMutable = isMutable;
        this.init = init;
    }

    @Nonnull
    @Override
    public Variable getVariable() {
        return variable;
    }

    @Override
    public boolean isMutable() {
        return isMutable;
    }

    @Nonnull
    @Override
    public String toCode(int indent) {
        String modifier = !isMutable ? "final " : "";
        String initExpression = init.trim().isEmpty() ? "" : " = " + init;
        return IndentUtil.withIndent(modifier + variable.getType().getVariableTypeName() + " " + variable.getName() + initExpression, indent);
    }
}
