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

import consulo.annotation.access.RequiredReadAction;
import consulo.component.util.WeighedItem;
import consulo.fileEditor.structureView.tree.Group;
import consulo.fileEditor.structureView.tree.TreeElement;
import com.intellij.java.language.psi.*;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.ex.ColoredItemPresentation;
import consulo.navigation.ItemPresentation;
import consulo.codeEditor.CodeInsightColors;
import consulo.colorScheme.TextAttributesKey;
import consulo.project.Project;
import consulo.language.psi.*;
import com.intellij.java.language.psi.util.PropertyUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.ui.image.Image;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

public class PropertyGroup implements Group, ColoredItemPresentation, AccessLevelProvider, WeighedItem {
    private final String myPropertyName;
    private final PsiType myPropertyType;

    private SmartPsiElementPointer myFieldPointer;
    private SmartPsiElementPointer myGetterPointer;
    private SmartPsiElementPointer mySetterPointer;
    private boolean myIsStatic;
    public static final Image PROPERTY_READ_ICON = PlatformIconGroup.nodesPropertyread();
    public static final Image PROPERTY_READ_STATIC_ICON = PlatformIconGroup.nodesPropertyreadstatic();
    public static final Image PROPERTY_WRITE_ICON = PlatformIconGroup.nodesPropertywrite();
    public static final Image PROPERTY_WRITE_STATIC_ICON = PlatformIconGroup.nodesPropertywritestatic();
    public static final Image PROPERTY_READ_WRITE_ICON = PlatformIconGroup.nodesPropertyreadwrite();
    public static final Image PROPERTY_READ_WRITE_STATIC_ICON = PlatformIconGroup.nodesPropertyreadwritestatic();
    private final Project myProject;
    private final Collection<TreeElement> myChildren = new ArrayList<>();

    private PropertyGroup(String propertyName, PsiType propertyType, boolean isStatic, Project project) {
        myPropertyName = propertyName;
        myPropertyType = propertyType;
        myIsStatic = isStatic;
        myProject = project;
    }

    public static PropertyGroup createOn(PsiElement object, TreeElement treeElement) {
        if (object instanceof PsiField field) {
            PropertyGroup group = new PropertyGroup(
                PropertyUtil.suggestPropertyName(field),
                field.getType(),
                field.isStatic(),
                object.getProject()
            );
            group.setField(field);
            group.myChildren.add(treeElement);
            return group;
        }
        else if (object instanceof PsiMethod method) {
            if (PropertyUtil.isSimplePropertyGetter(method)) {
                PropertyGroup group = new PropertyGroup(
                    PropertyUtil.getPropertyNameByGetter(method),
                    method.getReturnType(),
                    method.isStatic(),
                    object.getProject()
                );
                group.setGetter(method);
                group.myChildren.add(treeElement);
                return group;
            }
            else if (PropertyUtil.isSimplePropertySetter(method)) {
                PropertyGroup group =
                    new PropertyGroup(
                        PropertyUtil.getPropertyNameBySetter(method),
                        method.getParameterList().getParameters()[0].getType(),
                        method.isStatic(),
                        object.getProject()
                    );
                group.setSetter(method);
                group.myChildren.add(treeElement);
                return group;
            }
        }
        return null;
    }

    @Override
    public Collection<TreeElement> getChildren() {
        return myChildren;
    }

    @Override
    public ItemPresentation getPresentation() {
        return this;
    }

    @Override
    @RequiredReadAction
    public Image getIcon() {
        if (isStatic()) {
            if (getGetter() != null && getSetter() != null) {
                return PROPERTY_READ_WRITE_STATIC_ICON;
            }
            else if (getGetter() != null) {
                return PROPERTY_READ_STATIC_ICON;
            }
            else {
                return PROPERTY_WRITE_STATIC_ICON;
            }
        }
        else {
            if (getGetter() != null && getSetter() != null) {
                return PROPERTY_READ_WRITE_ICON;
            }
            else if (getGetter() != null) {
                return PROPERTY_READ_ICON;
            }
            else {
                return PROPERTY_WRITE_ICON;
            }
        }
    }

