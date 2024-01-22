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
package com.intellij.java.impl.psi.impl.beanProperties;

import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.util.PropertyUtil;
import consulo.application.AllIcons;
import consulo.document.util.TextRange;
import consulo.ide.IdeBundle;
import consulo.language.impl.psi.FakePsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.meta.PsiMetaData;
import consulo.language.psi.meta.PsiMetaOwner;
import consulo.language.psi.meta.PsiPresentableMetaData;
import consulo.ui.image.Image;
import consulo.util.collection.ArrayUtil;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author peter
*/
public class BeanPropertyElement extends FakePsiElement implements PsiMetaOwner, PsiPresentableMetaData {
  private final PsiMethod myMethod;
  private final String myName;

  public BeanPropertyElement(final PsiMethod method, final String name) {
    myMethod = method;
    myName = name;
  }

  @Nullable
  public PsiType getPropertyType() {
    return PropertyUtil.getPropertyType(myMethod);
  }

  @jakarta.annotation.Nonnull
  public PsiMethod getMethod() {
    return myMethod;
  }

  @Override
  public PsiElement getNavigationElement() {
    return myMethod;
  }

  @Override
  public PsiManager getManager() {
    return myMethod.getManager();
  }

  @Override
  public PsiElement getDeclaration() {
    return this;
  }

  @Override
  @NonNls
  public String getName(PsiElement context) {
    return getName();
  }

  @Override
  @Nonnull
  public String getName() {
    return myName;
  }

  @Override
  public void init(PsiElement element) {

  }

  @Override
  public Object[] getDependences() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  @Nullable
  public Image getIcon() {
    return AllIcons.Nodes.Property;
  }

  @Override
  public PsiElement getParent() {
    return myMethod;
  }

  @Override
  @jakarta.annotation.Nullable
  public PsiMetaData getMetaData() {
    return this;
  }

  @Override
  public String getTypeName() {
    return IdeBundle.message("bean.property");
  }

  @Override
  public TextRange getTextRange() {
    return TextRange.EMPTY_RANGE;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    BeanPropertyElement element = (BeanPropertyElement)o;

    if (myMethod != null ? !myMethod.equals(element.myMethod) : element.myMethod != null) return false;
    if (myName != null ? !myName.equals(element.myName) : element.myName != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myMethod != null ? myMethod.hashCode() : 0;
    result = 31 * result + (myName != null ? myName.hashCode() : 0);
    return result;
  }
}
