// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.codeInsight.completion;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiMethodReferenceExpression;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementPresentation;
import consulo.language.editor.completion.lookup.LookupItem;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;

import java.util.Objects;

class JavaMethodReferenceElement extends LookupElement {
  private final PsiMethod myMethod;
  private final PsiElement myRefPlace;

  JavaMethodReferenceElement(PsiMethod method, PsiElement refPlace) {
    myMethod = method;
    myRefPlace = refPlace;
  }

  @Nonnull
  @Override
  public Object getObject() {
    return myMethod;
  }

  @Nonnull
  @Override
  public String getLookupString() {
    return myMethod.isConstructor() ? "new" : myMethod.getName();
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    presentation.setIcon(IconDescriptorUpdaters.getIcon(myMethod, 0));
    super.renderElement(presentation);
  }

  @Override
  public void handleInsert(@Nonnull InsertionContext context) {
    if (!(myRefPlace instanceof PsiMethodReferenceExpression)) {
      PsiClass containingClass = Objects.requireNonNull(myMethod.getContainingClass());
      String qualifiedName = Objects.requireNonNull(containingClass.getQualifiedName());

      final Editor editor = context.getEditor();
      final Document document = editor.getDocument();
      final int startOffset = context.getStartOffset();

      document.insertString(startOffset, qualifiedName + "::");
      JavaCompletionUtil.shortenReference(context.getFile(), startOffset + qualifiedName.length() - 1);
    }
    JavaCompletionUtil
        .insertTail(context, this, LookupItem.handleCompletionChar(context.getEditor(), this, context.getCompletionChar()), false);
  }
}
