package com.intellij.java.impl.codeInsight.daemon.impl;

import com.intellij.java.language.JavaLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.annotation.Annotator;
import consulo.language.editor.annotation.AnnotatorFactory;

import org.jspecify.annotations.Nullable;

/**
 * @author VISTALL
 * @since 09/12/2022
 */
@ExtensionImpl
public class JavaDocAnnotatorFactory implements AnnotatorFactory {
  @Nullable
  @Override
  public Annotator createAnnotator() {
    return new JavaDocAnnotator();
  }

  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
