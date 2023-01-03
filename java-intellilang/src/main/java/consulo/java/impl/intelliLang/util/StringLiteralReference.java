/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package consulo.java.impl.intelliLang.util;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import consulo.document.util.TextRange;
import consulo.language.psi.ElementManipulators;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiLiteralExpression;
import consulo.language.psi.PsiReference;
import consulo.language.util.IncorrectOperationException;

/**
 * Base class for references in String literals.
 */
public abstract class StringLiteralReference implements PsiReference {
  protected final PsiLiteralExpression myValue;

  public StringLiteralReference(PsiLiteralExpression value) {
    myValue = value;
  }

  public PsiElement getElement() {
    return myValue;
  }

  public TextRange getRangeInElement() {
    return ElementManipulators.getValueTextRange(myValue);
  }

  @Nonnull
  public String getCanonicalText() {
    return myValue.getText();
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return myValue;
  }

  public PsiElement bindToElement(@Nonnull PsiElement element) throws IncorrectOperationException {
    return myValue;
  }

  public boolean isReferenceTo(PsiElement element) {
    return resolve() == element;
  }

  @Nullable
  protected String getValue() {
    return (String)myValue.getValue();
  }

}
