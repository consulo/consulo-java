package org.osmorc.manifest.lang;

import javax.annotation.Nonnull;

import consulo.virtualFileSystem.fileType.FileTypeConsumer;
import consulo.virtualFileSystem.fileType.FileTypeFactory;
import consulo.java.manifest.lang.BndFileType;

/**
 * @author yole
 */
public class ManifestFileTypeFactory extends FileTypeFactory
{
	public void createFileTypes(@Nonnull FileTypeConsumer consumer)
	{
		consumer.consume(ManifestFileType.INSTANCE);
		consumer.consume(BndFileType.INSTANCE);
	}
}
