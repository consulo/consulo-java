// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.ide.inlay;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiParenthesizedExpression;
import com.intellij.java.language.psi.PsiType;
import com.siyeh.ig.psiutils.ExpressionUtils;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.localize.JavaLocalize;
import consulo.language.Language;
import consulo.language.editor.inlay.*;
import consulo.language.editor.inlay.chain.AbstractDeclarativeCallChainProvider;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

import java.util.List;

@ExtensionImpl
public class JavaMethodChainsDeclarativeInlayProvider extends AbstractDeclarativeCallChainProvider<PsiMethodCallExpression, PsiType, Void> {
    public static final String PROVIDER_ID = "java.method.chains";

    @Nonnull
    @Override
    public Language getLanguage() {
        return JavaLanguage.INSTANCE;
    }

    @Nonnull
    @Override
    public  String getId() {
        return PROVIDER_ID;
    }

    @Nonnull
    @Override
    public LocalizeValue getName() {
        return JavaLocalize.javaMethodChainsInlayProviderName();
    }

    @Nonnull
    @Override
    public LocalizeValue getDescription() {
        return JavaLocalize.inlayMethodchainsinlayproviderDescription();
    }

    @Nonnull
    @Override
    public InlayGroup getGroup() {
        return InlayGroup.METHOD_CHAINS_GROUP;
    }

    @Nonnull
    @Override
    public LocalizeValue getPreviewFileText() {
        return JavaLocalize.inlayprovidersJavaMethodChains();
    }

    @Override
    protected void buildTree(PsiType receiver,
                             PsiElement expression,
                             Project project,
                             Void context,
                             DeclarativePresentationTreeBuilder treeBuilder) {
        JavaTypeHintsFactory.typeHint(receiver, treeBuilder);
    }

    @Override
    protected PsiType getType(PsiElement element, Void context) {
        return element instanceof PsiExpression ? ((PsiExpression) element).getType() : null;
    }

    @Override
    public Class<PsiMethodCallExpression> getDotQualifiedClass() {
        return PsiMethodCallExpression.class;
    }

    @Override
    protected Void getTypeComputationContext(PsiMethodCallExpression topmostDotQualifiedExpression) {
        // Java implementation doesn't use any additional type computation context
        return null;
    }

    @Override
    protected PsiElement skipParenthesesAndPostfixOperatorsDown(PsiElement element) {
        PsiElement expr = element;
        while (expr instanceof PsiParenthesizedExpression) {
            expr = ((PsiParenthesizedExpression) expr).getExpression();
        }
        return expr instanceof PsiMethodCallExpression ? expr : null;
    }

    @Override
    protected PsiElement getReceiver(PsiMethodCallExpression expression) {
        return expression.getMethodExpression().getQualifier();
    }

    @Override
    protected PsiMethodCallExpression getParentDotQualifiedExpression(PsiMethodCallExpression expression) {
        return ExpressionUtils.getCallForQualifier(expression);
    }

    @Override
    protected boolean isChainUnacceptable(List<ExpressionWithType<PsiType>> list) {
        return false;
    }
}
