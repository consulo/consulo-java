// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.codeInsight.daemon.impl;

import com.intellij.java.language.JavaFeature;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiTypeParameter;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.codeEditor.Editor;
import consulo.java.localize.JavaLocalize;
import consulo.language.editor.codeVision.CodeVisionRelativeOrdering;
import consulo.language.editor.impl.codeVision.CodeVisionProviderBase;
import consulo.language.editor.impl.codeVision.InheritorsCodeVisionProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.localize.LocalizeValue;
import org.jspecify.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;

@ExtensionImpl
public class JavaInheritorsCodeVisionProvider extends InheritorsCodeVisionProvider {
    public static final String ID = "java.inheritors";

    @Override
    public boolean acceptsFile(PsiFile file) {
        return file.getLanguage() == JavaLanguage.INSTANCE;
    }

    @Override
    public boolean acceptsElement(PsiElement element) {
        return (element instanceof PsiClass && !(element instanceof PsiTypeParameter))
            || element instanceof PsiMethod;
    }

    @Override
    @RequiredReadAction
    public CodeVisionProviderBase.@Nullable CodeVisionInfo getVisionInfo(PsiElement element, PsiFile file) {
        if (element instanceof PsiClass psiClass && !(element instanceof PsiTypeParameter)) {
            int inheritors = computeClassInheritors(psiClass);
            if (inheritors > 0) {
                boolean isInterface = psiClass.isInterface();
                return new CodeVisionProviderBase.CodeVisionInfo(
                    (isInterface
                        ? JavaLocalize.codeVisionImplementationsHint(inheritors)
                        : JavaLocalize.codeVisionInheritorsHint(inheritors)).get(),
                    inheritors
                );
            }
        }
        else if (element instanceof PsiMethod method) {
            int overrides = computeMethodInheritors(method);
            if (overrides > 0) {
                boolean isAbstract = isAbstractMethod(method);
                return new CodeVisionProviderBase.CodeVisionInfo(
                    (isAbstract
                        ? JavaLocalize.codeVisionImplementationsHint(overrides)
                        : JavaLocalize.codeVisionOverridesHint(overrides)).get(),
                    overrides
                );
            }
        }
        return null;
    }

    @Override
    public @Nullable String getHint(PsiElement element, PsiFile file) {
        CodeVisionProviderBase.CodeVisionInfo info = getVisionInfo(element, file);
        return info != null ? info.text() : null;
    }

    private int computeMethodInheritors(PsiMethod method) {
        return CachedValuesManager.getManager(method.getProject()).getCachedValue(method, () ->
            CachedValueProvider.Result.create(
                JavaTelescope.collectOverridingMethods(method),
                PsiModificationTracker.MODIFICATION_COUNT
            )
        );
    }

    private int computeClassInheritors(PsiClass aClass) {
        return CachedValuesManager.getManager(aClass.getProject()).getCachedValue(aClass, () ->
            CachedValueProvider.Result.create(
                JavaTelescope.collectInheritingClasses(aClass),
                PsiModificationTracker.MODIFICATION_COUNT
            )
        );
    }

    @Override
    public void handleClick(Editor editor, PsiElement element, @Nullable MouseEvent event) {
        MarkerType markerType = element instanceof PsiClass
            ? MarkerType.SUBCLASSED_CLASS
            : MarkerType.OVERRIDDEN_METHOD;
        if (element instanceof PsiNameIdentifierOwner owner) {
            PsiElement nameIdentifier = owner.getNameIdentifier();
            markerType.getNavigationHandler().navigate(event, nameIdentifier != null ? nameIdentifier : element);
        }
    }

    @Override
    public List<CodeVisionRelativeOrdering> getRelativeOrderings() {
        return Collections.emptyList();
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public LocalizeValue getName() {
        return JavaLocalize.settingsInlayJavaInheritors();
    }

    private boolean isAbstractMethod(PsiMethod method) {
        if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return true;
        }
        PsiClass aClass = method.getContainingClass();
        return aClass != null && aClass.isInterface() && !isDefaultMethod(aClass, method);
    }

    private boolean isDefaultMethod(PsiClass aClass, PsiMethod method) {
        return method.hasModifierProperty(PsiModifier.DEFAULT)
            && PsiUtil.isAvailable(JavaFeature.EXTENSION_METHODS, aClass);
    }
}
