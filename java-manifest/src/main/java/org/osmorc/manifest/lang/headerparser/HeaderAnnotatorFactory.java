package org.osmorc.manifest.lang.headerparser;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.annotation.Annotator;
import consulo.language.editor.annotation.AnnotatorFactory;
import jakarta.annotation.Nonnull;
import org.osmorc.manifest.lang.ManifestLanguage;

import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 14/12/2022
 */
@ExtensionImpl
public class HeaderAnnotatorFactory implements AnnotatorFactory {
  @Nullable
  @Override
  public Annotator createAnnotator() {
    return new HeaderAnnotator();
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return ManifestLanguage.INSTANCE;
  }
}
