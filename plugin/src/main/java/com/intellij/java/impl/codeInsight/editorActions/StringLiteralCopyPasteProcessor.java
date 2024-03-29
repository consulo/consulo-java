/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.editorActions;

import com.intellij.java.language.psi.JavaTokenType;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.LineTokenizer;
import consulo.codeEditor.Editor;
import consulo.codeEditor.RawText;
import consulo.codeEditor.SelectionModel;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.editor.action.CopyPastePreProcessor;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import static consulo.util.lang.StringUtil.unescapeStringCharacters;

@ExtensionImpl
public class StringLiteralCopyPasteProcessor implements CopyPastePreProcessor {

  @SuppressWarnings("ForLoopThatDoesntUseLoopVariable")
  @Override
  public String preprocessOnCopy(final PsiFile file, final int[] startOffsets, final int[] endOffsets, final String text) {
    // The main idea is to un-escape string/char literals content if necessary.
    // Example:
    //    Suppose we have a following text at the editor: String s = "first <selection>line \n second</selection> line"
    //    When user copies the selection we want to transform text \n to the real line feed, i.e. clipboard should contains the following:
    //        line 
    //        second
    //
    // However, we don't want to un-escape literal content if it's copied completely.
    // Example:
    //     String s = <selection>"my string"</selection>;

    StringBuilder buffer = new StringBuilder();
    int givenTextOffset = 0;
    boolean textWasChanged = false;
    for (int i = 0; i < startOffsets.length && givenTextOffset < text.length(); i++, givenTextOffset++) {
      if (i > 0) {
        buffer.append('\n'); // LF is added for block selection
      }
      // Calculate offsets offsets of the selection interval being processed now.
      final int fileStartOffset = startOffsets[i];
      final int fileEndOffset = endOffsets[i];
      int givenTextStartOffset = Math.min(givenTextOffset, text.length());
      final int givenTextEndOffset = Math.min(givenTextOffset + (fileEndOffset - fileStartOffset), text.length());
      givenTextOffset = givenTextEndOffset;
      for (
          PsiElement element = file.findElementAt(fileStartOffset);
          givenTextStartOffset < givenTextEndOffset;
          element = PsiTreeUtil.nextLeaf(element)) {
        if (element == null) {
          buffer.append(text.substring(givenTextStartOffset, givenTextEndOffset));
          break;
        }
        TextRange elementRange = element.getTextRange();
        int escapedStartOffset;
        int escapedEndOffset;
        if ((isStringLiteral(element) || isCharLiteral(element))
            // We don't want to un-escape if complete literal is copied.
            && (elementRange.getStartOffset() < fileStartOffset || elementRange.getEndOffset() > fileEndOffset)) {
          escapedStartOffset = elementRange.getStartOffset() + 1 /* String/char literal quote */;
          escapedEndOffset = elementRange.getEndOffset() - 1 /* String/char literal quote */;
        } else {
          escapedStartOffset = escapedEndOffset = elementRange.getStartOffset();
        }

        // Process text to the left of the escaped fragment (if any).
        int numberOfSymbolsToCopy = escapedStartOffset - Math.max(fileStartOffset, elementRange.getStartOffset());
        if (numberOfSymbolsToCopy > 0) {
          buffer.append(text.substring(givenTextStartOffset, givenTextStartOffset + numberOfSymbolsToCopy));
          givenTextStartOffset += numberOfSymbolsToCopy;
        }

        // Process escaped text (un-escape it).
        numberOfSymbolsToCopy = Math.min(escapedEndOffset, fileEndOffset) - Math.max(fileStartOffset, escapedStartOffset);
        if (numberOfSymbolsToCopy > 0) {
          textWasChanged = true;
          buffer.append(unescape(text.substring(givenTextStartOffset, givenTextStartOffset + numberOfSymbolsToCopy), element));
          givenTextStartOffset += numberOfSymbolsToCopy;
        }

        // Process text to the right of the escaped fragment (if any).
        numberOfSymbolsToCopy = Math.min(fileEndOffset, elementRange.getEndOffset()) - Math.max(fileStartOffset, escapedEndOffset);
        if (numberOfSymbolsToCopy > 0) {
          buffer.append(text.substring(givenTextStartOffset, givenTextStartOffset + numberOfSymbolsToCopy));
          givenTextStartOffset += numberOfSymbolsToCopy;
        }
      }
    }
    return textWasChanged ? buffer.toString() : null;
  }

