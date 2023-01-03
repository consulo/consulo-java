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

import consulo.application.AllIcons;
import consulo.component.util.WeighedItem;
import consulo.fileEditor.structureView.tree.Group;
import consulo.fileEditor.structureView.tree.TreeElement;
import com.intellij.java.language.psi.*;
import consulo.ui.ex.ColoredItemPresentation;
import consulo.navigation.ItemPresentation;
import consulo.codeEditor.CodeInsightColors;
import consulo.colorScheme.TextAttributesKey;
import consulo.project.Project;
import consulo.language.psi.*;
import com.intellij.java.language.psi.util.PropertyUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.ui.image.Image;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;

public class PropertyGroup implements Group, ColoredItemPresentation, AccessLevelProvider, WeighedItem {
  private final String myPropertyName;
  private final PsiType myPropertyType;

  private SmartPsiElementPointer myFieldPointer;
  private SmartPsiElementPointer myGetterPointer;
  private SmartPsiElementPointer mySetterPointer;
  private boolean myIsStatic;
  public static final Image PROPERTY_READ_ICON = AllIcons.Nodes.PropertyRead;
  public static final Image PROPERTY_READ_STATIC_ICON = AllIcons.Nodes.PropertyReadStatic;
  public static final Image PROPERTY_WRITE_ICON = AllIcons.Nodes.PropertyWrite;
  public static final Image PROPERTY_WRITE_STATIC_ICON = AllIcons.Nodes.PropertyWriteStatic;
  public static final Image PROPERTY_READ_WRITE_ICON = AllIcons.Nodes.PropertyReadWrite;
  public static final Image PROPERTY_READ_WRITE_STATIC_ICON = AllIcons.Nodes.PropertyReadWriteStatic;
  private final Project myProject;
  private final Collection<TreeElement> myChildren = new ArrayList<TreeElement>();

  private PropertyGroup(String propertyName, PsiType propertyType, boolean isStatic, @Nonnull Project project) {
    myPropertyName = propertyName;
    myPropertyType = propertyType;
    myIsStatic = isStatic;
    myProject = project;
  }

  public static PropertyGroup createOn(PsiElement object, final TreeElement treeElement) {
    if (object instanceof PsiField) {
      PsiField field = (PsiField)object;
      PropertyGroup group = new PropertyGroup(PropertyUtil.suggestPropertyName(field), field.getType(),
                                              field.hasModifierProperty(PsiModifier.STATIC), object.getProject());
      group.setField(field);
      group.myChildren.add(treeElement);
      return group;
    }
    else if (object instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)object;
      if (PropertyUtil.isSimplePropertyGetter(method)) {
        PropertyGroup group = new PropertyGroup(PropertyUtil.getPropertyNameByGetter(method), method.getReturnType(),
                                                method.hasModifierProperty(PsiModifier.STATIC), object.getProject());
        group.setGetter(method);
        group.myChildren.add(treeElement);
        return group;
      }
      else if (PropertyUtil.isSimplePropertySetter(method)) {
        PropertyGroup group =
          new PropertyGroup(PropertyUtil.getPropertyNameBySetter(method), method.getParameterList().getParameters()[0].getType(),
                            method.hasModifierProperty(PsiModifier.STATIC), object.getProject());
        group.setSetter(method);
        group.myChildren.add(treeElement);
        return group;
      }
    }
    return null;
  }

  public Collection<TreeElement> getChildren() {
    return myChildren;
  }

  public ItemPresentation getPresentation() {
    return this;
  }

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

  public String getLocationString() {
    return null;
  }

  public String getPresentableText() {
    return myPropertyName + ": " + myPropertyType.getPresentableText();
  }

  public String toString() {
    return myPropertyName;
  }


  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PropertyGroup)) return false;

    final PropertyGroup propertyGroup = (PropertyGroup)o;

    if (myPropertyName != null ? !myPropertyName.equals(propertyGroup.myPropertyName) : propertyGroup.myPropertyName != null) return false;

    if (myPropertyType != null && !myPropertyType.isValid()) return false;
    if (propertyGroup.myPropertyType != null && !propertyGroup.myPropertyType.isValid()) return false;

    if (myPropertyType != null && myPropertyType.isValid()
        ? !myPropertyType.equals(propertyGroup.myPropertyType)
        : propertyGroup.myPropertyType != null) {
      return false;
    }
    return true;
  }



  public int hashCode() {
    int result;
    result = myPropertyName != null?myPropertyName.hashCode():0;
    result = 29 * result + (myPropertyType != null ? myPropertyType.hashCode() : 0);
    return result;
  }


  public String getGetterName() {
    return PropertyUtil.suggestGetterName(myPropertyName, myPropertyType);
  }

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

  public int getSubLevel() {
    return 0;
  }

  public void setField(PsiField field) {
    myFieldPointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(field);
    myIsStatic &= field.hasModifierProperty(PsiModifier.STATIC);
  }

  public void setGetter(PsiMethod getter) {
    myGetterPointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(getter);
    myIsStatic &= getter.hasModifierProperty(PsiModifier.STATIC);
  }

  public void setSetter(PsiMethod setter) {
    mySetterPointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(setter);
    myIsStatic &= setter.hasModifierProperty(PsiModifier.STATIC);
  }

  public PsiField getField() {
    return (PsiField)(myFieldPointer == null ? null : myFieldPointer.getElement());
  }

  public PsiMethod getGetter() {
    return (PsiMethod)(myGetterPointer == null ? null : myGetterPointer.getElement());
  }

  public PsiMethod getSetter() {
    return (PsiMethod)(mySetterPointer == null ? null : mySetterPointer.getElement());
  }

  void copyAccessorsFrom(PropertyGroup group) {
    if (group.getGetter() != null) setGetter(group.getGetter());
    if (group.getSetter() != null) setSetter(group.getSetter());
    if (group.getField() != null) setField(group.getField());
    myChildren.addAll(group.myChildren);
  }

  public TextAttributesKey getTextAttributesKey() {
    return isDeprecated() ? CodeInsightColors.DEPRECATED_ATTRIBUTES : null;
  }

  private boolean isDeprecated() {
    return isDeprecated(getField()) && isDeprecated(getGetter()) && isDeprecated(getSetter());
  }

  private static boolean isDeprecated(final PsiElement element) {
    if (element == null) return false;
    if (!element.isValid()) return false;
    if (!(element instanceof PsiDocCommentOwner)) return false;
    return ((PsiDocCommentOwner)element).isDeprecated();
  }

  public boolean isComplete() {
    return getGetter() != null || getSetter() != null;
  }

  public Object getValue() {
    return this;
  }

  public int getWeight() {
    return 60;
  }
}
