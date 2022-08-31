/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.jam;

import javax.annotation.Nonnull;

import com.intellij.openapi.util.TextRange;
import com.intellij.pom.PomRenameableTarget;
import com.intellij.pom.PsiDeclaredTarget;
import com.intellij.psi.DelegatePsiTarget;
import com.intellij.psi.ElementManipulators;
import com.intellij.java.language.psi.PsiLiteral;
import com.intellij.util.ObjectUtil;

import javax.annotation.Nullable;

/**
 * @author peter
 */
public class JamPomTarget extends DelegatePsiTarget implements PsiDeclaredTarget, PomRenameableTarget<JamPomTarget>{
  private final JamElement myElement;
  private final JamStringAttributeElement myNameAttr;

  public JamPomTarget(JamElement element, JamStringAttributeElement nameAttr) {
    super(ObjectUtil.assertNotNull(nameAttr.getPsiElement()));
    myElement = element;
    myNameAttr = nameAttr;
  }

  @Nonnull
  public String getName() {
    String value = myNameAttr.getStringValue();
    if (value == null) {
      throw new AssertionError("Null name for " + myElement + "; " + myElement.getClass());
    }
    return value;
  }

  public JamPomTarget setName(@Nonnull String newName) {
    myNameAttr.setStringValue(newName);
    return this;
  }

  public JamElement getJamElement() {
    return myElement;
  }

  @Nullable
  public TextRange getNameIdentifierRange() {
    final PsiLiteral psiLiteral = myNameAttr.getPsiLiteral();
    return psiLiteral == null ? null : ElementManipulators.getValueTextRange(psiLiteral);
  }

  public boolean isWritable() {
    return getNavigationElement().isWritable();
  }

  public JamStringAttributeElement getNameAttr() {
    return myNameAttr;
  }
}
