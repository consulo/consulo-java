/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.java.language.psi.PsiClassInitializer;
import com.intellij.java.language.psi.PsiCodeBlock;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.fileEditor.structureView.StructureViewTreeElement;
import consulo.java.analysis.codeInsight.JavaCodeInsightBundle;
import consulo.language.editor.structureView.PsiTreeElementBase;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;

import java.util.Collection;
import java.util.Collections;

public class ClassInitializerTreeElement extends PsiTreeElementBase<PsiClassInitializer> implements AccessLevelProvider {
  public ClassInitializerTreeElement(PsiClassInitializer initializer) {
    super(initializer);
  }

  @Override
  public String getPresentableText() {
    PsiClassInitializer initializer = getElement();
    assert initializer != null;
    String isStatic = initializer.hasModifierProperty(PsiModifier.STATIC) ? PsiModifier.STATIC + " " : "";
    return JavaCodeInsightBundle.message("static.class.initializer", isStatic);
  }

  @Override
  public String getLocationString() {
    PsiClassInitializer initializer = getElement();
    assert initializer != null;
    PsiCodeBlock body = initializer.getBody();
    PsiElement first = body.getFirstBodyElement();
    if (first instanceof PsiWhiteSpace) {
      first = first.getNextSibling();
    }
    if (first == body.getRBrace()) {
      first = null;
    }
    if (first != null) {
      return StringUtil.first(first.getText(), 20, true);
    }
    return null;
  }

  @Override
  @Nonnull
  public Collection<StructureViewTreeElement> getChildrenBase() {
    return Collections.emptyList();
  }

  @Override
  public int getAccessLevel() {
    return PsiUtil.ACCESS_LEVEL_PRIVATE;
  }

  @Override
  public int getSubLevel() {
    return 0;
  }
}
