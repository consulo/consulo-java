package org.osmorc.manifest.lang.headerparser;

import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 16/12/2022
 */
public interface ManifestHeaderParserRegistrator {
  void register(@Nonnull String key, @Nonnull HeaderParser headerParser);
}
