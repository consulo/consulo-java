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
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementPresentation;
import consulo.component.util.Iconable;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import consulo.language.icon.IconDescriptorUpdaters;

import javax.annotation.Nonnull;

/**
 * @author peter
 */
public class SimpleMethodCallLookupElement extends LookupElement {
  private final PsiMethod myMethod;

  public SimpleMethodCallLookupElement(final PsiMethod method) {
    myMethod = method;
  }

  @Override
  @Nonnull
  public String getLookupString() {
    return myMethod.getName();
  }

  @Override
  public void handleInsert(InsertionContext context) {
    new MethodParenthesesHandler(myMethod, true).handleInsert(context, this);
  }

  public PsiMethod getMethod() {
    return myMethod;
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    presentation.setIcon(IconDescriptorUpdaters.getIcon(myMethod, Iconable.ICON_FLAG_VISIBILITY));
    presentation.setItemText(myMethod.getName());
    presentation.setTailText(PsiFormatUtil.formatMethod(myMethod,
                                                        PsiSubstitutor.EMPTY,
                                                        PsiFormatUtil.SHOW_PARAMETERS,
                                                        PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE));
    final PsiType returnType = myMethod.getReturnType();
    if (returnType != null) {
      presentation.setTypeText(returnType.getCanonicalText());
    }
  }

}