    private boolean isStatic() {
        return myIsStatic;
    }

    @Override
    public String getLocationString() {
        return null;
    }

    @Override
    public LocalizeValue getPresentableText() {
        return LocalizeValue.of(myPropertyName + ": " + myPropertyType.getPresentableText());
    }

    @Override
    public String toString() {
        return myPropertyName;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PropertyGroup that)) {
            return false;
        }

        if (!Objects.equals(myPropertyName, that.myPropertyName)) {
            return false;
        }

        if (myPropertyType != null && !myPropertyType.isValid()) {
            return false;
        }
        if (that.myPropertyType != null && !that.myPropertyType.isValid()) {
            return false;
        }

        return myPropertyType != null && myPropertyType.isValid()
            ? myPropertyType.equals(that.myPropertyType)
            : that.myPropertyType == null;
    }


    @Override
    public int hashCode() {
        return 29 * Objects.hashCode(myPropertyName) + Objects.hashCode(myPropertyType);
    }

    public String getGetterName() {
        return PropertyUtil.suggestGetterName(myPropertyName, myPropertyType);
    }

    @Override
    public int getAccessLevel() {
        int result = PsiUtil.ACCESS_LEVEL_PRIVATE;
        if (getGetter() != null) {
            result = Math.max(result, PsiUtil.getAccessLevel(getGetter().getModifierList()));
        }
        if (getSetter() != null) {
            result = Math.max(result, PsiUtil.getAccessLevel(getSetter().getModifierList()));
        }
        if (getField() != null) {
            result = Math.max(result, PsiUtil.getAccessLevel(getField().getModifierList()));
        }
        return result;
    }

    @Override
    public int getSubLevel() {
        return 0;
    }

    public void setField(PsiField field) {
        myFieldPointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(field);
        myIsStatic &= field.isStatic();
    }

    public void setGetter(PsiMethod getter) {
        myGetterPointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(getter);
        myIsStatic &= getter.isStatic();
    }

    public void setSetter(PsiMethod setter) {
        mySetterPointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(setter);
        myIsStatic &= setter.isStatic();
    }

    @RequiredReadAction
    public PsiField getField() {
        return (PsiField) (myFieldPointer == null ? null : myFieldPointer.getElement());
    }

    @RequiredReadAction
    public PsiMethod getGetter() {
        return (PsiMethod) (myGetterPointer == null ? null : myGetterPointer.getElement());
    }

    @RequiredReadAction
    public PsiMethod getSetter() {
        return (PsiMethod) (mySetterPointer == null ? null : mySetterPointer.getElement());
    }

    @RequiredReadAction
    void copyAccessorsFrom(PropertyGroup group) {
        if (group.getGetter() != null) {
            setGetter(group.getGetter());
        }
        if (group.getSetter() != null) {
            setSetter(group.getSetter());
        }
        if (group.getField() != null) {
            setField(group.getField());
        }
        myChildren.addAll(group.myChildren);
    }

    @Override
    @RequiredReadAction
    public TextAttributesKey getTextAttributesKey() {
        return isDeprecated() ? CodeInsightColors.DEPRECATED_ATTRIBUTES : null;
    }

    @RequiredReadAction
    private boolean isDeprecated() {
        return isDeprecated(getField()) && isDeprecated(getGetter()) && isDeprecated(getSetter());
    }

    @RequiredReadAction
    private static boolean isDeprecated(PsiElement element) {
        return element != null
            && element.isValid()
            && element instanceof PsiDocCommentOwner docCommentOwner
            && docCommentOwner.isDeprecated();
    }

    @RequiredReadAction
    public boolean isComplete() {
        return getGetter() != null || getSetter() != null;
    }

    public Object getValue() {
        return this;
    }

    @Override
    public int getWeight() {
        return 60;
    }
}
