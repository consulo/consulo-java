// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.codeInsight.daemon.impl;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiTypeParameter;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.java.localize.JavaLocalize;
import consulo.language.editor.codeVision.CodeVisionRelativeOrdering;
import consulo.language.editor.impl.codeVision.ReferencesCodeVisionProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import org.jspecify.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.List;

@ExtensionImpl
public class JavaReferencesCodeVisionProvider extends ReferencesCodeVisionProvider {
    public static final String ID = "java.references";

    @Override
    public boolean acceptsFile(PsiFile file) {
        return file.getLanguage() == JavaLanguage.INSTANCE;
    }

    @Override
    public boolean acceptsElement(PsiElement element) {
        return element instanceof PsiMember && !(element instanceof PsiTypeParameter);
    }

    @Override
    public @Nullable String getHint(PsiElement element, PsiFile file) {
        JavaTelescope.UsagesHint usagesHint = JavaTelescope.usagesHint((PsiMember) element, file);
        if (usagesHint == null) return null;
        return usagesHint.hint().get();
    }

    @Override
    public void handleClick(Editor editor, PsiElement psiElement, MouseEvent mouseEvent) {
    }

    @Override
    public List<CodeVisionRelativeOrdering> getRelativeOrderings() {
        return List.of(new CodeVisionRelativeOrdering.CodeVisionRelativeOrderingBefore(JavaInheritorsCodeVisionProvider.ID));
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public LocalizeValue getName() {
        return JavaLocalize.settingsInlayJavaUsages();
    }
}
