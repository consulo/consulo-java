package com.intellij.java.impl.spi;

import com.intellij.java.language.spi.SPILanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.annotation.Annotator;
import consulo.language.editor.annotation.AnnotatorFactory;
import jakarta.annotation.Nonnull;

import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 10/12/2022
 */
@ExtensionImpl
public class SPIAnnotatorFactory implements AnnotatorFactory {
  @Nullable
  @Override
  public Annotator createAnnotator() {
    return new SPIAnnotator();
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return SPILanguage.INSTANCE;
  }
}
