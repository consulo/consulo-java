package org.osmorc.manifest.lang.headerparser;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 16/12/2022
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface ManifestHeaderParserContributor {
  void contribute(@Nonnull ManifestHeaderParserRegistrator registrator);
}
