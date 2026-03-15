package org.osmorc.manifest.lang.headerparser;


/**
 * @author VISTALL
 * @since 16/12/2022
 */
public interface ManifestHeaderParserRegistrator {
  void register(String key, HeaderParser headerParser);
}
