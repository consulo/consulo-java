/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.language.impl.psi.impl.compiled;

import jakarta.annotation.Nonnull;

import com.intellij.java.language.psi.JavaElementVisitor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaDocElementType;
import consulo.language.impl.ast.TreeElement;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.javadoc.PsiDocTag;
import com.intellij.java.language.psi.javadoc.PsiDocTagValue;
import consulo.language.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

class ClsDocTagImpl extends ClsElementImpl implements PsiDocTag {
  private final ClsDocCommentImpl myDocComment;
  private final PsiElement myNameElement;

  public ClsDocTagImpl(ClsDocCommentImpl docComment, @NonNls String name) {
    myDocComment = docComment;
    myNameElement = new NameElement(this, name);
  }

  @Override
  public void appendMirrorText(int indentLevel, @Nonnull StringBuilder buffer) {
    buffer.append(myNameElement.getText());
  }

  @Override
  public void setMirror(@Nonnull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, JavaDocElementType.DOC_TAG);
  }

  @Override
  public String getText() {
    return myNameElement.getText();
  }

  @Override
  @Nonnull
  public char[] textToCharArray() {
    return myNameElement.textToCharArray();
  }

  @Override
  @Nonnull
  public String getName() {
    return getNameElement().getText().substring(1);
  }

  @Override
  public boolean textMatches(@Nonnull CharSequence text) {
    return myNameElement.textMatches(text);
  }

  @Override
  public boolean textMatches(@Nonnull PsiElement element) {
    return myNameElement.textMatches(element);
  }

  @Override
  public int getTextLength() {
    return myNameElement.getTextLength();
  }

  @Override
  @Nonnull
  public PsiElement[] getChildren() {
    return new PsiElement[]{myNameElement};
  }

  @Override
  public PsiElement getParent() {
    return getContainingComment();
  }

  @Override
  public PsiDocComment getContainingComment() {
    return myDocComment;
  }

  @Override
  public PsiElement getNameElement() {
    return myNameElement;
  }

  @Override
  public PsiElement[] getDataElements() {
    return PsiElement.EMPTY_ARRAY;
  }

  @Override
  public PsiDocTagValue getValueElement() {
    return null;
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitDocTag(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiElement setName(@Nonnull String name) throws IncorrectOperationException {
    PsiImplUtil.setName(getNameElement(), name);
    return this;
  }

  private static class NameElement extends ClsElementImpl {
    private final ClsDocTagImpl myParent;
    private final String myText;

    public NameElement(ClsDocTagImpl parent, String text) {
      myParent = parent;
      myText = text;
    }

    @Override
    public String getText() {
      return myText;
    }

    @Override
    @Nonnull
    public char[] textToCharArray() {
      return myText.toCharArray();
    }

    @Override
    @Nonnull
    public PsiElement[] getChildren() {
      return PsiElement.EMPTY_ARRAY;
    }

    @Override
    public void appendMirrorText(int indentLevel, @Nonnull StringBuilder buffer) {
    }

    @Override
    public void setMirror(@Nonnull TreeElement element) throws InvalidMirrorException {
      setMirrorCheckingType(element, null);
    }

    @Override
    public PsiElement getParent() {
      return myParent;
    }

    @Override
    public void accept(@Nonnull PsiElementVisitor visitor) {
      visitor.visitElement(this);
    }
  }
}
