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

import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiLiteral;
import consulo.language.psi.PsiReferenceBase;
import consulo.language.util.IncorrectOperationException;
import consulo.util.lang.ObjectUtil;
import jakarta.annotation.Nonnull;

/**
 * @author peter
*/
public class JamSimpleReference<T> extends PsiReferenceBase<PsiLiteral> {
  private final JamStringAttributeElement<T> myContext;
  private final JamSimpleReferenceConverter<T> myConverter;

  public JamSimpleReference(JamStringAttributeElement<T> context) {
    super(ObjectUtil.assertNotNull(context.getPsiLiteral()));
    myContext = context;
    myConverter = (JamSimpleReferenceConverter<T>)context.getConverter();
  }

  public PsiElement resolve() {
    final T result = myConverter.fromString(getValue(), myContext);
    if (result == null) {
      return null;
    }

    final PsiElement element = myConverter.getPsiElementFor(result);
    return element == null? myContext.getPsiLiteral() : element;
  }

  @Nonnull
  public Object[] getVariants() {
    return myConverter.getLookupVariants(myContext);
  }

  @Override
  public PsiElement bindToElement(@Nonnull PsiElement element) throws IncorrectOperationException {
    return myConverter.bindReference(myContext, element);
  }
}
