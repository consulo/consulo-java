// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.impl.codeInsight.completion;

import consulo.language.editor.completion.lookup.CharTailType;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.TailType;
import jakarta.annotation.Nonnull;

public class JavaTailTypes {
  private static final TailType HUMBLE_SPACE_BEFORE_WORD = new CharTailType(' ', false) {
    @Override
    public boolean isApplicable(@Nonnull InsertionContext context) {
      CharSequence text = context.getDocument().getCharsSequence();
      int tail = context.getTailOffset();
      if (text.length() > tail + 1 && text.charAt(tail) == ' ') {
        char ch = text.charAt(tail + 1);
        if (ch == '@' || Character.isLetter(ch)) {
          return false;
        }
      }
      return super.isApplicable(context);
    }

    @Override
    public String toString() {
      return "HUMBLE_SPACE_BEFORE_WORD";
    }
  };

  /**
   * insert a space unless there's one at the caret position already, followed by a word or '@'
   */
  public static TailType humbleSpaceBeforeWordType() {
    return HUMBLE_SPACE_BEFORE_WORD;
  }
}
