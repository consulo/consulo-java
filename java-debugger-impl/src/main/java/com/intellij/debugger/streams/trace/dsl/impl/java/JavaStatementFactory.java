// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.dsl.impl.java;

import com.intellij.debugger.streams.trace.impl.handler.PeekCall;
import consulo.execution.debug.stream.trace.dsl.*;
import consulo.execution.debug.stream.trace.dsl.impl.AssignmentStatement;
import consulo.execution.debug.stream.trace.dsl.impl.TextExpression;
import consulo.execution.debug.stream.trace.dsl.impl.VariableImpl;
import consulo.execution.debug.stream.trace.impl.handler.type.GenericType;
import consulo.execution.debug.stream.wrapper.IntermediateStreamCall;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * @author Vitaliy.Bibaev
 */
public class JavaStatementFactory implements StatementFactory {
    @Override
    public Types getTypes() {
        return JavaTypes.INSTANCE;
    }

    @Override
    public CompositeCodeBlock createEmptyCompositeCodeBlock() {
        return new JavaCodeBlock(this);
    }

    @Override
    public CodeBlock createEmptyCodeBlock() {
        return new JavaCodeBlock(this);
    }

    @Override
    public VariableDeclaration createVariableDeclaration(Variable variable, boolean isMutable) {
        return new JavaVariableDeclaration(variable, isMutable);
    }

    @Override
    public VariableDeclaration createVariableDeclaration(Variable variable, Expression init, boolean isMutable) {
        return new JavaVariableDeclaration(variable, isMutable, init.toCode());
    }

    @Override
    public ForLoopBody createEmptyForLoopBody(Variable iterateVariable) {
        return new JavaForLoopBody(this, iterateVariable);
    }

    @Override
    public Convertable createForEachLoop(Variable iterateVariable, Expression collection, ForLoopBody loopBody) {
        return new JavaForEachLoop(iterateVariable, collection, loopBody);
    }

    @Override
    public Convertable createForLoop(VariableDeclaration initialization,
                                     Expression condition,
                                     Expression afterThought,
                                     ForLoopBody loopBody) {
        return new JavaForLoop(initialization, condition, afterThought, loopBody);
    }

    @Override
    public LambdaBody createEmptyLambdaBody(String argName) {
        return new JavaLambdaBody(this, new TextExpression(argName));
    }

    @Override
    public Lambda createLambda(String argName, LambdaBody lambdaBody) {
        assert lambdaBody instanceof JavaLambdaBody;
        return new JavaLambda(argName, (JavaLambdaBody) lambdaBody);
    }

    @Override
    public Variable createVariable(GenericType type, String name) {
        return new VariableImpl(type, name);
    }

    @Override
    public Expression and(Expression left, Expression right) {
        return new TextExpression(left.toCode() + " && " + right.toCode());
    }

    @Override
    public Expression equals(Expression left, Expression right) {
        return new TextExpression("java.util.Objects.equals(" + left.toCode() + ", " + right.toCode() + ")");
    }

    @Override
    public Expression same(Expression left, Expression right) {
        return new TextExpression(left.toCode() + " == " + right.toCode());
    }

    @Override
    public IfBranch createIfBranch(Expression condition, CodeBlock thenBlock) {
        return new JavaIfBranch(condition, thenBlock, this);
    }

    @Override
    public AssignmentStatement createAssignmentStatement(Variable variable, Expression expression) {
        return new JavaAssignmentStatement(variable, expression);
    }

    @Override
    public MapVariable createMapVariable(GenericType keyType,
                                         GenericType valueType,
                                         String name,
                                         boolean linked,
                                         Expression... args) {
        Types types = getTypes();
        return new JavaMapVariable(linked ? types.linkedMap(keyType, valueType) : types.map(keyType, valueType), name);
    }

    @Override
    public ArrayVariable createArrayVariable(GenericType elementType, String name) {
        return new JavaArrayVariable(getTypes().array(elementType), name);
    }

    @Override
    public Convertable createScope(CodeBlock codeBlock) {
        return new Convertable() {
            @Override
            public String toCode(int indent) {
                return IndentUtil.withIndent("{\n", indent) +
                       codeBlock.toCode(indent + 1) +
                       IndentUtil.withIndent("}", indent);
            }
        };
    }

    @Override
    public TryBlock createTryBlock(CodeBlock block) {
        return new JavaTryBlock(block, this);
    }

    @Override
    public VariableDeclaration createTimeVariableDeclaration() {
        Types types = getTypes();
        return new JavaVariableDeclaration(createVariable(types.TIME(), "time"), false, types.TIME().getDefaultValue());
    }

    @Override
    public Expression currentTimeExpression() {
        return new TextExpression("time").call("get");
    }

    @Override
    public Expression updateCurrentTimeExpression() {
        return new TextExpression("time").call("incrementAndGet");
    }

    @Override
    public Expression currentNanosecondsExpression() {
        return new TextExpression("java.lang.System.nanoTime()");
    }

    @Override
    public Expression createNewArrayExpression(GenericType elementType, Expression... args) {
        String elements = Arrays.stream(args).map(Expression::toCode).collect(Collectors.joining(", "));
        return new TextExpression("new " + elementType.getVariableTypeName() + "[] { " + elements + " }");
    }

    @Override
    public Expression createNewSizedArray(GenericType elementType, Expression size) {
        return new TextExpression("new " + elementType.getVariableTypeName() + "[" + size.toCode() + "]");
    }

    @Override
    public Expression createNewListExpression(GenericType elementType, Expression... args) {
        if (args.length == 0) {
            return new TextExpression(getTypes().list(elementType).getDefaultValue());
        }
        String elements = Arrays.stream(args).map(Expression::toCode).collect(Collectors.joining(", "));
        return new TextExpression("java.util.Arrays.asList(" + elements + ")");
    }

    @Override
    public IntermediateStreamCall createPeekCall(GenericType elementsType, String lambda) {
        return new PeekCall(lambda, elementsType);
    }

    @Override
    public ListVariable createListVariable(GenericType elementType, String name) {
        return new JavaListVariable(getTypes().list(elementType), name);
    }

    @Override
    public Expression not(Expression expression) {
        return new TextExpression("!" + expression.toCode());
    }
}
