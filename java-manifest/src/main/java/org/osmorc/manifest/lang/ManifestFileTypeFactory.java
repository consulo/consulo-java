package org.osmorc.manifest.lang;

import consulo.annotation.component.ExtensionImpl;
import consulo.java.manifest.lang.BndFileType;
import consulo.virtualFileSystem.fileType.FileTypeConsumer;
import consulo.virtualFileSystem.fileType.FileTypeFactory;
import jakarta.annotation.Nonnull;

/**
 * @author yole
 */
@ExtensionImpl
public class ManifestFileTypeFactory extends FileTypeFactory {
  public void createFileTypes(@Nonnull FileTypeConsumer consumer) {
    consumer.consume(ManifestFileType.INSTANCE);
    consumer.consume(BndFileType.INSTANCE);
  }
}
