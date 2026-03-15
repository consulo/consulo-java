// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.lib.impl;

import com.intellij.debugger.streams.psi.impl.InheritanceBasedChainDetector;
import com.intellij.debugger.streams.psi.impl.JavaChainTransformerImpl;
import com.intellij.debugger.streams.psi.impl.JavaStreamChainBuilder;
import com.intellij.debugger.streams.trace.dsl.impl.java.JavaStatementFactory;
import com.intellij.debugger.streams.trace.impl.JavaTraceExpressionBuilder;
import com.intellij.java.language.psi.CommonClassNames;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.execution.debug.stream.lib.LibrarySupport;
import consulo.execution.debug.stream.trace.TraceExpressionBuilder;
import consulo.execution.debug.stream.trace.dsl.Lambda;
import consulo.execution.debug.stream.trace.dsl.impl.DslImpl;
import consulo.execution.debug.stream.trace.dsl.impl.TextExpression;
import consulo.execution.debug.stream.trace.impl.handler.type.GenericType;
import consulo.execution.debug.stream.wrapper.CallArgument;
import consulo.execution.debug.stream.wrapper.IntermediateStreamCall;
import consulo.execution.debug.stream.wrapper.StreamCallType;
import consulo.execution.debug.stream.wrapper.StreamChainBuilder;
import consulo.execution.debug.stream.wrapper.impl.CallArgumentImpl;
import consulo.project.Project;

import java.util.Collections;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
@ExtensionImpl
public final class JBIterableSupportProvider extends JvmLibrarySupportProvider {
    private static final String CLASS_NAME = "consulo.util.collection.JBIterable";

    private final LibrarySupport librarySupport = new JBIterableSupport();
    private final DslImpl dsl = new DslImpl(new JBIterableJavaStatementFactory());

    @Override
    public String getLanguageId() {
        return "JAVA";
    }

    @Override
    public StreamChainBuilder getChainBuilder() {
        return new JavaStreamChainBuilder(new JavaChainTransformerImpl(), new InheritanceBasedChainDetector(CLASS_NAME));
    }

    @Override
    public TraceExpressionBuilder getExpressionBuilder(Project project) {
        return new JavaTraceExpressionBuilder(project, librarySupport.createHandlerFactory(dsl), dsl);
    }

    @Override
    public LibrarySupport getLibrarySupport() {
        return librarySupport;
    }

    private static class JBIterableJavaStatementFactory extends JavaStatementFactory {
        @Override
        public IntermediateStreamCall createPeekCall(GenericType elementsType, Lambda lambda) {
            var lambdaBody = createEmptyLambdaBody(lambda.getVariableName());
            lambdaBody.add(lambda.getBody());
            lambdaBody.doReturn(new TextExpression("true"));
            var newLambda = createLambda(lambda.getVariableName(), lambdaBody);
            return new JBIterablePeekCall(elementsType, newLambda.toCode());
        }
    }

    private static class JBIterablePeekCall implements IntermediateStreamCall {
        private final GenericType elementsType;
        private final String argText;

        JBIterablePeekCall(GenericType elementsType, String argText) {
            this.elementsType = elementsType;
            this.argText = argText;
        }

        @Override
        public String getName() {
            return "filter";
        }

        @Override
        public String getGenericArguments() {
            return "";
        }

        @Override
        public List<CallArgument> getArguments() {
            return Collections.singletonList(new CallArgumentImpl(CommonClassNames.JAVA_LANG_OBJECT, argText));
        }

        @Override
        public StreamCallType getType() {
            return StreamCallType.INTERMEDIATE;
        }

        @Override
        public TextRange getTextRange() {
            return TextRange.EMPTY_RANGE;
        }

        @Override
        public GenericType getTypeBefore() {
            return elementsType;
        }

        @Override
        public GenericType getTypeAfter() {
            return elementsType;
        }
    }
}
