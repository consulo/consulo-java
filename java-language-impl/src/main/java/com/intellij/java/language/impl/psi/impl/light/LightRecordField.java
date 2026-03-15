// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.light;

import com.intellij.java.language.codeInsight.AnnotationTargetUtil;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.util.CachedValueProvider;
import consulo.content.scope.SearchScope;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.DumbService;

import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

public class LightRecordField extends LightField implements LightRecordMember {
    private final
    PsiRecordComponent myRecordComponent;

    public LightRecordField(
        PsiManager manager,
        PsiField field,
        PsiClass containingClass,
        PsiRecordComponent component
    ) {
        super(manager, field, containingClass);
        myRecordComponent = component;
    }

    @Override
    public PsiRecordComponent getRecordComponent() {
        return myRecordComponent;
    }

    @Override
    public int getTextOffset() {
        return myRecordComponent.getTextOffset();
    }

    @Override
    public PsiElement getNavigationElement() {
        return myRecordComponent.getNavigationElement();
    }

    @Override
    public boolean isWritable() {
        return true;
    }

    @Override
    public PsiFile getContainingFile() {
        PsiClass containingClass = getContainingClass();
        if (containingClass == null) {
            return null;
        }
        return containingClass.getContainingFile();
    }

    @Override
    public
    PsiType getType() {
        if (DumbService.isDumb(myRecordComponent.getProject())) {
            return myRecordComponent.getType();
        }
        return LanguageCachedValueUtil.getCachedValue(this, () -> {
            PsiType type = myRecordComponent.getType()
                .annotate(() -> Arrays.stream(myRecordComponent.getAnnotations())
                    .filter(LightRecordField::hasApplicableAnnotationTarget)
                    .toArray(PsiAnnotation[]::new)
                );
            return CachedValueProvider.Result.create(type, this);
        });
    }

    @Override
    public PsiAnnotation[] getAnnotations() {
        return getType().getAnnotations();
    }

    @Override
    public boolean hasAnnotation(String fqn) {
        PsiType type = getType();
        return type.hasAnnotation(fqn);
    }

    @Override
    @Nullable
    public PsiAnnotation getAnnotation(String fqn) {
        return getType().findAnnotation(fqn);
    }

//	@Override
//	public Icon getElementIcon(final int flags) {
//		final RowIcon baseIcon =
//			IconManager.getInstance().createLayeredIcon(this, PlatformIconGroup.nodesField(), ElementPresentationUtil.getFlags(this, false));
//		if (BitUtil.isSet(flags, ICON_FLAG_VISIBILITY)) {
//			VisibilityIcons.setVisibilityIcon(PsiUtil.ACCESS_LEVEL_PRIVATE, baseIcon);
//		}
//		return baseIcon;
//	}

    @Override
    public PsiElement getContext() {
        return getContainingClass();
    }

    @Override
    public
    SearchScope getUseScope() {
        PsiClass aClass = Objects.requireNonNull(getContainingClass());
        PsiClass containingClass = aClass.getContainingClass();
        while (containingClass != null) {
            aClass = containingClass;
            containingClass = containingClass.getContainingClass();
        }
        return new LocalSearchScope(aClass);
    }

    @RequiredReadAction
    private static boolean hasApplicableAnnotationTarget(PsiAnnotation annotation) {
        return AnnotationTargetUtil.findAnnotationTarget(
            annotation,
            PsiAnnotation.TargetType.TYPE_USE,
            PsiAnnotation.TargetType.FIELD
        ) != null;
    }

    @Override
    public void normalizeDeclaration() throws IncorrectOperationException {
        // no-op
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof LightRecordField &&
            myRecordComponent.equals(((LightRecordField) o).myRecordComponent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(myRecordComponent);
    }

}
