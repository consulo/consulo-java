/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.unwrap;

import com.intellij.java.language.psi.PsiCodeBlock;
import com.intellij.java.language.psi.PsiSynchronizedStatement;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;

public class JavaSynchronizedUnwrapper extends JavaUnwrapper {
  public JavaSynchronizedUnwrapper() {
    super(CodeInsightLocalize.unwrapSynchronized().get());
  }

  @Override
  public boolean isApplicableTo(PsiElement e) {
    return e instanceof PsiSynchronizedStatement;
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
    PsiCodeBlock body = ((PsiSynchronizedStatement)element).getBody();
    context.extractFromCodeBlock(body, element);

    context.delete(element);
  }
}