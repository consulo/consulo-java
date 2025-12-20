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
package com.intellij.java.impl.refactoring.rename.naming;

import consulo.language.psi.PsiNamedElement;
import consulo.language.editor.refactoring.rename.NameSuggester;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;

import java.util.List;

/**
 * @author peter
 */
public abstract class PsiNamedElementAutomaticRenamer<T extends PsiNamedElement> extends AutomaticUsageRenamer<T> {
  private static final Logger LOG = Logger.getInstance(PsiNamedElementAutomaticRenamer.class);

  protected PsiNamedElementAutomaticRenamer(List<? extends T> elements, String oldName, String newName) {
    super(elements, oldName, newName);
  }

  protected String getName(T element) {
    return element.getName();
  }

  protected void doRenameElement(T t) throws IncorrectOperationException {
    t.setName(getNewElementName(t));
  }

  protected String suggestName(T element) {
    String elementName = getName(element);
    NameSuggester suggester = new NameSuggester(getOldName(), getNewName());
    String canonicalName = nameToCanonicalName(elementName, element);
    String newCanonicalName = suggester.suggestName(canonicalName);
    if (newCanonicalName.length() == 0) {
      LOG.error("oldName = " + getOldName() + ", newName = " + getNewName() + ", name = " + elementName + ", canonicalName = " +
          canonicalName + ", newCanonicalName = " + newCanonicalName);
    }
    return canonicalNameToName(newCanonicalName, element);
  }

  protected String canonicalNameToName(String canonicalName, T element) {
    return canonicalName;
  }

  protected String nameToCanonicalName(String name, T element) {
    return name;
  }

}
