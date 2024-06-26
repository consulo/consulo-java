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

import com.intellij.java.language.psi.PsiForStatement;
import com.intellij.java.language.psi.PsiForeachStatement;
import com.intellij.java.language.psi.PsiLoopStatement;
import com.intellij.java.language.psi.PsiStatement;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;

public class JavaForUnwrapper extends JavaUnwrapper {
  public JavaForUnwrapper() {
    super(CodeInsightLocalize.unwrapFor().get());
  }

  @Override
  public boolean isApplicableTo(PsiElement e) {
    return e instanceof PsiForStatement || e instanceof PsiForeachStatement;
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
    if (element instanceof PsiForStatement) {
      unwrapInitializer(element, context);
    }
    unwrapBody(element, context);

    context.delete(element);
  }

  private void unwrapInitializer(PsiElement element, Context context) throws IncorrectOperationException {
    PsiStatement init = ((PsiForStatement)element).getInitialization();
    context.extractFromBlockOrSingleStatement(init, element);
  }

  private void unwrapBody(PsiElement element, Context context) throws IncorrectOperationException {
    PsiStatement body = ((PsiLoopStatement)element).getBody();
    context.extractFromBlockOrSingleStatement(body, element);
  }
}