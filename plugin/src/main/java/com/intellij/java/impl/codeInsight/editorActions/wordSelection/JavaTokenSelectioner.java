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
package com.intellij.java.impl.codeInsight.editorActions.wordSelection;

import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiCodeBlock;
import com.intellij.java.language.psi.PsiJavaToken;
import com.intellij.java.language.psi.PsiKeyword;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.*;
import consulo.document.util.TextRange;
import consulo.codeEditor.Editor;

import java.util.List;

@ExtensionImpl
public class JavaTokenSelectioner extends BasicSelectioner {
  @Override
  public boolean canSelect(PsiElement e) {
    return e instanceof PsiJavaToken && !(e instanceof PsiKeyword) && !(e.getParent()instanceof PsiCodeBlock);
  }

  @Override
  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    PsiJavaToken token = (PsiJavaToken)e;

    if (token.getTokenType() != JavaTokenType.SEMICOLON && token.getTokenType() != JavaTokenType.LPARENTH) {
      return super.select(e, editorText, cursorOffset, editor);
    }
    else {
      return null;
    }
  }
}
