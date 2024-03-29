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

import consulo.find.FindUsagesHandler;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiMethod;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;

/**
 * @author Dmitry Avdeev
 */
public class BeanPropertyFindUsagesHandler extends FindUsagesHandler {

  private final BeanProperty myProperty;

  public BeanPropertyFindUsagesHandler(final BeanProperty property) {
    super(property.getPsiElement());
    myProperty = property;
  }


  @Override
  @Nonnull
  public PsiElement[] getPrimaryElements() {
    final ArrayList<PsiElement> elements = new ArrayList<PsiElement>(3);
    elements.add(myProperty.getPsiElement());
    final PsiMethod getter = myProperty.getGetter();
    if (getter != null) {
      elements.add(getter);
    }
    final PsiMethod setter = myProperty.getSetter();
    if (setter != null) {
      elements.add(setter);
    }
    return elements.toArray(PsiElement.EMPTY_ARRAY);
  }
}
