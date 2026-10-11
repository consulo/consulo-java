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
package com.intellij.java.impl.ide.structureView.impl.java;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiModifierList;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.fileEditor.structureView.tree.Group;
import consulo.fileEditor.structureView.tree.TreeElement;
import consulo.ide.localize.IdeLocalize;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.localize.LocalizeValue;
import consulo.navigation.ItemPresentation;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.image.Image;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

public class SuperTypeGroup implements Group, ItemPresentation, AccessLevelProvider {
    private final SmartPsiElementPointer mySuperClassPointer;
    private final OwnershipType myOverrides;
    private final Collection<TreeElement> myChildren = new ArrayList<>();

    public static enum OwnershipType {
        IMPLEMENTS(PlatformIconGroup.gutterImplementingmethod()),
        OVERRIDES(PlatformIconGroup.gutterOverridingmethod()),
        INHERITS(PlatformIconGroup.hierarchySubtypes());

        private final Image myIcon;

        private OwnershipType(Image icon) {
            myIcon = icon;
        }

        public Image getIcon() {
            return myIcon;
        }
    }

    public SuperTypeGroup(PsiClass superClass, OwnershipType type) {
        myOverrides = type;
        mySuperClassPointer = SmartPointerManager.getInstance(superClass.getProject()).createSmartPsiElementPointer(superClass);
    }

    @Override
    public Collection<TreeElement> getChildren() {
        return myChildren;
    }

    @Nullable
    @RequiredReadAction
    private PsiClass getSuperClass() {
        return (PsiClass) mySuperClassPointer.getElement();
    }

    @Override
    public ItemPresentation getPresentation() {
        return this;
    }

    @Override
    public Image getIcon() {
        return myOverrides.getIcon();
    }

    @Override
    public String getLocationString() {
        return null;
    }

    @Override
    @RequiredReadAction
    public LocalizeValue getPresentableText() {
        PsiClass superClass = getSuperClass();
        return superClass != null ? LocalizeValue.ofNullable(superClass.getName()) : IdeLocalize.nodeStructureviewInvalid();
    }

    @Override
    @RequiredReadAction
    public String toString() {
        return getPresentableText().get();
    }

    @Override
    @RequiredReadAction
    public boolean equals(@Nullable Object o) {
        return this == o
            || o instanceof SuperTypeGroup that && myOverrides == that.myOverrides && Objects.equals(getSuperClass(), that.getSuperClass());
    }

    @Override
    @RequiredReadAction
    public int hashCode() {
        return Objects.hashCode(getSuperClass());
    }

    public Object getValue() {
        return this;
    }

    @Override
    @RequiredReadAction
    public int getAccessLevel() {
        PsiClass superClass = getSuperClass();
        PsiModifierList modifierList = superClass == null ? null : superClass.getModifierList();
        return modifierList == null ? PsiUtil.ACCESS_LEVEL_PUBLIC : PsiUtil.getAccessLevel(modifierList);
    }

    @Override
    public int getSubLevel() {
        return 1;
    }

    public void addMethod(TreeElement superMethod) {
        myChildren.add(superMethod);
    }
}
