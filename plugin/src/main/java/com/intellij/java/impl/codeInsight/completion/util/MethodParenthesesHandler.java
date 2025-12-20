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
package com.intellij.java.impl.codeInsight.completion.util;

import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.ParenthesesInsertHandler;
import consulo.language.editor.completion.lookup.LookupElement;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.psi.PsiElement;

/**
 * @author peter
 */
public class MethodParenthesesHandler extends ParenthesesInsertHandler<LookupElement> {
  private final PsiMethod myMethod;
  private final boolean myOverloadsMatter;

  public MethodParenthesesHandler(PsiMethod method, boolean overloadsMatter) {
    myMethod = method;
    myOverloadsMatter = overloadsMatter;
  }

  @Override
  protected boolean placeCaretInsideParentheses(InsertionContext context, LookupElement item) {
    return hasParams(item, context.getElements(), myOverloadsMatter, myMethod);
  }

  public static boolean hasParams(LookupElement item, LookupElement[] allItems, boolean overloadsMatter, PsiMethod method) {
    boolean hasParams = method.getParameterList().getParametersCount() > 0;
    if (overloadsMatter) {
      hasParams |= hasOverloads(allItems, method);
    }
    return hasParams;
  }

  private static boolean hasOverloads(LookupElement[] allItems, PsiMethod method) {
    String name = method.getName();
    for (LookupElement another : allItems) {
      PsiElement element = another.getPsiElement();
      if (method != element && element instanceof PsiMethod && ((PsiMethod) element).getName().equals(name)) {
        return true;
      }
    }
    return false;
  }

}