  @Nonnull
  protected String unescape(String text, PsiElement token) {
    return unescapeStringCharacters(text);
  }

  @Override
  public String preprocessOnPaste(final Project project, final PsiFile file, final Editor editor, String text, final RawText rawText) {
    final Document document = editor.getDocument();
    PsiDocumentManager.getInstance(project).commitDocument(document);
    final SelectionModel selectionModel = editor.getSelectionModel();

    // pastes in block selection mode (column mode) are not handled by a CopyPasteProcessor
    final int selectionStart = selectionModel.getSelectionStart();
    final int selectionEnd = selectionModel.getSelectionEnd();
    PsiElement token = findLiteralTokenType(file, selectionStart, selectionEnd);
    if (token == null) {
      return text;
    }

    if (isStringLiteral(token)) {
      StringBuilder buffer = new StringBuilder(text.length());
      @NonNls String breaker = getLineBreaker(token);
      final String[] lines = LineTokenizer.tokenize(text.toCharArray(), false, true);
      for (int i = 0; i < lines.length; i++) {
        buffer.append(escapeCharCharacters(lines[i], token));
        if (i != lines.length - 1) {
          buffer.append(breaker);
        } else if (text.endsWith("\n")) {
          buffer.append("\\n");
        }
      }
      text = buffer.toString();
    } else if (isCharLiteral(token)) {
      return escapeCharCharacters(text, token);
    }
    return text;
  }

  protected String getLineBreaker(PsiElement token) {
    CodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getSettings(token.getProject());
    return codeStyleSettings.BINARY_OPERATION_SIGN_ON_NEXT_LINE ? "\\n\"\n+ \"" : "\\n\" +\n\"";
  }

  @Nullable
  protected PsiElement findLiteralTokenType(PsiFile file, int selectionStart, int selectionEnd) {
    final PsiElement elementAtSelectionStart = file.findElementAt(selectionStart);
    if (elementAtSelectionStart == null) {
      return null;
    }
    if (!isStringLiteral(elementAtSelectionStart) && !isCharLiteral(elementAtSelectionStart)) {
      return null;
    }

    if (elementAtSelectionStart.getTextRange().getEndOffset() < selectionEnd) {
      final PsiElement elementAtSelectionEnd = file.findElementAt(selectionEnd);
      if (elementAtSelectionEnd == null) {
        return null;
      }
      if (elementAtSelectionEnd.getNode().getElementType() == elementAtSelectionStart.getNode().getElementType() &&
          elementAtSelectionEnd.getTextRange().getStartOffset() < selectionEnd) {
        return elementAtSelectionStart;
      }
    }

    final TextRange textRange = elementAtSelectionStart.getTextRange();
    if (selectionStart <= textRange.getStartOffset() || selectionEnd >= textRange.getEndOffset()) {
      return null;
    }
    return elementAtSelectionStart;
  }

  protected boolean isCharLiteral(@Nonnull PsiElement token) {
    ASTNode node = token.getNode();
    return node != null && node.getElementType() == JavaTokenType.CHARACTER_LITERAL;
  }

  protected boolean isStringLiteral(@Nonnull PsiElement token) {
    ASTNode node = token.getNode();
    return node != null && node.getElementType() == JavaTokenType.STRING_LITERAL;
  }

  @Nonnull
  protected String escapeCharCharacters(@Nonnull String s, @Nonnull PsiElement token) {
    StringBuilder buffer = new StringBuilder();
    StringUtil.escapeStringCharacters(s.length(), s, isStringLiteral(token) ? "\"" : "\'", buffer);
    return buffer.toString();
  }
}
