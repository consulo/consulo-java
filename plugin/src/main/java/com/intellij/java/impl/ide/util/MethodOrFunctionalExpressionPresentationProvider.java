// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.ide.util;

import com.intellij.java.language.impl.codeInsight.PsiClassListCellRenderer;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.impl.psi.presentation.java.ClassPresentationUtil;
import com.intellij.java.language.psi.util.PsiExpressionTrimRenderer;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.java.language.psi.util.PsiFormatUtilBase;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.Application;
import consulo.language.editor.ui.navigation.PsiTargetPresentationFactory;
import consulo.language.editor.ui.navigation.TargetPresentationProvider;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.psi.NavigatablePsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.localize.LocalizeValue;
import consulo.navigation.TargetPresentation;
import consulo.navigation.TargetPresentationBuilder;
import org.jspecify.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * What {@code MethodCellRenderer} and {@code MethodOrFunctionalExpressionCellRenderer} used to draw,
 * as data built once under a read lock.
 */
public class MethodOrFunctionalExpressionPresentationProvider implements TargetPresentationProvider<NavigatablePsiElement> {
    /** decided from the whole target list, which exists only after collection - so it is consulted, not captured */
    private final BooleanSupplier myShowMethodNames;

    public MethodOrFunctionalExpressionPresentationProvider(BooleanSupplier showMethodNames) {
        myShowMethodNames = showMethodNames;
    }

    @Override
    @RequiredReadAction
    public TargetPresentation getPresentation(NavigatablePsiElement element) {
        PsiTargetPresentationFactory factory = Application.get().getInstance(PsiTargetPresentationFactory.class);

        if (element instanceof PsiMethod method) {
            boolean showMethodNames = myShowMethodNames.getAsBoolean();

            PsiNamedElement container = fetchContainer(method);
            String text = container instanceof PsiClass psiClass
                ? ClassPresentationUtil.getNameForClass(psiClass, false)
                : String.valueOf(container.getName());
            if (showMethodNames) {
                text += "." + PsiFormatUtil.formatMethod(
                    method,
                    PsiSubstitutor.EMPTY,
                    PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                    PsiFormatUtilBase.SHOW_TYPE
                );
            }

            TargetPresentationBuilder builder = factory.presentationBuilder(method);
            builder = builder.withPresentableText(LocalizeValue.of(text));
            builder = builder.withIcon(IconDescriptorUpdaters.getIcon(showMethodNames ? method : container, 0));
            return withContainer(builder, method).build();
        }

        TargetPresentationBuilder builder = factory.presentationBuilder(element);
        builder = builder.withPresentableText(LocalizeValue.of(PsiExpressionTrimRenderer.render((PsiExpression)element)));
        builder = builder.withIcon(IconDescriptorUpdaters.getIcon(element, 0));
        return withContainer(builder, element).build();
    }

    @RequiredReadAction
    static TargetPresentationBuilder withContainer(TargetPresentationBuilder builder, PsiElement element) {
        @Nullable String containerText = PsiClassListCellRenderer.getContainerTextStatic(element);
        return containerText == null ? builder : builder.withContainerText(LocalizeValue.of(containerText));
    }

    @RequiredReadAction
    private static PsiNamedElement fetchContainer(PsiMethod method) {
        PsiClass psiClass = method.getContainingClass();
        return psiClass == null ? method.getContainingFile() : psiClass;
    }
}
