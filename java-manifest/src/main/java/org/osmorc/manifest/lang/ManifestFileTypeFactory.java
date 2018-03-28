package org.osmorc.manifest.lang;

import javax.annotation.Nonnull;

import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
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
