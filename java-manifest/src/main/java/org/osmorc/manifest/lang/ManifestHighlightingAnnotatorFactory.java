package org.osmorc.manifest.lang;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.language.Language;
import consulo.language.editor.annotation.Annotator;
import consulo.language.editor.annotation.AnnotatorFactory;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 14/12/2022
 */
@ExtensionImpl
public class ManifestHighlightingAnnotatorFactory implements AnnotatorFactory, DumbAware {
  @Nullable
  @Override
  public Annotator createAnnotator() {
    return new ManifestHighlightingAnnotator();
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return ManifestLanguage.INSTANCE;
  }
}
