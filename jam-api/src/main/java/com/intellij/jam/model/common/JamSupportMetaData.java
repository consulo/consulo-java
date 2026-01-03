/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.jam.model.common;

import consulo.application.presentation.TypePresentationService;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.meta.PsiPresentableMetaData;
import consulo.language.psi.meta.PsiWritableMetaData;
import consulo.logging.Logger;
import consulo.ui.image.Image;
import consulo.util.collection.SmartList;
import consulo.util.lang.StringUtil;
import consulo.xml.util.xml.DomElement;
import consulo.xml.util.xml.DomUtil;
import consulo.xml.util.xml.ElementPresentationManager;
import consulo.xml.util.xml.ModelMergerUtil;
import org.jetbrains.annotations.NonNls;

import java.util.List;

/**
 * @author peter
 */
public abstract class JamSupportMetaData<T extends CommonModelElement> implements PsiWritableMetaData, PsiPresentableMetaData {
  private static final Logger LOG = Logger.getInstance(JamSupportMetaData.class);
  private T myElement;

  protected final void setElement(final T element) {
    myElement = element;
  }

  protected final T getElement() {
    return myElement;
  }

  public final PsiElement getDeclaration() {
    final T element = myElement;
    if (element == null) {
      LOG.error(getClass());
    }
    return element.getIdentifyingPsiElement();
  }

  public Object[] getDependences() {
    if (myElement != null) {
      final List<Object> deps = new SmartList<Object>();
      deps.add(getDeclaration());
      deps.add(PsiModificationTracker.MODIFICATION_COUNT);
      for (final DomElement domElement : ModelMergerUtil.getImplementations(myElement, DomElement.class)) {
        if (domElement.isValid()) {
          deps.add(DomUtil.getFileElement(domElement));
        }
      }
      return deps.toArray();
    }

    return new Object[]{getDeclaration(), PsiModificationTracker.MODIFICATION_COUNT};
  }

  @NonNls
  public final String getName() {
    return StringUtil.notNullize(ElementPresentationManager.getElementName(myElement));
  }

  @NonNls
  public String getName(PsiElement context) {
    return getName();
  }

  public String getTypeName() {
    return TypePresentationService.getInstance().getTypeNameOrStub(myElement);
  }

  public Image getIcon() {
    return ElementPresentationManager.getIcon(myElement);
  }

}
