package org.osmorc.manifest.lang;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;

/**
 * @author yole
 */
public class ManifestFileTypeFactory extends FileTypeFactory
{
	public void createFileTypes(@NotNull FileTypeConsumer consumer)
	{
		consumer.consume(ManifestFileType.INSTANCE);
		consumer.consume(BndFileType.INSTANCE);
	}
}
