package com.intellij.java.impl.codeInsight.completion;

import com.intellij.java.language.JavaLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.completion.SkipAutopopupInStrings;

import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 14/12/2022
 */
@ExtensionImpl(id = "javaSkipAutopopupInStrings")
public class JavaSkipAutopopupInStrings extends SkipAutopopupInStrings {
  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
