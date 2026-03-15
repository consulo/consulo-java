// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

@ExtensionImpl
public class JavaImplicitTypeDeclarativeInlayHintsProvider implements DeclarativeInlayHintsProvider {
    public static final String PROVIDER_ID = "java.implicit.types";

    @Override
    public DeclarativeInlayHintsCollector createCollector(PsiFile file, Editor editor) {
        return new Collector();
    }

    @Override
    public Language getLanguage() {
        return JavaLanguage.INSTANCE;
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public LocalizeValue getName() {
        return JavaLocalize.settingsInlayJavaImplicitTypesLocal();
    }

    @Override
    public LocalizeValue getDescription() {
        return JavaLocalize.settingsInlayJavaImplicitTypesLocalDescription();
    }

    @Override
    public LocalizeValue getPreviewFileText() {
        return JavaLocalize.inlayprovidersJavaImplicitTypes();
    }

    @Override
    public InlayGroup getGroup() {
        return InlayGroup.TYPES_GROUP;
    }

    private static class Collector implements DeclarativeInlayHintsCollector.SharedBypassCollector {
        @Override
        public void collectFromElement(PsiElement element, DeclarativeInlayTreeSink sink) {
            if (!(element instanceof PsiLocalVariable)) return;
            PsiLocalVariable variable = (PsiLocalVariable) element;

            PsiElement init = variable.getInitializer();
            if (init == null) return;

            PsiIdentifier identifier = (PsiIdentifier) variable.getIdentifyingElement();
            if (identifier == null) return;

            if (init instanceof PsiLiteral
                || init instanceof PsiPolyadicExpression
                || init instanceof PsiNewExpression
                || init instanceof PsiTypeCastExpression) {
                return;
            }

            PsiTypeElement typeElement = variable.getTypeElement();
            if (!typeElement.isInferredType()) return;

            PsiType type = variable.getType();
            if (type == PsiTypes.nullType()) return;

            sink.addPresentation(
                new DeclarativeInlayPosition.InlineInlayPosition(identifier.getTextRange().getEndOffset(), true),
                null,
                null,
                new HintFormat(HintColorKind.Default, HintFontSize.ABitSmallerThanInEditor, HintMarginPadding.MarginAndSmallerPadding),
                builder -> {
                    builder.text(": ");
                    JavaTypeHintsFactory.typeHint(type, builder);
                }
            );
        }
    }
}
