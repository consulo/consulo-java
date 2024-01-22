/*
 * Copyright 2011 Bas Leijdekkers
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
package com.intellij.java.impl.ipp.types;

import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.impl.psi.impl.PsiDiamondTypeUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;

import jakarta.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ReplaceDiamondWithExplicitTypeArgumentsIntention", fileExtensions = "java", categories = {"Java", "Declaration"})
public class ReplaceDiamondWithExplicitTypeArgumentsIntention extends Intention {

  @Nonnull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new DiamondTypePredicate();
  }

  @Override
  protected void processIntention(@jakarta.annotation.Nonnull PsiElement element)
    throws IncorrectOperationException {
    PsiDiamondTypeUtil.replaceDiamondWithExplicitTypes(element);
  }
}
