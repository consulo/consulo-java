// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.light;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.SyntheticElement;
import jakarta.annotation.Nonnull;

public class LightRecordCanonicalConstructor extends LightMethod implements SyntheticElement {
    public LightRecordCanonicalConstructor(@Nonnull PsiMethod method, @Nonnull PsiClass containingClass) {
        super(method.getManager(), method, containingClass);
    }

    @Override
    public int getTextOffset() {
        return getNavigationElement().getTextOffset();
    }

    @Nonnull
    @Override
    public PsiElement getNavigationElement() {
        return getContainingClass();
    }

    @Override
    public PsiFile getContainingFile() {
        PsiClass containingClass = getContainingClass();
        return containingClass.getContainingFile();
    }

//	@Override
//	public Icon getElementIcon(final int flags) {
//		final RowIcon baseIcon =
//	        IconManager.getInstance().createLayeredIcon(this, PlatformIconGroup.nodesMethod(), ElementPresentationUtil.getFlags(this, false));
//		if(BitUtil.isSet(flags, ICON_FLAG_VISIBILITY)) {
//			VisibilityIcons.setVisibilityIcon(getContainingClass().getModifierList(), baseIcon);
//		}
//		return baseIcon;
//	}

    @Override
    public PsiElement getContext() {
        return getContainingClass();
    }
}
