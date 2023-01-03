package consulo.java.manifest.internal.header;

import consulo.application.Application;
import consulo.component.extension.ExtensionPoint;
import consulo.component.extension.ExtensionPointCacheKey;
import org.osmorc.manifest.lang.headerparser.HeaderParser;
import org.osmorc.manifest.lang.headerparser.ManifestHeaderParserContributor;
import org.osmorc.manifest.lang.headerparser.ManifestHeaderParserRegistrator;
import org.osmorc.manifest.lang.headerparser.impl.GenericComplexHeaderParser;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author VISTALL
 * @since 16/12/2022
 */
public class ManifestHeaderParserRegistratorImpl implements ManifestHeaderParserRegistrator {
  private static final ExtensionPointCacheKey<ManifestHeaderParserContributor, ManifestHeaderParserRegistratorImpl> KEY =
    ExtensionPointCacheKey.create("ManifestHeaderParserRegistratorImpl", contributors -> {
      ManifestHeaderParserRegistratorImpl impl = new ManifestHeaderParserRegistratorImpl();
      for (ManifestHeaderParserContributor contributor : contributors) {
        contributor.contribute(impl);
      }
      return impl;
    });

  @Nonnull
  public static ManifestHeaderParserRegistratorImpl get() {
    ExtensionPoint<ManifestHeaderParserContributor> point =
      Application.get().getExtensionPoint(ManifestHeaderParserContributor.class);
    return point.getOrBuildCache(KEY);
  }

  private Map<String, HeaderParser> myParsers = new ConcurrentHashMap<>();

  private GenericComplexHeaderParser myDefaultParser = new GenericComplexHeaderParser();

  @Override
  public void register(@Nonnull String key, @Nonnull HeaderParser headerParser) {
    if (myParsers.containsKey(key)) {
      throw new IllegalArgumentException("Duplicate registration " + key);
    }
    myParsers.put(key, headerParser);
  }

  public Map<String, HeaderParser> getParsers() {
    return myParsers;
  }

  public GenericComplexHeaderParser getDefaultParser() {
    return myDefaultParser;
  }
}
