package consulo.java.manifest.internal.header;

import consulo.annotation.component.ExtensionImpl;
import consulo.java.manifest.lang.headerparser.impl.SimpleHeaderParser;
import org.osmorc.manifest.lang.headerparser.ManifestHeaderParserContributor;
import org.osmorc.manifest.lang.headerparser.ManifestHeaderParserRegistrator;
import org.osmorc.manifest.lang.headerparser.impl.GenericComplexHeaderParser;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 16/12/2022
 */
@ExtensionImpl
public class DefaultManifestHeaderParserContributor implements ManifestHeaderParserContributor {
  @Override
  public void contribute(@Nonnull ManifestHeaderParserRegistrator registrator) {
    registrator.register("Created-By", new SimpleHeaderParser());
    registrator.register("Signature-Version", new SimpleHeaderParser());
    registrator.register("Class-Path", new SimpleHeaderParser());
    registrator.register("Implementation-Title", new SimpleHeaderParser());
    registrator.register("Implementation-Version", new SimpleHeaderParser());
    registrator.register("Implementation-Vendor", new SimpleHeaderParser());
    registrator.register("Implementation-Vendor-Id", new SimpleHeaderParser());
    registrator.register("Implementation-URL", new SimpleHeaderParser());
    registrator.register("Specification-Title", new SimpleHeaderParser());
    registrator.register("Specification-Version", new SimpleHeaderParser());
    registrator.register("Specification-Vendor", new SimpleHeaderParser());
    registrator.register("Sealed", new SimpleHeaderParser());
    registrator.register("Content-Type", new SimpleHeaderParser());
    registrator.register("Java-Bean", new SimpleHeaderParser());
    registrator.register("MD5-Digest", new SimpleHeaderParser());
    registrator.register("SHA-Digest", new SimpleHeaderParser());
    registrator.register("Magic", new SimpleHeaderParser());
    registrator.register("Name", new SimpleHeaderParser());
    registrator.register("Manifest-Version", new SimpleHeaderParser());

    // TODO [VISTALL] this is list of futures - that need replace to other plugins

    //[SpringDM] Spring futures
    registrator.register("Spring-Context", new GenericComplexHeaderParser());
    registrator.register("Import-Library", new GenericComplexHeaderParser());
    registrator.register("Library-SymbolicName", new GenericComplexHeaderParser());
    registrator.register("Library-Version", new GenericComplexHeaderParser());
    registrator.register("Library-Name", new GenericComplexHeaderParser());
    registrator.register("Library-Description", new GenericComplexHeaderParser());
    registrator.register("Import-Bundle", new GenericComplexHeaderParser());
    registrator.register("Application-TraceLevels", new GenericComplexHeaderParser());
    registrator.register("SpringExtender-Version", new SimpleHeaderParser());
    registrator.register("Module-Type", new SimpleHeaderParser());
    registrator.register("Web-ContextPath", new SimpleHeaderParser());
    registrator.register("Web-DispatcherServletUrlPatterns", new SimpleHeaderParser());
  }
}
