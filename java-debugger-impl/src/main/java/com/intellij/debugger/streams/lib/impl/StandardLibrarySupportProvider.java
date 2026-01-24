// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.lib.impl;

import com.intellij.debugger.streams.psi.impl.JavaChainTransformerImpl;
import com.intellij.debugger.streams.psi.impl.JavaStreamChainBuilder;
import com.intellij.debugger.streams.psi.impl.PackageChainDetector;
import com.intellij.debugger.streams.trace.dsl.impl.java.JavaStatementFactory;
import com.intellij.debugger.streams.trace.impl.JavaTraceExpressionBuilder;
import consulo.annotation.component.ExtensionImpl;
import consulo.execution.debug.stream.lib.LibrarySupport;
import consulo.execution.debug.stream.trace.TraceExpressionBuilder;
import consulo.execution.debug.stream.trace.dsl.Dsl;
import consulo.execution.debug.stream.trace.dsl.impl.DslImpl;
import consulo.execution.debug.stream.wrapper.StreamChainBuilder;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

/**
 * @author Vitaliy.Bibaev
 */
@ExtensionImpl
public final class StandardLibrarySupportProvider extends JvmLibrarySupportProvider {
    private static final StreamChainBuilder BUILDER = new JavaStreamChainBuilder(
        new JavaChainTransformerImpl(),
        PackageChainDetector.forJavaStreams("java.util.stream")
    );
    private static final LibrarySupport SUPPORT = new StandardLibrarySupport();
    private static final Dsl DSL = new DslImpl(new JavaStatementFactory());

    @Nonnull
    @Override
    public String getLanguageId() {
        return "JAVA";
    }

    @Nonnull
    @Override
    public TraceExpressionBuilder getExpressionBuilder(@Nonnull Project project) {
        return new JavaTraceExpressionBuilder(project, SUPPORT.createHandlerFactory(DSL), DSL);
    }

    @Nonnull
    @Override
    public StreamChainBuilder getChainBuilder() {
        return BUILDER;
    }

    @Nonnull
    @Override
    public LibrarySupport getLibrarySupport() {
        return SUPPORT;
    }
}
