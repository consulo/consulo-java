// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl.impl.java;

import consulo.execution.debug.stream.trace.dsl.CodeBlock;
import consulo.execution.debug.stream.trace.dsl.Expression;
import consulo.execution.debug.stream.trace.dsl.Statement;
import consulo.execution.debug.stream.trace.dsl.impl.common.IfBranchBase;
import jakarta.annotation.Nonnull;

/**
 * @author Vitaliy.Bibaev
 */
public class JavaIfBranch extends IfBranchBase {
    public JavaIfBranch(@Nonnull Expression condition,
                        @Nonnull CodeBlock codeBlock,
                        @Nonnull JavaStatementFactory statementFactory) {
        super(condition, codeBlock, statementFactory);
    }

    @Nonnull
    @Override
    public String toCode(int indent) {
        Statement elseBlockVar = elseBlock;
        String ifThen = IndentUtil.withIndent("if(" + condition.toCode(0) + ") {\n", indent) +
                        thenBlock.toCode(indent + 1) +
                        IndentUtil.withIndent("}", indent);
        if (elseBlockVar != null) {
            return ifThen + " else { \n" +
                   elseBlockVar.toCode(indent + 1) +
                   IndentUtil.withIndent("}", indent);
        }
        return ifThen;
    }
}
