// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.codeInsight.lookup;

import com.intellij.java.language.psi.PsiJavaCodeReferenceCodeFragment;
import consulo.application.AllIcons;
import consulo.language.editor.AutoPopupController;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementPresentation;
import consulo.language.editor.completion.lookup.TailType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiPackage;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author peter
 */
public class PackageLookupItem extends LookupElement {
  private final PsiPackage myPackage;
  private final String myString;
  private final boolean myAddDot;

  public PackageLookupItem(@jakarta.annotation.Nonnull PsiPackage aPackage) {
    this(aPackage, null);
  }

  public PackageLookupItem(@jakarta.annotation.Nonnull PsiPackage pkg, @Nullable PsiElement context) {
    myPackage = pkg;
    myString = StringUtil.notNullize(myPackage.getName());

    PsiFile file = context == null ? null : context.getContainingFile();
    myAddDot = !(file instanceof PsiJavaCodeReferenceCodeFragment) || ((PsiJavaCodeReferenceCodeFragment) file).isClassesAccepted();
  }

  @Nonnull
  @Override
  public Object getObject() {
    return myPackage;
  }

  @Nonnull
  @Override
  public String getLookupString() {
    return myString;
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    super.renderElement(presentation);
    if (myAddDot) {
      presentation.setItemText(myString + ".");
    }
    presentation.setIcon(AllIcons.Nodes.Package);
  }

  @Override
  public void handleInsert(@Nonnull InsertionContext context) {
    if (myAddDot) {
      context.setAddCompletionChar(false);
      TailType.DOT.processTail(context.getEditor(), context.getTailOffset());
    }
    if (myAddDot || context.getCompletionChar() == '.') {
      AutoPopupController.getInstance(context.getProject()).scheduleAutoPopup(context.getEditor());
    }
  }
}
