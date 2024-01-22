package com.intellij.java.impl.codeInsight.completion;

import com.intellij.java.language.JavaLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.completion.SkipAutopopupInComments;
import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 14/12/2022
 */
@ExtensionImpl(id = "javaComments")
public class JavadocSkipAutopopupInComments extends SkipAutopopupInComments {
  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
