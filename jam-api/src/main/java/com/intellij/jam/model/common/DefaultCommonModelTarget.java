/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.intellij.jam.model.common;

import consulo.language.impl.psi.DelegatePsiTarget;
import consulo.language.pom.PomRenameableTarget;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.xml.util.xml.ElementPresentationManager;
import jakarta.annotation.Nonnull;

/**
 * @author Gregory.Shrago
 */
public class DefaultCommonModelTarget extends DelegatePsiTarget implements PomRenameableTarget<Object>, CommonModelTarget {
  private final CommonModelElement.PsiBase myElement;

  public DefaultCommonModelTarget(@Nonnull CommonModelElement.PsiBase element) {
    super(element.getPsiElement());
    myElement = element;
  }

  public CommonModelElement getCommonElement() {
    return myElement;
  }

  public boolean isWritable() {
    return getNavigationElement().isWritable();
  }

  public Object setName(@Nonnull final String newName) {
    final PsiElement element = getNavigationElement();
    if (element instanceof PomRenameableTarget) {
      return ((PomRenameableTarget)element).setName(newName);
    }
    throw new IncorrectOperationException("not implemented");
  }

  public String getName() {
    return ElementPresentationManager.getElementName(myElement);
  }
}
