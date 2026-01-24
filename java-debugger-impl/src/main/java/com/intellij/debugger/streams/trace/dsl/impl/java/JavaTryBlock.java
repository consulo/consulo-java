// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl.impl.java;

import consulo.execution.debug.stream.trace.dsl.CodeBlock;
import consulo.execution.debug.stream.trace.dsl.StatementFactory;
import consulo.execution.debug.stream.trace.dsl.impl.common.TryBlockBase;
import jakarta.annotation.Nonnull;

/**
 * @author Vitaliy.Bibaev
 */
public class JavaTryBlock extends TryBlockBase {
    private final CodeBlock block;

    public JavaTryBlock(@Nonnull CodeBlock block, @Nonnull StatementFactory statementFactory) {
        super(statementFactory);
        this.block = block;
    }

    @Nonnull
    @Override
    public String toCode(int indent) {
        CatchBlockDescriptor descriptor = myCatchDescriptor;
        if (descriptor == null) {
            throw new IllegalStateException("catch block must be specified");
        }
        return IndentUtil.withIndent("try {\n", indent) +
               block.toCode(indent + 1) +
               "} catch(" + statementFactory.createVariableDeclaration(descriptor.getVariable(), true).toCode() + ") {\n" +
               descriptor.getBlock().toCode(indent + 1) +
               IndentUtil.withIndent("}", indent);
    }
}
