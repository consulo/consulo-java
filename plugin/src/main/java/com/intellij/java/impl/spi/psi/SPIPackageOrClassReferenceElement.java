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
package com.intellij.java.impl.spi.psi;

import com.intellij.java.language.impl.spi.SPIFileType;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.util.ClassUtil;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.impl.psi.ASTWrapperPsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFileFactory;
import consulo.language.psi.PsiPackage;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.util.collection.ArrayUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * User: anna
 */
public class SPIPackageOrClassReferenceElement extends ASTWrapperPsiElement implements PsiReference {
  public SPIPackageOrClassReferenceElement(@Nonnull ASTNode node) {
    super(node);
  }

  @Override
  public PsiElement getElement() {
    return this;
  }

  @Override
  public TextRange getRangeInElement() {
    PsiElement last = PsiTreeUtil.getDeepestLast(this);
    return new TextRange(last.getStartOffsetInParent(), getTextLength());
  }

  @Nonnull
  @Override
  public String getCanonicalText() {
    return getText();
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    SPIClassProvidersElementList firstChild =
      (SPIClassProvidersElementList)PsiFileFactory.getInstance(getProject())
        .createFileFromText("spi_dummy", SPIFileType.INSTANCE, newElementName).getFirstChild();
    return replace(firstChild.getElements().get(0));
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    PsiPackage aPackage = JavaPsiFacade.getInstance(getProject()).findPackage(getText());
    if (aPackage != null) {
      return aPackage;
    }
    return ClassUtil.findPsiClass(getManager(), getText(), null, true, getResolveScope());
  }

  @Override
  public PsiElement bindToElement(@Nonnull PsiElement element) throws IncorrectOperationException {
    if (element instanceof PsiPackage) {
      return handleElementRename(((PsiPackage)element).getQualifiedName());
    } else if (element instanceof PsiClass) {
      String className = ClassUtil.getJVMClassName((PsiClass)element);
      return className != null ? handleElementRename(className) : null;
    }
    return null;
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    if (element instanceof PsiPackage) {
      return getText().equals(((PsiPackage)element).getQualifiedName());
    } else if (element instanceof PsiClass) {
      return getText().equals(ClassUtil.getJVMClassName((PsiClass)element));
    }
    return false;
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  @Override
  public PsiReference getReference() {
    return this;
  }

  @Nonnull
  @Override
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }
}
