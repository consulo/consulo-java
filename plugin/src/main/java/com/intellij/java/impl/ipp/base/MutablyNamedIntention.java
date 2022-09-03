/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ipp.base;

import javax.annotation.Nonnull;

import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;

public abstract class MutablyNamedIntention extends Intention {

  private String m_text = null;

  protected abstract String getTextForElement(PsiElement element);

  @Override
  @Nonnull
  public final String getText() {
    return m_text == null ? "" : m_text;
  }

  @Override
  public final boolean isAvailable(@Nonnull Project project, Editor editor,
                                   @Nonnull PsiElement node) {
    final PsiElement element = findMatchingElement(node, editor);
    if (element == null) {
      return false;
    }
    m_text = getTextForElement(element);
    return m_text != null;
  }
}