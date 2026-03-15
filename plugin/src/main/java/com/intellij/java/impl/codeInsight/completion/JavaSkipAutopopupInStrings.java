package com.intellij.java.impl.codeInsight.completion;

import com.intellij.java.language.JavaLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.completion.SkipAutopopupInStrings;


/**
 * @author VISTALL
 * @since 14/12/2022
 */
@ExtensionImpl(id = "javaSkipAutopopupInStrings")
public class JavaSkipAutopopupInStrings extends SkipAutopopupInStrings {
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
