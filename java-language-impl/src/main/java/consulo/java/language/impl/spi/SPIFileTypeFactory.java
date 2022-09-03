package consulo.java.language.impl.spi;

import consulo.virtualFileSystem.fileType.FileTypeConsumer;
import consulo.virtualFileSystem.fileType.FileTypeFactory;
import com.intellij.java.language.impl.spi.SPIFileType;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 2020-11-07
 */
public class SPIFileTypeFactory extends FileTypeFactory
{
	@Override
	public void createFileTypes(@Nonnull FileTypeConsumer fileTypeConsumer)
	{
		fileTypeConsumer.consume(SPIFileType.INSTANCE);
	}
}
