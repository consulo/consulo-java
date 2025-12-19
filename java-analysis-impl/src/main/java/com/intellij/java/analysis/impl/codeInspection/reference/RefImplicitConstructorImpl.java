/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.analysis.impl.codeInspection.reference;

import com.intellij.java.analysis.codeInspection.reference.RefClass;
import com.intellij.java.analysis.codeInspection.reference.RefImplicitConstructor;
import com.intellij.java.analysis.codeInspection.reference.RefJavaUtil;
import com.intellij.java.language.psi.PsiModifierListOwner;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.ReadAction;
import consulo.java.analysis.impl.localize.JavaInspectionsLocalize;
import consulo.language.psi.PsiFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author max
 * @since 2001-11-28
 */
public class RefImplicitConstructorImpl extends RefMethodImpl implements RefImplicitConstructor {
    RefImplicitConstructorImpl(RefClass ownerClass) {
        super(JavaInspectionsLocalize.inspectionReferenceImplicitConstructorName(ownerClass.getName()), ownerClass);
    }

    @Override
    @RequiredReadAction
    public void buildReferences() {
        getRefManager().fireBuildReferences(this);
    }

    @Override
    public boolean isSuspicious() {
        return ((RefClassImpl) getOwnerClass()).isSuspicious();
    }

    @Nonnull
    @Override
    public String getName() {
        return JavaInspectionsLocalize.inspectionReferenceImplicitConstructorName(getOwnerClass().getName()).get();
    }

    @Override
    public String getExternalName() {
        return getOwnerClass().getExternalName();
    }

    @Override
    public boolean isValid() {
        return ReadAction.compute(() -> getOwnerClass().isValid());
    }

    @Override
    public String getAccessModifier() {
        return getOwnerClass().getAccessModifier();
    }

    @Override
    public void setAccessModifier(String am) {
        RefJavaUtil.getInstance().setAccessModifier(getOwnerClass(), am);
    }

    @Override
    public PsiModifierListOwner getElement() {
        return getOwnerClass().getElement();
    }

    @Nullable
    @Override
    public PsiFile getContainingFile() {
        return getOwnerClass().getContainingFile();
    }

    @Override
    public RefClass getOwnerClass() {
        return myOwnerClass == null ? super.getOwnerClass() : myOwnerClass;
    }
}
