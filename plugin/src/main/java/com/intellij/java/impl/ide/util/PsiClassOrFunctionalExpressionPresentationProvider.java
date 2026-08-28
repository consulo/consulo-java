// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.ide.util;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.impl.psi.presentation.java.ClassPresentationUtil;
import com.intellij.java.language.psi.util.PsiExpressionTrimRenderer;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.Application;
import consulo.language.editor.ui.navigation.PsiTargetPresentationFactory;
import consulo.language.editor.ui.navigation.TargetPresentationProvider;
import consulo.language.psi.NavigatablePsiElement;
import consulo.localize.LocalizeValue;
import consulo.navigation.TargetPresentation;
import consulo.navigation.TargetPresentationBuilder;

/**
 * What {@code PsiClassOrFunctionalExpressionListCellRenderer} used to draw, as data built once under
 * a read lock.
 */
public class PsiClassOrFunctionalExpressionPresentationProvider implements TargetPresentationProvider<NavigatablePsiElement> {
    @Override
    @RequiredReadAction
    public TargetPresentation getPresentation(NavigatablePsiElement element) {
        String text = element instanceof PsiClass psiClass
            ? ClassPresentationUtil.getNameForClass(psiClass, false)
            : PsiExpressionTrimRenderer.render((PsiExpression)element);

        TargetPresentationBuilder builder = Application.get().getInstance(PsiTargetPresentationFactory.class)
            .presentationBuilder(element);
        builder = builder.withPresentableText(LocalizeValue.of(text));
        return MethodOrFunctionalExpressionPresentationProvider.withContainer(builder, element).build();
    }
}
