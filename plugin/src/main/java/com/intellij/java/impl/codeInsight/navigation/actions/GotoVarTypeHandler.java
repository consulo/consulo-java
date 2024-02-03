// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.codeInsight.navigation.actions;

import com.intellij.java.language.psi.PsiKeyword;
import com.intellij.java.language.psi.PsiTypeElement;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.navigation.GotoDeclarationHandlerBase;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nullable;

@ExtensionImpl
public final class GotoVarTypeHandler extends GotoDeclarationHandlerBase {
  @Override
  @Nullable
  @RequiredReadAction
  public PsiElement getGotoDeclarationTarget(@Nullable PsiElement elementAt, Editor editor) {
    if (elementAt instanceof PsiKeyword && PsiKeyword.VAR.equals(elementAt.getText())) {
      PsiElement parent = elementAt.getParent();
      if (parent instanceof PsiTypeElement && ((PsiTypeElement)parent).isInferredType()) {
        return PsiUtil.resolveClassInClassTypeOnly(((PsiTypeElement)parent).getType());
      }
    }
    return null;
  }
}
