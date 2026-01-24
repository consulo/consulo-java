// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl.impl.java;

import com.intellij.debugger.streams.trace.impl.handler.PeekCall;
import consulo.execution.debug.stream.trace.dsl.*;
import consulo.execution.debug.stream.trace.dsl.impl.AssignmentStatement;
import consulo.execution.debug.stream.trace.dsl.impl.TextExpression;
import consulo.execution.debug.stream.trace.dsl.impl.VariableImpl;
import consulo.execution.debug.stream.trace.impl.handler.type.GenericType;
import consulo.execution.debug.stream.wrapper.IntermediateStreamCall;
import jakarta.annotation.Nonnull;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * @author Vitaliy.Bibaev
 */
public class JavaStatementFactory implements StatementFactory {
    @Nonnull
    @Override
    public Types getTypes() {
        return JavaTypes.INSTANCE;
    }

    @Nonnull
    @Override
    public CompositeCodeBlock createEmptyCompositeCodeBlock() {
        return new JavaCodeBlock(this);
    }

    @Nonnull
    @Override
    public CodeBlock createEmptyCodeBlock() {
        return new JavaCodeBlock(this);
    }

    @Nonnull
    @Override
    public VariableDeclaration createVariableDeclaration(@Nonnull Variable variable, boolean isMutable) {
        return new JavaVariableDeclaration(variable, isMutable);
    }

    @Nonnull
    @Override
    public VariableDeclaration createVariableDeclaration(@Nonnull Variable variable, @Nonnull Expression init, boolean isMutable) {
        return new JavaVariableDeclaration(variable, isMutable, init.toCode());
    }

    @Nonnull
    @Override
    public ForLoopBody createEmptyForLoopBody(@Nonnull Variable iterateVariable) {
        return new JavaForLoopBody(this, iterateVariable);
    }

    @Nonnull
    @Override
    public Convertable createForEachLoop(@Nonnull Variable iterateVariable, @Nonnull Expression collection, @Nonnull ForLoopBody loopBody) {
        return new JavaForEachLoop(iterateVariable, collection, loopBody);
    }

    @Nonnull
    @Override
    public Convertable createForLoop(@Nonnull VariableDeclaration initialization,
                                     @Nonnull Expression condition,
                                     @Nonnull Expression afterThought,
                                     @Nonnull ForLoopBody loopBody) {
        return new JavaForLoop(initialization, condition, afterThought, loopBody);
    }

    @Nonnull
    @Override
    public LambdaBody createEmptyLambdaBody(@Nonnull String argName) {
        return new JavaLambdaBody(this, new TextExpression(argName));
    }

    @Nonnull
    @Override
    public Lambda createLambda(@Nonnull String argName, @Nonnull LambdaBody lambdaBody) {
        assert lambdaBody instanceof JavaLambdaBody;
        return new JavaLambda(argName, (JavaLambdaBody) lambdaBody);
    }

    @Nonnull
    @Override
    public Variable createVariable(@Nonnull GenericType type, @Nonnull String name) {
        return new VariableImpl(type, name);
    }

    @Nonnull
    @Override
    public Expression and(@Nonnull Expression left, @Nonnull Expression right) {
        return new TextExpression(left.toCode() + " && " + right.toCode());
    }

    @Nonnull
    @Override
    public Expression equals(@Nonnull Expression left, @Nonnull Expression right) {
        return new TextExpression("java.util.Objects.equals(" + left.toCode() + ", " + right.toCode() + ")");
    }

    @Nonnull
    @Override
    public Expression same(@Nonnull Expression left, @Nonnull Expression right) {
        return new TextExpression(left.toCode() + " == " + right.toCode());
    }

    @Nonnull
    @Override
    public IfBranch createIfBranch(@Nonnull Expression condition, @Nonnull CodeBlock thenBlock) {
        return new JavaIfBranch(condition, thenBlock, this);
    }

    @Nonnull
    @Override
    public AssignmentStatement createAssignmentStatement(@Nonnull Variable variable, @Nonnull Expression expression) {
        return new JavaAssignmentStatement(variable, expression);
    }

    @Nonnull
    @Override
    public MapVariable createMapVariable(@Nonnull GenericType keyType,
                                         @Nonnull GenericType valueType,
                                         @Nonnull String name,
                                         boolean linked,
                                         @Nonnull Expression... args) {
        Types types = getTypes();
        return new JavaMapVariable(linked ? types.linkedMap(keyType, valueType) : types.map(keyType, valueType), name);
    }

    @Nonnull
    @Override
    public ArrayVariable createArrayVariable(@Nonnull GenericType elementType, @Nonnull String name) {
        return new JavaArrayVariable(getTypes().array(elementType), name);
    }

    @Nonnull
    @Override
    public Convertable createScope(@Nonnull CodeBlock codeBlock) {
        return new Convertable() {
            @Nonnull
            @Override
            public String toCode(int indent) {
                return IndentUtil.withIndent("{\n", indent) +
                       codeBlock.toCode(indent + 1) +
                       IndentUtil.withIndent("}", indent);
            }
        };
    }

    @Nonnull
    @Override
    public TryBlock createTryBlock(@Nonnull CodeBlock block) {
        return new JavaTryBlock(block, this);
    }

    @Nonnull
    @Override
    public VariableDeclaration createTimeVariableDeclaration() {
        Types types = getTypes();
        return new JavaVariableDeclaration(createVariable(types.TIME(), "time"), false, types.TIME().getDefaultValue());
    }

    @Nonnull
    @Override
    public Expression currentTimeExpression() {
        return new TextExpression("time").call("get");
    }

    @Nonnull
    @Override
    public Expression updateCurrentTimeExpression() {
        return new TextExpression("time").call("incrementAndGet");
    }

    @Nonnull
    @Override
    public Expression currentNanosecondsExpression() {
        return new TextExpression("java.lang.System.nanoTime()");
    }

    @Nonnull
    @Override
    public Expression createNewArrayExpression(@Nonnull GenericType elementType, @Nonnull Expression... args) {
        String elements = Arrays.stream(args).map(Expression::toCode).collect(Collectors.joining(", "));
        return new TextExpression("new " + elementType.getVariableTypeName() + "[] { " + elements + " }");
    }

    @Nonnull
    @Override
    public Expression createNewSizedArray(@Nonnull GenericType elementType, @Nonnull Expression size) {
        return new TextExpression("new " + elementType.getVariableTypeName() + "[" + size.toCode() + "]");
    }

    @Nonnull
    @Override
    public Expression createNewListExpression(@Nonnull GenericType elementType, @Nonnull Expression... args) {
        if (args.length == 0) {
            return new TextExpression(getTypes().list(elementType).getDefaultValue());
        }
        String elements = Arrays.stream(args).map(Expression::toCode).collect(Collectors.joining(", "));
        return new TextExpression("java.util.Arrays.asList(" + elements + ")");
    }

    @Nonnull
    @Override
    public IntermediateStreamCall createPeekCall(@Nonnull GenericType elementsType, @Nonnull String lambda) {
        return new PeekCall(lambda, elementsType);
    }

    @Nonnull
    @Override
    public ListVariable createListVariable(@Nonnull GenericType elementType, @Nonnull String name) {
        return new JavaListVariable(getTypes().list(elementType), name);
    }

    @Nonnull
    @Override
    public Expression not(@Nonnull Expression expression) {
        return new TextExpression("!" + expression.toCode());
    }
}
