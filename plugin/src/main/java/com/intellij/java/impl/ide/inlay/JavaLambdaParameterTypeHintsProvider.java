// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.ide.inlay;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.java.localize.JavaLocalize;
import consulo.language.Language;
import consulo.language.editor.inlay.*;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class JavaLambdaParameterTypeHintsProvider implements DeclarativeInlayHintsProvider {
    public static final String PROVIDER_ID = "java.implicit.types.lambda";

    @Override
    public DeclarativeInlayHintsCollector createCollector(PsiFile file, Editor editor) {
        return new Collector();
    }

    @Nonnull
    @Override
    public Language getLanguage() {
        return JavaLanguage.INSTANCE;
    }

    @Nonnull
    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Nonnull
    @Override
    public LocalizeValue getName() {
        return JavaLocalize.settingsInlayJavaImplicitTypesLambda();
    }

    @Nonnull
    @Override
    public LocalizeValue getDescription() {
        return JavaLocalize.settingsInlayJavaImplicitTypesLambdaDescription();
    }

    @Nonnull
    @Override
    public InlayGroup getGroup() {
        return InlayGroup.TYPES_GROUP;
    }

    @Nonnull
    @Override
    public LocalizeValue getPreviewFileText() {
        return null;
    }

    private static class Collector implements DeclarativeInlayHintsCollector.SharedBypassCollector {
        @Override
        public void collectFromElement(PsiElement element, DeclarativeInlayTreeSink sink) {
            if (!(element instanceof PsiParameter)) return;
            if (!(element.getParent() instanceof PsiParameterList)) return;
            if (!(element.getParent().getParent() instanceof PsiLambdaExpression)) return;
            PsiParameter parameter = (PsiParameter) element;
            if (parameter.getTypeElement() != null) return;
            PsiIdentifier identifier = parameter.getNameIdentifier();
            if (identifier == null) return;
            var type = parameter.getType();
            if (type == PsiTypes.nullType() || type instanceof PsiLambdaParameterType) return;
            sink.addPresentation(
                new DeclarativeInlayPosition.InlineInlayPosition(identifier.getTextRange().getStartOffset(), false),
                null,
                null,
                new HintFormat(HintColorKind.Default, HintFontSize.ABitSmallerThanInEditor, HintMarginPadding.MarginAndSmallerPadding),
                factory -> JavaTypeHintsFactory.typeHint(type, factory)
            );
        }
    }
}
