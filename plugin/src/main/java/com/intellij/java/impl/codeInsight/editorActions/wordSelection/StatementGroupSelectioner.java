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

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.LineTokenizer;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;

import java.util.ArrayList;
import java.util.List;

@ExtensionImpl
public class StatementGroupSelectioner extends BasicSelectioner {
  @Override
  public boolean canSelect(PsiElement e) {
    return e instanceof PsiStatement || e instanceof PsiComment && !(e instanceof PsiDocComment);
  }

  @Override
  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    List<TextRange> result = new ArrayList<TextRange>();

    PsiElement parent = e.getParent();

    if (!(parent instanceof PsiCodeBlock) && !(parent instanceof PsiBlockStatement)/* || parent instanceof JspCodeBlock*/) {
      return result;
    }


    PsiElement startElement = e;
    PsiElement endElement = e;


    while (startElement.getPrevSibling() != null) {
      PsiElement sibling = startElement.getPrevSibling();

      if (sibling instanceof PsiJavaToken) {
        PsiJavaToken token = (PsiJavaToken)sibling;
        if (token.getTokenType() == JavaTokenType.LBRACE) {
          break;
        }
      }

      if (sibling instanceof PsiWhiteSpace) {
        PsiWhiteSpace whiteSpace = (PsiWhiteSpace)sibling;

        String[] strings = LineTokenizer.tokenize(whiteSpace.getText().toCharArray(), false);
        if (strings.length > 2) {
          break;
        }
      }

      startElement = sibling;
    }

    while (startElement instanceof PsiWhiteSpace) {
      startElement = startElement.getNextSibling();
    }

    while (endElement.getNextSibling() != null) {
      PsiElement sibling = endElement.getNextSibling();

      if (sibling instanceof PsiJavaToken) {
        PsiJavaToken token = (PsiJavaToken)sibling;
        if (token.getTokenType() == JavaTokenType.RBRACE) {
          break;
        }
      }

      if (sibling instanceof PsiWhiteSpace) {
        PsiWhiteSpace whiteSpace = (PsiWhiteSpace)sibling;

        String[] strings = LineTokenizer.tokenize(whiteSpace.getText().toCharArray(), false);
        if (strings.length > 2) {
          break;
        }
      }

      endElement = sibling;
    }

    while (endElement instanceof PsiWhiteSpace) {
      endElement = endElement.getPrevSibling();
    }

    result.addAll(expandToWholeLine(editorText, new TextRange(startElement.getTextRange().getStartOffset(),
                                                              endElement.getTextRange().getEndOffset())));

    return result;
  }
}
