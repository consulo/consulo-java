// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.ide.structureView.impl.java;

import com.intellij.java.language.psi.PsiRecordComponent;
import consulo.fileEditor.structureView.StructureViewTreeElement;
import jakarta.annotation.Nonnull;

import java.util.Collection;
import java.util.Collections;

public class JavaRecordComponentTreeElement extends JavaVariableBaseTreeElement<PsiRecordComponent> {
  public JavaRecordComponentTreeElement(PsiRecordComponent field, boolean isInherited) {
    super(isInherited, field);
  }

  @Override
  public @Nonnull Collection<StructureViewTreeElement> getChildrenBase() {
    return Collections.emptyList();
  }
}
