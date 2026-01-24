// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.impl;

import com.intellij.debugger.streams.psi.impl.MethodReferenceToLambdaTransformer;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiCodeBlock;
import com.intellij.java.language.psi.PsiElementFactory;
import consulo.application.ApplicationManager;
import consulo.execution.debug.stream.lib.HandlerFactory;
import consulo.execution.debug.stream.trace.dsl.Dsl;
import consulo.execution.debug.stream.trace.impl.TraceExpressionBuilderBase;
import consulo.execution.debug.stream.wrapper.StreamChain;
import consulo.logging.Logger;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

import java.util.function.Supplier;

/**
 * @author Vitaliy.Bibaev
 */
public class JavaTraceExpressionBuilder extends TraceExpressionBuilderBase {
    private static final Logger LOG = Logger.getInstance(JavaTraceExpressionBuilder.class);

    private final Project project;

    public JavaTraceExpressionBuilder(@Nonnull Project project,
                                      @Nonnull HandlerFactory handlerFactory,
                                      @Nonnull Dsl dsl) {
        super(dsl, handlerFactory);
        this.project = project;
    }

    @Nonnull
    @Override
    public String createTraceExpression(@Nonnull StreamChain chain) {
        String codeBlock = super.createTraceExpression(chain);
        PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);

        return ApplicationManager.getApplication().runReadAction((Supplier<String>) () -> {
            PsiCodeBlock block = elementFactory.createCodeBlockFromText(codeBlock, chain.getContext());

            MethodReferenceToLambdaTransformer.INSTANCE.transform(block);

            String resultDeclaration = dsl.declaration(
                dsl.variable(dsl.getTypes().ANY(), resultVariableName),
                dsl.getNullExpression(),
                true
            ).toCode();

            String result = resultDeclaration + "; \n " +
                            block.getText() + " \n" +
                resultVariableName;

            LOG.debug("trace expression: \n" + result);
            return result;
        });
    }
}
